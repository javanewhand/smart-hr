package com.smarthr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "smart-hr.minio")
public class MinioProperties {

    private boolean enabled = true;

    private String endpoint = "http://localhost:9000";

    private String accessKey = "minioadmin";

    private String secretKey = "minioadmin";

    private String bucket = "smarthr-resumes";

    private int connectTimeout = 10;

    private int writeTimeout = 60;

    private int readTimeout = 10;
}
