package com.bi1kbu.qslmanagement.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QslLogSyncMappingTest {

    private static final QslLogSyncMapping.MappingContext CONTEXT =
        new QslLogSyncMapping.MappingContext("LOTW", "QSO", "BI1KBU");

    private static Map<String, String> record(String... pairs) {
        var map = new LinkedHashMap<String, String>();
        for (var index = 0; index < pairs.length; index += 2) {
            map.put(pairs[index], pairs[index + 1]);
        }
        return map;
    }

    @Test
    void shouldMapFullRecordToQsoFields() {
        var result = QslLogSyncMapping.map(record(
            "CALL", "ba1abc",
            "QSO_DATE", "20260314",
            "TIME_ON", "1234",
            "FREQ", "14.074",
            "MODE", "FT8",
            "RST_SENT", "599",
            "RST_RCVD", "579",
            "OPERATOR", "bi1kbu",
            "MY_RIG", "IC-7300",
            "MY_ANTENNA", "DP",
            "MY_GRIDSQUARE", "OM89"
        ), CONTEXT);

        assertTrue(result.error() == null || result.error().isBlank(), "不应产生映射错误");
        var qso = result.qso();
        assertEquals("QSO", qso.sceneType());
        assertEquals("BA1ABC", qso.callSign());
        assertEquals("2026-03-14", qso.date());
        assertEquals("1234", qso.time());
        assertEquals("UTC", qso.timezone());
        assertEquals("14.074", qso.freq());
        assertEquals("FT8", qso.myRigMode());
        assertEquals("599", qso.rstSent());
        assertEquals("579", qso.rstRcvd());
        assertEquals("BI1KBU", qso.operator());
        assertEquals("IC-7300", qso.myRig());
        assertEquals("DP", qso.myRigAnt());
        assertEquals("OM89", qso.myQth(), "MY_GRIDSQUARE 是本台网格，应写入本台 QTH");
        assertEquals("", qso.qth());
    }

    @Test
    void shouldFailWhenCallSignMissing() {
        var result = QslLogSyncMapping.map(record("QSO_DATE", "20260314"), CONTEXT);

        assertFalse(result.isSuccess());
        assertTrue(result.error().contains("呼号"));
    }

    @Test
    void shouldFailWhenDateInvalid() {
        var result = QslLogSyncMapping.map(record(
            "CALL", "BA1ABC",
            "QSO_DATE", "2026-03-14"
        ), CONTEXT);

        assertFalse(result.isSuccess());
        assertTrue(result.error().contains("QSO_DATE"));
    }

    @Test
    void shouldFallbackToTimeOffWhenTimeOnMissing() {
        var result = QslLogSyncMapping.map(record(
            "CALL", "BA1ABC",
            "QSO_DATE", "20260314",
            "TIME_OFF", "2359"
        ), CONTEXT);

        assertTrue(result.isSuccess());
        assertEquals("2359", result.qso().time());
    }

    @Test
    void shouldCombineModeAndSubmode() {
        var result = QslLogSyncMapping.map(record(
            "CALL", "BA1ABC",
            "QSO_DATE", "20260314",
            "TIME_ON", "0102",
            "MODE", "ssb",
            "SUBMODE", "usb"
        ), CONTEXT);

        assertTrue(result.isSuccess());
        assertEquals("SSB/USB", result.qso().myRigMode());
    }

    @Test
    void shouldClearRstSentAndUseSwlScene() {
        var swlContext = new QslLogSyncMapping.MappingContext("LOTW", "SWL", "BI1KBU");
        var result = QslLogSyncMapping.map(record(
            "CALL", "BA1ABC",
            "QSO_DATE", "20260314",
            "TIME_ON", "0102"
        ), swlContext);

        assertTrue(result.isSuccess());
        assertEquals("SWL", result.qso().sceneType());
        assertEquals("", result.qso().rstSent());
        assertEquals("59", result.qso().rstRcvd());
    }

    @Test
    void shouldKeepUnconsumedFieldsInRemarksButDropQrzLogId() {
        var result = QslLogSyncMapping.map(record(
            "CALL", "BA1ABC",
            "QSO_DATE", "20260314",
            "TIME_ON", "0102",
            "APP_QRZLOG_LOGID", "123456",
            "GRIDSQUARE", "PM01",
            "LOTW_QSL_SENT", "Y"
        ), CONTEXT);

        assertTrue(result.isSuccess());
        var remarks = result.qso().remarks();
        assertTrue(remarks.contains("LOTW_QSL_SENT=Y"), "未消费字段应写入备注：" + remarks);
        assertFalse(remarks.contains("APP_QRZLOG_LOGID"), "APP_QRZLOG_LOGID 只用作游标，不进备注");
    }

    @Test
    void shouldReadCursorFromQrzLogId() {
        var cursor = QslLogSyncMapping.readCursor(record("APP_QRZLOG_LOGID", "98765"));

        assertEquals("98765", cursor);
    }

    @Test
    void shouldNormalizeDefaultSceneType() {
        assertEquals("SWL", QslLogSyncMapping.defaultSceneType("swl"));
        assertEquals("QSO", QslLogSyncMapping.defaultSceneType("EYEBALL"));
        assertEquals("QSO", QslLogSyncMapping.defaultSceneType(null));
    }

    @Test
    void shouldBuildDuplicateKeyFromMappedFields() {
        var result = QslLogSyncMapping.map(record(
            "CALL", "ba1abc",
            "QSO_DATE", "20260314",
            "TIME_ON", "1234",
            "FREQ", "14.074",
            "MODE", "FT8"
        ), CONTEXT);

        assertTrue(result.isSuccess());
        assertEquals(
            QsoDuplicateKey.build("QSO", "BA1ABC", "2026-03-14", "1234", "UTC", "FT8", "14.074"),
            result.qso().duplicateKey()
        );
    }
}
