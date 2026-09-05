package com.nukkad.common.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.time.Duration;

@Configuration
public class S3Config {

    // Without an explicit timeout the SDK falls back to very long defaults, so an unreachable R2
    // endpoint would hang the request thread instead of failing with a clean error the caller can
    // surface as "storage unavailable, try again." 45s comfortably covers the largest allowed
    // upload (50MB) on a slow connection without leaving requests hanging indefinitely.
    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(45);

    @Bean
    public S3Client s3Client(StorageProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .forcePathStyle(properties.forcePathStyle())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(API_CALL_TIMEOUT)
                        .apiCallAttemptTimeout(API_CALL_TIMEOUT)
                        .build());
        if (properties.endpointOverride() != null && !properties.endpointOverride().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpointOverride()));
        }
        return builder.build();
    }
}
