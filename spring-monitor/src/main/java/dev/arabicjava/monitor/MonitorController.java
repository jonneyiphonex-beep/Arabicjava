package dev.arabicjava.monitor;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitor")
public class MonitorController {
	private final RequestMetricsService metricsService;
	private final SystemTelemetryService telemetryService;
	private final IdeIssueService ideIssueService;
	private final WorkspaceDiscoveryService discoveryService;

	public MonitorController(
			RequestMetricsService metricsService,
			SystemTelemetryService telemetryService,
			IdeIssueService ideIssueService,
			WorkspaceDiscoveryService discoveryService) {
		this.metricsService = metricsService;
		this.telemetryService = telemetryService;
		this.ideIssueService = ideIssueService;
		this.discoveryService = discoveryService;
	}

	@GetMapping("/summary")
	public MetricsSummary summary() {
		return metricsService.summary();
	}

	@GetMapping("/system")
	public SystemTelemetry system() {
		return telemetryService.snapshot();
	}

	@GetMapping("/discovery")
	public WorkspaceDiscovery discover(@RequestParam(defaultValue = "false") boolean refresh) {
		return discoveryService.discover(refresh);
	}

	@GetMapping("/requests")
	public List<RequestEvent> requests(@RequestParam(defaultValue = "20") int limit) {
		return metricsService.recent(clampLimit(limit));
	}

	@GetMapping("/errors")
	public List<RequestEvent> errors(@RequestParam(defaultValue = "10") int limit) {
		return metricsService.recentErrors(clampLimit(limit));
	}

	@GetMapping("/ide-issues")
	public List<IdeIssueEvent> ideIssues(
			@RequestParam(required = false) String source,
			@RequestParam(defaultValue = "50") int limit) {
		return ideIssueService.recent(source, clampIssueLimit(limit));
	}

	@PostMapping("/ide-issues")
	public IdeIssueEvent reportIdeIssue(@RequestBody IdeIssueRequest request) {
		return ideIssueService.report(request);
	}

	private int clampLimit(int limit) {
		return Math.max(1, Math.min(limit, 100));
	}

	private int clampIssueLimit(int limit) {
		return Math.max(1, Math.min(limit, 200));
	}
}