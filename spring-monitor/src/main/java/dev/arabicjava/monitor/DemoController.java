package dev.arabicjava.monitor;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class DemoController {
	@GetMapping("/ping")
	public Map<String, Object> ping() {
		return Map.of("status", "ok", "timestamp", Instant.now().toString());
	}

	@GetMapping("/work")
	public Map<String, Object> work(@RequestParam(defaultValue = "150000") int iterations) {
		int boundedIterations = Math.max(1, Math.min(iterations, 1_500_000));
		long checksum = 0;
		for (int index = 0; index < boundedIterations; index++) {
			checksum = checksum * 31 + index;
		}
		return Map.of("iterations", boundedIterations, "checksum", checksum);
	}

	@GetMapping("/error")
	public ResponseEntity<Map<String, Object>> error() {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "خطأ تجريبي مقصود", "timestamp", Instant.now().toString()));
	}
}