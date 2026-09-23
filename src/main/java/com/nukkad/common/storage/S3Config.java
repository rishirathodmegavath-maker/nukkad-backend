package com.nukkad.common.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

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

    /**
     * A presigned URL must be signed for the endpoint the browser will actually reach — the public one —
     * not {@code endpointOverride}, which is the internal Docker-network address {@link #s3Client} talks
     * to and which a browser can't reach at all. Derived from {@code publicBaseUrl} (already always
     * "scheme://host[:port]/bucket" under the path-style convention this app uses everywhere) by
     * stripping the trailing "/bucket" segment, rather than requiring a second, separately-configured
     * endpoint property. Credentials/region/path-style come from the same properties {@link #s3Client}
     * uses — presigning needs no network reachability to the signing endpoint itself, only a matching
     * signature scope, so the internal-vs-public split only matters for what the resulting URL points at.
     */
    @Bean
    public S3Presigner s3Presigner(StorageProperties properties) {
        URI publicBase = URI.create(properties.publicBaseUrl());
        URI publicEndpoint = URI.create(publicBase.getScheme() + "://" + publicBase.getAuthority());
        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .endpointOverride(publicEndpoint)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.forcePathStyle())
                        .build())
                .build();
    }
}
