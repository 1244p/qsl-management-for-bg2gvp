package com.bi1kbu.qslmanagement.extension.model;

import com.bi1kbu.qslmanagement.extension.QslBaseExtension;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.GVK;

/**
 * 外部日志同步作业：一次同步一行，记录拉取与落库的结果，便于页面回看与失败排查。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(
    group = "qsl-management.bi1kbu.com",
    version = "v1alpha1",
    kind = "LogSyncJob",
    plural = "log-sync-jobs",
    singular = "log-sync-job"
)
public class LogSyncJob extends QslBaseExtension<LogSyncJob.LogSyncJobSpec, LogSyncJob.LogSyncJobStatus> {

    @Data
    public static class LogSyncJobSpec {
        /** 配置资源名（关联 LogSyncProfile.metadata.name） */
        private String profileName;
        /** 数据源类型（配置删除后仍可查） */
        private String sourceType;
        /** 本台呼号 */
        private String myCallSign;
        /** 触发方式（MANUAL / SCHEDULED） */
        private String triggerType;
        /** 本次起点游标 */
        private String cursorFrom;
        /** 本次终点游标（成功后写回配置的 lastCursor） */
        private String cursorTo;
        /** 单次上限（条） */
        private Integer maxRecords;
    }

    @Data
    public static class LogSyncJobStatus {
        /** 状态（RUNNING / SUCCESS / PARTIAL / FAILED） */
        private String status;
        /** 拉取条数 */
        private Long fetchedCount;
        /** 新增条数 */
        private Long createdCount;
        /** 更新条数 */
        private Long updatedCount;
        /** 跳过条数（判重命中） */
        private Long skippedCount;
        /** 失败条数 */
        private Long failedCount;
        /** 错误摘要（已脱敏） */
        private String errorSummary;
        /** 失败行明细（最多 200 条，超出截断并在摘要标注） */
        private List<String> errorLines;
        /** 开始时间（ISO-8601 UTC） */
        private String startedAt;
        /** 结束时间（ISO-8601 UTC） */
        private String finishedAt;
    }
}
