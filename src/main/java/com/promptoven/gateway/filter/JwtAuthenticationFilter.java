package com.promptoven.gateway.filter;

import java.util.Objects;

import com.promptoven.gateway.auth.JwtProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptoven.gateway.common.exception.BaseResponseStatus;
import com.promptoven.gateway.common.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


@Slf4j
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        super(Config.class);
        this.jwtProvider = jwtProvider;
    }

    public static class Config {
        // Put the configuration properties
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            if(!request.getHeaders().containsKey("Authorization")) {
                return handleException(
                        exchange,BaseResponseStatus.NO_JWT_TOKEN.getCode(),
                        BaseResponseStatus.NO_JWT_TOKEN.getMessage()
                );
            }
            String token = Objects.requireNonNull(request.getHeaders().get("Authorization"))
                .getFirst().replace("Bearer ", "");
            if(!jwtProvider.validateToken(token)) {
                return handleException(
                        exchange, BaseResponseStatus.TOKEN_NOT_VALID.getCode(),
                        BaseResponseStatus.TOKEN_NOT_VALID.getMessage()
                );
            }
            return chain.filter(exchange);
        };
    }

    private Mono<Void> handleException(ServerWebExchange exchange, Integer errorCode, String errorMessage) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<String> apiResponse = new ApiResponse<>(HttpStatus.UNAUTHORIZED.value(), errorCode, errorMessage);

        ObjectMapper objectMapper = new ObjectMapper();
        byte[] data;
        try {
            data = objectMapper.writeValueAsBytes(apiResponse);
        } catch (JsonProcessingException e) {
            data = new byte[0];
        }

        DataBuffer buffer = response.bufferFactory().wrap(data);
        return response.writeWith(Mono.just(buffer)).then(Mono.empty());
    }
}
