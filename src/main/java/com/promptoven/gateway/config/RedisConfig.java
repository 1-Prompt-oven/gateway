package com.promptoven.gateway.config;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import jakarta.annotation.PostConstruct;

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
import org.springframework.data.redis.serializer.StringRedisSerializer;

import io.lettuce.core.ClientOptions;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class RedisConfig {

	@Value("${spring.data.redis.host}")
	String host;
	@Value("${spring.data.redis.port}")
	int port;


	   @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        log.info("Creating Redis connection factory for {}:{}", host, port);
        
        // Create standalone configuration
        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
        serverConfig.setHostName(host);
        serverConfig.setPort(port);
        
        // Configure Lettuce client
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofSeconds(5))
            .clientOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .keepAlive(true)
                    .build())
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofSeconds(5)))
                .build())
            .build();


        // Create factory with configurations
        LettuceConnectionFactory factory = new LettuceConnectionFactory(serverConfig, clientConfig);
        
        // Test connection with detailed logging
        try {
            log.info("Initializing Redis connection factory...");
            factory.afterPropertiesSet();
            
            log.info("Attempting to get Redis connection...");
            RedisConnection connection = factory.getConnection();
            
            log.info("Executing PING command...");
            String pong = new String(connection.ping());
            
            log.info("Redis PING response: {}", pong);
            connection.close();
            
            return factory;
        } catch (Exception e) {
            log.error("Redis connection initialization failed", e);
            // Print stack trace for debugging
            e.printStackTrace();
            
            // Additional connection debugging
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 2000);
                log.info("TCP Socket connection successful");
                socket.close();
            } catch (Exception se) {
                log.error("TCP Socket connection failed", se);
            }
            
            throw new RuntimeException("Could not initialize Redis connection", e);
        }
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
}