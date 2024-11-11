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
		
		RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
		serverConfig.setHostName(resolvedHost);
		serverConfig.setPort(redisPort);
		
		LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
			.clientOptions(ClientOptions.builder()
				.socketOptions(SocketOptions.builder()
					.connectTimeout(Duration.ofSeconds(10))
					.build())
				.build())
			.build();

		return new LettuceConnectionFactory(serverConfig, clientConfig);
	}

	private String resolveHostAddress() {
		try {
			InetAddress address = InetAddress.getByName(redisHost);
			String resolvedAddress = address.getHostAddress();
			log.info("Successfully resolved {} to {}", redisHost, resolvedAddress);
			return resolvedAddress;
		} catch (UnknownHostException e) {
			log.error("Failed to resolve host {}, falling back to original hostname", redisHost);
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
		log.info("Testing Redis connection after context initialization");
		testConnection();
	}

	private void testConnection() {
		log.info("Testing connection with hostname: {}", redisHost);
		testAddress(redisHost);
		
		String resolvedIp = resolveHostAddress();
		log.info("Testing connection with resolved IP: {}", resolvedIp);
		testAddress(resolvedIp);
	}

	private void testAddress(String host) {
		try (Socket socket = new Socket()) {
			log.info("Attempting to connect to {}:{}", host, redisPort);
			socket.connect(new InetSocketAddress(host, redisPort), 5000);
			log.info("Socket connection successful to {}:{}", host, redisPort);
			
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			
			out.println("PING");
			String response = in.readLine();
			log.info("Redis PING response from {}: {}", host, response);
		} catch (Exception e) {
			log.error("Connection test failed for " + host, e);
		}
	}
}