package com.promptoven.gateway.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import io.lettuce.core.ClientOptions;
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
		log.info("Creating Redis connection factory for {}:{}", redisHost, redisPort);

		RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
		serverConfig.setHostName(redisHost);
		serverConfig.setPort(redisPort);
		serverConfig.setDatabase(0);

		LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
			.clientOptions(ClientOptions.builder()
				.autoReconnect(true)
				.build())
			.commandTimeout(Duration.ofSeconds(5))
			.build();

		return new LettuceConnectionFactory(serverConfig, clientConfig);
	}

	@Bean
	public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, String> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		template.setKeySerializer(new StringRedisSerializer());
		template.setValueSerializer(new StringRedisSerializer());
		template.setHashKeySerializer(new StringRedisSerializer());
		template.setHashValueSerializer(new StringRedisSerializer());
		template.afterPropertiesSet();
		return template;
	}

	// Uncomment this method to test the connection to Redis
	// @EventListener(ContextRefreshedEvent.class)
	// public void testConnection() {
	// 	try {
	// 		// Test basic TCP connection first
	// 		log.info("Testing TCP connection to {}:{}", redisHost, redisPort);
	// 		Socket socket = new Socket();
	// 		socket.connect(new InetSocketAddress(redisHost, redisPort), 1000);
	// 		socket.close();
	// 		log.info("TCP connection successful");
	//
	// 		// Test Redis connection
	// 		RedisConnection conn = redisConnectionFactory().getConnection();
	// 		String result = new String(conn.ping());
	// 		log.info("Redis PING result: {}", result);
	// 		conn.close();
	//
	// 	} catch (Exception e) {
	// 		log.error("Connection test failed", e);
	// 		// Print detailed connection info
	// 		try {
	// 			InetAddress[] addresses = InetAddress.getAllByName(redisHost);
	// 			for (InetAddress addr : addresses) {
	// 				log.info("Resolved {} to: {}", redisHost, addr.getHostAddress());
	// 			}
	// 		} catch (Exception ex) {
	// 			log.error("DNS resolution failed", ex);
	// 		}
	// 	}
	// }
}