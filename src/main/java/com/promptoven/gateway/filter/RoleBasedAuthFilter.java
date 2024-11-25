package com.promptoven.gateway.filter;

import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import com.promptoven.gateway.common.exception.BaseResponseStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RoleBasedAuthFilter extends AbstractGatewayFilterFactory<RoleBasedAuthFilter.Config> {

	private final ExceptionHandler exceptionHandler;

	public RoleBasedAuthFilter(ExceptionHandler exceptionHandler) {
		super(Config.class);
		this.exceptionHandler = exceptionHandler;
	}

	@Override
	public GatewayFilter apply(Config config) {
		return (exchange, chain) -> {
			ServerHttpRequest request = exchange.getRequest();
			String userRole = request.getHeaders().getFirst("X-User-Role");

			if (userRole == null) {
				return exceptionHandler.handleException(exchange, BaseResponseStatus.NO_PERMISSION);
			}

			if (!config.getPermittedRoles().contains(userRole)) {
				log.warn("Access denied for role {} on path {}, Permitted on {}", userRole, request.getPath().value(),
					config.getPermittedRoles().toString());
				return exceptionHandler.handleException(exchange, BaseResponseStatus.NO_PERMISSION);
			}

			return chain.filter(exchange);
		};
	}

	public static class Config {
		private List<String> permittedRoles;

		public Config(List<String> permittedRoles) {
			this.permittedRoles = permittedRoles;
		}

		public List<String> getPermittedRoles() {
			return permittedRoles;
		}
	}
}
