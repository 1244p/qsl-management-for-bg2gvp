package com.bi1kbu.qslmanagement.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 外部日志同步的定时触发器。
 *
 * <p>{@code documented}（https://docs.halo.run/developer-guide/plugin/basics/server/scheduled-tasks）：
 * Halo 插件运行在独立的 Spring 应用上下文中，启动类加 {@code @EnableScheduling} 后即可用
 * {@code @Scheduled}；Spring Framework 6.1 起调度器会自动订阅方法返回的 {@code Mono}/{@code Flux}，
 * 并在插件上下文关闭时取消后续调度，因此这里直接把响应式链作为返回值，不手动 subscribe，
 * 也不自行管理生命周期。</p>
 *
 * <p>这里用 {@code fixedDelay} 每分钟触发一次「查一下哪些配置到期了」，真正的间隔由
 * {@link QslLogSyncService#runDueSyncs()} 按每个配置自己的 {@code syncIntervalMinutes}
 * 与持久化的 {@code lastSyncAt} 判定。这样做的好处是：改间隔不用重启插件注册新的调度，
 * 多副本部署时多个实例的扫描也只会让先到者真正执行（落库的 RUNNING 作业做互斥）。</p>
 *
 * <p>{@code initialDelay} 留出两分钟，避免插件热重载时立刻打外部接口。方法内部已把异常收敛，
 * 这里再兜一层，确保调度线程永远看不到失败。</p>
 */
@Component
public class QslLogSyncScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(QslLogSyncScheduler.class);
    private static final long TICK_DELAY_MS = 60_000L;
    private static final long INITIAL_DELAY_MS = 120_000L;

    private final QslLogSyncService logSyncService;

    public QslLogSyncScheduler(QslLogSyncService logSyncService) {
        this.logSyncService = logSyncService;
    }

    /** 每分钟检查一次是否有到期的同步配置。 */
    @Scheduled(fixedDelay = TICK_DELAY_MS, initialDelay = INITIAL_DELAY_MS)
    public Mono<Void> runDueSyncs() {
        return logSyncService.runDueSyncs()
            // 拉取是阻塞式 HTTP 调用，挪到弹性线程池执行，避免占住默认单线程调度器。
            .subscribeOn(Schedulers.boundedElastic())
            .doOnError(error -> LOGGER.warn("外部日志定时同步失败：{}", error.getClass().getSimpleName()))
            .onErrorResume(error -> Mono.empty());
    }
}
