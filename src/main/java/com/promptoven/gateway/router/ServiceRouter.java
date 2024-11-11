package com.promptoven.gateway.router;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import com.promptoven.gateway.filter.JwtAuthorizationFilter;
import com.promptoven.gateway.filter.RoleBasedAuthFilter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class ServiceRouter {

	@Value("#{'${services.names}'.split(',')}")
	private List<String> serviceNames;

	@Value("#{'${authority.admin}'.split(',')}")
	private List<String> adminRoles;

	@Value("#{'${authority.seller}'.split(',')}")
	private List<String> sellerRoles;

	@Value("#{'${authority.member}'.split(',')}")
	private List<String> memberRoles;

	@Value("${gateway.host}")
	private String gatewayHost;

	@Value("${server.port}")
	private String serverPort;

	@Autowired
	private JwtAuthorizationFilter jwtAuthorizationFilter;

	@Autowired
	private RoleBasedAuthFilter roleBasedAuthFilter;

	@Bean
	public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
		var routes = builder.routes();
		routes = addSwaggerRoutes(routes);
		routes = addRoleBasedRoutes(routes);
		routes = addDefaultProtectedRoutes(routes);
		
		return routes.build();
	}

	private RouteLocatorBuilder.Builder addSwaggerRoutes(RouteLocatorBuilder.Builder routes) {
		// Add route for swagger-config
		routes = routes.route("swagger-config",
			r -> r.path("/v3/api-docs/swagger-config")
				.filters(f -> f
					.addResponseHeader("Access-Control-Allow-Origin", "*")
					.addResponseHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
					.addResponseHeader("Access-Control-Allow-Headers",
						"Authorization, Refreshtoken, Content-Type, X-Requested-With, X-XSRF-TOKEN"))
				.uri(gatewayHost)
		);

		for (String serviceName : serviceNames) {
			String serviceId = serviceName.toLowerCase();

			// Add route for API docs
			routes = routes.route(serviceId + "-api-docs",
				r -> r.path("/" + serviceId + "/v3/api-docs/**")
					.filters(f -> f
						.rewritePath("/" + serviceId + "/v3/api-docs(?<remaining>.*)", 
								   "/v3/api-docs${remaining}")
						.modifyResponseBody(String.class, String.class, (exchange, s) -> {
							if (s != null) {
								log.debug("Modifying API docs response for {}", serviceId);
								String modified = s.replaceAll(
									"\"servers\":\\s*\\[\\s*\\{\\s*\"url\":\\s*\"[^\"]*\"",
									"\"servers\":[{\"url\":" + gatewayHost + "\"
								);
								return Mono.just(modified);
							}
							return Mono.empty();
						})
						.addResponseHeader("Access-Control-Allow-Origin", "*")
						.addResponseHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
						.addResponseHeader("Access-Control-Allow-Headers",
							"Authorization, Refreshtoken, Content-Type, X-Requested-With, X-XSRF-TOKEN"))
					.uri("lb://" + serviceName)
			);
		}
		return routes;
	}


	private RouteLocatorBuilder.Builder addDefaultProtectedRoutes(RouteLocatorBuilder.Builder routes) {
		// Add default protected routes for each service
		for (String serviceName : serviceNames) {
			String serviceId = serviceName.toLowerCase();
			String baseServiceName = serviceId.replace("-service", "");

			// Default protected routes (requires authentication but no specific role)
			routes = routes.route(serviceId + "-default-routes",
				r -> r.path("/v1/" + baseServiceName + "/**")
					.filters(f -> f
						.stripPrefix(0)
						.addResponseHeader("Access-Control-Allow-Origin", "*")
						.addResponseHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
						.addResponseHeader("Access-Control-Allow-Headers",
							"Authorization, Refreshtoken, Content-Type, X-Requested-With, X-XSRF-TOKEN")
					)
					.uri("lb://" + serviceName)
			);
		}
		return routes;
	}

	private RouteLocatorBuilder.Builder addRoleBasedRoutes(RouteLocatorBuilder.Builder routes) {
		// For each service, add admin, seller, and member routes
		for (String serviceName : serviceNames) {
			String serviceId = serviceName.toLowerCase();
			String baseServiceName = serviceId.replace("-service", "");

			// Admin routes for this service
			routes = routes.route(baseServiceName + "-admin-routes",
				r -> r.path("/v1/admin/" + baseServiceName + "/**")
					.filters(f -> f
						.stripPrefix(0)
						.filter(jwtAuthorizationFilter.apply(new JwtAuthorizationFilter.Config()))
						.filter(roleBasedAuthFilter.apply(new RoleBasedAuthFilter.Config(adminRoles)))
						.addResponseHeader("Access-Control-Allow-Origin", "*")
						.addResponseHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
						.addResponseHeader("Access-Control-Allow-Headers",
							"Authorization, Refreshtoken, Content-Type, X-Requested-With, X-XSRF-TOKEN")
					)
					.uri("lb://" + serviceName)
			);

			// Seller routes for this service
			routes = routes.route(baseServiceName + "-seller-routes",
				r -> r.path("/v1/seller/" + baseServiceName + "/**")
					.filters(f -> f
						.stripPrefix(0)
						.filter(jwtAuthorizationFilter.apply(new JwtAuthorizationFilter.Config()))
						.filter(roleBasedAuthFilter.apply(new RoleBasedAuthFilter.Config(sellerRoles)))
						.addResponseHeader("Access-Control-Allow-Origin", "*")
						.addResponseHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
						.addResponseHeader("Access-Control-Allow-Headers",
							"Authorization, Refreshtoken, Content-Type, X-Requested-With, X-XSRF-TOKEN")
					)
					.uri("lb://" + serviceName)
			);

			// Member routes for this service
			routes = routes.route(baseServiceName + "-member-routes",
				r -> r.path("/v1/member/" + baseServiceName + "/**")
					.filters(f -> f
						.stripPrefix(0)
						.filter(jwtAuthorizationFilter.apply(new JwtAuthorizationFilter.Config()))
						.filter(roleBasedAuthFilter.apply(new RoleBasedAuthFilter.Config(memberRoles)))
						.addResponseHeader("Access-Control-Allow-Origin", "*")
						.addResponseHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
						.addResponseHeader("Access-Control-Allow-Headers",
							"Authorization, Refreshtoken, Content-Type, X-Requested-With, X-XSRF-TOKEN")
					)
					.uri("lb://" + serviceName)
			);
		}
		return routes;
	}
}
