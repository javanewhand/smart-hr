package com.smarthr.config;

import okhttp3.OkHttpClient;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;

import java.util.concurrent.TimeUnit;

@Configuration
public class HttpTimeoutConfig {

    @Bean
    public RestClientCustomizer timeoutCustomizer() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        return builder -> builder.requestFactory(new OkHttp3ClientHttpRequestFactory(client));
    }
}
