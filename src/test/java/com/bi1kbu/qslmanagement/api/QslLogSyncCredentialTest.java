package com.bi1kbu.qslmanagement.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bi1kbu.qslmanagement.extension.model.LogSyncProfile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;

class QslLogSyncCredentialTest {

    private static final String PROFILE_NAME = "log-sync-lotw-bg2gvp";
    private static final String SECRET_NAME = "qsl-log-sync-credential-log-sync-lotw-bg2gvp";

    private static LogSyncProfile profile(String sourceType) {
        var profile = new LogSyncProfile();
        var metadata = new Metadata();
        metadata.setName(PROFILE_NAME);
        profile.setMetadata(metadata);
        var spec = new LogSyncProfile.LogSyncProfileSpec();
        spec.setSourceType(sourceType);
        spec.setMyCallSign("BG2GVP");
        profile.setSpec(spec);
        profile.setStatus(new LogSyncProfile.LogSyncProfileStatus());
        return profile;
    }

    private static QslLogSyncService service(ReactiveExtensionClient client, QslAuditService audit) {
        // 必须装配真实适配器：凭据是否强制密码由数据源声明决定，空列表会把所有源当成“必须密码”。
        return new QslLogSyncService(client, audit,
            List.of(new QslLotwLogSyncSource(), new QslQrzLogbookSyncSource()));
    }

    /** 模拟「Secret 存在但值以 base64 的 data 返回」——Halo 持久化后的真实形态。 */
    private static Secret persistedSecret(String account, String password) {
        var secret = new Secret();
        var metadata = new Metadata();
        metadata.setName(SECRET_NAME);
        secret.setMetadata(metadata);
        secret.setType(Secret.SECRET_TYPE_OPAQUE);
        secret.setData(new java.util.HashMap<>(java.util.Map.of(
            "account", java.util.Base64.getEncoder().encode(account.getBytes(StandardCharsets.UTF_8)),
            "password", java.util.Base64.getEncoder().encode(password.getBytes(StandardCharsets.UTF_8))
        )));
        return secret;
    }

    @Test
    @DisplayName("首次保存凭据要真的创建 Secret（此前 loadSecret 为空导致整段被跳过）")
    void shouldCreateSecretOnFirstSave() {
        var client = Mockito.mock(ReactiveExtensionClient.class);
        var audit = Mockito.mock(QslAuditService.class);
        var created = new AtomicReference<Secret>();

        when(client.fetch(eq(LogSyncProfile.class), eq(PROFILE_NAME))).thenReturn(Mono.just(profile("LOTW")));
        when(client.fetch(eq(Secret.class), eq(SECRET_NAME))).thenAnswer(invocation -> {
            var secret = created.get();
            return secret == null ? Mono.<Secret>empty() : Mono.just(secret);
        });
        when(client.create(any(Secret.class))).thenAnswer(invocation -> {
            Secret secret = invocation.getArgument(0);
            created.set(secret);
            return Mono.just(secret);
        });
        when(client.update(any(LogSyncProfile.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(audit.appendAuditLog(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        var view = service(client, audit)
            .saveCredential(PROFILE_NAME,
                new QslLogSyncService.SaveCredentialCommand("BG2GVP", "lotw-password"), "tester", "127.0.0.1")
            .block();

        assertThat(created.get()).isNotNull();
        assertThat(created.get().getStringData())
            .containsEntry("account", "BG2GVP")
            .containsEntry("password", "lotw-password");
        assertThat(view).isNotNull();
        assertThat(view.credentialAccountSaved()).isTrue();
        assertThat(view.credentialPasswordSaved()).isTrue();
        assertThat(view.credentialConfigured()).isTrue();
        assertThat(view.credentialSecretName()).isEqualTo(SECRET_NAME);
    }

    @Test
    @DisplayName("已有 Secret 时再次保存密码留空表示不修改")
    void shouldKeepExistingPasswordWhenSavingWithoutPassword() {
        var client = Mockito.mock(ReactiveExtensionClient.class);
        var audit = Mockito.mock(QslAuditService.class);
        var existing = persistedSecret("BG2GVP", "old-password");

        when(client.fetch(eq(LogSyncProfile.class), eq(PROFILE_NAME))).thenReturn(Mono.just(profile("LOTW")));
        when(client.fetch(eq(Secret.class), eq(SECRET_NAME))).thenReturn(Mono.just(existing));
        when(client.update(any(Secret.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(client.update(any(LogSyncProfile.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(audit.appendAuditLog(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        var view = service(client, audit)
            .saveCredential(PROFILE_NAME, new QslLogSyncService.SaveCredentialCommand("BG2GVP", ""),
                "tester", "127.0.0.1")
            .block();

        verify(client).update(existing);
        assertThat(existing.getStringData()).containsEntry("password", "old-password");
        assertThat(view).isNotNull();
        assertThat(view.credentialPasswordSaved()).isTrue();
    }

    @Test
    @DisplayName("需要密码的数据源首次只填账号要直接拒绝，不能写成半截凭据")
    void shouldRejectMissingPasswordForLotw() {
        var client = Mockito.mock(ReactiveExtensionClient.class);
        var audit = Mockito.mock(QslAuditService.class);

        when(client.fetch(eq(LogSyncProfile.class), eq(PROFILE_NAME))).thenReturn(Mono.just(profile("LOTW")));
        when(client.fetch(eq(Secret.class), eq(SECRET_NAME))).thenReturn(Mono.empty());

        var service = service(client, audit);

        assertThatThrownBy(() -> service.saveCredential(PROFILE_NAME,
            new QslLogSyncService.SaveCredentialCommand("BG2GVP", ""), "tester", "127.0.0.1").block())
            .isInstanceOf(QslApiException.class)
            .extracting(error -> ((QslApiException) error).getCode())
            .isEqualTo("QSL-400-LS-0003");

        verify(client, never()).create(any(Secret.class));
        verify(client, never()).update(any(LogSyncProfile.class));
    }

    @Test
    @DisplayName("QRZ Logbook 只要 Key，密码留空也能创建凭据")
    void shouldCreateQrzCredentialWithoutPassword() {
        var client = Mockito.mock(ReactiveExtensionClient.class);
        var audit = Mockito.mock(QslAuditService.class);
        var created = new AtomicReference<Secret>();

        when(client.fetch(eq(LogSyncProfile.class), eq(PROFILE_NAME)))
            .thenReturn(Mono.just(profile("QRZ_LOGBOOK")));
        when(client.fetch(eq(Secret.class), eq(SECRET_NAME))).thenAnswer(invocation -> {
            var secret = created.get();
            return secret == null ? Mono.<Secret>empty() : Mono.just(secret);
        });
        when(client.create(any(Secret.class))).thenAnswer(invocation -> {
            Secret secret = invocation.getArgument(0);
            created.set(secret);
            return Mono.just(secret);
        });
        when(client.update(any(LogSyncProfile.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(audit.appendAuditLog(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        var view = service(client, audit)
            .saveCredential(PROFILE_NAME,
                new QslLogSyncService.SaveCredentialCommand("ABCD-1234-EFGH-5678", ""), "tester", "127.0.0.1")
            .block();

        assertThat(created.get().getStringData()).containsEntry("account", "ABCD-1234-EFGH-5678");
        assertThat(view).isNotNull();
        assertThat(view.credentialPasswordRequired()).isFalse();
        assertThat(view.credentialConfigured()).isTrue();
    }
}
