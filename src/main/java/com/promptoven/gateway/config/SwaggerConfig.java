package com.promptoven.gateway.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springdoc.core.models.GroupedOpenApi;
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

	@Value("${gateway.host}")
	private String gatewayHost;

	@Bean
	public SwaggerUiConfigParameters swaggerUiConfigParameters() {
		SwaggerUiConfigProperties properties = new SwaggerUiConfigProperties();
		SwaggerUiConfigParameters config = new SwaggerUiConfigParameters(properties);

		// Clear existing configurations
		config.getUrls().clear();

		// Create set of SwaggerUrl objects
		Set<SwaggerUrl> urls = new HashSet<>();

		serviceNames.stream()
			.filter(serviceName -> serviceName != null && !serviceName.trim().isEmpty())
			.forEach(serviceName -> {
				String serviceId = serviceName.toLowerCase().trim();
				String url = "/" + serviceId + "/v3/api-docs";

				log.debug("Adding Swagger URL for service: {} -> {}", serviceId, url);

				SwaggerUrl swaggerUrl = new SwaggerUrl();
				swaggerUrl.setName(serviceId);
				swaggerUrl.setUrl(url);
				swaggerUrl.setDisplayName(serviceId.toUpperCase());
				urls.add(swaggerUrl);
			});

		// Set all URLs at once
		if (!urls.isEmpty()) {
			config.setUrls(urls);
		}

		// Configure Swagger UI properties
		properties.setPath("/swagger-ui.html");
		properties.setConfigUrl("/v3/api-docs/swagger-config");
		properties.setDisableSwaggerDefaultUrl(true);
		properties.setUseRootPath(true);
		properties.setDisplayRequestDuration(true);
		properties.setDefaultModelsExpandDepth(1);
		properties.setDefaultModelExpandDepth(1);
		properties.setShowExtensions(true);
		properties.setShowCommonExtensions(true);
		properties.setTryItOutEnabled(true);
		
		properties.setOperationsSorter("alpha");
		properties.setTagsSorter("alpha");
		properties.setLayout("BaseLayout");
		properties.setPersistAuthorization(true);
		properties.setQueryConfigEnabled(true);

		// Add OAuth2 configuration if needed
		properties.setOauth2RedirectUrl(gatewayHost + "/swagger-ui/oauth2-redirect.html");

		return config;
	}

	@Bean
	public GroupedOpenApi gatewayApi() {
		return GroupedOpenApi.builder()
			.group("gateway")
			.pathsToMatch("/**")
			.build();
	}
}