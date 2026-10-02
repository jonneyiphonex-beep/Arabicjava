package dev.arabicjava.monitor;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestTimingFilter extends OncePerRequestFilter {
	private final RequestMetricsService metricsService;

	public RequestTimingFilter(RequestMetricsService metricsService) {
		this.metricsService = metricsService;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return !path.startsWith("/api/") || path.startsWith("/api/monitor");
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		long startedAt = System.nanoTime();
		try {
			filterChain.doFilter(request, response);
		} catch (IOException | ServletException exception) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			throw exception;
		} catch (RuntimeException exception) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			throw exception;
		} finally {
			long durationNanos = System.nanoTime() - startedAt;
			metricsService.record(request.getMethod(), request.getRequestURI(), response.getStatus(), durationNanos);
		}
	}
}