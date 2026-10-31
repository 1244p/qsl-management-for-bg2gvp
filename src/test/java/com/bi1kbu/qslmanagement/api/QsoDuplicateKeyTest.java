package com.bi1kbu.qslmanagement.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class QsoDuplicateKeyTest {

    @Test
    void shouldJoinSevenPartsWithPipeInFixedOrder() {
        String key = QsoDuplicateKey.build("QSO", "bi1kbu", "2026-10-30", "1234", "UTC", "ssb", "14.074");

        assertEquals("QSO|BI1KBU|2026-10-30|1234|UTC|SSB|14.074", key);
    }

    @Test
    void shouldStripInnerWhitespaceAndUpperCaseEveryPart() {
        assertEquals("BI1KBU", QsoDuplicateKey.normalizePart("  bi1 kbu  "));
        assertEquals("", QsoDuplicateKey.normalizePart(null));
        assertEquals("14.074", QsoDuplicateKey.normalizePart("14.074"));
    }

    @Test
    void shouldTreatOnlySwlAsSwlAndEverythingElseAsQso() {
        assertEquals("SWL", QsoDuplicateKey.normalizeSceneType(" swl "));
        assertEquals("QSO", QsoDuplicateKey.normalizeSceneType("QSO"));
        assertEquals("QSO", QsoDuplicateKey.normalizeSceneType("ONLINE_EYEBALL"));
        assertEquals("QSO", QsoDuplicateKey.normalizeSceneType(""));
        assertEquals("QSO", QsoDuplicateKey.normalizeSceneType(null));
    }

    @Test
    void shouldProduceSameKeyForSameLogSourceRegardlessOfCaseAndSpacing() {
        String fromLotw = QsoDuplicateKey.build(
            "qso", "BI1KBU", "2026-10-30", "1234", "utc", "SSB", "14.074"
        );
        String fromManualForm = QsoDuplicateKey.build(
            "QSO", " bi1kbu ", "2026-10-30 ", " 1234", "UTC", "ssb ", " 14.074"
        );

        assertEquals(fromLotw, fromManualForm);
    }

    @Test
    void shouldDistinguishRecordsThatDifferInAnySinglePart() {
        String base = QsoDuplicateKey.build("QSO", "BI1KBU", "2026-10-30", "1234", "UTC", "SSB", "14.074");

        assertNotEquals(base, QsoDuplicateKey.build("QSO", "BI1KBU", "2026-10-30", "1235", "UTC", "SSB", "14.074"));
        assertNotEquals(base, QsoDuplicateKey.build("QSO", "BI1KBU", "2026-10-30", "1234", "UTC", "CW", "14.074"));
        assertNotEquals(base, QsoDuplicateKey.build("QSO", "BI1KBU", "2026-10-30", "1234", "UTC", "SSB", "14.075"));
        assertNotEquals(base, QsoDuplicateKey.build("SWL", "BI1KBU", "2026-10-30", "1234", "UTC", "SSB", "14.074"));
    }
}
