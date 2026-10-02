package dev.arabicjava.monitor;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdeIssueService {
	private static final int MAX_HISTORY = 500;
	private final ConcurrentLinkedDeque<IdeIssueEvent> issues = new ConcurrentLinkedDeque<>();

	public IdeIssueEvent report(IdeIssueRequest request) {
		if (request == null) {
			throw badRequest("Issue payload is required");
		}
		if (request.line() != null && request.line() < 1) {
			throw badRequest("Line must be a positive number");
		}

		IdeIssueEvent issue = new IdeIssueEvent(
				UUID.randomUUID().toString(),
				normalizeSource(request.source()),
				truncate(required(request.project(), "project"), 160),
				normalizeSeverity(request.severity()),
				truncate(required(request.message(), "message"), 4000),
				truncate(optional(request.file()), 1000),
				request.line(),
				truncate(optional(request.code()), 160),
				Instant.now());

		issues.addFirst(issue);
		while (issues.size() > MAX_HISTORY) {
			issues.pollLast();
		}
		return issue;
	}

	public List<IdeIssueEvent> recent(String source, int limit) {
		String sourceFilter = source == null || source.isBlank() ? null : normalizeSource(source);
		return issues.stream()
				.filter(issue -> sourceFilter == null || issue.source().equals(sourceFilter))
				.limit(limit)
				.toList();
	}

	private String normalizeSource(String source) {
		if (source == null || source.isBlank()) {
			throw badRequest("Source must be android-studio or xcode");
		}
		return switch (source.trim().toLowerCase(Locale.ROOT).replace('_', '-')) {
			case "android", "androidstudio", "android-studio" -> "android-studio";
			case "xcode", "x-code" -> "xcode";
			default -> throw badRequest("Source must be android-studio or xcode");
		};
	}

	private String normalizeSeverity(String severity) {
		if (severity == null) {
			throw badRequest("Severity must be error, warning, or info");
		}
		return switch (severity.trim().toLowerCase(Locale.ROOT)) {
			case "error", "warning", "info" -> severity.trim().toLowerCase(Locale.ROOT);
			default -> throw badRequest("Severity must be error, warning, or info");
		};
	}

	private String required(String value, String field) {
		if (value == null || value.isBlank()) {
			throw badRequest(field + " is required");
		}
		return value.trim();
	}

	private String optional(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private String truncate(String value, int maxLength) {
		return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
	}

	private ResponseStatusException badRequest(String reason) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
	}
}