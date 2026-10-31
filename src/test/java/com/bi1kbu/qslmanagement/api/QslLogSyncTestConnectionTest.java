package com.bi1kbu.qslmanagement.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bi1kbu.qslmanagement.extension.model.LogSyncProfile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;

class QslLogSyncTestConnectionTest {

    private static final String PROFILE_NAME = "log-sync-lotw-bg2gvp";
    private static final String SECRET_NAME = "qsl-log-sync-credential-log-sync-lotw-bg2gvp";

    /** 固定回放一段 ADIF 的假数据源，避免测试触网。 */
    private static QslLogSyncSource fakeSource(String adif) {
        return new QslLogSyncSource() {
            @Override
            public String sourceType() {
                return "LOTW";
            }

            @Override
            public Mono<FetchResult> fetch(FetchRequest request) {
                return Mono.just(new FetchResult(adif, ""));
            }
        };
    }

    private static LogSyncProfile profile() {
        var profile = new LogSyncProfile();
        var metadata = new Metadata();
        metadata.setName(PROFILE_NAME);
        profile.setMetadata(metadata);
        var spec = new LogSyncProfile.LogSyncProfileSpec();
        spec.setSourceType("LOTW");
        spec.setMyCallSign("BG2GVP");
        spec.setAccountName("BG2GVP");
        profile.setSpec(spec);
        profile.setStatus(new LogSyncProfile.LogSyncProfileStatus());
        return profile;
    }

    private static Secret credentialSecret() {
        var secret = new Secret();
        var metadata = new Metadata();
        metadata.setName(SECRET_NAME);
        secret.setMetadata(metadata);
        secret.setType(Secret.SECRET_TYPE_OPAQUE);
        secret.setData(Map.of(
            "account", java.util.Base64.getEncoder().encode("BG2GVP".getBytes(StandardCharsets.UTF_8)),
            "password", java.util.Base64.getEncoder().encode("secret".getBytes(StandardCharsets.UTF_8))
        ));
        return secret;
    }

    @Test
    @DisplayName("测试连接要返回上游记录样例，便于确认拉到的到底是谁的记录")
    void shouldReturnRecordSamples() {
        var adif = """
            <adif_ver:5>3.1.4
            <programid:6>QSLMS
            <EOH>
            <CALL:6>BH1ABC<QSO_DATE:8>20261030<TIME_ON:4>1234<MODE:3>SSB<FREQ:6>14.074<EOR>
            <CALL:6>JA1XYZ<QSO_DATE:8>20261029<TIME_ON:4>2359<MODE:2>CW<BAND:3>20m<EOR>
            """;

        var client = Mockito.mock(ReactiveExtensionClient.class);
        var audit = Mockito.mock(QslAuditService.class);
        when(client.fetch(eq(LogSyncProfile.class), eq(PROFILE_NAME))).thenReturn(Mono.just(profile()));
        when(client.fetch(eq(Secret.class), eq(SECRET_NAME))).thenReturn(Mono.just(credentialSecret()));
        when(audit.appendAuditLog(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        var service = new QslLogSyncService(client, audit, List.of(fakeSource(adif)));
        var result = service.testConnection(PROFILE_NAME, "tester", "127.0.0.1").block();

        assertThat(result).isNotNull();
        assertThat(result.ok()).isTrue();
        assertThat(result.recordCount()).isEqualTo(2);
        assertThat(result.records()).hasSize(2);
        assertThat(result.records().get(0)).isEqualTo("BH1ABC · 20261030 1234 · SSB · 14.074");
        assertThat(result.records().get(1)).isEqualTo("JA1XYZ · 20261029 2359 · CW · 20m");
    }

    @Test
    @DisplayName("上游返回空日志时样例为空且计数为 0，不能报成失败")
    void shouldHandleEmptyLog() {
        var adif = """
            <adif_ver:5>3.1.4
            <EOH>
            """;

        var client = Mockito.mock(ReactiveExtensionClient.class);
        var audit = Mockito.mock(QslAuditService.class);
        when(client.fetch(eq(LogSyncProfile.class), eq(PROFILE_NAME))).thenReturn(Mono.just(profile()));
        when(client.fetch(eq(Secret.class), eq(SECRET_NAME))).thenReturn(Mono.just(credentialSecret()));
        when(audit.appendAuditLog(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        var service = new QslLogSyncService(client, audit, List.of(fakeSource(adif)));
        var result = service.testConnection(PROFILE_NAME, "tester", "127.0.0.1").block();

        assertThat(result).isNotNull();
        assertThat(result.ok()).isTrue();
        assertThat(result.recordCount()).isZero();
        assertThat(result.records()).isEmpty();
    }

    @Test
    @DisplayName("凭据缺失时测试连接返回失败结果而不是抛异常")
    void shouldReturnFailureResultWhenCredentialMissing() {
        var client = Mockito.mock(ReactiveExtensionClient.class);
        var audit = Mockito.mock(QslAuditService.class);
        when(client.fetch(eq(LogSyncProfile.class), eq(PROFILE_NAME))).thenReturn(Mono.just(profile()));
        when(client.fetch(eq(Secret.class), eq(SECRET_NAME))).thenReturn(Mono.empty());
        when(audit.appendAuditLog(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        var service = new QslLogSyncService(client, audit, List.of(fakeSource("")));
        var result = service.testConnection(PROFILE_NAME, "tester", "127.0.0.1").block();

        assertThat(result).isNotNull();
        assertThat(result.ok()).isFalse();
        assertThat(result.records()).isEmpty();
        assertThat(result.message()).contains("未配置");
    }
}
