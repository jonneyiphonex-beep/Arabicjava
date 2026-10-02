package dev.arabicjava.monitor;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceDiscoveryService {
	private static final int MAX_DEPTH = 8;
	private static final int MAX_ENTRIES = 10_000;
	private static final int MAX_PROJECTS = 100;
	private static final Duration CACHE_LIFETIME = Duration.ofSeconds(30);
	private static final Set<String> IGNORED_DIRECTORIES = Set.of(
			".git", ".gradle", ".idea", ".vscode", "build", "DerivedData", "node_modules", "Pods", "target", "vendor");
	private static final List<ToolDefinition> TOOL_DEFINITIONS = List.of(
			new ToolDefinition("java", "Java"),
			new ToolDefinition("gradle", "Gradle"),
			new ToolDefinition("adb", "Android SDK / ADB"),
			new ToolDefinition("xcodebuild", "Xcode CLI"),
			new ToolDefinition("swift", "Swift"),
			new ToolDefinition("flutter", "Flutter"),
			new ToolDefinition("node", "Node.js"),
			new ToolDefinition("npm", "npm"),
			new ToolDefinition("mvn", "Maven"));

	private final Path scanRoot;
	private volatile WorkspaceDiscovery cached;
	private volatile Instant cachedAt;

	public WorkspaceDiscoveryService(@Value("${monitor.discovery.root:..}") String configuredRoot) {
		Path configured = Path.of(configuredRoot);
		this.scanRoot = (configured.isAbsolute()
				? configured
				: Path.of(System.getProperty("user.dir")).resolve(configured)).toAbsolutePath().normalize();
	}

	public WorkspaceDiscovery discover(boolean forceRefresh) {
		WorkspaceDiscovery current = cached;
		Instant lastScan = cachedAt;
		if (!forceRefresh && current != null && lastScan != null
				&& Duration.between(lastScan, Instant.now()).compareTo(CACHE_LIFETIME) < 0) {
			return current;
		}
		synchronized (this) {
			current = cached;
			lastScan = cachedAt;
			if (!forceRefresh && current != null && lastScan != null
					&& Duration.between(lastScan, Instant.now()).compareTo(CACHE_LIFETIME) < 0) {
				return current;
			}
			cached = scan();
			cachedAt = Instant.now();
			return cached;
		}
	}

	private WorkspaceDiscovery scan() {
		List<DiscoveredProject> projects = new ArrayList<>();
		if (Files.isDirectory(scanRoot)) {
			try {
				int[] visitedEntries = { 0 };
				Files.walkFileTree(scanRoot, Set.of(), MAX_DEPTH, new SimpleFileVisitor<>() {
					@Override
					public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
						if (!directory.equals(scanRoot)
								&& IGNORED_DIRECTORIES.contains(directory.getFileName().toString())) {
							return FileVisitResult.SKIP_SUBTREE;
						}
					if (++visitedEntries[0] > MAX_ENTRIES || projects.size() >= MAX_PROJECTS) {
						return FileVisitResult.TERMINATE;
					}
					DiscoveredProject project = identifyProject(directory);
					if (project != null) {
						projects.add(project);
						return FileVisitResult.SKIP_SUBTREE;
					}
					return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
						return ++visitedEntries[0] > MAX_ENTRIES || projects.size() >= MAX_PROJECTS
								? FileVisitResult.TERMINATE
								: FileVisitResult.CONTINUE;
					}
				});
			} catch (IOException ignored) {
				projects.clear();
			}
		}

		return new WorkspaceDiscovery(
				scanRoot.toString(),
				Instant.now(),
				List.copyOf(projects),
				TOOL_DEFINITIONS.stream()
						.map(tool -> new ToolchainStatus(tool.id(), tool.name(), isExecutableOnPath(tool.id())))
						.toList());
	}

	private DiscoveredProject identifyProject(Path directory) {
		String name = directory.getFileName() == null ? directory.toString() : directory.getFileName().toString();
		if (name.endsWith(".xcodeproj") || name.endsWith(".xcworkspace")) {
			return new DiscoveredProject(stripSuffix(name), "Xcode", relativePath(directory), name.endsWith(".xcodeproj") ? "*.xcodeproj" : "*.xcworkspace");
		}

		if (Files.isRegularFile(directory.resolve("pubspec.yaml"))
				&& (Files.isDirectory(directory.resolve("android")) || Files.isDirectory(directory.resolve("ios")))) {
			return new DiscoveredProject(name, "Flutter", relativePath(directory), "pubspec.yaml + android/ios");
		}

		Path packageJson = directory.resolve("package.json");
		if (Files.isRegularFile(packageJson) && hasText(packageJson, "react-native")
				&& (Files.isDirectory(directory.resolve("android")) || Files.isDirectory(directory.resolve("ios")))) {
			return new DiscoveredProject(name, "React Native", relativePath(directory), "package.json + android/ios");
		}

		boolean gradleProject = hasAnyFile(directory, "settings.gradle", "settings.gradle.kts", "build.gradle", "build.gradle.kts");
		if (gradleProject && (hasAndroidManifest(directory) || hasAndroidPlugin(directory))) {
			return new DiscoveredProject(name, "Android Studio", relativePath(directory), "Gradle + Android manifest/plugin");
		}
		if (Files.isRegularFile(directory.resolve("Package.swift"))) {
			return new DiscoveredProject(name, "Swift / Xcode", relativePath(directory), "Package.swift");
		}

		Path pom = directory.resolve("pom.xml");
		if (Files.isRegularFile(pom) && hasText(pom, "spring-boot")) {
			return new DiscoveredProject(name, "Spring Boot", relativePath(directory), "pom.xml");
		}
		return null;
	}

	private boolean hasAndroidManifest(Path directory) {
		return Files.isRegularFile(directory.resolve("AndroidManifest.xml"))
				|| Files.isRegularFile(directory.resolve("src/main/AndroidManifest.xml"))
				|| Files.isRegularFile(directory.resolve("app/src/main/AndroidManifest.xml"));
	}

	private boolean hasAndroidPlugin(Path directory) {
		return hasText(directory.resolve("build.gradle"), "com.android.application")
				|| hasText(directory.resolve("build.gradle"), "com.android.library")
				|| hasText(directory.resolve("build.gradle.kts"), "com.android.application")
				|| hasText(directory.resolve("build.gradle.kts"), "com.android.library");
	}

	private boolean hasAnyFile(Path directory, String... names) {
		for (String name : names) {
			if (Files.isRegularFile(directory.resolve(name))) {
				return true;
			}
		}
		return false;
	}

	private boolean hasText(Path file, String text) {
		if (!Files.isRegularFile(file)) {
			return false;
		}
		try (var reader = Files.newBufferedReader(file)) {
			char[] buffer = new char[8192];
			int read = reader.read(buffer);
			return read > 0 && new String(buffer, 0, read).contains(text);
		} catch (IOException | SecurityException ignored) {
			return false;
		}
	}

	private String relativePath(Path directory) {
		return scanRoot.relativize(directory).toString().replace(directory.getFileSystem().getSeparator(), "/");
	}

	private String stripSuffix(String name) {
		return name.replaceFirst("\\.(xcodeproj|xcworkspace)$", "");
	}

	private boolean isExecutableOnPath(String executable) {
		String path = System.getenv("PATH");
		if (path == null || path.isBlank()) {
			return false;
		}
		String[] extensions = System.getProperty("os.name", "").toLowerCase().contains("win")
				? new String[] { "", ".exe", ".cmd", ".bat" }
				: new String[] { "" };
		for (String directory : path.split(java.util.regex.Pattern.quote(System.getProperty("path.separator")))) {
			for (String extension : extensions) {
				Path candidate = Path.of(directory).resolve(executable + extension);
				if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
					return true;
				}
			}
		}
		return false;
	}

	private record ToolDefinition(String id, String name) {
	}
}