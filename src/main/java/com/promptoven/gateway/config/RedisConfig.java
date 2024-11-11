package com.promptoven.gateway.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.protocol.ProtocolVersion;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class RedisConfig {

	@Value("${spring.data.redis.host}")
	private String redisHost;

	@Value("${spring.data.redis.port}")
	private int redisPort;

	@Bean
	public RedisConnectionFactory redisConnectionFactory() {
		log.info("Initializing Redis connection factory for {}:{}", redisHost, redisPort);

		RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
		serverConfig.setHostName(redisHost);
		serverConfig.setPort(redisPort);

		LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
			.clientOptions(ClientOptions.builder()
				.protocolVersion(ProtocolVersion.RESP3)
				.build())
			.commandTimeout(Duration.ofSeconds(5))
			.build();

		return new LettuceConnectionFactory(serverConfig, clientConfig);
	}

	@Bean
	public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		template.setKeySerializer(new StringRedisSerializer());
		template.setValueSerializer(new StringRedisSerializer());
		template.afterPropertiesSet();
		return template;
	}

	@EventListener(ContextRefreshedEvent.class)
	public void onApplicationEvent() {
		log.info("Verifying Redis connection...");
		RedisConnectionFactory factory = redisConnectionFactory();
		try {
			RedisConnection connection = factory.getConnection();
			String pong = new String(connection.ping());
			log.info("Redis connection verified: {}", pong);
			connection.close();
		} catch (Exception e) {
			log.error("Redis connection verification failed", e);
		}
	}
}