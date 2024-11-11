package com.promptoven.gateway.filter;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptoven.gateway.common.exception.BaseResponseStatus;
import com.promptoven.gateway.common.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class ExceptionHandler {
    private final ObjectMapper objectMapper;

    public ExceptionHandler() {
        this.objectMapper = new ObjectMapper();
    }

    public Mono<Void> handleException(ServerWebExchange exchange, BaseResponseStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<String> apiResponse = new ApiResponse<>(
            HttpStatus.UNAUTHORIZED.value(),
            status.getCode(),
            status.getMessage()
        );

        byte[] data;
        try {
            data = objectMapper.writeValueAsBytes(apiResponse);
        } catch (Exception e) {
            log.error("Error processing JSON response", e);
            data = "{}".getBytes();
        }

        DataBuffer buffer = response.bufferFactory().wrap(data);
        return response.writeWith(Mono.just(buffer));
    }
} 