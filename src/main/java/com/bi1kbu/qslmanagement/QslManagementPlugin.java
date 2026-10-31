package com.bi1kbu.qslmanagement;

import com.bi1kbu.qslmanagement.api.QslApiSupport;
import com.bi1kbu.qslmanagement.api.QslAiService;
import com.bi1kbu.qslmanagement.api.QslAiPromptDefaults;
import com.bi1kbu.qslmanagement.api.QslAuditService;
import com.bi1kbu.qslmanagement.api.QslCardRequestService;
import com.bi1kbu.qslmanagement.api.QslLogSyncService;
import com.bi1kbu.qslmanagement.api.QslMigrationStateService;
import com.bi1kbu.qslmanagement.api.QslQrzAddressLookupService;
import com.bi1kbu.qslmanagement.extension.model.StationProfile;
import com.bi1kbu.qslmanagement.extension.model.SystemSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * 插件启动类。
 *
 * <p>{@code @EnableScheduling}：外部日志同步的定时任务需要 Spring 调度能力，
 * 见 https://docs.halo.run/developer-guide/plugin/basics/server/scheduled-tasks 。
 * 调度注册在插件自己的应用上下文里，插件停止时随之取消，不需要手工清理。</p>
 */
@Component
@EnableScheduling
public class QslManagementPlugin extends BasePlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(QslManagementPlugin.class);
    private static final String DEFAULT_SYSTEM_SETTING_NAME = "qsl-system-setting-default";
    private static final String DEFAULT_STATION_PROFILE_NAME = "qsl-station-profile-default";

    private final ReactiveExtensionClient client;
    private final QslMigrationStateService migrationStateService;
    private final QslCardRequestService qslCardRequestService;
    private final QslLogSyncService qslLogSyncService;

    public QslManagementPlugin(
        PluginContext pluginContext,
        ReactiveExtensionClient client,
        QslCardRequestService qslCardRequestService,
        QslLogSyncService qslLogSyncService
    ) {
        super(pluginContext);
        this.client = client;
        this.migrationStateService = new QslMigrationStateService(client);
        this.qslCardRequestService = qslCardRequestService;
        this.qslLogSyncService = qslLogSyncService;
    }

    @Bean
    public QslMigrationStateService qslMigrationStateService(ReactiveExtensionClient client) {
        return migrationStateService;
    }

    @Bean
    public QslAiService qslAiService(ReactiveExtensionClient client, QslAuditService auditService) {
        return new QslAiService(client, auditService);
    }

    @Bean
    public QslQrzAddressLookupService qslQrzAddressLookupService(
        ReactiveExtensionClient client,
        QslAuditService auditService,
        QslAiService aiService
    ) {
        return new QslQrzAddressLookupService(client, auditService, aiService);
    }

    @Override
    public void start() {
        ensureDefaultResources()
            .then(migrationStateService.ensureMigrationState(context.getVersion()))
            .then(qslCardRequestService.reconcileReservations())
            .doOnNext(result -> LOGGER.info(
                "实体卡申请QSO占用修复完成：扫描={}，释放={}，保留={}",
                result.scanned(), result.released(), result.kept()
            ))
            // 重启后不可能还有作业在跑，把遗留的「进行中」作业收尾成失败，
            // 否则页面上会永远显示进行中，并挡住重置游标与删除配置。
            .then(qslLogSyncService.reconcileRunningJobs())
            .then()
            .subscribe(
            ignored -> {
            },
            error -> LOGGER.warn("初始化默认资源或修复实体卡申请占用失败：{}", error.getMessage())
        );
        LOGGER.info("QSL 管理插件启动成功。");
    }

    @Override
    public void stop() {
        LOGGER.info("QSL 管理插件已停止。");
    }

    private Mono<Void> ensureDefaultResources() {
        return ensureDefaultSystemSetting()
            .then(ensureDefaultStationProfile());
    }

    private Mono<Void> ensureDefaultSystemSetting() {
        return client.fetch(SystemSetting.class, DEFAULT_SYSTEM_SETTING_NAME)
            .then()
            .switchIfEmpty(Mono.defer(() -> {
                var systemSetting = new SystemSetting();
                systemSetting.setMetadata(QslApiSupport.createMetadata(DEFAULT_SYSTEM_SETTING_NAME));
                var spec = new SystemSetting.SystemSettingSpec();
                spec.setGuestQueryPerMinute(30);
                spec.setRequiresExchangeReview(Boolean.TRUE);
                spec.setOnlineExchangeRequestPolicy("MANUAL");
                spec.setOnlineExchangeRequestCooldownMinutes(5);
                spec.setOnlineAutoApprovedRequestMailPolicy("AUTO_SKIP");
                spec.setAutoNotifyOnCardCreated(Boolean.FALSE);
                spec.setAutoNotifyOnCardSent(Boolean.FALSE);
                spec.setAutoNotifyOnCardReceived(Boolean.FALSE);
                spec.setAutoNotifyOnExchangeReviewed(Boolean.FALSE);
                spec.setQsoCardCreatedMailPolicy("MANUAL");
                spec.setQsoCardSentMailPolicy("MANUAL");
                spec.setQsoCardReceivedMailPolicy("MANUAL");
                spec.setOnlineCardCreatedMailPolicy("MANUAL");
                spec.setOnlineCardSentMailPolicy("MANUAL");
                spec.setOnlineCardReceivedMailPolicy("MANUAL");
                spec.setOnlineExchangeReviewedMailPolicy("MANUAL");
                spec.setQsoAutoNotifyOnCardCreated(Boolean.FALSE);
                spec.setQsoAutoNotifyOnCardSent(Boolean.FALSE);
                spec.setQsoAutoNotifyOnCardReceived(Boolean.FALSE);
                spec.setOnlineAutoNotifyOnCardCreated(Boolean.FALSE);
                spec.setOnlineAutoNotifyOnCardSent(Boolean.FALSE);
                spec.setOnlineAutoNotifyOnCardReceived(Boolean.FALSE);
                spec.setOnlineAutoNotifyOnExchangeReviewed(Boolean.FALSE);
                spec.setOfflineAutoNotifyOnCardReceived(Boolean.FALSE);
                spec.setCardRecordSequence(1000);
                spec.setReceiveRecordSequence(0);
                spec.setAiEnabled(Boolean.FALSE);
                spec.setAiProvider("openai-compatible");
                spec.setAiBaseUrl("https://api.openai.com/v1");
                spec.setAiModel("");
                spec.setAiSecretName("qsl-ai-openai-api-key");
                spec.setAiTemperature(0.2D);
                spec.setAiTimeoutSeconds(30);
                spec.setAiMaxConcurrentRequests(1);
                spec.setAiMaxInputCharacters(30000);
                spec.setAiOnlineImportParseEnabled(Boolean.FALSE);
                spec.setAiAddressCleanupEnabled(Boolean.FALSE);
                spec.setAiSystemPrompt(QslAiPromptDefaults.SYSTEM_PROMPT);
                spec.setAiOnlineImportPrompt(QslAiPromptDefaults.ONLINE_IMPORT_PROMPT);
                spec.setAiAddressCleanupPrompt(QslAiPromptDefaults.ADDRESS_CLEANUP_PROMPT);
                spec.setAiCallbookAddressPrompt(QslAiPromptDefaults.CALLBOOK_ADDRESS_PROMPT);
                spec.setQrzComEnabled(Boolean.FALSE);
                spec.setQrzComUsername("");
                spec.setQrzComSecretName("qsl-qrz-com-credential");
                spec.setQrzComXmlBaseUrl("https://xmldata.qrz.com/xml/current/");
                spec.setQrzCnEnabled(Boolean.FALSE);
                spec.setQrzCnUsername("");
                spec.setQrzCnSecretName("qsl-qrz-cn-credential");
                spec.setQrzCnLookupUrlTemplate("https://www.qrz.cn/call/{callSign}");
                spec.setQrzTimeoutSeconds(30);
                systemSetting.setSpec(spec);
                return client.create(systemSetting)
                    .then()
                    .onErrorResume(this::isDuplicateNameError, ignored -> Mono.empty());
            }));
    }

    private Mono<Void> ensureDefaultStationProfile() {
        return client.fetch(StationProfile.class, DEFAULT_STATION_PROFILE_NAME)
            .then()
            .switchIfEmpty(Mono.defer(() -> {
                var stationProfile = new StationProfile();
                stationProfile.setMetadata(QslApiSupport.createMetadata(DEFAULT_STATION_PROFILE_NAME));
                var spec = new StationProfile.StationProfileSpec();
                spec.setMyCallSign("");
                spec.setMyName("");
                spec.setMyNameEn("");
                spec.setMyTelephone("");
                spec.setMyPostalCode("");
                spec.setMyAddress("");
                spec.setMyAddressEn("");
                spec.setMyEmail("");
                spec.setStationRemarks("");
                stationProfile.setSpec(spec);
                return client.create(stationProfile)
                    .then()
                    .onErrorResume(this::isDuplicateNameError, ignored -> Mono.empty());
            }));
    }

    private boolean isDuplicateNameError(Throwable error) {
        return error != null
            && error.getMessage() != null
            && error.getMessage().contains("Duplicate name detected.");
    }

}
