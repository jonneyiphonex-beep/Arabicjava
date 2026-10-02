package dev.arabicjava.monitor;

import java.time.Instant;

public record RequestEvent(
		String id,
		Instant timestamp,
		String method,
		String path,
		int status,
		long durationNanos,
		String errorMessage) {
}