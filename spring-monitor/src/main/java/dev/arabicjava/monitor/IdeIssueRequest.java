package dev.arabicjava.monitor;

public record IdeIssueRequest(
		String source,
		String project,
		String severity,
		String message,
		String file,
		Integer line,
		String code) {
}