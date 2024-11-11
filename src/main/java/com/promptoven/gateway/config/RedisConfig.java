package com.promptoven.gateway.config;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
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
import org.springframework.data.redis.core.StringRedisTemplate;

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
		log.info("Creating Redis connection factory for {}:{}", redisHost, redisPort);

		RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
		serverConfig.setHostName(redisHost);
		serverConfig.setPort(redisPort);
		serverConfig.setDatabase(0);

		LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
			.clientOptions(ClientOptions.builder()
				.protocolVersion(ProtocolVersion.RESP2)
				.pingBeforeActivateConnection(true)
				.build())
			.commandTimeout(Duration.ofSeconds(5))
			.build();

		return new LettuceConnectionFactory(serverConfig, clientConfig);
	}

	@Bean
	public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
		return new StringRedisTemplate(connectionFactory);
	}

	@EventListener(ContextRefreshedEvent.class)
	public void testConnection() {
		try {
			// Test basic TCP connection first
			log.info("Testing TCP connection to {}:{}", redisHost, redisPort);
			Socket socket = new Socket();
			socket.connect(new InetSocketAddress(redisHost, redisPort), 1000);
			socket.close();
			log.info("TCP connection successful");

			// Test Redis connection
			RedisConnection conn = redisConnectionFactory().getConnection();
			String result = new String(conn.ping());
			log.info("Redis PING result: {}", result);
			conn.close();

		} catch (Exception e) {
			log.error("Connection test failed", e);
			// Print detailed connection info
			try {
				InetAddress[] addresses = InetAddress.getAllByName(redisHost);
				for (InetAddress addr : addresses) {
					log.info("Resolved {} to: {}", redisHost, addr.getHostAddress());
				}
			} catch (Exception ex) {
				log.error("DNS resolution failed", ex);
			}
		}
	}
}