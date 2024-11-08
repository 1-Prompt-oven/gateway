package com.promptoven.gateway.router;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.promptoven.gateway.filter.JwtAuthorizationFilter;

@Configuration
public class ServiceRouter {

	@Value("#{'${services.names}'.split(',')}")
	private List<String> serviceNames;

	@Autowired
	private JwtAuthorizationFilter jwtAuthorizationFilter;

	@Bean
	public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
		var routes = builder.routes();
		return addRoleBasedRoutes(addDefaultProtectedRoutes(routes)).build();
	}

	private RouteLocatorBuilder.Builder addRoleBasedRoutes(RouteLocatorBuilder.Builder routes) {
		// For each service, add admin, seller, and member routes
		for (String serviceName : serviceNames) {
			String serviceId = serviceName.toLowerCase();
			String baseServiceName = serviceId.replace("-service", "");
			System.out.println("Adding routes for service: " + serviceName + " with base name: " + baseServiceName);

			// Admin routes for this service
			routes = routes.route(baseServiceName + "-admin-routes",
				r -> r.path("/v1/admin/" + baseServiceName + "/**")
					.filters(f -> f.stripPrefix(0)
						.filter(jwtAuthorizationFilter.apply(new JwtAuthorizationFilter.Config())))
					.uri("lb://" + serviceName)
			);

			// Seller routes for this service
			routes = routes.route(baseServiceName + "-seller-routes",
				r -> r.path("/v1/seller/" + baseServiceName + "/**")
					.filters(f -> f.stripPrefix(0)
						.filter(jwtAuthorizationFilter.apply(new JwtAuthorizationFilter.Config())))
					.uri("lb://" + serviceName)
			);

			// Member routes for this service
			routes = routes.route(baseServiceName + "-member-routes",
				r -> r.path("/v1/member/" + baseServiceName + "/**")
					.filters(f -> f.stripPrefix(0)
						.filter(jwtAuthorizationFilter.apply(new JwtAuthorizationFilter.Config())))
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
					.filters(f -> f.stripPrefix(0))
					.uri("lb://" + serviceName)
			);
		}
		return routes;
	}
}
