package com.promptoven.gateway.config;

import java.net.InetSocketAddress;
import java.net.Socket;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
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
		RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(host, port);
		LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig);
		factory.afterPropertiesSet();
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