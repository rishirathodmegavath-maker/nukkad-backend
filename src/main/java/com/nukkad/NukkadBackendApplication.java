package com.nukkad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// UserDetailsServiceAutoConfiguration is excluded: auth is JWT-only against our own User
// table (AuthService does the BCrypt check directly), so Spring Security's default
// in-memory user/UserDetailsService is never consulted and only adds startup log noise.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class NukkadBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(NukkadBackendApplication.class, args);
	}

}
