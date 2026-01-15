package ru.mifi.booking.apigateway.filter;

import java.util.UUID;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.mifi.booking.common.http.RequestHeaders;

@Component
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(RequestHeaders.X_REQUEST_ID);

        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }

        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .header(RequestHeaders.X_REQUEST_ID, requestId)
                .build();

        exchange.getResponse().getHeaders().set(RequestHeaders.X_REQUEST_ID, requestId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
    @Override
    public int getOrder() {
        return -100;
    }
}
