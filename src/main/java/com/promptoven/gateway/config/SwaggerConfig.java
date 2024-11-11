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
import org.springframework.context.annotation.Primary;

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
	@Primary
	public SwaggerUiConfigProperties swaggerUiConfigProperties() {
		SwaggerUiConfigProperties properties = new SwaggerUiConfigProperties();
		
		// Create set of SwaggerUrl objects
		Set<SwaggerUrl> urls = new HashSet<>();

		serviceNames.stream()
			.filter(serviceName -> serviceName != null && !serviceName.trim().isEmpty())
			.forEach(serviceName -> {
				String serviceId = serviceName.toLowerCase().trim();
				String url = "http://" + gatewayHost+ "/" + serviceId + "/v3/api-docs";
				String displayName = serviceId.toUpperCase();

				log.info("Adding Swagger URL for service: {} -> {}", displayName, url);

				SwaggerUrl swaggerUrl = new SwaggerUrl();
				swaggerUrl.setName(displayName);
				swaggerUrl.setUrl(url);
				swaggerUrl.setDisplayName(displayName);
				urls.add(swaggerUrl);
			});

		// Configure Swagger UI properties
		properties.setPath("/swagger-ui.html");
		properties.setConfigUrl("/v3/api-docs/swagger-config");
		properties.setUrls(urls);
		properties.setUseRootPath(true);
		properties.setTryItOutEnabled(true);
		properties.setOperationsSorter("alpha");
		properties.setTagsSorter("alpha");
		properties.setPersistAuthorization(true);
		return properties;
	}

	@Bean
	public SwaggerUiConfigParameters swaggerUiConfigParameters(SwaggerUiConfigProperties properties) {
		return new SwaggerUiConfigParameters(properties);
	}

	@Bean
	public GroupedOpenApi gatewayApi() {
		return GroupedOpenApi.builder()
			.group("gateway")
			.pathsToMatch("/**")
			.build();
	}
}
