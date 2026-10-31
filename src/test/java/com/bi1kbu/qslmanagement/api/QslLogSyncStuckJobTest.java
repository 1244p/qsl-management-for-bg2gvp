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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

/**
 * 卡住的同步作业的收尾行为。
 *
 * <p>作业停在 RUNNING 有两层危害：页面上永远显示「进行中」，以及并发保护会把
 * 「重置游标」「删除配置」都拒掉。这里锁住两条自救通道——强制停止与启动时收尾。
 */
class QslLogSyncStuckJobTest {

    private static final String PROFILE_NAME = "log-sync-lotw-bg2gvp";

    private static LogSyncProfile profile() {
        var profile = new LogSyncProfile();
        var metadata = new Metadata();
        metadata.setName(PROFILE_NAME);
        profile.setMetadata(metadata);
        var spec = new LogSyncProfile.LogSyncProfileSpec();
        spec.setSourceType("LOTW");
        spec.setMyCallSign("BG2GVP");
        spec.setEnabled(Boolean.TRUE);
        profile.setSpec(spec);
        profile.setStatus(new LogSyncProfile.LogSyncProfileStatus());
        return profile;
    }

    private static LogSyncJob job(String name, String status, String startedAt) {
        var job = new LogSyncJob();
        var metadata = new Metadata();
        metadata.setName(name);
        job.setMetadata(metadata);
        var spec = new LogSyncJob.LogSyncJobSpec();
        spec.setProfileName(PROFILE_NAME);
        job.setSpec(spec);
        var jobStatus = new LogSyncJob.LogSyncJobStatus();
        jobStatus.setStatus(status);
        jobStatus.setStartedAt(startedAt);
        job.setStatus(jobStatus);
        return job;
    }

    private static QslLogSyncService service(ReactiveExtensionClient client,
        QslAuditService auditService) {
        return new QslLogSyncService(client, auditService, List.of());
    }

