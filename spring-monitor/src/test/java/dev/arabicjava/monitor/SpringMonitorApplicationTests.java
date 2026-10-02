package dev.arabicjava.monitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"monitor.auth.username=test-admin",
		"monitor.auth.password=test-password-12345678901234567890"
})
@AutoConfigureMockMvc
class SpringMonitorApplicationTests {
	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void discoversAndroidAndXcodeProjectsButSkipsCaches(@TempDir Path workspace) throws IOException {
		Path androidProject = workspace.resolve("AndroidSample");
		Files.createDirectories(androidProject.resolve("app/src/main"));
		Files.writeString(androidProject.resolve("settings.gradle.kts"), "rootProject.name = \"AndroidSample\"");
		Files.writeString(androidProject.resolve("app/src/main/AndroidManifest.xml"), "<manifest />");

		Files.createDirectories(workspace.resolve("ios/IOSSample.xcodeproj"));
		Files.createDirectories(workspace.resolve(".gradle/HiddenAndroid"));
		Files.writeString(workspace.resolve(".gradle/HiddenAndroid/settings.gradle"), "rootProject.name = 'hidden'");
		Files.createDirectories(workspace.resolve("node_modules/HiddenXcode.xcodeproj"));

		WorkspaceDiscovery discovery = new WorkspaceDiscoveryService(workspace.toString()).discover(true);

		assertThat(discovery.projects()).extracting(DiscoveredProject::platform)
				.containsExactlyInAnyOrder("Android Studio", "Xcode");
		assertThat(discovery.projects()).extracting(DiscoveredProject::name)
				.doesNotContain("HiddenAndroid", "HiddenXcode");
	}

	@Test
	void exposesLivenessAndReadinessHealthProbes() throws Exception {
		mockMvc.perform(get("/actuator/health/liveness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
		mockMvc.perform(get("/actuator/health/readiness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

}
