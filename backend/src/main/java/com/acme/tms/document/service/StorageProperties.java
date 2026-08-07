package com.acme.tms.document.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param endpoint blank against real S3, set to the MinIO address locally and in tests. When it is
 *     set, path-style addressing is forced — MinIO does not do virtual-host buckets.
 * @param allowedMimeTypes the upload allow-list. A deny-list is the wrong shape here: the set of
 *     things a browser will happily execute is open-ended, and the set of things this product needs
 *     to accept is three.
 * @param maxSizeBytes 10 MiB per 08 §14.1
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
    String bucket,
    String endpoint,
    String region,
    String accessKey,
    String secretKey,
    long maxSizeBytes,
    List<String> allowedMimeTypes,
    long uploadUrlTtlSeconds,
    long downloadUrlTtlSeconds
) {

    public boolean usesCustomEndpoint() {
        return endpoint != null && !endpoint.isBlank();
    }
}
