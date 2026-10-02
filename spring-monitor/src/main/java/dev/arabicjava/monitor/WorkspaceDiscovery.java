package dev.arabicjava.monitor;

import java.time.Instant;
import java.util.List;

public record WorkspaceDiscovery(String root, Instant scannedAt, List<DiscoveredProject> projects, List<ToolchainStatus> toolchains) {
}