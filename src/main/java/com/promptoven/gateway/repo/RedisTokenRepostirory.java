package com.promptoven.gateway.repo;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@EnableRedisRepositories
@RequiredArgsConstructor
public class RedisTokenRepostirory {

	private final RedisTemplate<String, String> redisTemplate;

	public boolean isTokenBlocked(String token) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(token));
	}
}
