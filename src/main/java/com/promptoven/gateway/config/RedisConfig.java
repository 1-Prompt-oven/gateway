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

@Configuration
@Slf4j
public class RedisConfig {

	@Value("${spring.data.redis.host}")
	private String redisHost;

	@Value("${spring.data.redis.port}")
	private int redisPort;

	@Bean
	public RedisConnectionFactory redisConnectionFactory() {
		log.info("Initializing Redis connection factory with {}:{}", redisHost, redisPort);
		
		// Basic standalone configuration
		RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
		serverConfig.setHostName(redisHost);
		serverConfig.setPort(redisPort);
		
		// Lettuce specific configuration
		LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
			.clientOptions(ClientOptions.builder()
				.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
				.autoReconnect(true)
				.socketOptions(SocketOptions.builder()
					.connectTimeout(Duration.ofSeconds(10))
					.keepAlive(true)
					.tcpNoDelay(true)
					.build())
				.build())
			.commandTimeout(Duration.ofSeconds(10))
			.shutdownTimeout(Duration.ofSeconds(10))
			.build();

		LettuceConnectionFactory factory = new LettuceConnectionFactory(serverConfig, clientConfig);
		factory.setShareNativeConnection(false);  // Create new connection for each operation
		factory.setValidateConnection(true);      // Validate connection before use
		
		return factory;
	}

	@Bean
	public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		
		// Use StringRedisSerializer for keys
		template.setKeySerializer(new StringRedisSerializer());
		template.setHashKeySerializer(new StringRedisSerializer());
		
		// Use GenericJackson2JsonRedisSerializer for values
		template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
		template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
		
		template.setEnableTransactionSupport(false);  // Disable transaction support if not needed
		template.afterPropertiesSet();
		
		return template;
	}

	@PostConstruct
	public void validateConnection() {
		log.info("Validating Redis connection...");
		try {
			RedisConnection conn = redisConnectionFactory().getConnection();
			String pong = new String(conn.ping());
			log.info("Redis PING response: {}", pong);
			conn.close();
		} catch (Exception e) {
			log.error("Redis validation failed", e);
			// Try raw socket connection for comparison
			try (Socket socket = new Socket()) {
				socket.connect(new InetSocketAddress(redisHost, redisPort), 5000);
				log.info("Raw socket connection successful");
				
				// Try simple Redis protocol
				OutputStream out = socket.getOutputStream();
				out.write("*1\r\n$4\r\nPING\r\n".getBytes());
				out.flush();
				
				byte[] response = new byte[1024];
				int bytes = socket.getInputStream().read(response);
				log.info("Raw Redis response: {}", new String(response, 0, bytes));
			} catch (Exception se) {
				log.error("Raw socket test failed", se);
			}
		}
	}
}