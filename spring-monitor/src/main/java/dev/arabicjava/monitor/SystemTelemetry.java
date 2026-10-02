package dev.arabicjava.monitor;

import java.util.List;

public record SystemTelemetry(
		Double cpuUsagePercent,
		int logicalProcessors,
		long usedMemoryBytes,
		long totalMemoryBytes,
		Double memoryUsagePercent,
		long diskUsedBytes,
		long diskTotalBytes,
		Double diskUsagePercent,
		Double diskReadBytesPerSecond,
		Double diskWriteBytesPerSecond,
		String boardName,
		List<SensorReading> temperatures,
		List<SensorReading> fans,
		List<SensorReading> coolingDevices) {
}