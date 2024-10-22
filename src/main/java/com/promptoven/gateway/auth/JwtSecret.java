package com.promptoven.gateway.auth;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class JwtSecret {

	@Value("${jwt.rsa.private}")
	private String stringPrivateKey;

	@Value("${jwt.rsa.public}")
	private String stringPublicKey;

	private RSAPrivateKey privateKey;
	private RSAPublicKey publicKey;

	@PostConstruct
	public void init() throws Exception {
		privateKey = RSAKeyConverter.stringToPrivateKey(stringPrivateKey);
		publicKey = RSAKeyConverter.stringToPublicKey(stringPublicKey);
	}

	public RSAPrivateKey getPrivateKey() {
		return privateKey;
	}

	public RSAPublicKey getPublicKey() {
		return publicKey;
	}
}
