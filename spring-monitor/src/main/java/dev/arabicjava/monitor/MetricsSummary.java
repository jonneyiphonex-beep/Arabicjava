package dev.arabicjava.monitor;

public record MetricsSummary(
		long totalRequests,
		long totalErrors,
		double errorRatePercent,
		long averageNanos,
		long maxNanos,
		long uptimeSeconds,
		int recentSampleSize) {
}