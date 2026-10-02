package dev.arabicjava.monitor;

import java.time.Instant;

public record IdeIssueEvent(
		String id,
		String source,
		String project,
		String severity,
		String message,
		String file,
		Integer line,
		String code,
		Instant reportedAt) {
}