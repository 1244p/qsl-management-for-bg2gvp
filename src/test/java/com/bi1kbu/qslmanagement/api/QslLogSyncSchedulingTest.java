package com.bi1kbu.qslmanagement.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bi1kbu.qslmanagement.extension.model.LogSyncProfile;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 定时同步的到期判定与两个数据源适配器的请求构造。 */
class QslLogSyncSchedulingTest {

    private static final Instant NOW = Instant.parse("2026-10-31T08:00:00Z");

    private static LogSyncProfile profile(Boolean enabled, Boolean autoSync, Integer interval, String lastSyncAt) {
        var profile = new LogSyncProfile();
        var spec = new LogSyncProfile.LogSyncProfileSpec();
        spec.setSourceType("LOTW");
        spec.setMyCallSign("BI1KBU");
        spec.setEnabled(enabled);
        spec.setAutoSyncEnabled(autoSync);
        spec.setSyncIntervalMinutes(interval);
        profile.setSpec(spec);
        var status = new LogSyncProfile.LogSyncProfileStatus();
        status.setLastSyncAt(lastSyncAt);
        profile.setStatus(status);
        return profile;
    }

    @Test
    @DisplayName("未开启自动同步的配置不参与定时任务")
    void shouldSkipWhenAutoSyncDisabled() {
        assertThat(QslLogSyncService.isDueForScheduledSync(profile(true, false, 60, null), NOW)).isFalse();
        assertThat(QslLogSyncService.isDueForScheduledSync(profile(true, null, 60, null), NOW)).isFalse();
    }

    @Test
    @DisplayName("已停用的配置即使开了自动同步也不执行")
    void shouldSkipWhenDisabled() {
        assertThat(QslLogSyncService.isDueForScheduledSync(profile(false, true, 60, null), NOW)).isFalse();
    }

    @Test
    @DisplayName("从未同步过视为到期")
    void shouldRunWhenNeverSynced() {
        assertThat(QslLogSyncService.isDueForScheduledSync(profile(true, true, 60, null), NOW)).isTrue();
        assertThat(QslLogSyncService.isDueForScheduledSync(profile(true, true, 60, "  "), NOW)).isTrue();
    }

    @Test
    @DisplayName("未到间隔不执行，到点后执行")
    void shouldRespectInterval() {
        assertThat(QslLogSyncService.isDueForScheduledSync(
            profile(true, true, 60, "2026-10-31T07:30:00Z"), NOW)).isFalse();
        assertThat(QslLogSyncService.isDueForScheduledSync(
            profile(true, true, 60, "2026-10-31T07:00:00Z"), NOW)).isTrue();
    }

    @Test
    @DisplayName("间隔小于最小间隔时按 60 分钟下限计算")
    void shouldClampIntervalToMinimum() {
        // 配了 1 分钟，但下限是 60 分钟，因此 30 分钟前同步过的还不该跑。
        assertThat(QslLogSyncService.isDueForScheduledSync(
            profile(true, true, 1, "2026-10-31T07:30:00Z"), NOW)).isFalse();
    }

    @Test
    @DisplayName("时间戳损坏时按到期处理，让下次成功同步修正")
    void shouldRunWhenTimestampBroken() {
        assertThat(QslLogSyncService.isDueForScheduledSync(
            profile(true, true, 60, "不是时间"), NOW)).isTrue();
    }

    @Test
    @DisplayName("LoTW 请求带 qso_mydetail 与游标")
    void shouldBuildLotwQuery() {
        var url = QslLotwLogSyncSource.buildQueryUrl("BI1KBU", "p@ss word", "2026-10-30 12:00:00");
        assertThat(url).startsWith("https://lotw.arrl.org/lotwuser/lotwreport.adi?");
        assertThat(url).contains("login=BI1KBU");
        assertThat(url).contains("qso_query=1");
        assertThat(url).contains("qso_qsl=no");
        assertThat(url).contains("qso_withown=yes");
        assertThat(url).contains("qso_mydetail=yes");
        assertThat(url).contains("qso_qsorxsince=2026-10-30+12%3A00%3A00");
    }

    @Test
    @DisplayName("LoTW 首次同步必须带 1900-01-01 起点，否则只能拉到最近一条")
    void shouldUseEarliestSinceOnFirstLotwSync() {
        var url = QslLotwLogSyncSource.buildQueryUrl("BI1KBU", "pass", "");
        // 不带这个参数时 LoTW 只回最近 1 条记录（2026-10-31 实测），必须给足够早的起点。
        assertThat(url).contains("qso_qsorxsince=1900-01-01");
    }

    @Test
    @DisplayName("失败说明要能引用 LoTW 页面里的原话")
    void shouldExtractVisibleTextFromLoginPage() {
        var html = "<html><head><style>.a{}</style><script>var x=1;</script></head>"
            + "<body><p>Username/password incorrect</p></body></html>";
        assertThat(QslLotwLogSyncSource.visibleText(html)).isEqualTo("Username/password incorrect");
        assertThat(QslLotwLogSyncSource.visibleText("")).isEqualTo("（空）");
    }

