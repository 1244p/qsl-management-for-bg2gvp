package com.bi1kbu.qslmanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bi1kbu.qslmanagement.api.QslCardRequestService;
import com.bi1kbu.qslmanagement.api.QslLogSyncService;
import com.bi1kbu.qslmanagement.extension.model.QslMigrationState;
import com.bi1kbu.qslmanagement.extension.model.StationProfile;
import com.bi1kbu.qslmanagement.extension.model.SystemSetting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.PluginContext;

@ExtendWith(MockitoExtension.class)
class QslManagementPluginTest {

    @Mock
    PluginContext context;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    QslCardRequestService qslCardRequestService;

    @Mock
    QslLogSyncService qslLogSyncService;

    @InjectMocks
    QslManagementPlugin plugin;

    @Test
    void contextLoads() {
        when(client.fetch(eq(SystemSetting.class), anyString())).thenReturn(Mono.empty());
        when(client.fetch(eq(StationProfile.class), anyString())).thenReturn(Mono.empty());
        when(client.fetch(eq(QslMigrationState.class), anyString())).thenReturn(Mono.empty());
        when(client.create(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(qslCardRequestService.reconcileReservations()).thenReturn(Mono.just(
            new QslCardRequestService.ReservationReconcileResult(0, 0, 0)
        ));
        when(qslLogSyncService.reconcileRunningJobs()).thenReturn(Mono.empty());
        when(context.getVersion()).thenReturn("2.3.22");
        plugin.start();
        verify(qslCardRequestService).reconcileReservations();
        // 重启后遗留的「进行中」作业必须被收尾，否则页面永远显示进行中。
        verify(qslLogSyncService).reconcileRunningJobs();
        plugin.stop();
    }
}
