package com.promptoven.gateway.filter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import com.promptoven.gateway.auth.JwtProvider;
import com.promptoven.gateway.common.exception.BaseResponseStatus;
import com.promptoven.gateway.repo.RedisTokenRepostirory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtAuthorizationFilter extends AbstractGatewayFilterFactory<JwtAuthorizationFilter.Config> {

	private final JwtProvider jwtProvider;
	private final RedisTokenRepostirory redisTokenRepostirory;
	private final ExceptionHandler exceptionHandler;

	public JwtAuthorizationFilter(@Autowired JwtProvider jwtProvider,
			@Autowired RedisTokenRepostirory redisTokenRepostirory,
			@Autowired ExceptionHandler exceptionHandler) {
		super(Config.class);
		this.jwtProvider = jwtProvider;
		this.redisTokenRepostirory = redisTokenRepostirory;
		this.exceptionHandler = exceptionHandler;
	}

	@Override
	public GatewayFilter apply(Config config) {
		return (exchange, chain) -> {
			ServerHttpRequest request = exchange.getRequest();

			String token = extractToken(request);
			if (token == null) {
				return exceptionHandler.handleException(exchange, BaseResponseStatus.NO_JWT_TOKEN);
			}

			// Check if token is blocked
			if (redisTokenRepostirory.isTokenBlocked(token)) {
				log.info("Blocked token detected: {}", maskToken(token));
				return exceptionHandler.handleException(exchange, BaseResponseStatus.TOKEN_NOT_VALID);
			}

			// Decrypt and validate token in one operation
			JwtProvider.TokenInfo tokenInfo = jwtProvider.validateAndDecryptToken(token);
			if (tokenInfo == null) {
				log.info("Invalid token detected: {}", maskToken(token));
				return exceptionHandler.handleException(exchange, BaseResponseStatus.TOKEN_NOT_VALID);
			}

			// Add user role to request headers for RoleBasedAuthFilter
			ServerHttpRequest modifiedRequest = request.mutate()
					.header("X-User-Role", tokenInfo.getRole())
					.build();

			return chain.filter(exchange.mutate().request(modifiedRequest).build());
		};
	}

	private String extractToken(ServerHttpRequest request) {
		if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
			return null;
		}
		String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
		return token != null ? token.replace("Bearer ", "") : null;
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
