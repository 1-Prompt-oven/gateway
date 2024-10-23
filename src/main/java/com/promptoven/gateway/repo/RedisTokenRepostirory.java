package com.promptoven.gateway.repo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;

@Service
@EnableRedisRepositories
public class RedisTokenRepostirory {

    private final RedisTemplate<String, String> redisTemplate;

	public RedisTokenRepostirory(@Value("${spring.data.redis.host}") String host,
		@Value("${spring.data.redis.port}") int port) {
		this.redisTemplate = createRedisTemplate(host, port);
	}

	private RedisTemplate<String, String> createRedisTemplate(String host, int port) {
		RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration(host, port);
		RedisConnectionFactory connectionFactory = new LettuceConnectionFactory(redisConfiguration);

		RedisTemplate<String, String> template = new RedisTemplate<>();
		template.setKeySerializer(new StringRedisSerializer());
		template.setValueSerializer(new StringRedisSerializer());
		template.setConnectionFactory(connectionFactory);
		template.afterPropertiesSet();
		return template;
	}

	public boolean isTokenBlocked(String token) {
		return redisTemplate.hasKey(token);
	}
}
