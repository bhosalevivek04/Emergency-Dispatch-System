package com.vivek.api_gateway.filter;

import java.util.UUID;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER_NAME = "X-Correlation-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incomingCorrelationId = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        final String correlationId = StringUtils.hasText(incomingCorrelationId)
                ? incomingCorrelationId
                : UUID.randomUUID().toString();

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HEADER_NAME, correlationId))
                .build();
        ServerWebExchange updatedExchange = exchange.mutate().request(request).build();
        updatedExchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);

        return chain.filter(updatedExchange);
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
