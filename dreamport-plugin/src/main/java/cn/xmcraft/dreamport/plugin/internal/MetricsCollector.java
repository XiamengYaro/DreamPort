package cn.xmcraft.dreamport.plugin.internal;

import org.bukkit.Bukkit;

import java.lang.management.ManagementFactory;

/**
 * 资源指标采集（心跳附带，Paper 1.20+）：
 * TPS（getTPS，1/5/15 分钟）、平均 tick 耗时、JVM 内存、进程 CPU、运行时长。
 * 在异步线程调用安全（getTPS 仅读快照数组）。
 */
public final class MetricsCollector {

    private MetricsCollector() {
    }

    public record Snapshot(Double tps1m, Double tps5m, Double tps15m, Double avgTickMs,
                           Integer memUsedMb, Integer memMaxMb, Double cpuLoad, Long uptimeSeconds) {
    }

    public static Snapshot collect() {
        Double tps1m = null, tps5m = null, tps15m = null, avgTickMs = null, cpuLoad = null;
        try {
            double[] tps = Bukkit.getTPS();
            if (tps != null && tps.length >= 3) {
                tps1m = round2(tps[0]);
                tps5m = round2(tps[1]);
                tps15m = round2(tps[2]);
            }
            avgTickMs = round2(Bukkit.getAverageTickTime());
        } catch (Throwable ignored) {
            // 非 Paper/旧 API:TPS 指标留空
        }
        Runtime rt = Runtime.getRuntime();
        int memUsedMb = (int) ((rt.totalMemory() - rt.freeMemory()) / 1024 / 1024);
        int memMaxMb = (int) (rt.maxMemory() / 1024 / 1024);
        try {
            var os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
                double load = sun.getProcessCpuLoad();
                cpuLoad = load < 0 ? null : round2(load);
            }
        } catch (Throwable ignored) {
        }
        Long uptimeSeconds = null;
        try {
            uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        } catch (Throwable ignored) {
        }
        return new Snapshot(tps1m, tps5m, tps15m, avgTickMs, memUsedMb, memMaxMb, cpuLoad, uptimeSeconds);
    }

    private static Double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
