package com.bi1kbu.qslmanagement.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QslAdifReaderTest {

    private static final String SAMPLE_ADIF = """
        ADIF export from LoTW
        <adif_ver:5>3.1.7
        <created_timestamp:15>20261031 081500
        <programid:8>QSL-TEST
        <EOH>
        <call:6>bi1kbu <gridsquare:4>OM89 <mode:3>SSB <qso_date:8>20261030 <time_on:6>123456 \
        <band:3>20M <freq:9>14.074000 <station_callsign:6>BI1KBU <my_gridsquare:6>OM89FX \
        <rst_sent:2>59 <rst_rcvd:2>59 <app_lotw_qsl_sent:1>N <eor>
        <call:5>JA1XX <mode:2>CW <qso_date:8>20261029 <time_on:4>0905 \
        <freq:6>7.0100 <rst_sent:3>-15 <rst_rcvd:3>-10 <band:3>40M <eor>
        """;

    @Test
    void shouldSkipHeaderFieldsAndReturnEveryRecord() {
        List<Map<String, String>> records = QslAdifReader.parseRecords(SAMPLE_ADIF);

        assertEquals(2, records.size());
        // 值保持原文大小写，大写化由映射层负责，与前端一致。
        assertEquals("bi1kbu", records.get(0).get("CALL"));
        assertEquals("20261030", records.get(0).get("QSO_DATE"));
        assertEquals("SSB", records.get(0).get("MODE"));
        assertEquals("N", records.get(0).get("APP_LOTW_QSL_SENT"));
        assertEquals("JA1XX", records.get(1).get("CALL"));
        // 头部字段不能混进记录。
        assertTrue(!records.get(0).containsKey("ADIF_VER"));
    }

    @Test
    void shouldKeepValueContainingTagLikeTextWhenLengthIsGiven() {
        String content = "<EOH><call:6>N0CALL<comment:10>hi<EOR>ha!<eor>";

        List<Map<String, String>> records = QslAdifReader.parseRecords(content);

        assertEquals(1, records.size());
        // 长度前缀保护了值内部的 <EOR> 文本，记录没有被切碎。
        assertEquals("hi<EOR>ha!", records.get(0).get("COMMENT"));
    }

    @Test
    void shouldParseRecordsWhenEndOfHeaderIsMissing() {
        List<Map<String, String>> records = QslAdifReader.parseRecords(
            "<call:6>BI1KBU<qso_date:8>20261030<time_on:4>1200<eor>"
        );

        assertEquals(1, records.size());
        assertEquals("BI1KBU", records.get(0).get("CALL"));
    }

    @Test
    void shouldKeepTrailingRecordWithoutEndOfRecordTag() {
        List<Map<String, String>> records = QslAdifReader.parseRecords(
            "<EOH><call:6>BI1KBU<qso_date:8>20261030"
        );

        assertEquals(1, records.size());
        assertEquals("20261030", records.get(0).get("QSO_DATE"));
    }

    @Test
    void shouldNormalizeAdifDateAndTime() {
        assertEquals("2026-10-30", QslAdifReader.parseAdifDate("20261030"));
        assertEquals("", QslAdifReader.parseAdifDate("2026-10-30"));
        assertEquals("", QslAdifReader.parseAdifDate("202610"));
        assertEquals("", QslAdifReader.parseAdifDate(null));

        assertEquals("1234", QslAdifReader.parseAdifTime("123456"));
        assertEquals("1234", QslAdifReader.parseAdifTime("1234"));
        assertEquals("", QslAdifReader.parseAdifTime("12"));
        assertEquals("", QslAdifReader.parseAdifTime("12:34"));
    }

    @Test
    void shouldTrimBomAndIgnoreTrailingGarbageText() {
        List<Map<String, String>> records = QslAdifReader.parseRecords(
            "\uFEFF<EOH><call:6>BI1KBU<eor>trailing note"
        );

        assertEquals(1, records.size());
        assertEquals("BI1KBU", records.get(0).get("CALL"));
    }

    @Test
    void shouldReturnEmptyListForBlankContent() {
        assertTrue(QslAdifReader.parseRecords("").isEmpty());
        assertTrue(QslAdifReader.parseRecords(null).isEmpty());
    }
}
