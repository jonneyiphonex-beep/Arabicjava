package dev.arabicjava.monitor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

@Service
public class RequestMetricsService {
	private static final int MAX_HISTORY = 500;

	private final ConcurrentLinkedDeque<RequestEvent> events = new ConcurrentLinkedDeque<>();
	private final AtomicLong totalRequests = new AtomicLong();
	private final AtomicLong totalErrors = new AtomicLong();
	private final long startedAtNanos = System.nanoTime();

	public void record(String method, String path, int status, long durationNanos) {
		String errorMessage = status >= 400 ? "HTTP " + status : null;
		RequestEvent event = new RequestEvent(
				UUID.randomUUID().toString(), Instant.now(), method, path, status, durationNanos, errorMessage);

		events.addFirst(event);
		while (events.size() > MAX_HISTORY) {
			events.pollLast();
		}
		totalRequests.incrementAndGet();
		if (status >= 400) {
			totalErrors.incrementAndGet();
		}
	}

	public List<RequestEvent> recent(int limit) {
		return events.stream().limit(limit).toList();
	}

	public List<RequestEvent> recentErrors(int limit) {
		return events.stream().filter(event -> event.status() >= 400).limit(limit).toList();
	}

	public MetricsSummary summary() {
		List<RequestEvent> snapshot = recent(MAX_HISTORY);
		long total = totalRequests.get();
		long errors = totalErrors.get();
		long sumNanos = snapshot.stream().mapToLong(RequestEvent::durationNanos).sum();
		long maxNanos = snapshot.stream().mapToLong(RequestEvent::durationNanos).max().orElse(0);
		long averageNanos = snapshot.isEmpty() ? 0 : sumNanos / snapshot.size();
		double errorRate = total == 0 ? 0 : errors * 100.0 / total;
		long uptimeSeconds = (System.nanoTime() - startedAtNanos) / 1_000_000_000L;

		return new MetricsSummary(total, errors, errorRate, averageNanos, maxNanos, uptimeSeconds, snapshot.size());
	}
}