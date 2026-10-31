package com.bi1kbu.qslmanagement.api;

import com.bi1kbu.qslmanagement.extension.model.LogSyncJob;
import com.bi1kbu.qslmanagement.extension.model.LogSyncProfile;
import com.bi1kbu.qslmanagement.extension.model.QsoRecord;
import com.bi1kbu.qslmanagement.extension.model.StationEquipment;
import com.bi1kbu.qslmanagement.extension.model.StationProfile;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;

/**
 * 外部日志同步服务：配置管理、凭据管理、连通性测试与一次同步的完整编排。
 *
 * <p>职责边界：拉取交给 {@link QslLogSyncSource} 实现，字段映射交给 {@link QslLogSyncMapping}，
 * 本类只负责读配置、取凭据、判重、落库与记作业。
 */
@Service
public class QslLogSyncService {

    private static final ListOptions EMPTY_OPTIONS = ListOptions.builder().build();
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("metadata.creationTimestamp"));
    private static final String STATION_PROFILE_NAME = "qsl-station-profile-default";
    private static final String SECRET_ACCOUNT = "account";
    private static final String SECRET_PASSWORD = "password";
    private static final String SECRET_NAME_PREFIX = "qsl-log-sync-credential-";
    private static final String SECRET_DISPLAY_PREFIX = "QSL 外部日志同步凭据：";
    private static final Pattern QSO_RECORD_NAME = Pattern.compile("^QSO(\\d+)$");
    private static final Pattern RESOURCE_NAME_SAFE = Pattern.compile("[^a-z0-9.-]+");

    private static final int MAX_ERROR_LINES = 200;
    private static final int MAX_JOB_HISTORY = 20;
    private static final int DEFAULT_MAX_RECORDS = 5000;
    private static final int MIN_SYNC_INTERVAL_MINUTES = 60;
    private static final int MAX_PAGES_PER_SYNC = 40;
    private static final long STALE_JOB_MINUTES = 10;

    private static final String JOB_RUNNING = "RUNNING";
    private static final String JOB_SUCCESS = "SUCCESS";
    private static final String JOB_PARTIAL = "PARTIAL";
    private static final String JOB_FAILED = "FAILED";
    private static final String FORCE_STOP_MESSAGE = "作业已被强制停止：上一次同步没有正常收尾";
    private static final String INTERRUPTED_MESSAGE =
        "作业执行被中断（插件重启或请求被取消），已标记为失败，可重新发起同步";

    /** 定时同步在审计与作业里显示的操作员，区别于控制台人工操作。 */
    public static final String SCHEDULE_OPERATOR = "外部日志同步（定时）";
    /** 定时同步没有真实客户端 IP，留标记值便于审计区分。 */
    private static final String SCHEDULED_CLIENT_IP = "scheduler";

    private final ReactiveExtensionClient client;
    private final QslAuditService auditService;
    private final Map<String, QslLogSyncSource> sources;
    /**
     * 每个账号上一次发起请求的时间，用于执行数据源自己的最小请求间隔。
     *
     * <p>LoTW 对同一用户限一分钟一次下载，超限返回 503 限流页并连带后续重试全部失败。
     * 这道闸拦在「发起请求之前」，同步、连通性测试、定时任务共用它，所以手动连点也不会
     * 把上游打到限流。进程内状态即可：重启后一次无人问津的请求不会造成伤害。
     */
    private final Map<String, Instant> lastRequestAt = new ConcurrentHashMap<>();

    public QslLogSyncService(
        ReactiveExtensionClient client,
        QslAuditService auditService,
        List<QslLogSyncSource> sourceList
    ) {
        this.client = client;
        this.auditService = auditService;
        var registry = new LinkedHashMap<String, QslLogSyncSource>();
        for (var source : sourceList) {
            registry.put(QslLogSyncSource.normalizeSourceType(source.sourceType()), source);
        }
        this.sources = registry;
    }

    /**
     * 把拉取到的记录压成「呼号 · 日期 时间 · 模式 · 频率」样例行，供页面确认上游到底返回了什么。
     *
     * <p>只取前 20 条：这是给人看的诊断信息，不是数据接口；上游返回几万条也只需要知道头几条长什么样。
     * 记录缺少呼号或日期时照样列出来，用占位符表示，避免「上游有数据但我们全判失败」时页面上是空的。
     */
    private static List<String> summarizeRecords(List<Map<String, String>> records) {
        var samples = new ArrayList<String>();
        for (var record : records) {
            if (samples.size() >= 20) {
                break;
            }
            var callSign = firstNonBlank(record.get("CALL"), "-");
            var date = firstNonBlank(record.get("QSO_DATE"), "-");
            var time = firstNonBlank(record.get("TIME_ON"), record.get("TIME_OFF"));
            var mode = firstNonBlank(record.get("MODE"), record.get("APP_LOTW_MODE"));
            var freq = firstNonBlank(record.get("FREQ"), record.get("BAND"));
            var parts = new ArrayList<String>();
            parts.add(callSign);
            parts.add(time.isBlank() ? date : date + " " + time);
            if (!mode.isBlank()) {
                parts.add(mode);
            }
            if (!freq.isBlank()) {
                parts.add(freq);
            }
            samples.add(String.join(" · ", parts));
        }
        return samples;
    }

    /** 可用数据源清单，供页面下拉。 */
    public List<SourceDescriptor> supportedSources() {
        return List.of(
            new SourceDescriptor(
                QslLotwLogSyncSource.SOURCE_TYPE,
                "ARRL LoTW",
                true,
                "用 ARRL 账号与密码下载 ADIF；该接口非 ARRL 官方文档化接口，改版即可能失效。"
            ),
            new SourceDescriptor(
                QslQrzLogbookSyncSource.SOURCE_TYPE,
                "QRZ Logbook",
                true,
                "需要 QRZ Logbook 的 API Key，且账号需具备取数（FETCH）订阅。"
            )
        );
    }

    /** 列出全部配置，并标注凭据是否已保存（不回传凭据本体）。 */
    public Mono<List<ProfileView>> listProfiles() {
        return client.listAll(LogSyncProfile.class, EMPTY_OPTIONS, DEFAULT_SORT)
            .concatMap(this::toView)
            .collectList();
    }

    /** 列出某条配置最近的同步作业，按创建时间倒序。 */
    public Mono<List<JobView>> listJobs(String profileName) {
        var normalized = profileName == null ? "" : profileName.trim();
        if (normalized.isBlank()) {
            return Mono.error(new QslApiException(HttpStatus.BAD_REQUEST, "QSL-400-LS-0002",
                "缺少配置名称"));
        }
        return client.listAll(LogSyncJob.class, EMPTY_OPTIONS, DEFAULT_SORT)
            .filter(job -> job.getSpec() != null && normalized.equals(job.getSpec().getProfileName()))
            .take(MAX_JOB_HISTORY)
            .map(job -> JobView.from(job, ""))
            .collectList();
    }

    /** 新建或更新一条配置。 */
    public Mono<ProfileView> saveProfile(SaveProfileCommand command, String operator, String clientIp) {
        if (command == null) {
            return Mono.error(new QslApiException(HttpStatus.BAD_REQUEST, "QSL-400-LS-0002",
                "缺少配置内容"));
        }
        var sourceType = QslLogSyncSource.normalizeSourceType(command.sourceType());
        if (!sources.containsKey(sourceType)) {
            return Mono.error(new QslApiException(HttpStatus.BAD_REQUEST, "QSL-400-LS-0001",
                "不支持的数据源类型：" + sourceType));
        }
        var callSign = QslApiSupport.normalizeCallSign(command.myCallSign());
        if (callSign.isBlank()) {
            return Mono.error(new QslApiException(HttpStatus.BAD_REQUEST, "QSL-400-LS-0002",
                "本台呼号不能为空"));
        }
        var profileName = buildProfileName(sourceType, callSign);
        return client.fetch(LogSyncProfile.class, profileName)
            .flatMap(existing -> {
                applySpec(existing.getSpec(), command, sourceType, callSign);
                return client.update(existing);
            })
            .switchIfEmpty(Mono.defer(() -> {
                var created = new LogSyncProfile();
                created.setMetadata(QslApiSupport.createMetadata(profileName));
                var spec = new LogSyncProfile.LogSyncProfileSpec();
                applySpec(spec, command, sourceType, callSign);
                created.setSpec(spec);
                created.setStatus(new LogSyncProfile.LogSyncProfileStatus());
                return client.create(created);
            }))
            .flatMap(saved -> auditService.appendAuditLog(
                    "保存外部日志同步配置",
                    "LogSyncProfile",
                    saved.getMetadata().getName(),
                    sourceType + " / " + callSign,
                    operator,
                    clientIp
                ).thenReturn(saved))
            .flatMap(this::toView);
    }

    /**
     * 清空游标，让下一次同步按全量重新拉取。
     *
     * <p>存在的原因：游标一旦写错（例如早期版本因为缺少 {@code qso_qsorxsince} 只拉到最新一条，
     * 却把那条的时间当成了「已同步到此」），后续每次增量都只会在那个错误起点之后取数，
     * 用正常操作永远补不回历史记录。这个接口把起点退回初始状态，不删任何已同步的通联记录。
     */
    public Mono<ProfileView> resetCursor(String profileName, String operator, String clientIp) {
        return fetchProfile(profileName)
            .flatMap(profile -> ensureNoRunningJob(profileName).thenReturn(profile))
            .flatMap(profile -> {
                var status = profile.getStatus() == null
                    ? new LogSyncProfile.LogSyncProfileStatus()
                    : profile.getStatus();
                status.setLastCursor("");
                status.setLastError("");
                status.setConsecutiveFailures(0);
                profile.setStatus(status);
                return client.update(profile);
            })
            .flatMap(saved -> auditService.appendAuditLog(
                    "重置外部日志同步游标",
                    "LogSyncProfile",
                    saved.getMetadata().getName(),
                    "下次同步按全量重新拉取",
                    operator,
                    clientIp
                ).thenReturn(saved))
            .flatMap(this::toView);
    }

    /** 保存账号密码：凭据只进 Halo Secret，配置里只记 Secret 名称。 */
    public Mono<ProfileView> saveCredential(
        String profileName,
        SaveCredentialCommand command,
        String operator,
        String clientIp
    ) {
        return fetchProfile(profileName)
            .flatMap(profile -> {
                var account = command == null || command.accountName() == null
                    ? ""
                    : command.accountName().trim();
                var password = command == null || command.password() == null
                    ? ""
                    : command.password();
                if (account.isBlank() && password.isBlank()) {
                    return Mono.error(new QslApiException(HttpStatus.BAD_REQUEST, "QSL-400-LS-0003",
                        "账号与密码至少填写一项"));
                }
                var secretName = secretNameFor(profileName);
                var passwordRequired = requiresPassword(profile.getSpec() == null
                    ? null
                    : profile.getSpec().getSourceType());
                return loadSecret(secretName)
                    .flatMap(current -> upsertSecret(secretName, profileName, current, account, password,
                        passwordRequired))
                    // 首次保存时 Secret 还不存在，loadSecret 发的是空流；若不在这里补一次创建，
                    // flatMap 永远不执行，凭据就永远写不进去（表现为「保存成功但一直显示未配置」）。
                    .switchIfEmpty(Mono.defer(() -> upsertSecret(secretName, profileName, null, account,
                        password, passwordRequired)))
                    .then(Mono.defer(() -> {
                        var spec = profile.getSpec() == null
                            ? new LogSyncProfile.LogSyncProfileSpec()
                            : profile.getSpec();
                        if (!account.isBlank()) {
                            spec.setAccountName(account);
                        }
                        profile.setSpec(spec);
                        return client.update(profile);
                    }));
            })
            .flatMap(saved -> auditService.appendAuditLog(
                    "保存外部日志同步凭据",
                    "LogSyncProfile",
                    saved.getMetadata().getName(),
                    "凭据已写入 Secret，接口不回传内容",
                    operator,
                    clientIp
                ).thenReturn(saved))
            .flatMap(this::toView);
    }

    /**
     * 删除配置：连带删除凭据 Secret 与该配置的历史同步作业。
     *
     * <p>先拦运行中的作业——同步进行中删掉配置，作业会写回一个已经不存在的配置，
     * 部分更新还会把已删资源“复活”成半成品。历史作业一并删除，否则作业表里会留下一堆
     * 指向不存在配置的孤儿记录。
     */
    public Mono<Void> deleteProfile(String profileName, String operator, String clientIp) {
        return fetchProfile(profileName)
            .flatMap(profile -> ensureNoRunningJob(profileName)
                // 用 defer 包住：否则内层 Mono 在装配阶段就会去 fetch，删除前的检查形同虚设。
                .then(Mono.defer(() -> deleteSecret(secretName(profile))))
                .then(Mono.defer(() -> deleteJobsOfProfile(profileName)))
                .then(Mono.defer(() -> client.delete(profile)))
                .then(Mono.defer(() -> auditService.appendAuditLog(
                    "删除外部日志同步配置",
                    "LogSyncProfile",
                    profileName,
                    "配置、凭据与历史作业已一并删除",
                    operator,
                    clientIp
                ))));
    }

    private Mono<Void> deleteSecret(String targetSecretName) {
        return loadSecret(targetSecretName)
            .flatMap(secret -> client.delete(secret))
            .then();
    }

    private Mono<Void> deleteJobsOfProfile(String profileName) {
        return client.listAll(LogSyncJob.class, EMPTY_OPTIONS, DEFAULT_SORT)
            .filter(job -> job.getSpec() != null && profileName.equals(job.getSpec().getProfileName()))
            .concatMap(job -> client.delete(job))
            .then();
    }

    /** 清空凭据：只删 Secret 里的两个键，不删除配置本身。 */
    public Mono<ProfileView> clearCredential(String profileName, String operator, String clientIp) {
        return fetchProfile(profileName)
            .flatMap(profile -> {
                var secretName = secretNameFor(profileName);
                return loadSecret(secretName)
                    .flatMap(secret -> {
                        if (secret.getStringData() != null) {
                            secret.setStringData(new LinkedHashMap<>());
                        }
                        secret.setData(null);
                        return client.update(secret);
                    })
                    .switchIfEmpty(Mono.empty())
                    .then(Mono.just(profile));
            })
            .flatMap(saved -> auditService.appendAuditLog(
                    "清空外部日志同步凭据",
                    "LogSyncProfile",
                    saved.getMetadata().getName(),
                    "凭据已清空",
                    operator,
                    clientIp
                ).thenReturn(saved))
            .flatMap(this::toView);
    }

    /** 连通性测试：真实拉一小段 ADIF，但不落库。 */
    public Mono<TestResult> testConnection(String profileName, String operator, String clientIp) {
        return fetchProfile(profileName)
            .flatMap(profile -> {
                var spec = profile.getSpec();
                var sourceType = QslLogSyncSource.normalizeSourceType(spec.getSourceType());
                var source = sources.get(sourceType);
                if (source == null) {
                    return Mono.error(new QslApiException(HttpStatus.BAD_REQUEST, "QSL-400-LS-0001",
                        "不支持的数据源类型：" + sourceType));
                }
                return loadCredentials(profile, source.requiresPassword())
                    .flatMap(credentials -> ensureRequestInterval(source, credentials.account())
                        .then(Mono.defer(() -> {
                            markRequestSent(source, credentials.account());
                            return source.fetch(new QslLogSyncSource.FetchRequest(
                                spec.getMyCallSign(),
                                credentials.account(),
                                credentials.password(),
                                "",
                                5
                            ));
                        })))
                    .map(result -> {
                        var records = QslAdifReader.parseRecords(result.adif());
                        return new TestResult(
                            true,
                            "连接成功，可读到 " + records.size() + " 条记录",
                            records.size(),
                            QslApiSupport.nowText(),
                            summarizeRecords(records)
                        );
                    });
            })
            .onErrorResume(QslApiException.class, error -> Mono.just(new TestResult(
                false,
                error.getMessage(),
                0,
                QslApiSupport.nowText(),
                List.of()
            )))
            .flatMap(result -> auditService.appendAuditLog(
                    "测试外部日志源连接",
                    "LogSyncProfile",
                    profileName,
                    result.message(),
                    operator,
                    clientIp
                ).thenReturn(result));
    }

    /** 执行一次同步。 */
    public Mono<JobView> runSync(String profileName, String triggerType, String operator, String clientIp) {
        var trigger = "SCHEDULED".equalsIgnoreCase(triggerType) ? "SCHEDULED" : "MANUAL";
        return fetchProfile(profileName)
            .flatMap(profile -> ensureNoRunningJob(profileName).thenReturn(profile))
            .flatMap(this::requireEnabled)
            .flatMap(profile -> createJob(profile, trigger)
                .flatMap(job -> runSyncForJob(profile, job)));
    }

    /**
     * 立即触发一次同步，但只等到作业创建成功就返回。
     *
     * <p>拉取不能挂在请求上：LoTW 整份报表可能耗时数十秒，页面刷新或关闭会让
     * 请求被取消，链一取消作业就永远停在 RUNNING——页面一直显示「进行中」，
     * 还会挡住重置游标与删除配置。改成后台执行后，作业由服务端自己收尾，
     * 页面刷新作业表即可看到结果。</p>
     */
    public Mono<JobView> startSync(String profileName, String triggerType, String operator,
        String clientIp) {
        var trigger = "SCHEDULED".equalsIgnoreCase(triggerType) ? "SCHEDULED" : "MANUAL";
        return fetchProfile(profileName)
            .flatMap(profile -> ensureNoRunningJob(profileName).thenReturn(profile))
            .flatMap(this::requireEnabled)
            .flatMap(profile -> createJob(profile, trigger)
                .flatMap(job -> {
                    runSyncForJob(profile, job)
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe(
                            view -> {
                            },
                            error -> {
                                // 失败原因已由 finishJobWithError 写回作业与配置，这里不再上抛。
                            }
                        );
                    return Mono.just(JobView.from(job, "任务已启动，稍后刷新作业表查看结果"));
                }));
    }

    private Mono<JobView> runSyncForJob(LogSyncProfile profile, LogSyncJob job) {
        return executeSync(profile, job)
            .onErrorResume(error -> finishJobWithError(profile, job, error));
    }

    private Mono<LogSyncProfile> requireEnabled(LogSyncProfile profile) {
        var spec = profile.getSpec();
        if (!Boolean.FALSE.equals(spec.getEnabled())) {
            return Mono.just(profile);
        }
        return Mono.error(new QslApiException(HttpStatus.BAD_REQUEST, "QSL-400-LS-0004",
            "该配置已停用，不能同步"));
    }

    /**
     * 强制停止该配置下进行中的作业。
     *
     * <p>用于收尾卡住的作业：作业停在 RUNNING 时「重置游标」与「删除配置」都会被
     * 并发保护挡住，用户必须有一个自救入口，否则只能等十分钟的陈旧判定或重启插件。</p>
     */
    public Mono<JobView> forceStop(String profileName, String operator, String clientIp) {
        return fetchProfile(profileName)
            .flatMap(profile -> client.listAll(LogSyncJob.class, EMPTY_OPTIONS, DEFAULT_SORT)
                .filter(job -> profileName.equals(
                    job.getSpec() == null ? "" : job.getSpec().getProfileName()))
                .filter(this::isRunning)
                .collectList()
                .flatMap(jobs -> {
                    if (jobs.isEmpty()) {
                        return Mono.error(new QslApiException(HttpStatus.CONFLICT,
                            "QSL-409-LS-0002", "该配置没有正在进行的同步作业"));
                    }
                    return Flux.fromIterable(jobs)
                        .concatMap(job -> markJobInterrupted(job, FORCE_STOP_MESSAGE))
                        .collectList()
                        .flatMap(saved -> auditService.appendAuditLog(
                                "强制停止外部日志同步",
                                "LogSyncProfile",
                                profileName,
                                "停止 " + saved.size() + " 个进行中的作业",
                                operator == null ? "" : operator,
                                clientIp == null ? "" : clientIp
                            )
                            .thenReturn(JobView.from(saved.get(0), FORCE_STOP_MESSAGE)));
                }));
    }

    /**
     * 插件启动时收尾遗留的进行中作业。
     *
     * <p>进程重启后不可能还有作业在跑，但持久化状态不会自己变——不收尾的话页面上
     * 会永远留着一条「进行中」，并持续挡住重置游标与删除配置。</p>
     */
    public Mono<Void> reconcileRunningJobs() {
        return client.listAll(LogSyncJob.class, EMPTY_OPTIONS, DEFAULT_SORT)
            .filter(this::isRunning)
            .concatMap(job -> markJobInterrupted(job, INTERRUPTED_MESSAGE))
            .then()
            .onErrorResume(error -> Mono.empty());
    }

    private boolean isRunning(LogSyncJob job) {
        return job.getStatus() != null && JOB_RUNNING.equals(job.getStatus().getStatus());
    }

    private Mono<LogSyncJob> markJobInterrupted(LogSyncJob job, String message) {
        var jobStatus = job.getStatus() == null
            ? new LogSyncJob.LogSyncJobStatus()
            : job.getStatus();
        jobStatus.setStatus(JOB_FAILED);
        jobStatus.setErrorSummary(message);
        jobStatus.setErrorLines(new ArrayList<>(List.of(message)));
        jobStatus.setFinishedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        job.setStatus(jobStatus);
        return saveJob(job);
    }

    /**
     * 保存作业，冲突时重新取最新版本再写。
     *
     * <p>收尾写入若因乐观锁冲突失败，作业会永远停在 RUNNING——这类故障只影响
     * 页面显示，却会连带挡住重置游标和删除配置，所以宁可贵一次重取。</p>
     */
    private Mono<LogSyncJob> saveJob(LogSyncJob job) {
        var jobName = job.getMetadata().getName();
        return client.update(job)
            .onErrorResume(error -> client.fetch(LogSyncJob.class, jobName)
                .map(fresh -> {
                    fresh.setSpec(job.getSpec());
                    fresh.setStatus(job.getStatus());
                    return fresh;
                })
                .flatMap(client::update));
    }

    /**
     * 定时同步入口：把所有「已启用 + 开启自动同步 + 已到间隔」的配置各跑一次。
     *
     * <p>设计约束（Halo 官方定时任务文档）：任务必须幂等——间隔判断只看持久化的
     * {@code status.lastSyncAt}，不看内存状态；多副本部署时用「已有 RUNNING 作业」
     * 做互斥，第二个副本会被跳过而不是重复拉取。任何单个配置失败都不影响其余配置，
     * 也不让异常冒泡到调度器。</p>
     */
    public Mono<Void> runDueSyncs() {
        return client.listAll(LogSyncProfile.class, EMPTY_OPTIONS, DEFAULT_SORT)
            .filter(this::isDueForScheduledSync)
            .concatMap(profile -> {
                var profileName = profile.getMetadata().getName();
                return ensureNoRunningJob(profileName)
                    .then(Mono.defer(() -> runSync(
                        profileName,
                        "SCHEDULED",
                        SCHEDULE_OPERATOR,
                        SCHEDULED_CLIENT_IP
                    )))
                    // 失败原因已由 runSync 内部写回配置，这里只吞掉异常，不让它冒泡到调度器。
                    .onErrorResume(error -> Mono.<JobView>empty())
                    .then();
            })
            .then();
    }

    /** 该配置本轮是否该由定时任务执行。 */
    private boolean isDueForScheduledSync(LogSyncProfile profile) {
        return isDueForScheduledSync(profile, Instant.now());
    }

    /**
     * 到期判定（纯函数，便于测试）。
     *
     * @param profile 同步配置
     * @param now 当前时间
     * @return 已启用、已开启自动同步、且距上次同步已超过配置间隔时为真
     */
    static boolean isDueForScheduledSync(LogSyncProfile profile, Instant now) {
        var spec = profile == null ? null : profile.getSpec();
        if (spec == null) {
            return false;
        }
        if (Boolean.FALSE.equals(spec.getEnabled()) || !Boolean.TRUE.equals(spec.getAutoSyncEnabled())) {
            return false;
        }
        var status = profile.getStatus();
        var lastSyncAt = status == null || status.getLastSyncAt() == null
            ? ""
            : status.getLastSyncAt().trim();
        if (lastSyncAt.isBlank()) {
            // 从未同步过：视为到期（首次配置后不必等一个间隔）。
            return true;
        }
        var interval = spec.getSyncIntervalMinutes() == null
            ? MIN_SYNC_INTERVAL_MINUTES
            : Math.max(MIN_SYNC_INTERVAL_MINUTES, spec.getSyncIntervalMinutes());
        try {
            return !Instant.parse(lastSyncAt).plus(interval, ChronoUnit.MINUTES).isAfter(now);
        } catch (RuntimeException error) {
            // 时间戳损坏时按「该跑」处理，让下一次成功同步把它修正掉。
            return true;
        }
    }

    /**
     * 记录一次由定时任务触发的失败。
     *
     * <p>把 {@code lastSyncAt} 一起推到当前时间，是为了让失败也遵守同步间隔——
     * 否则上游一直不可用时会每轮重试，变成对第三方站点的连续冲击。
     * 「已有同步在进行」的并发冲突不记为失败（那是正常互斥）。</p>
     */
    private Mono<Void> recordProfileFailure(LogSyncProfile profile, Throwable error) {
        if (error instanceof QslApiException apiError
            && "QSL-409-LS-0001".equals(apiError.getCode())) {
            return Mono.empty();
        }
        var status = profile.getStatus() == null
            ? new LogSyncProfile.LogSyncProfileStatus()
            : profile.getStatus();
        status.setLastSyncAt(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        status.setLastError(sanitize(error));
        status.setConsecutiveFailures(
            status.getConsecutiveFailures() == null ? 1 : status.getConsecutiveFailures() + 1
        );
        profile.setStatus(status);
        return client.update(profile).then();
    }

    private Mono<JobView> executeSync(LogSyncProfile profile, LogSyncJob job) {
        var spec = profile.getSpec();
        var sourceType = QslLogSyncSource.normalizeSourceType(spec.getSourceType());
        var source = sources.get(sourceType);
        if (source == null) {
            return finishJobWithError(profile, job, new QslApiException(HttpStatus.BAD_REQUEST,
                "QSL-400-LS-0001", "不支持的数据源类型：" + sourceType));
        }
        var maxRecords = spec.getMaxRecordsPerSync() == null || spec.getMaxRecordsPerSync() <= 0
            ? DEFAULT_MAX_RECORDS
            : spec.getMaxRecordsPerSync();
        var conflictStrategy = "OVERWRITE".equalsIgnoreCase(spec.getConflictStrategy())
            ? "OVERWRITE"
            : "SKIP";
        return Mono.zip(
                loadCredentials(profile, source.requiresPassword()),
                loadStationContext(profile),
                buildDuplicateIndex()
            )
            .flatMap(tuple -> {
                var credentials = tuple.getT1();
                var mappingContext = tuple.getT2();
                var index = tuple.getT3();
                var counters = new SyncCounters(maxRecords);
                var cursorStart = profile.getStatus() == null
                    || profile.getStatus().getLastCursor() == null
                    ? ""
                    : profile.getStatus().getLastCursor();
                return fetchAllPages(
                    source,
                    profile,
                    credentials,
                    cursorStart,
                    maxRecords,
                    mappingContext,
                    conflictStrategy,
                    index,
                    counters
                )
                    .then(Mono.defer(() -> {
                        var jobStatus = job.getStatus();
                        jobStatus.setStatus(counters.failed > 0 && counters.created + counters.updated == 0
                            ? JOB_FAILED
                            : counters.failed > 0 ? JOB_PARTIAL : JOB_SUCCESS);
                        jobStatus.setFetchedCount(counters.fetched);
                        jobStatus.setCreatedCount(counters.created);
                        jobStatus.setUpdatedCount(counters.updated);
                        jobStatus.setSkippedCount(counters.skipped);
                        jobStatus.setFailedCount(counters.failed);
                        jobStatus.setErrorLines(truncateErrors(counters.errors));
                        jobStatus.setErrorSummary(counters.errors.isEmpty()
                            ? ""
                            : "失败 " + counters.failed + " 条，明细见失败行");
                        jobStatus.setFinishedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
                        job.setStatus(jobStatus);
                        job.getSpec().setCursorTo(counters.cursorTo);
                        return saveJob(job);
                    }))
                    .flatMap(savedJob -> updateProfileAfterSync(profile, counters).thenReturn(savedJob))
                    .flatMap(savedJob -> auditService.appendAuditLog(
                            "执行外部日志同步",
                            "LogSyncProfile",
                            profile.getMetadata().getName(),
                            "源 " + sourceType + "，拉取 " + counters.fetched + "，新增 " + counters.created
                                + "，更新 " + counters.updated + "，跳过 " + counters.skipped
                                + "，失败 " + counters.failed,
                            "外部日志同步",
                            "plugin"
                        )
                        .thenReturn(JobView.from(savedJob, "任务已启动")));
            })
            .onErrorResume(error -> finishJobWithError(profile, job, error));
    }

    private Mono<Void> fetchAllPages(
        QslLogSyncSource source,
        LogSyncProfile profile,
        Credentials credentials,
        String cursor,
        int maxRecords,
        QslLogSyncMapping.MappingContext mappingContext,
        String conflictStrategy,
        DuplicateIndex index,
        SyncCounters counters
    ) {
        return fetchPage(
            source,
            profile,
            credentials,
            cursor,
            maxRecords,
            mappingContext,
            conflictStrategy,
            index,
            counters,
            1
        );
    }

    private Mono<Void> fetchPage(
        QslLogSyncSource source,
        LogSyncProfile profile,
        Credentials credentials,
        String cursor,
        int maxRecords,
        QslLogSyncMapping.MappingContext mappingContext,
        String conflictStrategy,
        DuplicateIndex index,
        SyncCounters counters,
        int page
    ) {
        if (page > MAX_PAGES_PER_SYNC || counters.fetched >= maxRecords) {
            return Mono.empty();
        }
        var spec = profile.getSpec();
        return ensureRequestInterval(source, credentials.account())
            .then(Mono.defer(() -> {
                markRequestSent(source, credentials.account());
                return source.fetch(new QslLogSyncSource.FetchRequest(
                    spec.getMyCallSign(),
                    credentials.account(),
                    credentials.password(),
                    cursor,
                    (int) Math.min(250L, (long) maxRecords - counters.fetched)
                ));
            }))
            .flatMap(result -> {
                if (!result.cursorTo().isBlank()) {
                    counters.cursorTo = result.cursorTo();
                }
                var records = QslAdifReader.parseRecords(result.adif());
                if (records.isEmpty()) {
                    return Mono.empty();
                }
                return Flux.fromIterable(records)
                    .concatMap(record -> applyRecord(
                        record,
                        profile,
                        mappingContext,
                        conflictStrategy,
                        index,
                        counters
                    ))
                    .then(Mono.defer(() -> {
                        var nextCursor = counters.cursorTo;
                        if (!source.supportsPaging() || nextCursor.isBlank() || nextCursor.equals(cursor)
                            || counters.fetched >= maxRecords || records.size() < 250) {
                            return Mono.empty();
                        }
                        return fetchPage(
                            source,
                            profile,
                            credentials,
                            nextCursor,
                            maxRecords,
                            mappingContext,
                            conflictStrategy,
                            index,
                            counters,
                            page + 1
                        );
                    }));
            });
    }

    private Mono<Void> applyRecord(
        Map<String, String> record,
        LogSyncProfile profile,
        QslLogSyncMapping.MappingContext mappingContext,
        String conflictStrategy,
        DuplicateIndex index,
        SyncCounters counters
    ) {
        counters.fetched++;
        var mapped = QslLogSyncMapping.map(record, mappingContext);
        if (!mapped.isSuccess()) {
            counters.failed++;
            counters.errors.add(mapped.error() + "（" + shortRecord(record) + "）");
            return Mono.empty();
        }
        var qso = mapped.qso();
        var key = qso.duplicateKey();
        var existing = index.byKey().get(key);
        if (existing != null) {
            var existingSource = QslLogSyncSource.normalizeSourceType(
                existing.getStatus() == null ? "" : existing.getStatus().getSource()
            );
            var sameSource = existingSource.equals(mappingContext.sourceType());
            if (!"OVERWRITE".equals(conflictStrategy) || !sameSource) {
                counters.skipped++;
                return Mono.empty();
            }
            applyOverwrite(existing.getSpec(), qso);
            return client.update(existing)
                .doOnNext(updated -> counters.updated++)
                .then();
        }
        if (counters.created >= counters.maxRecords) {
            counters.skipped++;
            return Mono.empty();
        }
        var created = new QsoRecord();
        created.setMetadata(QslApiSupport.createMetadata(counters.nextRecordName(index.startSequence())));
        var spec = new QsoRecord.QsoRecordSpec();
        applyNew(spec, qso);
        created.setSpec(spec);
        var status = new QsoRecord.QsoRecordStatus();
        status.setAutoCreated(Boolean.TRUE);
        status.setSource(mappingContext.sourceType());
        created.setStatus(status);
        return client.create(created)
            .doOnNext(saved -> {
                counters.created++;
                index.byKey().put(key, saved);
            })
            .then();
    }

    private void applyNew(QsoRecord.QsoRecordSpec spec, QslLogSyncMapping.MappedQso qso) {
        spec.setSceneType(qso.sceneType());
        spec.setCallSign(qso.callSign());
        spec.setDate(qso.date());
        spec.setTime(qso.time());
        spec.setTimezone(qso.timezone());
        spec.setFreq(qso.freq());
        spec.setMyRigMode(qso.myRigMode());
        spec.setRstSent(qso.rstSent());
        spec.setRstRcvd(qso.rstRcvd());
        spec.setOperator(qso.operator());
        spec.setMyRig(qso.myRig());
        spec.setMyRigAnt(qso.myRigAnt());
        spec.setMyQth(qso.myQth());
        spec.setQth(qso.qth());
        spec.setRemarks(qso.remarks());
    }

    /** 覆盖时只动 ADIF 能可靠映射的字段，保留备注与设备/天线等人工字段。 */
    private void applyOverwrite(QsoRecord.QsoRecordSpec spec, QslLogSyncMapping.MappedQso qso) {
        spec.setCallSign(qso.callSign());
        spec.setDate(qso.date());
        spec.setTime(qso.time());
        spec.setTimezone(qso.timezone());
        spec.setFreq(qso.freq());
        spec.setMyRigMode(qso.myRigMode());
        spec.setRstSent(qso.rstSent());
        spec.setRstRcvd(qso.rstRcvd());
        spec.setOperator(qso.operator());
        spec.setMyQth(qso.myQth());
        spec.setQth(qso.qth());
    }

    private Mono<LogSyncJob> createJob(LogSyncProfile profile, String trigger) {
        var spec = profile.getSpec();
        var job = new LogSyncJob();
        job.setMetadata(QslApiSupport.createMetadata(QslApiSupport.createResourceName("log-sync-job")));
        var jobSpec = new LogSyncJob.LogSyncJobSpec();
        jobSpec.setProfileName(profile.getMetadata().getName());
        jobSpec.setSourceType(spec.getSourceType());
        jobSpec.setMyCallSign(spec.getMyCallSign());
        jobSpec.setTriggerType(trigger);
        jobSpec.setCursorFrom(profile.getStatus() == null || profile.getStatus().getLastCursor() == null
            ? ""
            : profile.getStatus().getLastCursor());
        jobSpec.setMaxRecords(spec.getMaxRecordsPerSync() == null
            ? DEFAULT_MAX_RECORDS
            : spec.getMaxRecordsPerSync());
        job.setSpec(jobSpec);
        var jobStatus = new LogSyncJob.LogSyncJobStatus();
        jobStatus.setStatus(JOB_RUNNING);
        jobStatus.setFetchedCount(0L);
        jobStatus.setCreatedCount(0L);
        jobStatus.setUpdatedCount(0L);
        jobStatus.setSkippedCount(0L);
        jobStatus.setFailedCount(0L);
        jobStatus.setStartedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        job.setStatus(jobStatus);
        return client.create(job);
    }

    /**
     * 把一次失败的同步落盘：作业置为 FAILED 并留下原因，同时把原因写回配置。
     *
     * <p>写回配置这一步不能省——否则页面上只有作业表格里一个 FAILED，用户看不到
     * 到底为什么失败（凭据没配、LoTW 返回登录页、网络超时都是不同处置方式）。</p>
     */
    private Mono<JobView> finishJobWithError(LogSyncProfile profile, LogSyncJob job, Throwable error) {
        var summary = sanitize(error);
        var jobStatus = job.getStatus();
        jobStatus.setStatus(JOB_FAILED);
        jobStatus.setErrorSummary(summary);
        jobStatus.setErrorLines(new ArrayList<>(List.of(summary)));
        jobStatus.setFinishedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        job.setStatus(jobStatus);
        return saveJob(job)
            .flatMap(saved -> recordProfileFailure(profile, error)
                .thenReturn(JobView.from(saved, summary)));
    }

    private Mono<Void> updateProfileAfterSync(LogSyncProfile profile, SyncCounters counters) {
        var status = profile.getStatus() == null
            ? new LogSyncProfile.LogSyncProfileStatus()
            : profile.getStatus();
        status.setLastSyncAt(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        if (!counters.cursorTo.isBlank()) {
            status.setLastCursor(counters.cursorTo);
        }
        if (counters.failed == 0) {
            status.setLastError("");
            status.setConsecutiveFailures(0);
        } else {
            status.setLastError("本次有 " + counters.failed + " 条记录未能写入，详见同步作业失败行");
            status.setConsecutiveFailures(
                status.getConsecutiveFailures() == null ? 1 : status.getConsecutiveFailures() + 1
            );
        }
        profile.setStatus(status);
        return client.update(profile).then();
    }

    private Mono<Void> ensureNoRunningJob(String profileName) {
        return client.listAll(LogSyncJob.class, EMPTY_OPTIONS, DEFAULT_SORT)
            .filter(job -> profileName.equals(job.getSpec() == null ? "" : job.getSpec().getProfileName()))
            .filter(job -> job.getStatus() != null && JOB_RUNNING.equals(job.getStatus().getStatus()))
            .filter(job -> !isStale(job))
            .hasElements()
            .flatMap(running -> running
                ? Mono.error(new QslApiException(HttpStatus.CONFLICT, "QSL-409-LS-0001",
                    "该配置已有一次同步在进行，请稍后再试"))
                : Mono.empty());
    }

    private boolean isStale(LogSyncJob job) {
        var startedAt = job.getStatus() == null ? "" : job.getStatus().getStartedAt();
        if (startedAt == null || startedAt.isBlank()) {
            return true;
        }
        try {
            var started = Instant.parse(startedAt);
            return started.plus(STALE_JOB_MINUTES, ChronoUnit.MINUTES).isBefore(Instant.now());
        } catch (RuntimeException error) {
            return true;
        }
    }

    /** 该数据源是否必须填写密码（未知源按必须处理）。 */
    /**
     * 数据源请求间隔闸门。
     *
     * <p>间隔未到时直接以 429 拒绝，并说明还要等几秒；不要在这里 sleep——同步是后台任务，
     * 但连通性测试是用户点击触发的，卡住六十秒只会让页面像坏了一样。
     */
    private Mono<Void> ensureRequestInterval(QslLogSyncSource source, String account) {
        var intervalSeconds = source.minRequestIntervalSeconds();
        if (intervalSeconds <= 0) {
            return Mono.empty();
        }
        var last = lastRequestAt.get(requestKey(source, account));
        if (last == null) {
            return Mono.empty();
        }
        var elapsed = Duration.between(last, Instant.now()).toSeconds();
        if (elapsed >= intervalSeconds) {
            return Mono.empty();
        }
        return Mono.error(new QslApiException(HttpStatus.TOO_MANY_REQUESTS, "QSL-429-LS-0002",
            "同一账号距上次请求仅 " + Math.max(0, elapsed) + " 秒，" + source.sourceType()
                + " 要求两次请求至少间隔 " + intervalSeconds + " 秒，请等 "
                + (intervalSeconds - Math.max(0, elapsed)) + " 秒后再试"));
    }

    /** 记录一次真实发出的请求时间；只有需要限流的数据源才记。 */
    private void markRequestSent(QslLogSyncSource source, String account) {
        if (source.minRequestIntervalSeconds() <= 0) {
            return;
        }
        lastRequestAt.put(requestKey(source, account), Instant.now());
    }

    /** 限流维度的键：数据源 + 账号，账号按呼号归一化规则大写去空白。 */
    static String requestKey(QslLogSyncSource source, String account) {
        return source.sourceType() + "|" + QslApiSupport.normalizeCallSign(account == null ? "" : account);
    }

    private boolean requiresPassword(String sourceType) {
        var source = sources.get(QslLogSyncSource.normalizeSourceType(sourceType));
        return source == null || source.requiresPassword();
    }

    private Mono<Credentials> loadCredentials(LogSyncProfile profile, boolean passwordRequired) {
        var spec = profile.getSpec() == null
            ? new LogSyncProfile.LogSyncProfileSpec()
            : profile.getSpec();
        var fallbackName = secretName(profile);
        var account = spec.getAccountName() == null ? "" : spec.getAccountName();
        return loadSecret(fallbackName)
            .map(secret -> new Credentials(
                firstNonBlank(readSecretValue(secret, SECRET_ACCOUNT), account),
                readSecretValue(secret, SECRET_PASSWORD)
            ))
            .defaultIfEmpty(new Credentials(account, ""))
            .flatMap(credentials -> {
                var needsPassword = passwordRequired && credentials.password().isBlank();
                var needsAccount = credentials.account().isBlank();
                if (needsPassword || needsAccount) {
                    var detail = needsPassword
                        ? "账号或密码未配置，请在页面填写后重试"
                        : "账号（凭据）未配置，请在页面填写后重试";
                    return Mono.error(new QslApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "QSL-422-LS-0002", detail));
                }
                return Mono.just(credentials);
            });
    }

    /**
     * 组装映射上下文：默认场景与回落操作员来自同步配置与站点档案。
     *
     * <p>天线回落值刻意留空——站点设备目录里没有天线字段，凭空造一个会污染映射结果；
     * 缺失时宁可让 ADIF 的 MY_ANTENNA 为空，也不猜。</p>
     */
    private Mono<QslLogSyncMapping.MappingContext> loadStationContext(LogSyncProfile logSyncProfile) {
        var logSyncSpec = logSyncProfile.getSpec();
        var defaultSceneType = QslLogSyncMapping.defaultSceneType(
            logSyncSpec == null ? null : logSyncSpec.getDefaultSceneType());
        return client.fetch(StationProfile.class, STATION_PROFILE_NAME)
            .map(profile -> {
                var spec = profile.getSpec();
                var myCallSign = spec == null ? "" : QslApiSupport.normalizeCallSign(spec.getMyCallSign());
                return new QslLogSyncMapping.MappingContext(
                    QslLogSyncSource.normalizeSourceType(logSyncSpec == null ? null : logSyncSpec.getSourceType()),
                    defaultSceneType,
                    myCallSign
                );
            })
            .defaultIfEmpty(new QslLogSyncMapping.MappingContext(
                QslLogSyncSource.normalizeSourceType(logSyncSpec == null ? null : logSyncSpec.getSourceType()),
                defaultSceneType,
                ""
            ));
    }

    private Mono<DuplicateIndex> buildDuplicateIndex() {
        return client.listAll(QsoRecord.class, EMPTY_OPTIONS, DEFAULT_SORT)
            .collectList()
            .map(records -> {
                Map<String, QsoRecord> index = new LinkedHashMap<>();
                var nextSequence = 1;
                for (var record : records) {
                    var spec = record.getSpec();
                    if (spec != null) {
                        index.put(QsoDuplicateKey.build(
                            spec.getSceneType(),
                            spec.getCallSign(),
                            spec.getDate(),
                            spec.getTime(),
                            spec.getTimezone(),
                            spec.getMyRigMode(),
                            spec.getFreq()
                        ), record);
                    }
                    var name = record.getMetadata().getName();
                    var matcher = QSO_RECORD_NAME.matcher(name == null ? "" : name);
                    if (matcher.matches()) {
                        try {
                            nextSequence = Math.max(nextSequence, Integer.parseInt(matcher.group(1)) + 1);
                        } catch (NumberFormatException ignored) {
                            // 超长数字序号忽略，保持当前序号继续。
                        }
                    }
                }
                return new DuplicateIndex(index, nextSequence);
            });
    }

    private Mono<LogSyncProfile> fetchProfile(String profileName) {
        var safeName = profileName == null ? "" : profileName.trim();
        if (safeName.isBlank()) {
            return Mono.error(new QslApiException(HttpStatus.BAD_REQUEST, "QSL-400-LS-0005",
                "缺少配置标识"));
        }
        return client.fetch(LogSyncProfile.class, safeName)
            .switchIfEmpty(Mono.error(new QslApiException(HttpStatus.NOT_FOUND, "QSL-404-LS-0001",
                "同步配置不存在：" + safeName)));
    }

    private Mono<ProfileView> toView(LogSyncProfile profile) {
        var spec = profile.getSpec() == null ? new LogSyncProfile.LogSyncProfileSpec() : profile.getSpec();
        var status = profile.getStatus() == null
            ? new LogSyncProfile.LogSyncProfileStatus()
            : profile.getStatus();
        var targetSecretName = secretName(profile);
        return loadSecret(targetSecretName)
            .map(secret -> new CredentialState(
                readSecretValue(secret, SECRET_ACCOUNT),
                readSecretValue(secret, SECRET_PASSWORD)
            ))
            .defaultIfEmpty(new CredentialState("", ""))
            .map(state -> toView(profile, spec, status, targetSecretName, state));
    }

    private ProfileView toView(
        LogSyncProfile profile,
        LogSyncProfile.LogSyncProfileSpec spec,
        LogSyncProfile.LogSyncProfileStatus status,
        String targetSecretName,
        CredentialState credentials
    ) {
        // 账号既可能来自 Secret，也可能只写在配置里（QRZ 的 Key 允许只存配置），两处任一非空即视为已保存。
        var accountValue = firstNonBlank(credentials.account(),
            spec.getAccountName() == null ? "" : spec.getAccountName());
        var accountSaved = !accountValue.isBlank();
        var passwordSaved = !credentials.password().isBlank();
        var passwordRequired = requiresPassword(spec.getSourceType());
        return new ProfileView(
            profile.getMetadata().getName(),
            spec.getSourceType(),
            spec.getMyCallSign(),
            spec.getAccountName(),
            accountSaved && (passwordSaved || !passwordRequired),
            accountSaved,
            passwordSaved,
            passwordRequired,
            targetSecretName,
            !Boolean.FALSE.equals(spec.getEnabled()),
            Boolean.TRUE.equals(spec.getAutoSyncEnabled()),
            spec.getSyncIntervalMinutes() == null ? 1440 : spec.getSyncIntervalMinutes(),
            spec.getDefaultSceneType(),
            spec.getConflictStrategy(),
            spec.getMaxRecordsPerSync() == null ? DEFAULT_MAX_RECORDS : spec.getMaxRecordsPerSync(),
            status.getLastSyncAt(),
            status.getLastCursor(),
            status.getLastError(),
            status.getConsecutiveFailures()
        );
    }

    private void applySpec(
        LogSyncProfile.LogSyncProfileSpec spec,
        SaveProfileCommand command,
        String sourceType,
        String callSign
    ) {
        spec.setSourceType(sourceType);
        spec.setMyCallSign(callSign);
        if (command.accountName() != null) {
            spec.setAccountName(command.accountName().trim());
        }
        spec.setEnabled(command.enabled() == null ? Boolean.TRUE : command.enabled());
        spec.setAutoSyncEnabled(Boolean.TRUE.equals(command.autoSyncEnabled()));
        var interval = command.syncIntervalMinutes() == null
            ? 1440
            : command.syncIntervalMinutes();
        spec.setSyncIntervalMinutes(Math.max(MIN_SYNC_INTERVAL_MINUTES, interval));
        spec.setDefaultSceneType(QsoDuplicateKey.normalizeSceneType(command.defaultSceneType()));
        spec.setConflictStrategy("OVERWRITE".equalsIgnoreCase(command.conflictStrategy())
            ? "OVERWRITE"
            : "SKIP");
        spec.setMaxRecordsPerSync(command.maxRecordsPerSync() == null || command.maxRecordsPerSync() <= 0
            ? DEFAULT_MAX_RECORDS
            : command.maxRecordsPerSync());
    }

    static String buildProfileName(String sourceType, String callSign) {
        var source = QslLogSyncSource.normalizeSourceType(sourceType).toLowerCase(Locale.ROOT)
            .replace('_', '-');
        var call = callSign == null ? "" : callSign.toLowerCase(Locale.ROOT);
        var raw = "log-sync-" + source + "-" + call;
        var safe = RESOURCE_NAME_SAFE.matcher(raw).replaceAll("-");
        return safe.length() > 63 ? safe.substring(0, 63) : safe;
    }

    /**
     * 凭据 Secret 名一律由配置名派生。
     *
     * <p>读、写、清理三处必须同源，否则会出现「保存成功但页面一直显示未配置」的假故障。
     */
    private String secretName(LogSyncProfile profile) {
        return secretNameFor(profile.getMetadata() == null ? "" : profile.getMetadata().getName());
    }

    /** Secret 里两个键的原值（只在服务端流转，绝不返回给前端）。 */
    private record CredentialState(String account, String password) {
    }

    private String secretNameFor(String profileName) {
        var safe = RESOURCE_NAME_SAFE.matcher(profileName == null ? "" : profileName.toLowerCase(Locale.ROOT))
            .replaceAll("-");
        var name = SECRET_NAME_PREFIX + safe;
        return name.length() > 63 ? name.substring(0, 63) : name;
    }

    private Mono<Secret> loadSecret(String secretName) {
        if (secretName == null || secretName.isBlank()) {
            return Mono.empty();
        }
        return client.fetch(Secret.class, secretName);
    }

    private Mono<Secret> upsertSecret(
        String secretName,
        String profileName,
        Secret current,
        String account,
        String password,
        boolean passwordRequired
    ) {
        var merged = new LinkedHashMap<String, String>();
        var existingAccount = current == null ? "" : readSecretValue(current, SECRET_ACCOUNT);
        var existingPassword = current == null ? "" : readSecretValue(current, SECRET_PASSWORD);
        var mergedPassword = password.isBlank() ? existingPassword : password;
        if (mergedPassword.isBlank() && passwordRequired) {
            // 只有账号没有密码时同步必然失败。这里直接拒绝，避免用户以为凭据已保存、
            // 却只看到作业表中的 FAILED。
            return Mono.error(new QslApiException(HttpStatus.BAD_REQUEST, "QSL-400-LS-0003",
                "密码（或应用密码）为空：首次保存凭据必须填写，之后再保存可留空表示不修改"));
        }
        merged.put(SECRET_ACCOUNT, account.isBlank() ? existingAccount : account);
        merged.put(SECRET_PASSWORD, mergedPassword);
        if (current != null) {
            current.setType(Secret.SECRET_TYPE_OPAQUE);
            current.setData(null);
            current.setStringData(merged);
            return client.update(current);
        }
        var secret = new Secret();
        secret.setMetadata(QslApiSupport.createMetadata(secretName));
        secret.setType(Secret.SECRET_TYPE_OPAQUE);
        secret.setStringData(merged);
        secret.getMetadata().setAnnotations(new LinkedHashMap<>());
        secret.getMetadata().getAnnotations().put(
            "qsl-management.bi1kbu.com/log-sync-profile",
            profileName == null ? "" : profileName
        );
        secret.getMetadata().getAnnotations().put(
            "qsl-management.bi1kbu.com/display-name",
            SECRET_DISPLAY_PREFIX + profileName
        );
        return client.create(secret);
    }

    private String readSecretValue(Secret secret, String key) {
        if (secret == null) {
            return "";
        }
        if (secret.getStringData() != null && secret.getStringData().containsKey(key)) {
            var value = secret.getStringData().get(key);
            return value == null ? "" : value.trim();
        }
        if (secret.getData() == null || !secret.getData().containsKey(key)) {
            return "";
        }
        var raw = new String(secret.getData().get(key), StandardCharsets.UTF_8).trim();
        if (raw.isBlank()) {
            return "";
        }
        try {
            return new String(java.util.Base64.getDecoder().decode(raw), StandardCharsets.UTF_8).trim();
        } catch (IllegalArgumentException error) {
            return raw;
        }
    }

    private List<String> truncateErrors(Set<String> errors) {
        var list = new ArrayList<String>();
        var iterator = errors.iterator();
        while (iterator.hasNext() && list.size() < MAX_ERROR_LINES) {
            list.add(iterator.next());
        }
        return list;
    }

    private String shortRecord(Map<String, String> record) {
        var callSign = record.getOrDefault("CALL", "");
        var date = record.getOrDefault("QSO_DATE", "");
        var time = record.getOrDefault("TIME_ON", "");
        return "CALL=" + callSign + " DATE=" + date + " TIME=" + time;
    }

    private String sanitize(Throwable error) {
        if (error instanceof QslApiException qslApiException) {
            return qslApiException.getMessage();
        }
        return "同步失败：" + error.getClass().getSimpleName();
    }

    private static String firstNonBlank(String first, String second) {
        var safeFirst = first == null ? "" : first.trim();
        return safeFirst.isBlank() ? (second == null ? "" : second.trim()) : safeFirst;
    }

    /** 一次同步的计数器（可变，供 concatMap 累加）。 */
    private final class SyncCounters {
        private final int maxRecords;
        private long fetched;
        private long created;
        private long updated;
        private long skipped;
        private long failed;
        private String cursorTo = "";
        private final Set<String> errors = new LinkedHashSet<>();
        private int nextRecordIndex = -1;

        private SyncCounters(int maxRecords) {
            this.maxRecords = maxRecords;
        }

        private String nextRecordName(int startSequence) {
            if (nextRecordIndex < 0) {
                nextRecordIndex = startSequence;
            }
            return "QSO" + (nextRecordIndex++);
        }
    }

    /** 判重索引：既有记录的键 → 记录，外加既有记录里 QSO 序号的下一个可用值。 */
    private record DuplicateIndex(Map<String, QsoRecord> byKey, int startSequence) {
    }

    /** 凭据对（只在内存中短暂存在）。 */
    private record Credentials(String account, String password) {
    }

    /** 数据源描述，供页面展示。 */
    public record SourceDescriptor(String sourceType, String displayName, boolean requiresCredential,
        String note) {
    }

    /** 配置视图（不含凭据）。 */
    public record ProfileView(
        String name,
        String sourceType,
        String myCallSign,
        String accountName,
        boolean credentialConfigured,
        boolean credentialAccountSaved,
        boolean credentialPasswordSaved,
        boolean credentialPasswordRequired,
        String credentialSecretName,
        boolean enabled,
        boolean autoSyncEnabled,
        Integer syncIntervalMinutes,
        String defaultSceneType,
        String conflictStrategy,
        Integer maxRecordsPerSync,
        String lastSyncAt,
        String lastCursor,
        String lastError,
        Integer consecutiveFailures
    ) {
    }

    /** 保存配置的入参。 */
    public record SaveProfileCommand(
        String sourceType,
        String myCallSign,
        String accountName,
        Boolean enabled,
        Boolean autoSyncEnabled,
        Integer syncIntervalMinutes,
        String defaultSceneType,
        String conflictStrategy,
        Integer maxRecordsPerSync
    ) {
    }

    /** 保存凭据的入参。 */
    public record SaveCredentialCommand(String accountName, String password) {
    }

    /** 连通性测试结果。 */
    public record TestResult(
        boolean ok,
        String message,
        int recordCount,
        String testedAt,
        List<String> records
    ) {
    }

    /** 作业视图。 */
    public record JobView(
        String name,
        String status,
        String message,
        Long fetchedCount,
        Long createdCount,
        Long updatedCount,
        Long skippedCount,
        Long failedCount,
        String startedAt,
        String finishedAt,
        List<String> errorLines
    ) {

        static JobView from(LogSyncJob job, String message) {
            var status = job.getStatus() == null ? new LogSyncJob.LogSyncJobStatus() : job.getStatus();
            return new JobView(
                job.getMetadata().getName(),
                status.getStatus(),
                message,
                status.getFetchedCount(),
                status.getCreatedCount(),
                status.getUpdatedCount(),
                status.getSkippedCount(),
                status.getFailedCount(),
                status.getStartedAt(),
                status.getFinishedAt(),
                Optional.ofNullable(status.getErrorLines()).orElseGet(List::of)
            );
        }
    }
}
