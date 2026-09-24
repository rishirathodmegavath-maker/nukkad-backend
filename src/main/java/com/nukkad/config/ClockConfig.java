package com.nukkad.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** A single injectable {@link Clock}, so time-dependent logic (currently: affinity decay in
 *  UserTopicAffinityService) can be unit-tested against a fixed instant instead of the real clock. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