    @Test
    @DisplayName("登录页与非 ADIF 响应要能被识别，避免一律报成密码错误")
    void shouldDistinguishLoginPageFromAdif() {
        assertThat(QslLotwLogSyncSource.looksLikeLoginPage("<!DOCTYPE html><html>Log in</html>")).isTrue();
        assertThat(QslLotwLogSyncSource.looksLikeLoginPage("Please enter your password")).isTrue();
        assertThat(QslLotwLogSyncSource.looksLikeLoginPage("<ADIF_VER:5>3.1.4<EOH><CALL:6>BH1ABC<EOR>"))
            .isFalse();
        assertThat(QslLotwLogSyncSource.looksLikeLoginPage("")).isFalse();
    }

    @Test
    @DisplayName("LoTW 一次拉全量不分页，QRZ 用游标分页")
    void shouldDeclarePagingSupportPerSource() {
        // LoTW 返回的是整份报表，再翻页只会把同一批记录反复拉一遍。
        assertThat(new QslLotwLogSyncSource().supportsPaging()).isFalse();
        assertThat(new QslQrzLogbookSyncSource().supportsPaging()).isTrue();
    }

    @Test
    @DisplayName("LoTW 游标优先取头部 APP_LoTW_LASTQSORX")
    void shouldReadLotwCursorFromHeader() {
        var adif = """
            <APP_LoTW_LASTQSORX:19>2026-10-30 12:34:56<EOH>
            <CALL:6>JA1XXX<QSO_DATE:8>20261030<TIME_ON:4>1200<EOR>
            """;
        assertThat(QslLotwLogSyncSource.resolveCursor(adif)).isEqualTo("2026-10-30 12:34:56");
    }

    @Test
    @DisplayName("LoTW 无头部游标时回退到最大 QSO_DATE")
    void shouldFallbackToMaxQsoDate() {
        var adif = """
            <CALL:6>JA1XXX<QSO_DATE:8>20261028<TIME_ON:4>1200<EOR>
            <CALL:6>JA2YYY<QSO_DATE:8>20261030<TIME_ON:4>0900<EOR>
            """;
        assertThat(QslLotwLogSyncSource.resolveCursor(adif)).isEqualTo("2026-10-30");
    }

    @Test
    @DisplayName("LoTW 不是 HTML 登录页才算拿到数据")
    void shouldDetectLoginPage() {
        assertThat(QslLotwLogSyncSource.looksLikeAdif(
            "<!DOCTYPE html><html><body>Login</body></html>")).isFalse();
        assertThat(QslLotwLogSyncSource.looksLikeAdif(
            "APP_LoTW_LASTQSORX:19:2026-10-30 12:34:56<EOH><CALL:6>JA1XXX<EOR>")).isTrue();
    }

    @Test
    @DisplayName("QRZ 首次请求用 ALL，有游标时改用 AFTERLOGID 且不再带 ALL")
    void shouldBuildQrzFormBody() {
        var first = QslQrzLogbookSyncSource.buildFormBody("KEY-1", "", 250);
        assertThat(first).contains("KEY=KEY-1");
        assertThat(first).contains("ACTION=FETCH");
        assertThat(first).contains("ALL%2CTYPE%3AADIF%2CMAX%3A250");

        var next = QslQrzLogbookSyncSource.buildFormBody("KEY-1", "130877826", 250);
        assertThat(next).contains("AFTERLOGID%3A130877826");
        // 官方文档：OPTION 里给了 ALL 时只能再带 TYPE 与 STATUS，因此增量请求必须去掉 ALL。
        assertThat(next).doesNotContain("ALL%2C");
    }

    @Test
    @DisplayName("QRZ 游标取最大 logid 加一，避免重复拉最后一条")
    void shouldAdvanceQrzCursorByOne() {
        var adif = """
            <CALL:6>JA1XXX<APP_QRZLOG_LOGID:9>130877825<EOR>
            <CALL:6>JA2YYY<APP_QRZLOG_LOGID:9>130877830<EOR>
            """;
        assertThat(QslQrzLogbookSyncSource.maxLogId(adif)).isEqualTo("130877831");
    }

    @Test
    @DisplayName("QRZ 空响应不产生游标")
    void shouldReturnBlankCursorForEmptyQrzBody() {
        assertThat(QslQrzLogbookSyncSource.maxLogId("")).isEmpty();
    }

    @Test
    @DisplayName("LoTW 必须填写密码，QRZ Logbook 只要 Key")
    void shouldDeclarePasswordRequirementPerSource() {
        assertThat(new QslLotwLogSyncSource(java.net.http.HttpClient.newHttpClient()).requiresPassword())
            .isTrue();
        assertThat(new QslQrzLogbookSyncSource(java.net.http.HttpClient.newHttpClient()).requiresPassword())
            .isFalse();
    }

    @Test
    @DisplayName("数据源不要求密码时，空密码不影响请求构造")
    void shouldBuildQrzRequestWithoutPassword() {
        var source = new QslQrzLogbookSyncSource(java.net.http.HttpClient.newHttpClient());
        assertThat(source.requiresPassword()).isFalse();
        // Key 走 accountName，password 留空仍要能拼出合法请求体。
        assertThat(QslQrzLogbookSyncSource.buildFormBody("abcd1234", "", 250))
            .contains("KEY=abcd1234")
            .contains("ACTION=FETCH");
    }
}