    @Test
    @DisplayName("强制停止把进行中的作业收尾成失败并写审计")
    void shouldForceStopRunningJob() {
        var client = Mockito.mock(ReactiveExtensionClient.class);
        var auditService = Mockito.mock(QslAuditService.class);
        var running = job("log-sync-job-stuck", "RUNNING", Instant.now().toString());

        when(client.fetch(eq(LogSyncProfile.class), eq(PROFILE_NAME)))
            .thenReturn(Mono.just(profile()));
        when(client.listAll(eq(LogSyncJob.class), any(), any())).thenReturn(Flux.just(running));
        when(client.update(any(LogSyncJob.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(auditService.appendAuditLog(any(), any(), any(), any(), any(), any()))
            .thenReturn(Mono.empty());

        var view = service(client, auditService).forceStop(PROFILE_NAME, "tester", "127.0.0.1").block();

        assertThat(view).isNotNull();
        assertThat(view.status()).isEqualTo("FAILED");
        assertThat(view.message()).contains("强制停止");
        assertThat(view.finishedAt()).isNotBlank();
        // 收尾后必须留下「为什么结束」，否则页面上只剩一个 FAILED。
        assertThat(running.getStatus().getErrorLines()).isNotEmpty();
        verify(client).update(running);
        verify(auditService).appendAuditLog(
            eq("强制停止外部日志同步"),
            eq("LogSyncProfile"),
            eq(PROFILE_NAME),
            any(),
            eq("tester"),
            eq("127.0.0.1")
        );
    }

    @Test
    @DisplayName("没有进行中的作业时强制停止返回冲突码")
    void shouldRejectForceStopWhenNoRunningJob() {
        var client = Mockito.mock(ReactiveExtensionClient.class);
        var auditService = Mockito.mock(QslAuditService.class);
        var finished = job("log-sync-job-done", "SUCCESS", Instant.now().toString());

        when(client.fetch(eq(LogSyncProfile.class), eq(PROFILE_NAME)))
            .thenReturn(Mono.just(profile()));
        when(client.listAll(eq(LogSyncJob.class), any(), any())).thenReturn(Flux.just(finished));

        var service = service(client, auditService);

        assertThatThrownBy(() -> service.forceStop(PROFILE_NAME, "tester", "127.0.0.1").block())
            .isInstanceOf(QslApiException.class)
            .extracting(error -> ((QslApiException) error).getCode())
            .isEqualTo("QSL-409-LS-0002");
        verify(client, never()).update(any(LogSyncJob.class));
    }

    @Test
    @DisplayName("插件启动时把遗留的进行中作业收尾成失败")
    void shouldReconcileLeftoverRunningJobsOnStartup() {
        var client = Mockito.mock(ReactiveExtensionClient.class);
        var auditService = Mockito.mock(QslAuditService.class);
        var leftover = job("log-sync-job-left", "RUNNING", "2026-10-31T12:51:24Z");
        var done = job("log-sync-job-done", "SUCCESS", "2026-10-31T12:44:48Z");

        when(client.listAll(eq(LogSyncJob.class), any(), any())).thenReturn(Flux.just(leftover, done));
        when(client.update(any(LogSyncJob.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service(client, auditService).reconcileRunningJobs().block();

        // 重启后不可能还有作业在跑：遗留的必须收尾，已完成的不许再动。
        assertThat(leftover.getStatus().getStatus()).isEqualTo("FAILED");
        assertThat(leftover.getStatus().getErrorSummary()).contains("中断");
        assertThat(done.getStatus().getStatus()).isEqualTo("SUCCESS");
        verify(client).update(leftover);
        verify(client, never()).update(done);
    }

    @Test
    @DisplayName("发起同步时如果已有进行中的作业，仍然拒绝，避免并发写同一批记录")
    void shouldRejectStartSyncWhenAnotherJobIsRunning() {
        var client = Mockito.mock(ReactiveExtensionClient.class);
        var auditService = Mockito.mock(QslAuditService.class);
        var running = job("log-sync-job-running", "RUNNING", Instant.now().toString());

        when(client.fetch(eq(LogSyncProfile.class), eq(PROFILE_NAME)))
            .thenReturn(Mono.just(profile()));
        when(client.listAll(eq(LogSyncJob.class), any(), any())).thenReturn(Flux.just(running));

        var service = service(client, auditService);

        assertThatThrownBy(() -> service.startSync(PROFILE_NAME, "MANUAL", "tester", "127.0.0.1").block())
            .isInstanceOf(QslApiException.class)
            .extracting(error -> ((QslApiException) error).getCode())
            .isEqualTo("QSL-409-LS-0001");
        verify(client, never()).create(any());
    }

    @Test
    @DisplayName("上次同步留下的陈旧 RUNNING 作业不再阻塞新的同步")
    void shouldIgnoreStaleRunningJob() {
        var client = Mockito.mock(ReactiveExtensionClient.class);
        var auditService = Mockito.mock(QslAuditService.class);
        var stale = job("log-sync-job-stale", "RUNNING",
            Instant.now().minus(30, ChronoUnit.MINUTES).toString());
        var created = job("log-sync-job-new", "RUNNING", Instant.now().toString());

        when(client.fetch(eq(LogSyncProfile.class), eq(PROFILE_NAME)))
            .thenReturn(Mono.just(profile()));
        when(client.listAll(eq(LogSyncJob.class), any(), any())).thenReturn(Flux.just(stale));
        when(client.create(any(LogSyncJob.class))).thenReturn(Mono.just(created));
        // 后台链会继续跑一小段，这里让凭据缺失直接失败，把作业收尾成 FAILED。
        when(client.fetch(eq(run.halo.app.extension.Secret.class), any()))
            .thenReturn(Mono.empty());
        when(client.update(any(LogSyncJob.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(client.update(any(LogSyncProfile.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(client.fetch(eq(com.bi1kbu.qslmanagement.extension.model.StationProfile.class), any()))
            .thenReturn(Mono.empty());
        when(client.listAll(eq(com.bi1kbu.qslmanagement.extension.model.QsoRecord.class), any(), any()))
            .thenReturn(Flux.empty());

        var view = service(client, auditService)
            .startSync(PROFILE_NAME, "MANUAL", "tester", "127.0.0.1").block();

        assertThat(view).isNotNull();
        assertThat(view.message()).contains("任务已启动");
        verify(client).create(any(LogSyncJob.class));
    }
}
