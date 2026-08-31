package com.nukkad.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nukkad.storage")
public record StorageProperties(
        String bucket,
        String region,
        String endpointOverride,
        boolean forcePathStyle,
        String publicBaseUrl
) {
}
