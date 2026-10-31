package com.bi1kbu.qslmanagement.extension.model;

import com.bi1kbu.qslmanagement.extension.QslBaseExtension;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.GVK;

/**
 * 外部日志源配置：一个「数据源 + 本台呼号」对应一条记录，账号密码等凭据只存 Halo Secret 的名称。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(
    group = "qsl-management.bi1kbu.com",
    version = "v1alpha1",
    kind = "LogSyncProfile",
    plural = "log-sync-profiles",
    singular = "log-sync-profile"
)
public class LogSyncProfile extends QslBaseExtension<LogSyncProfile.LogSyncProfileSpec, LogSyncProfile.LogSyncProfileStatus> {

    @Data
    public static class LogSyncProfileSpec {
        /** 数据源类型（LOTW / QRZ_LOGBOOK / CLUBLOG / EQSL） */
        private String sourceType;
        /** 本台呼号（大写，默认取站点资料里的个人呼号） */
        private String myCallSign;
        /** 账号名（非敏感，LoTW 用呼号本身即可留空） */
        private String accountName;
        /**
         * 兼容字段：早期版本把凭据 Secret 名写在这里。
         *
         * <p>现在 Secret 名一律由配置名派生（见 QslLogSyncService.secretName），读写不再依赖这个字段；
         * 保留声明只为让旧数据反序列化不报错，代码里不再写入也不再读取。
         */
        @Deprecated
        private String credentialSecretName;
        /** 是否启用（关闭后不参与任何同步） */
        private Boolean enabled;
        /** 是否定时自动同步（默认关闭，只能手动触发） */
        private Boolean autoSyncEnabled;
        /** 同步间隔（分钟，最小 60，默认 1440） */
        private Integer syncIntervalMinutes;
        /** 默认场景（QSO / SWL，默认 QSO） */
        private String defaultSceneType;
        /** 冲突策略（SKIP / OVERWRITE，默认 SKIP） */
        private String conflictStrategy;
        /** 单次同步上限（条，默认 5000） */
        private Integer maxRecordsPerSync;
    }

    @Data
    public static class LogSyncProfileStatus {
        /** 上次成功同步时间（ISO-8601 UTC） */
        private String lastSyncAt;
        /** 增量游标（源相关；空表示下次全量） */
        private String lastCursor;
        /** 最近错误摘要（已脱敏，不含凭据与完整 URL） */
        private String lastError;
        /** 连续失败次数（用于退避） */
        private Integer consecutiveFailures;
    }
}
