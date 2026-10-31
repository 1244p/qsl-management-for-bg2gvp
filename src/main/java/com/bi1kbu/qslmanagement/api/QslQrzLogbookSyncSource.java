package com.bi1kbu.qslmanagement.api;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * QRZ Logbook 适配器。
 *
 * <p>{@code documented}（https://www.qrz.com/docs/logbook/QRZLogbookAPI.html）：单一入口
 * {@code POST https://logbook.qrz.com/api}，表单参数 {@code KEY} + {@code ACTION=FETCH} +
 * {@code OPTION}（逗号分隔的 {@code 名称:值}，如 {@code ALL,TYPE:ADIF,MAX:250}、
 * {@code AFTERLOGID:123}、{@code MODSINCE:...}）。响应是 {@code key=value&} 串，含
 * {@code RESULT=OK|FAIL|AUTH}、{@code REASON}、{@code COUNT}、{@code ADIF}（值本身再做一次
 * URL 编码）。取数（FETCH）在 QRZ 侧需要付费订阅，因此该源在配置页标注「需要 QRZ 订阅」。
 *
 * <p>分页：官方建议单次 {@code MAX:250}，用返回 ADIF 里最大的 {@code APP_QRZLOG_LOGID}
 * 作为下一次的 {@code AFTERLOGID}。这里把它作为游标交给上层保存。
 */
@Component
public class QslQrzLogbookSyncSource implements QslLogSyncSource {

    static final String SOURCE_TYPE = "QRZ_LOGBOOK";
    static final String ENDPOINT = "https://logbook.qrz.com/api";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
    private static final String USER_AGENT = "QSLManagement-Halo-Plugin/2.3 (+external-log-sync)";
    private static final int PAGE_SIZE = 250;

    private final HttpClient httpClient;

    public QslQrzLogbookSyncSource() {
        this(HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build());
    }

    QslQrzLogbookSyncSource(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    /**
     * QRZ Logbook 只认 {@code KEY}（Access Key），没有配套密码，因此不要求填写密码。
     *
     * @see <a href="https://www.qrz.com/docs/logbook/QRZLogbookAPI.html">QRZ Logbook API</a>
     */
    @Override
    public boolean requiresPassword() {
        return false;
    }

    @Override
    public Mono<FetchResult> fetch(FetchRequest request) {
        return Mono.fromCallable(() -> requestPage(request))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private FetchResult requestPage(FetchRequest request) {
        var key = request == null ? "" : request.accountName();
        if (key == null || key.isBlank()) {
            throw new QslApiException(HttpStatus.UNPROCESSABLE_ENTITY, "QSL-422-LS-0002",
                "QRZ Logbook API Key 未配置");
        }
        var body = buildFormBody(key, request.cursor(), PAGE_SIZE);
        var httpRequest = HttpRequest.newBuilder(URI.create(ENDPOINT))
            .timeout(REQUEST_TIMEOUT)
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new QslApiException(HttpStatus.BAD_GATEWAY, "QSL-502-LS-0001", "QRZ Logbook 请求被中断");
        } catch (Exception error) {
            throw new QslApiException(HttpStatus.BAD_GATEWAY, "QSL-502-LS-0001",
                "QRZ Logbook 无法连接：" + error.getClass().getSimpleName());
        }
        if (response.statusCode() >= 400) {
            throw new QslApiException(HttpStatus.BAD_GATEWAY, "QSL-502-LS-0001",
                "QRZ Logbook 返回状态码 " + response.statusCode());
        }
        var parsed = parseResponse(response.body());
        if (!"OK".equals(parsed.result())) {
            var reason = parsed.reason().isBlank() ? "未返回原因" : parsed.reason();
            if ("AUTH".equals(parsed.result())) {
                throw new QslApiException(HttpStatus.BAD_GATEWAY, "QSL-401-LS-0001",
                    "QRZ Logbook API Key 无效：" + reason);
            }
            throw new QslApiException(HttpStatus.BAD_GATEWAY, "QSL-502-LS-0001",
                "QRZ Logbook 拉取失败：" + reason);
        }
        var adif = parsed.adif();
        var cursor = maxLogId(adif);
        return new FetchResult(adif, cursor);
    }

    static String buildFormBody(String key, String cursor, int pageSize) {
        var options = new StringBuilder("ALL,TYPE:ADIF,MAX:").append(Math.max(1, pageSize));
        var since = cursor == null ? "" : cursor.trim();
        if (!since.isBlank()) {
            options = new StringBuilder("TYPE:ADIF,MAX:").append(Math.max(1, pageSize))
                .append(",AFTERLOGID:").append(urlEncode(since));
        }
        return "KEY=" + urlEncode(key) + "&ACTION=FETCH&OPTION=" + urlEncode(options.toString());
    }

    /** 解析 {@code key=value&} 响应串；值再做一次 URL 解码。 */
    static QrzResponse parseResponse(String body) {
        Map<String, String> fields = new LinkedHashMap<>();
        var raw = body == null ? "" : body.trim();
        for (var pair : raw.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            var index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            var name = pair.substring(0, index).trim().toUpperCase(Locale.ROOT);
            var value = pair.substring(index + 1);
            fields.put(name, urlDecode(value));
        }
        return new QrzResponse(
            fields.getOrDefault("RESULT", "").toUpperCase(Locale.ROOT),
            fields.getOrDefault("REASON", ""),
            fields.getOrDefault("ADIF", ""),
            fields.getOrDefault("COUNT", "")
        );
    }

    /**
     * 取 ADIF 中最大的 {@code APP_QRZLOG_LOGID} 并加一，作为下次游标。
     *
     * <p>{@code documented}：{@code AFTERLOGID:n} 的语义是「返回 {@code app_qrzlog_logid}
     * 大于或等于 n 的记录」，官方分页建议也是「下一轮把 AFTERLOGID 设为上一轮最大的 logid 加一」。
     * 因此这里必须加一，否则每轮都会把最后一条重复拉一遍。
     */
    static String maxLogId(String adif) {
        if (adif == null || adif.isBlank()) {
            return "";
        }
        long max = -1;
        var records = QslAdifReader.parseRecords(adif);
        for (var record : records) {
            var raw = QslLogSyncMapping.readCursor(record);
            if (raw.isBlank()) {
                continue;
            }
            try {
                var value = Long.parseLong(raw);
                if (value > max) {
                    max = value;
                }
            } catch (NumberFormatException ignored) {
                // 非数字游标直接跳过，不影响其余记录。
            }
        }
        return max < 0 ? "" : Long.toString(max + 1);
    }

    /**
     * 该源是否提供增量游标：有游标时用 {@code AFTERLOGID} 取增量，无游标时全量。
     */
    static boolean supportsCursor() {
        return true;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            return value == null ? "" : value;
        }
    }

    /** QRZ Logbook 响应解析结果。 */
    record QrzResponse(String result, String reason, String adif, String count) {
    }
}
