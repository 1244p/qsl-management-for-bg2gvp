package com.bi1kbu.qslmanagement.api;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 外部日志 ADIF 记录 → 通联记录字段的映射。
 *
 * <p>规则基准是控制台前端的手工 ADIF 导入（{@code QsoRecordModule.vue:1265-1420}）与设计基线
 * {@code docs/plans/外部日志同步设计基线.md} 第 7.4 节：同一份 ADIF 无论走手工导入还是外部同步，
 * 落库字段与判重键都必须一致。
 *
 * <p>本类只做纯函数映射，不做 I/O，便于单元测试；写入与判重由 {@link QslLogSyncService} 负责。
 */
public final class QslLogSyncMapping {

    /** 已在映射中消费的 ADIF 字段，不再重复写进备注。 */
    private static final Set<String> CONSUMED_FIELDS = Set.of(
        "CALL",
        "QSO_DATE",
        "TIME_ON",
        "TIME_OFF",
        "FREQ",
        "MODE",
        "APP_LOTW_MODE",
        "SUBMODE",
        "RST_SENT",
        "RST_RCVD",
        "OPERATOR",
        "STATION_CALLSIGN",
        "MY_RIG",
        "MY_ANTENNA",
        "MY_SIG",
        "MY_SIG_INFO",
        "MY_GRIDSQUARE",
        "QTH",
        "GRIDSQUARE",
        "BAND"
    );

    /** 游标字段，必须吞掉，不能出现在备注里。 */
    private static final String CURSOR_FIELD = "APP_QRZLOG_LOGID";

    private QslLogSyncMapping() {
    }

    /** 默认场景归一化：只保留 SWL，其余一律 QSO。 */
    public static String defaultSceneType(String value) {
        return QsoDuplicateKey.normalizeSceneType(value);
    }

    /**
     * 映射上下文。
     *
     * @param sourceType 数据源类型，写进状态的来源标记
     * @param defaultSceneType 默认场景（QSO / SWL）
     * @param fallbackOperator 操作员缺省值，通常取站点资料里的个人呼号
     */
    public record MappingContext(
        String sourceType,
        String defaultSceneType,
        String fallbackOperator
    ) {
    }

    /** 映射结果：成功时 {@code error} 为空。 */
    public record MappedQso(
        String sceneType,
        String callSign,
        String date,
        String time,
        String timezone,
        String freq,
        String myRigMode,
        String rstSent,
        String rstRcvd,
        String operator,
        String myRig,
        String myRigAnt,
        String myQth,
        String qth,
        String remarks
    ) {

        /** 与前端同规则的判重键。 */
        public String duplicateKey() {
            return QsoDuplicateKey.build(sceneType, callSign, date, time, timezone, myRigMode, freq);
        }
    }

    /**
     * 映射单条 ADIF 记录。
     *
     * @return 映射成功返回结果，缺呼号或日期时间非法返回带原因的失败
     */
    public static MappingResult map(Map<String, String> record, MappingContext context) {
        if (record == null || record.isEmpty()) {
            return MappingResult.failure("空记录");
        }
        var callSign = upper(record.get("CALL"));
        if (callSign.isBlank()) {
            return MappingResult.failure("缺少呼号（CALL）");
        }
        var date = QslAdifReader.parseAdifDate(record.get("QSO_DATE"));
        if (date.isBlank()) {
            return MappingResult.failure("通联日期（QSO_DATE）不合法：" + text(record.get("QSO_DATE")));
        }
        var rawTime = firstNonBlank(record.get("TIME_ON"), record.get("TIME_OFF"));
        var time = QslAdifReader.parseAdifTime(rawTime);
        if (time.isBlank()) {
            return MappingResult.failure("通联时间（TIME_ON/TIME_OFF）不合法：" + text(rawTime));
        }

        var sceneType = QsoDuplicateKey.normalizeSceneType(
            context == null ? null : context.defaultSceneType()
        );
        var isSwl = QsoDuplicateKey.SCENE_SWL.equals(sceneType);

        // MODE 缺失时回退 APP_LoTW_MODE：ARRL 明确写「模式无法用合规 ADIF 值表示时将不发 MODE，
        // 改在 APP_LoTW_MODE 提供」，只读 MODE 会把这批记录的模式判定为空。
        var mode = text(firstNonBlank(record.get("MODE"), record.get("APP_LOTW_MODE"))).toUpperCase();
        var subMode = text(record.get("SUBMODE")).toUpperCase();
        if (!subMode.isBlank()) {
            mode = mode.isBlank() ? subMode : mode + "/" + subMode;
        }
        var myQth = "POTA".equalsIgnoreCase(text(record.get("MY_SIG")))
            ? text(record.get("MY_SIG_INFO"))
            : text(record.get("MY_GRIDSQUARE"));
        if (myQth.isBlank()) {
            myQth = text(record.get("MY_GRIDSQUARE"));
        }
        var qth = firstNonBlank(record.get("QTH"), record.get("GRIDSQUARE"));
        var operator = firstNonBlank(record.get("OPERATOR"), record.get("STATION_CALLSIGN"))
            .toUpperCase();
        if (operator.isBlank() && context != null) {
            operator = text(context.fallbackOperator());
        }
        var myRigAnt = text(record.get("MY_ANTENNA"));

        var mapped = new MappedQso(
            sceneType,
            callSign,
            date,
            time,
            "UTC",
            text(record.get("FREQ")),
            upper(mode),
            isSwl ? "" : defaultIfBlank(record.get("RST_SENT"), "59"),
            defaultIfBlank(record.get("RST_RCVD"), "59"),
            operator,
            text(record.get("MY_RIG")),
            myRigAnt,
            myQth,
            qth,
            buildRemarks(record)
        );
        return MappingResult.success(mapped);
    }

    /** 未消费字段并入备注，格式 {@code ADIF:KEY=VALUE;...}，键值各自去空白。 */
    static String buildRemarks(Map<String, String> record) {
        var parts = new java.util.ArrayList<String>();
        for (var entry : new LinkedHashMap<>(record).entrySet()) {
            var name = upper(entry.getKey());
            if (name.isBlank() || name.equals(CURSOR_FIELD) || CONSUMED_FIELDS.contains(name)) {
                continue;
            }
            var value = text(entry.getValue());
            if (value.isBlank()) {
                continue;
            }
            parts.add(name + "=" + value);
        }
        return parts.isEmpty() ? "" : "ADIF:" + String.join(";", parts);
    }

    /** 读取 QRZ Logbook 的增量游标（APP_QRZLOG_LOGID）。 */
    public static String readCursor(Map<String, String> record) {
        return record == null ? "" : text(record.get(CURSOR_FIELD));
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstNonBlank(String first, String second) {
        var safeFirst = text(first);
        return safeFirst.isBlank() ? text(second) : safeFirst;
    }

    private static String defaultIfBlank(String value, String fallback) {
        var safe = text(value);
        return safe.isBlank() ? fallback : safe;
    }

    /** 映射结果载体。 */
    public record MappingResult(MappedQso qso, String error) {

        static MappingResult success(MappedQso qso) {
            return new MappingResult(qso, "");
        }

        static MappingResult failure(String error) {
            return new MappingResult(null, error);
        }

        public boolean isSuccess() {
            return qso != null;
        }
    }
}
