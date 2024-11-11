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
		
		RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
		serverConfig.setHostName(redisHost);
		serverConfig.setPort(redisPort);
		
		LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
			.clientOptions(ClientOptions.builder()
				.autoReconnect(true)
				.socketOptions(SocketOptions.builder()
					.connectTimeout(Duration.ofSeconds(10))
					.keepAlive(true)
					.build())
				.build())
			.commandTimeout(Duration.ofSeconds(10))
			.build();

		return new LettuceConnectionFactory(serverConfig, clientConfig);
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
		try {
			RedisConnection conn = redisConnectionFactory().getConnection();
			String pong = new String(conn.ping());
			log.info("Redis PING response: {}", pong);
			conn.close();
		} catch (Exception e) {
			log.error("Redis connection test failed", e);
			testRawConnection();
		}
	}

	private void testRawConnection() {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(redisHost, redisPort), 5000);
			log.info("Raw socket connection successful");
			
			OutputStream out = socket.getOutputStream();
			out.write("*1\r\n$4\r\nPING\r\n".getBytes());
			out.flush();
			
			byte[] response = new byte[1024];
			int bytes = socket.getInputStream().read(response);
			log.info("Raw Redis response: {}", new String(response, 0, bytes));
		} catch (Exception e) {
			log.error("Raw socket connection failed", e);
		}
	}
}