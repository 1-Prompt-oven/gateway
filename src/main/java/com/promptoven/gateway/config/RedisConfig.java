package com.promptoven.gateway.config;

import java.net.InetSocketAddress;
import java.net.Socket;
import io.lettuce.core.SocketOptions;
import jakarta.annotation.PostConstruct;

import java.io.OutputStream;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import io.lettuce.core.ClientOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.context.event.ContextRefreshedEvent;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.resource.DefaultClientResources;

@Configuration
@Slf4j
public class RedisConfig {

	@Value("${spring.data.redis.host}")
	private String redisHost;

	@Value("${spring.data.redis.port}")
	private int redisPort;

	@Bean
	public RedisConnectionFactory redisConnectionFactory() {
		log.info("Initializing Redis connection factory...");
		
		String resolvedHost = resolveHostAddress();
		log.info("Resolved Redis host {} to {}", redisHost, resolvedHost);
		
		RedisURI redisUri = RedisURI.builder()
			.withHost(resolvedHost)
			.withPort(redisPort)
			.withTimeout(Duration.ofSeconds(30))
			.build();
		
		RedisClient redisClient = RedisClient.create(redisUri);
		redisClient.setOptions(ClientOptions.builder()
			.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
			.socketOptions(SocketOptions.builder()
				.connectTimeout(Duration.ofSeconds(30))
				.keepAlive(true)
				.build())
			.build());
		
		LettuceConnectionFactory factory = new LettuceConnectionFactory(
			new RedisStandaloneConfiguration(resolvedHost, redisPort),
			LettuceClientConfiguration.builder()
				.clientResources(DefaultClientResources.builder()
					.ioThreadPoolSize(4)
					.computationThreadPoolSize(4)
					.build())
				.clientOptions(redisClient.getOptions())
				.commandTimeout(Duration.ofSeconds(30))
				.build()
		);
		
		factory.afterPropertiesSet();
		return factory;
	}

	private String resolveHostAddress() {
		try {
			InetAddress address = InetAddress.getByName(redisHost);
			String resolvedAddress = address.getHostAddress();
			log.info("Successfully resolved {} to {}", redisHost, resolvedAddress);
			return resolvedAddress;
		} catch (UnknownHostException e) {
			log.error("Failed to resolve host {}, using original hostname", redisHost, e);
			return redisHost;
		}
	}

	@Bean
	public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
			RedisTemplate<String, Object> template = new RedisTemplate<>();
			template.setConnectionFactory(connectionFactory);
			
			template.setKeySerializer(new StringRedisSerializer());
			template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
			template.setHashKeySerializer(new StringRedisSerializer());
			template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
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