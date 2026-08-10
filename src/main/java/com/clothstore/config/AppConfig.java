package com.clothstore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * Shared RestClient for Razorpay API with short connect/read timeouts
     * so checkout fails fast instead of hanging on network issues.
     */
    @Bean(name = "razorpayRestClient")
    public RestClient razorpayRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(12_000);
        return RestClient.builder()
                .baseUrl("https://api.razorpay.com/v1")
                .requestFactory(factory)
                .build();
    }
}