package com.bi1kbu.qslmanagement.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bi1kbu.qslmanagement.extension.model.LogSyncJob;
import com.bi1kbu.qslmanagement.extension.model.LogSyncProfile;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;

class QslLogSyncDeleteTest {

    private static final String PROFILE_NAME = "log-sync-lotw-bg2gvp";
    private static final String SECRET_NAME = "qsl-log-sync-credential-log-sync-lotw-bg2gvp";

    private static LogSyncProfile profile() {
        var profile = new LogSyncProfile();
        var metadata = new Metadata();
        metadata.setName(PROFILE_NAME);
        profile.setMetadata(metadata);
        var spec = new LogSyncProfile.LogSyncProfileSpec();
        spec.setSourceType("LOTW");
        spec.setMyCallSign("BG2GVP");
        profile.setSpec(spec);
        profile.setStatus(new LogSyncProfile.LogSyncProfileStatus());
        return profile;
    }

    private static LogSyncJob job(String name, String profileName, String status, String startedAt) {
        var job = new LogSyncJob();
        var metadata = new Metadata();
        metadata.setName(name);
        job.setMetadata(metadata);
        var spec = new LogSyncJob.LogSyncJobSpec();
        spec.setProfileName(profileName);
        job.setSpec(spec);
        var jobStatus = new LogSyncJob.LogSyncJobStatus();
        jobStatus.setStatus(status);
        jobStatus.setStartedAt(startedAt);
        job.setStatus(jobStatus);
        return job;
    }

    private static QslLogSyncService service(
        ReactiveExtensionClient client,
        QslAuditService auditService
    ) {
        return new QslLogSyncService(client, auditService, List.of());
    }

