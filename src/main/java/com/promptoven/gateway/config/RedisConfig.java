package com.promptoven.gateway.config;


import java.net.InetAddress;
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


import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class RedisConfig {

	@Value("${spring.data.redis.host}")
	String host;
	@Value("${spring.data.redis.port}")
	int port;


	@PostConstruct
    public void testConnection() {
        try {
            RedisConnectionFactory factory = redisConnectionFactory();
            RedisConnection connection = factory.getConnection();
            connection.ping();
            log.info("Successfully connected to Redis at {}:{}", host, port);
            connection.close();
        } catch (Exception e) {
            log.error("Failed to connect to Redis: {}", e.getMessage(), e);
            // Print more detailed connection info
            try {
                InetAddress address = InetAddress.getByName(host);
                log.info("Redis host IP: {}", address.getHostAddress());
            } catch (Exception ex) {
                log.error("Failed to resolve Redis host: {}", ex.getMessage());
            }
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