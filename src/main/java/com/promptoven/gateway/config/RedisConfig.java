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
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import io.lettuce.core.ClientOptions;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

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
				.build())
			.commandTimeout(Duration.ofSeconds(5))
			.build();

		LettuceConnectionFactory factory = new LettuceConnectionFactory(serverConfig, clientConfig);
		factory.afterPropertiesSet();

		return factory;
	}

	@Bean
	public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		
		ObjectMapper mapper = new ObjectMapper();
		mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
		mapper.activateDefaultTyping(
			mapper.getPolymorphicTypeValidator(),
			ObjectMapper.DefaultTyping.NON_FINAL,
			JsonTypeInfo.As.PROPERTY
		);
		
		Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(
			mapper,
			Object.class
		);
		
		StringRedisSerializer stringSerializer = new StringRedisSerializer();
		
		template.setKeySerializer(stringSerializer);
		template.setHashKeySerializer(stringSerializer);
		template.setValueSerializer(serializer);
		template.setHashValueSerializer(serializer);
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