    @Test
    @DisplayName("删除配置会连带删除凭据 Secret 与属于该配置的历史作业")
    void shouldDeleteProfileWithCredentialAndJobs() {
        var client = Mockito.mock(ReactiveExtensionClient.class);
        var auditService = Mockito.mock(QslAuditService.class);
        var profile = profile();
        var ownJob = job("log-sync-job-1", PROFILE_NAME, "FAILED", "2026-10-31T11:27:05Z");
        var otherJob = job("log-sync-job-2", "log-sync-qrz-other", "SUCCESS", "2026-10-31T11:20:00Z");

        when(client.fetch(eq(LogSyncProfile.class), eq(PROFILE_NAME))).thenReturn(Mono.just(profile));
        when(client.fetch(eq(Secret.class), eq(SECRET_NAME))).thenReturn(Mono.just(new Secret()));
        when(client.listAll(eq(LogSyncJob.class), any(), any()))
            .thenReturn(Flux.just(ownJob, otherJob));
        when(client.delete(any(LogSyncProfile.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(client.delete(any(Secret.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(client.delete(any(LogSyncJob.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(auditService.appendAuditLog(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        service(client, auditService).deleteProfile(PROFILE_NAME, "tester", "127.0.0.1").block();

        verify(client).delete(profile);
        verify(client).delete(any(Secret.class));
        verify(client).delete(ownJob);
        // 别的配置的作业不能一并删掉。
        verify(client, never()).delete(otherJob);
        verify(auditService).appendAuditLog(
            eq("删除外部日志同步配置"),
            eq("LogSyncProfile"),
            eq(PROFILE_NAME),
            any(),
            eq("tester"),
            eq("127.0.0.1")
        );
    }

    @Test
    @DisplayName("同步进行中时拒绝删除，避免写回已删除的配置")
    void shouldRejectDeleteWhenSyncIsRunning() {
        var client = Mockito.mock(ReactiveExtensionClient.class);
        var auditService = Mockito.mock(QslAuditService.class);
        var runningJob = job("log-sync-job-running", PROFILE_NAME, "RUNNING", java.time.Instant.now().toString());

        when(client.fetch(eq(LogSyncProfile.class), eq(PROFILE_NAME))).thenReturn(Mono.just(profile()));
        when(client.listAll(eq(LogSyncJob.class), any(), any())).thenReturn(Flux.just(runningJob));

        var service = service(client, auditService);

        assertThatThrownBy(() -> service.deleteProfile(PROFILE_NAME, "tester", "127.0.0.1").block())
            .isInstanceOf(QslApiException.class)
            .extracting(error -> ((QslApiException) error).getCode())
            .isEqualTo("QSL-409-LS-0001");

        verify(client, never()).delete(any(LogSyncProfile.class));
        verify(client, never()).delete(any(Secret.class));
        verify(auditService, never()).appendAuditLog(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("配置不存在时删除返回 404")
    void shouldRejectDeleteWhenProfileMissing() {
        var client = Mockito.mock(ReactiveExtensionClient.class);
        var auditService = Mockito.mock(QslAuditService.class);
        when(client.fetch(eq(LogSyncProfile.class), eq(PROFILE_NAME))).thenReturn(Mono.empty());

        var service = service(client, auditService);

        assertThatThrownBy(() -> service.deleteProfile(PROFILE_NAME, "tester", "127.0.0.1").block())
            .isInstanceOf(QslApiException.class)
            .extracting(error -> ((QslApiException) error).getCode())
            .isEqualTo("QSL-404-LS-0001");
    }

    @Test
    @DisplayName("凭据缺失时删除仍然成功（配置与作业照删）")
    void shouldDeleteProfileWhenCredentialMissing() {
        var client = Mockito.mock(ReactiveExtensionClient.class);
        var auditService = Mockito.mock(QslAuditService.class);
        var profile = profile();

        when(client.fetch(eq(LogSyncProfile.class), eq(PROFILE_NAME))).thenReturn(Mono.just(profile));
        when(client.fetch(eq(Secret.class), eq(SECRET_NAME))).thenReturn(Mono.empty());
        when(client.listAll(eq(LogSyncJob.class), any(), any())).thenReturn(Flux.empty());
        when(client.delete(any(LogSyncProfile.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(auditService.appendAuditLog(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        service(client, auditService).deleteProfile(PROFILE_NAME, "tester", "127.0.0.1").block();

        verify(client).delete(profile);
        verify(client, never()).delete(any(Secret.class));
        assertThat(profile.getMetadata().getName()).isEqualTo(PROFILE_NAME);
    }

    @Test
    @DisplayName("重置游标要清空游标与错误状态，且不动已同步记录")
    void shouldResetCursorWithoutDeletingRecords() {
        var client = Mockito.mock(ReactiveExtensionClient.class);
        var auditService = Mockito.mock(QslAuditService.class);
        var profile = profile();
        var status = new LogSyncProfile.LogSyncProfileStatus();
        status.setLastCursor("2026-10-23 09:19:02");
        status.setLastError("旧的错误");
        status.setConsecutiveFailures(3);
        profile.setStatus(status);

        when(client.fetch(eq(LogSyncProfile.class), eq(PROFILE_NAME))).thenReturn(Mono.just(profile));
        when(client.listAll(eq(LogSyncJob.class), any(), any())).thenReturn(Flux.empty());
        when(client.update(any(LogSyncProfile.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(client.fetch(eq(Secret.class), eq(SECRET_NAME))).thenReturn(Mono.empty());
        when(auditService.appendAuditLog(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        service(client, auditService).resetCursor(PROFILE_NAME, "tester", "127.0.0.1").block();

        // 游标一旦写错，增量永远补不回历史，所以必须能退回初始状态。
        assertThat(profile.getStatus().getLastCursor()).isEmpty();
        assertThat(profile.getStatus().getLastError()).isEmpty();
        assertThat(profile.getStatus().getConsecutiveFailures()).isZero();
        verify(client, never()).delete(any(LogSyncProfile.class));
        verify(auditService).appendAuditLog(
            eq("重置外部日志同步游标"),
            eq("LogSyncProfile"),
            eq(PROFILE_NAME),
            any(),
            eq("tester"),
            eq("127.0.0.1")
        );
    }

    @Test
    @DisplayName("同步进行中时拒绝重置游标")
    void shouldRejectResetCursorWhenSyncIsRunning() {
        var client = Mockito.mock(ReactiveExtensionClient.class);
        var auditService = Mockito.mock(QslAuditService.class);
        var runningJob = job("log-sync-job-running", PROFILE_NAME, "RUNNING", java.time.Instant.now().toString());

        when(client.fetch(eq(LogSyncProfile.class), eq(PROFILE_NAME))).thenReturn(Mono.just(profile()));
        when(client.listAll(eq(LogSyncJob.class), any(), any())).thenReturn(Flux.just(runningJob));

        var service = service(client, auditService);

        assertThatThrownBy(() -> service.resetCursor(PROFILE_NAME, "tester", "127.0.0.1").block())
            .isInstanceOf(QslApiException.class)
            .extracting(error -> ((QslApiException) error).getCode())
            .isEqualTo("QSL-409-LS-0001");

        verify(client, never()).update(any(LogSyncProfile.class));
    }
}
