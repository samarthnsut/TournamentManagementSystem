package com.acme.tms.document.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/** One place that knows whether we are talking to S3 or to MinIO; nothing downstream cares. */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    S3Client s3Client(StorageProperties properties) {
        var builder = S3Client.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())));

        if (properties.usesCustomEndpoint()) {
            builder.endpointOverride(URI.create(properties.endpoint()))
                // MinIO serves buckets as a path, not as a subdomain.
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }

        return builder.build();
    }

    @Bean
    S3Presigner s3Presigner(StorageProperties properties) {
        var builder = S3Presigner.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())));

        if (properties.usesCustomEndpoint()) {
            builder.endpointOverride(URI.create(properties.endpoint()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }

        return builder.build();
    }
}
