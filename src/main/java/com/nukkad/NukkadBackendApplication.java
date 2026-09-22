package com.nukkad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// UserDetailsServiceAutoConfiguration is excluded: auth is JWT-only against our own User
// table (AuthService does the BCrypt check directly), so Spring Security's default
// in-memory user/UserDetailsService is never consulted and only adds startup log noise.
// EnableScheduling backs the AI grant-discovery pipeline's two cron jobs (GrantDiscoveryScheduler)
// -- both are no-ops unless nukkad.discovery.enabled is explicitly turned on.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class NukkadBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(NukkadBackendApplication.class, args);
	}

}
