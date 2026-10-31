package com.bi1kbu.qslmanagement.api;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * ARRL Logbook of the World（LoTW）适配器。
 *
 * <p>{@code documented}（https://www.arrl.org/adif）：LoTW 没有官方程序接口，公开可用的是报表下载地址
 * {@code https://lotw.arrl.org/lotwuser/lotwreport.adi}，用 ARRL 账号密码做 HTTP 基本校验。
 * {@code qso_query=1} 才会返回 QSO 记录，{@code qso_qsl=no} 表示取「已上传的 QSO」而不是「已确认的 QSL」，
 * 且只有 {@code qso_qsl=no} 时 {@code qso_qsorxsince} 才生效。增量起点用头部返回的
 * {@code APP_LoTW_LASTQSORX}（官方建议）。凭据错误时服务端返回 200 + HTML 登录页，因此必须靠
 * 「响应里有没有 ADIF 标记」判定，不能只看状态码。
 *
 * <p>安全：密码只出现在出站 URL 中，任何异常消息、日志、配置回显都不得带出 URL 原文。
 */
@Component
public class QslLotwLogSyncSource implements QslLogSyncSource {

    static final String SOURCE_TYPE = "LOTW";
    static final String ENDPOINT = "https://lotw.arrl.org/lotwuser/lotwreport.adi";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);
    /** 首次同步的默认起点：早于 LoTW 有记录的任何时间，用来一次性导出全部 QSO。 */
    private static final String DEFAULT_SINCE = "1900-01-01";

    private static final String USER_AGENT = "QSLManagement-Halo-Plugin/2.3 (+external-log-sync)";

    /** LoTW 对同一用户的下载限制：一分钟一次。 */
    static final int MIN_REQUEST_INTERVAL_SECONDS = 60;
    /** 命中限流后等待多久再重试一次（略长于上游提示的一分钟）。 */
    private static final Duration LIMIT_RETRY_DELAY = Duration.ofSeconds(65);
    /** 限流重试次数上限。 */
    private static final int MAX_LIMIT_RETRIES = 1;

    private final HttpClient httpClient;
    /** 限流后的等待时长，抽成字段是为了让测试不必真的等一分钟。 */
    private final Duration limitRetryDelay;

    public QslLotwLogSyncSource() {
        this(HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(), LIMIT_RETRY_DELAY);
    }

    QslLotwLogSyncSource(HttpClient httpClient) {
        this(httpClient, LIMIT_RETRY_DELAY);
    }

    QslLotwLogSyncSource(HttpClient httpClient, Duration limitRetryDelay) {
        this.httpClient = httpClient;
        this.limitRetryDelay = limitRetryDelay == null ? Duration.ZERO : limitRetryDelay;
    }

    @Override
    public boolean supportsPaging() {
        return false;
    }

    @Override
    public int minRequestIntervalSeconds() {
        return MIN_REQUEST_INTERVAL_SECONDS;
    }

    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public Mono<FetchResult> fetch(FetchRequest request) {
        return Mono.fromCallable(() -> requestAdifWithLimitRetry(request))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 命中 LoTW 的 {@code Page Request Limit} 时限流重试一次。
     *
     * <p>上游对同一用户同时只允许一次报表下载：前一次还没收完，或者有人连着点了测试与同步，
     * 都会返回 503 的限流页面。上游自己写的是「等一分钟再试」，所以这里等 65 秒重试一次；
     * 仍被拒才把限流说明抛给页面。
     */
    private FetchResult requestAdifWithLimitRetry(FetchRequest request) {
        try {
            return requestAdif(request, false);
        } catch (QslApiException error) {
            if (MAX_LIMIT_RETRIES < 1 || !"QSL-429-LS-0001".equals(error.getCode())) {
                throw error;
            }
            try {
                Thread.sleep(limitRetryDelay.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw error;
            }
            return requestAdif(request, true);
        }
    }

    /**
     * 解析实际用于登录的账号名。
     *
     * <p>{@code documented}（https://www.arrl.org/adif）：{@code login} 是 LoTW 用户名，
     * 「多数情况下等于主呼号，但并非总是如此，不应假定」。配置里的「账号」就是它，
     * 只有留空时才回落到本台呼号。
     */
    static String resolveLoginName(FetchRequest request) {
        if (request == null) {
            return "";
        }
        var account = request.accountName() == null ? "" : request.accountName().trim();
        return account.isBlank() ? QslApiSupport.normalizeCallSign(request.myCallSign()) : account;
    }

    private FetchResult requestAdif(FetchRequest request, boolean retried) {
        var loginName = resolveLoginName(request);
        var password = request == null ? "" : request.password();
        if (loginName.isBlank() || password == null || password.isBlank()) {
            throw new QslApiException(HttpStatus.UNPROCESSABLE_ENTITY, "QSL-422-LS-0002",
                "LoTW 账号或密码未配置");
        }
        var uri = URI.create(buildQueryUrl(loginName, password, request.cursor()));
        var httpRequest = HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/plain,application/octet-stream")
            .GET()
            .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new QslApiException(HttpStatus.BAD_GATEWAY, "QSL-502-LS-0001", "LoTW 请求被中断");
        } catch (Exception error) {
            // 只带异常类型，绝不带 URL（URL 里有密码）。
            throw new QslApiException(HttpStatus.BAD_GATEWAY, "QSL-502-LS-0001",
                "LoTW 无法连接：" + error.getClass().getSimpleName());
        }
        var body = response.body() == null ? "" : response.body();
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new QslApiException(HttpStatus.BAD_GATEWAY, "QSL-401-LS-0001",
                "LoTW 账号或密码不正确（HTTP " + response.statusCode() + "）"
                    + describeBody(response, body));
        }
        if (response.statusCode() == 429 || response.statusCode() == 503 || looksLikeRequestLimit(body)) {
            // 503 + 「Page Request Limit」是 LoTW 自己的限流页，不是凭据问题，也不是网络故障。
            throw new QslApiException(HttpStatus.TOO_MANY_REQUESTS, "QSL-429-LS-0001",
                "LoTW 限制同一账号每分钟只能发起一次报表下载，本次请求被拒绝（"
                    + (retried ? "已等待一分钟后重试，仍未放行" : "已自动等待一分钟后重试仍失败")
                    + "）。请稍后再同步；插件内已按账号加了请求间隔，正常不会连续触发。页面原文："
                    + visibleText(body) + describeBody(response, body));
        }
        if (response.statusCode() >= 400) {
            throw new QslApiException(HttpStatus.BAD_GATEWAY, "QSL-502-LS-0001",
                "LoTW 返回状态码 " + response.statusCode() + describeBody(response, body));
        }
        if (!looksLikeAdif(body)) {
            // 不能一律说成密码错误：LoTW 在账号未启用、被限流、参数被拒时也可能回 200 的 HTML 页面。
            // 把状态码、内容类型与开头片段带出来，用户与维护者才能判断是凭据问题还是接入问题。
            var hint = looksLikeLoginPage(body)
                ? "LoTW 返回的是登录页面，说明这次请求没有被认可（LoTW 用户名与主呼号不一定相同，密码是登录 lotw.arrl.org 的密码）。页面原文："
                    + visibleText(body)
                : "LoTW 返回的内容不是 ADIF";
            throw new QslApiException(HttpStatus.BAD_GATEWAY, "QSL-401-LS-0001",
                hint + describeBody(response, body));
        }
        return new FetchResult(body, resolveCursor(body));
    }

    /** 拼接下载地址；{@code cursor} 来自上一份报表的 {@code APP_LoTW_LASTQSORX}。 */
    static String buildQueryUrl(String callSign, String password, String cursor) {
        var builder = new StringBuilder(ENDPOINT)
            .append("?login=").append(urlEncode(callSign))
            .append("&password=").append(urlEncode(password))
            .append("&qso_query=1")
            .append("&qso_qsl=no")
            .append("&qso_withown=yes")
            // 本台站址字段（MY_GRIDSQUARE / MY_STATE 等）只在 qso_mydetail=yes 时返回。
            .append("&qso_mydetail=yes");
        var since = cursor == null ? "" : cursor.trim();
        if (since.isBlank()) {
            // 首次同步必须显式给一个足够早的起点：不带这个参数时 LoTW 只回最近的极少记录
            // （实测只回 1 条），加上它才会导出账号里的全部 QSO。该参数按官方说明只在
            // qso_qsl=no 时生效，而本适配器恒定传 qso_qsl=no，所以这里恒定传值。
            since = DEFAULT_SINCE;
        }
        // 官方允许 YYYY-MM-DD 与 YYYY-MM-DD HH:MM:SS 两种格式，这里原样传。
        return builder.append("&qso_qsorxsince=").append(urlEncode(since)).toString();
    }

    /**
     * 取出 HTML 里的可见文字，供失败说明直接引用上游自己的措辞。
     *
     * <p>只看开头几百字符就够了：LoTW 把「用户名或密码不正确」「账号被锁」「需要证书」这类
     * 判定都写在页面正文里，把这些话原样带出来，比我们自己猜原因准得多。
     */
    static String visibleText(String html) {
        if (html == null || html.isBlank()) {
            return "（空）";
        }
        var text = html.replaceAll("(?is)<script.*?</script>", " ")
            .replaceAll("(?is)<style.*?</style>", " ")
            .replaceAll("(?s)<[^>]+>", " ")
            .replaceAll("&nbsp;", " ")
            .replaceAll("\s+", " ")
            .trim();
        if (text.length() > 400) {
            text = text.substring(0, 400) + "…";
        }
        return text.isBlank() ? "（无可读文字）" : text;
    }

    /**
     * 判断响应是不是 LoTW 的限流页面。
     *
     * <p>原文形如 {@code <TITLE>Error 503</TITLE> ... <B>Page Request Limit!</B>
     * Only one page request per user at ...}，只能靠正文识别（状态码有时被前置代理改写）。
     */
    static boolean looksLikeRequestLimit(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        var lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("page request limit") || lower.contains("one page request per user")
            || lower.contains("request limit");
    }

    /** 判断响应是不是 LoTW 的登录/提示页面，用于给出更准确的失败说明。 */
    static boolean looksLikeLoginPage(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        var lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("<!doctype") || lower.contains("<html")
            || lower.contains("log in") || lower.contains("login")
            || lower.contains("password") || lower.contains("not authorized");
    }

    /**
     * 把响应的可诊断信息压成一句话：状态码、内容类型、长度与开头片段。
     *
     * <p>片段只用于排障，去掉连续空白并截断；请求地址里带密码，这里绝不回显 URL。
     */
    private static String describeBody(HttpResponse<String> response, String body) {
        // 这是诊断路径：任何取不到的信息都退化成 "-"，绝不能因为拿不到内容类型
        // 就抛 NPE 把真正的失败原因盖掉。
        var headers = response.headers();
        var contentType = headers == null ? "-" : headers.firstValue("Content-Type").orElse("-");
        var excerpt = body == null ? "" : body.replaceAll("\s+", " ").trim();
        if (excerpt.length() > 240) {
            excerpt = excerpt.substring(0, 240) + "…";
        }
        return "（HTTP " + response.statusCode() + "，" + contentType + "，" + (body == null ? 0 : body.length())
            + " 字节，开头：" + (excerpt.isBlank() ? "空" : excerpt) + "）";
    }

    /**
     * 从报表头部取下一次的查询起点。
     *
     * <p>{@code documented}（https://www.arrl.org/adif）：{@code qso_qsl=no} 时头部带
     * {@code APP_LoTW_LASTQSORX}，是本次报表里最近一条记录的入库时间，官方明确建议
     * 用它作为下一次的 {@code qso_qsorxsince}。取不到时回退到记录里的最大
     * {@code QSO_DATE}（精度到天，可能重复拉取当天的记录，由判重兜住）。
     */
    static String resolveCursor(String adif) {
        var header = QslAdifReader.parseHeader(adif);
        var lastQsoRx = header.getOrDefault("APP_LOTW_LASTQSORX", "").trim();
        if (!lastQsoRx.isBlank()) {
            return lastQsoRx;
        }
        String maxDate = "";
        for (var record : QslAdifReader.parseRecords(adif)) {
            var date = QslAdifReader.parseAdifDate(record.get("QSO_DATE"));
            if (!date.isBlank() && date.compareTo(maxDate) > 0) {
                maxDate = date;
            }
        }
        return maxDate;
    }

    /** 判定响应是否真的是 ADIF：有 ADIF 标记，且不是 HTML 页面。 */
    static boolean looksLikeAdif(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        var lower = body.toLowerCase(Locale.ROOT);
        if (lower.contains("<html") || lower.contains("<!doctype")) {
            return false;
        }
        var upper = body.toUpperCase(Locale.ROOT);
        return upper.contains("<EOH>") || upper.contains("<EOR>") || upper.contains("<CALL:");
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
