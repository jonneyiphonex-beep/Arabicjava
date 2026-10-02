package dev.arabicjava.monitor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/actuator/health/**").permitAll()
						.anyRequest().authenticated())
				.httpBasic(Customizer.withDefaults());
		return http.build();
	}

	@Bean
	UserDetailsService userDetailsService(
			@Value("${monitor.auth.username:}") String username,
			@Value("${monitor.auth.password:}") String password,
			PasswordEncoder passwordEncoder) {
		if (username.isBlank() || password.length() < 24) {
			throw new IllegalStateException("Set MONITOR_AUTH_USERNAME and a MONITOR_AUTH_PASSWORD with at least 24 characters");
		}

		return new InMemoryUserDetailsManager(User.withUsername(username)
				.password(passwordEncoder.encode(password))
				.roles("ADMIN")
				.build());
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}
}