package ru.mifi.booking.bookingservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.client.RestTemplate;
@Configuration
public class RestTemplateConfig {

    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public RestTemplateConfig(
            @Value("${services.hotel-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${services.hotel-service.read-timeout-ms:3000}") int readTimeoutMs
    ) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
