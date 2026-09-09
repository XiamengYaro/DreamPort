-- 服务器资源指标:各服心跳(60s)采集的 TPS/MSPT/内存/CPU 时序
-- 数据源:dreamport-plugin 心跳上报(Bukkit.getTPS 等),Velocity 代理无 tick 循环仅报内存/CPU
-- 保留 7 天,由 ServerStatsService 采样任务顺带清理
CREATE TABLE dp_server_metrics (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    server_id      VARCHAR(64)  NOT NULL,
    tps_1m         DECIMAL(5,2) NULL,
    tps_5m         DECIMAL(5,2) NULL,
    tps_15m        DECIMAL(5,2) NULL,
    avg_tick_ms    DECIMAL(6,2) NULL,
    mem_used_mb    INT          NULL,
    mem_max_mb     INT          NULL,
    cpu_load       DECIMAL(5,4) NULL,   -- 进程 CPU 占用 0~1,-1/不可用时存 NULL
    uptime_seconds BIGINT       NULL,
    created_at     BIGINT       NOT NULL,
    KEY idx_server_metrics_server_time (server_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
