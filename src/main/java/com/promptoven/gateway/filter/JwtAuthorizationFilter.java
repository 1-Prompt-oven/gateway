package com.promptoven.gateway.filter;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptoven.gateway.auth.JwtProvider;
import com.promptoven.gateway.common.exception.BaseResponseStatus;
import com.promptoven.gateway.common.response.ApiResponse;
import com.promptoven.gateway.repo.RedisTokenRepostirory;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class JwtAuthorizationFilter extends AbstractGatewayFilterFactory<JwtAuthorizationFilter.Config> {

	private final JwtProvider jwtProvider;
	private final RedisTokenRepostirory redisTokenRepostirory;
	private final ObjectMapper objectMapper;

	@Value("#{'${authority.admin}'.split(',')}")
	private List<String> adminRoles;

	@Value("#{'${authority.seller}'.split(',')}")
	private List<String> sellerRoles;

	@Value("#{'${authority.member}'.split(',')}")
	private List<String> memberRoles;

	public JwtAuthorizationFilter(@Autowired JwtProvider jwtProvider,
		@Autowired RedisTokenRepostirory redisTokenRepostirory) {
		super(Config.class);
		this.jwtProvider = jwtProvider;
		this.redisTokenRepostirory = redisTokenRepostirory;
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public GatewayFilter apply(Config config) {
		return (exchange, chain) -> {
			ServerHttpRequest request = exchange.getRequest();
			
			// Extract and validate token
			String token = extractToken(request);
			if (token == null) {
				return handleException(exchange, BaseResponseStatus.NO_JWT_TOKEN);
			}

			// Check if token is blocked
			if (redisTokenRepostirory.isTokenBlocked(token)) {
				log.info("Blocked token detected: {}", maskToken(token));
				return handleException(exchange, BaseResponseStatus.TOKEN_NOT_VALID);
			}

			// Validate token
			if (!jwtProvider.validateToken(token)) {
				log.info("Invalid token detected: {}", maskToken(token));
				return handleException(exchange, BaseResponseStatus.TOKEN_NOT_VALID);
			}

			// Get user role and validate access
			String userRole = jwtProvider.getUserRole(token);
			if (!validateRoleAccess(request.getPath().value(), userRole)) {
				log.warn("Access denied for role {} on path {}", userRole, request.getPath().value());
				return handleException(exchange, BaseResponseStatus.NO_PERMISSION);
			}

			return chain.filter(exchange);
		};
	}

	private String extractToken(ServerHttpRequest request) {
		if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
			return null;
		}
		String token = Objects.requireNonNull(request.getHeaders().get(HttpHeaders.AUTHORIZATION)).getFirst();
		return token != null && token.startsWith("Bearer ") ? token.substring(7) : token;
	}

	private boolean validateRoleAccess(String path, String userRole) {
		if (userRole == null) {
			return false;
		}

		if (path.startsWith("/v1/admin/")) {
			return adminRoles.contains(userRole);
		}
		if (path.startsWith("/v1/seller/")) {
			return sellerRoles.contains(userRole);
		}
		if (path.startsWith("/v1/member/")) {
			return memberRoles.contains(userRole);
		}

		// For paths that don't require specific role validation
		return true;
	}

	private Mono<Void> handleException(ServerWebExchange exchange, BaseResponseStatus status) {
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
		} catch (JsonProcessingException e) {
			log.error("Error processing JSON response", e);
			data = "{}".getBytes();
		}

		DataBuffer buffer = response.bufferFactory().wrap(data);
		return response.writeWith(Mono.just(buffer));
	}

	private String maskToken(String token) {
		if (token == null || token.length() < 10) {
			return "***";
		}
		return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
	}

	public static class Config {
		// Configuration properties if needed
	}
}
