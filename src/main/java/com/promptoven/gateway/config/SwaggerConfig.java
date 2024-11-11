package com.promptoven.gateway.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties.SwaggerUrl;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SwaggerConfig {

	@Value("#{'${services.names}'.split(',')}")
	private List<String> serviceNames;

	@Value("${server.port:8000}")
	private String gatewayPort;

	@Bean
	public SwaggerUiConfigParameters swaggerUiConfigParameters() {
		SwaggerUiConfigProperties properties = new SwaggerUiConfigProperties();
		SwaggerUiConfigParameters config = new SwaggerUiConfigParameters(properties);

		// Clear existing configurations
		config.getUrls().clear();

		// Create set of SwaggerUrl objects
		Set<SwaggerUrl> urls = new HashSet<>();
		SwaggerUrl gwSwagger = new SwaggerUrl();
		gwSwagger.setName("gateway");
		gwSwagger.setUrl("/v3/api-docs");
		gwSwagger.setDisplayName("Gateway");
		urls.add(gwSwagger);

		serviceNames.stream()
			.filter(serviceName -> serviceName != null && !serviceName.trim().isEmpty())
			.forEach(serviceName -> {
				String serviceId = serviceName.toLowerCase().trim();
				String url = "/" + serviceId + "/v3/api-docs";

				log.info("Adding Swagger URL for service: {} -> {}", serviceId, url);

				SwaggerUrl swaggerUrl = new SwaggerUrl();
				swaggerUrl.setName(serviceId);
				swaggerUrl.setUrl(url);
				swaggerUrl.setDisplayName(serviceId);
				urls.add(swaggerUrl);
			});

		// Set all URLs at once
		if (!urls.isEmpty()) {
			config.setUrls(urls);
		}

		// Configure Swagger UI to use Gateway URL
		properties.setConfigUrl("/v3/api-docs/swagger-config");
		properties.setPath("/swagger-ui.html");
        properties.setConfigUrl("/v3/api-docs/swagger-config");
        properties.setDisableSwaggerDefaultUrl(true);
        properties.setUseRootPath(true);

		return config;
	}
}