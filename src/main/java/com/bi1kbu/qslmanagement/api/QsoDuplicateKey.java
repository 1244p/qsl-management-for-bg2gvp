package com.bi1kbu.qslmanagement.api;

import java.util.Locale;

/**
 * 通联记录判重键。
 *
 * <p>逐字对齐控制台前端 {@code ui/src/views/modules/QsoRecordModule.vue} 的
 * {@code buildQsoDuplicateKey}（:1117-1136）与 {@code normalizeQsoDuplicatePart}：
 * 七个片段按 {@code sceneType|callSign|date|time|timezone|mode|freq} 拼接，
 * 每片段 {@code trim → toUpperCase → 去全部空白}；sceneType 只认 {@code SWL}，
 * 其他一律归为 {@code QSO}。
 *
 * <p>前后端算出的键必须完全相同，否则同一条通联会在手工导入与外部同步之间重复入库。
 * 变更本类必须同步改前端并补两侧对齐测试。
 */
public final class QsoDuplicateKey {

    /** 片段分隔符。 */
    public static final String SEPARATOR = "|";

    /** 场景类型：通用通联。 */
    public static final String SCENE_QSO = "QSO";

    /** 场景类型：短波收听。 */
    public static final String SCENE_SWL = "SWL";

    private QsoDuplicateKey() {
    }

    /** 归一化单个片段：去首尾空白、转大写、删掉中间所有空白。 */
    public static String normalizePart(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    /** 归一化场景类型：只保留 SWL，其余一律 QSO。 */
    public static String normalizeSceneType(String value) {
        if (value == null) {
            return SCENE_QSO;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        return SCENE_SWL.equals(upper) ? SCENE_SWL : SCENE_QSO;
    }

    /** 拼出判重键。 */
    public static String build(
        String sceneType,
        String callSign,
        String date,
        String time,
        String timezone,
        String mode,
        String freq
    ) {
        return String.join(
            SEPARATOR,
            normalizeSceneType(sceneType),
            normalizePart(callSign),
            normalizePart(date),
            normalizePart(time),
            normalizePart(timezone),
            normalizePart(mode),
            normalizePart(freq)
        );
    }
}
