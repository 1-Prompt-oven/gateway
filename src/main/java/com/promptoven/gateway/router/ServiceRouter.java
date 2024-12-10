package com.promptoven.gateway.router;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.promptoven.gateway.filter.JwtAuthorizationFilter;
import com.promptoven.gateway.filter.RoleBasedAuthFilter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class ServiceRouter {

	private final JwtAuthorizationFilter jwtAuthorizationFilter;
	private final RoleBasedAuthFilter roleBasedAuthFilter;
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

	private static GatewayFilterSpec getDefaultGatewayFilterSpec(GatewayFilterSpec f) {
		return f
			.setResponseHeader("Access-Control-Allow-Origin", "*")
			.setResponseHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
			.setResponseHeader("Access-Control-Allow-Headers",
				"Authorization, Refreshtoken, Content-Type, X-Requested-With, X-XSRF-TOKEN, X-Session-ID");
	}

	private GatewayFilterSpec applyAuthFilters(GatewayFilterSpec f, List<String> roles) {
		return f
			.filter(jwtAuthorizationFilter.apply(new JwtAuthorizationFilter.Config()))
			.filter(roleBasedAuthFilter.apply(new RoleBasedAuthFilter.Config(roles)));
	}

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
				.filters(ServiceRouter::getDefaultGatewayFilterSpec)
				.uri(gatewayHost)
		);

		for (String serviceName : serviceNames) {
			String serviceId = serviceName.toLowerCase();

			// Add route for API docs
			routes = routes.route(serviceId + "-api-docs",
				r -> r.path("/" + serviceId + "/v3/api-docs/**")
					.filters(f -> getDefaultGatewayFilterSpec(f
						.rewritePath("/" + serviceId + "/v3/api-docs(?<remaining>.*)",
							"/v3/api-docs${remaining}")
						.modifyResponseBody(String.class, String.class, (exchange, s) -> {
							if (s != null) {
								String modified = updateSwaggerDoc(s, serviceId);
								return Mono.just(modified);
							}
							return Mono.empty();
						})))
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

			// Default routes for this service - matches /v{n}/
			routes = routes.route(serviceId + "-default-routes",
				r -> r.path("/v**/" + baseServiceName + "/**")
					.filters(ServiceRouter::getDefaultGatewayFilterSpec)
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

			// Admin routes for this service - matches /v{n}/admin/...
			routes = routes.route(baseServiceName + "-admin-routes",
				r -> r.path("/v**/admin/" + baseServiceName + "/**")
					.filters(f -> getDefaultGatewayFilterSpec(applyAuthFilters(f, adminRoles)))
					.uri("lb://" + serviceName)
			);

			// Seller routes for this service - matches /v{n}/seller/...
			routes = routes.route(baseServiceName + "-seller-routes",
				r -> r.path("/v**/seller/" + baseServiceName + "/**")
					.filters(f -> getDefaultGatewayFilterSpec(applyAuthFilters(f, sellerRoles)))
					.uri("lb://" + serviceName)
			);

			// Member routes for this service - matches /v{n}/member/...
			routes = routes.route(baseServiceName + "-member-routes",
				r -> r.path("/v**/member/" + baseServiceName + "/**")
					.filters(f -> getDefaultGatewayFilterSpec(applyAuthFilters(f, memberRoles)))
					.uri("lb://" + serviceName)
			);
		}
		return routes;
	}

	//this method is parsing the swagger doc and updating the url to point to the gateway
	//this is necessary because the swagger doc is generated by the services and they don't know about the gateway
	//so we need to update the url to point to the gateway
	//also we are adding the security scheme to the swagger doc
	//this is necessary because the services don't know about the gateway and the gateway is the one that is handling the authentication
	//so we need to add the security scheme to the swagger doc
	//this method depends on fasterxml.jackson library
	private String updateSwaggerDoc(String s, String serviceId) {
		//parse the swagger doc
		ObjectMapper mapper = new ObjectMapper();
		JsonNode node = null;
		try {
			node = mapper.readTree(s);
		} catch (JsonProcessingException e) {
			log.error("Error parsing swagger doc for service: {}", serviceId);
			return s;
		}
		//update APi Doc's Name and descriptions
		JsonNode info = node.get("info");
		if (null != info) {
			((ObjectNode)info).put("title", "Promptoven " + serviceId + " API");
			((ObjectNode)info).put("description", "API for " + serviceId + " service");
			((ObjectNode)info).put("version", "0.0.1");
			((ObjectNode)info).put("termsOfService", "/termsOfService.html");
		}
		//update the url to point to the gateway
		JsonNode servers = node.get("servers");
		if (null != servers && servers.isArray()) {
			for (JsonNode server : servers) {
				JsonNode url = server.get("url");
				if (url != null) {
					((ObjectNode)server).put("url", gatewayHost);
				}
			}
		}
		//update the security in top level of the swagger doc
		JsonNode security = node.get("security");
		if (null != security) {
			((ObjectNode)node).remove("security");
		}
		((ObjectNode)node).putArray("security")
			.addObject()
			.putArray("JWT");
		//add the security scheme to the swagger doc
		JsonNode components = node.get("components");
		if (null == components){
			((ObjectNode) node).put("components","");
		}
		JsonNode securitySchemes = components.get("securitySchemes");
		if (null != securitySchemes) {
			((ObjectNode)components).remove("securitySchemes");
		}
		((ObjectNode)components).putObject("securitySchemes");
		securitySchemes = components.get("securitySchemes");
		((ObjectNode)securitySchemes).putObject("JWT")
			.put("type", "http")
			.put("name", "JWT")
			.put("scheme", "bearer")
			.put("bearerFormat", "JWT");
		//return the updated swagger doc
		return node.toPrettyString();
	}

}
