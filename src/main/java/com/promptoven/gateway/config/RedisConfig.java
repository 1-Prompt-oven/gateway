package com.promptoven.gateway.config;


import java.net.InetSocketAddress;
import java.net.Socket;

import jakarta.annotation.PostConstruct;

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


import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class RedisConfig {

	@Value("${spring.data.redis.host}")
	String host;
	@Value("${spring.data.redis.port}")
	int port;


	@PostConstruct
	public void logRedisConfig() {
		log.info("Attempting Redis connection to {}:{}", host, port);
		try {
			Socket socket = new Socket();
			socket.connect(new InetSocketAddress(host, port), 1000);
			socket.close();
			log.info("TCP connection to Redis successful");
		} catch (Exception e) {
			log.error("TCP connection to Redis failed: {}", e.getMessage());
		}
	}
	
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        log.info("Creating Redis connection factory for {}:{}", host, port);
        
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofSeconds(5))
            .shutdownTimeout(Duration.ofSeconds(2))
            .build();

        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration(host, port);
        
        LettuceConnectionFactory factory = new LettuceConnectionFactory(serverConfig, clientConfig);
        factory.afterPropertiesSet(); // Initialize the factory
        
        // Test the connection
        try {
            factory.getConnection().ping();
            log.info("Redis connection test successful");
        } catch (Exception e) {
            log.error("Redis connection test failed: {}", e.getMessage());
        }
        
        return factory;
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