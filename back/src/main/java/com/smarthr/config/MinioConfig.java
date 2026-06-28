package com.smarthr.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "smart-hr.minio", name = "enabled", havingValue = "true")
public class MinioConfig {

    private final MinioProperties minioProperties;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
        client.setTimeout(
                minioProperties.getConnectTimeout() * 1000L,
                minioProperties.getWriteTimeout() * 1000L,
                minioProperties.getReadTimeout() * 1000L
        );
        ensureBucket(client);
        log.info("MinIO client connected to: {}, bucket: {}", minioProperties.getEndpoint(), minioProperties.getBucket());
        return client;
    }

    private void ensureBucket(MinioClient client) {
        try {
            String bucket = minioProperties.getBucket();
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket created: {}", bucket);
            }
        } catch (Exception e) {
            log.error("Failed to ensure MinIO bucket: {}", e.getMessage());
        }
    }
}
