package com.bi1kbu.qslmanagement.api;

import java.util.Locale;
import reactor.core.publisher.Mono;

/**
 * 外部日志源适配器。
 *
 * <p>每个源只负责「拿回一段 ADIF 文本」，解析、判重、落库全部由 {@link QslLogSyncService} 统一处理，
 * 这样新增数据源不会碰到业务逻辑。
 */
public interface QslLogSyncSource {

    /** 数据源类型（LOTW / QRZ_LOGBOOK ...），与 {@code LogSyncProfile.spec.sourceType} 对应。 */
    String sourceType();

    /** 拉取一次 ADIF。失败必须抛 {@link QslApiException}，不得把原始响应体带进异常消息（可能含凭据）。 */
    Mono<FetchResult> fetch(FetchRequest request);

    /**
     * 该数据源是否支持游标分页。
     *
     * <p>LoTW 的下载接口是一次性整份报表，没有分页参数，返回多少条就是账号里的全部记录；
     * 对它继续翻页只会把同一批记录反复拉一遍（判重后全部跳过），白等几十个往返。
     */
    default boolean supportsPaging() {
        return true;
    }

    /**
     * 该数据源是否必须配置密码。
     *
     * <p>QRZ Logbook 只用一长串 Key 认证（Key 放在 {@code FetchRequest.accountName}），不需要密码；
     * 默认实现要求密码，覆盖为 {@code false} 的源在保存与同步时都不再要求密码非空。
     */
    default boolean requiresPassword() {
        return true;
    }

    /**
     * 同一账号两次请求之间的最小间隔（秒），0 表示不限。
     *
     * <p>LoTW 对同一用户的下载有硬限制：超出后返回 503 的 {@code Page Request Limit} 页面，
     * 之后一段时间内的请求全部被拒。把这个规则写进适配器，同步与连通性测试共用同一道闸，
     * 避免手动点「测试连接」再点「立即同步」把上游打到限流。
     */
    default int minRequestIntervalSeconds() {
        return 0;
    }

    /** 拉取参数。 */
    record FetchRequest(
        String myCallSign,
        String accountName,
        String password,
        String cursor,
        int maxRecords
    ) {
    }

    /** 拉取结果：ADIF 原文 + 本次终点游标（源不提供游标时为空串）。 */
    record FetchResult(String adif, String cursorTo) {

        public static FetchResult of(String adif) {
            return new FetchResult(adif, "");
        }
    }

    /** 归一化数据源类型，供配置与适配器注册表共用。 */
    static String normalizeSourceType(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
