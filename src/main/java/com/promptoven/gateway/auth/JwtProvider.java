package com.promptoven.gateway.auth;

import java.security.interfaces.RSAPrivateKey;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtProvider {

	private static final String JWT_ISSUER = "Prompt Oven Service development group";
	private static final List<String> JWT_AUDIENCE = List.of("prompt oven service");
	private RSADecrypter decrypter;

	@Autowired
	private JwtSecret jwtSecret;

	@PostConstruct
	public void init() {
		try {
			RSAPrivateKey privateKey = jwtSecret.getPrivateKey();
			this.decrypter = new RSADecrypter(privateKey);
		} catch (Exception e) {
			log.error("Failed to initialize JWT provider", e);
			throw new RuntimeException("Failed to initialize JWT provider", e);
		}
	}

	private EncryptedJWT parseAndDecrypt(String token) {
		try {
			log.debug("Attempting to parse JWT token");
			EncryptedJWT jwt = EncryptedJWT.parse(token);
			
			log.debug("Token parsed successfully, attempting decryption");
			if (decrypter == null) {
				log.error("Decrypter is null - JWT provider may not be properly initialized");
				throw new RuntimeException("JWT provider not properly initialized");
			}
			
			jwt.decrypt(decrypter);
			log.debug("Token decrypted successfully");
			return jwt;
		} catch (ParseException e) {
			log.error("Failed to parse JWT token", e);
			throw new RuntimeException("Invalid JWT format", e);
		} catch (JOSEException e) {
			if (e.getCause() instanceof javax.crypto.AEADBadTagException) {
				log.error("JWT decryption failed - token may be corrupted or encrypted with different key. Token: {}", 
						 token.substring(0, Math.min(token.length(), 10)) + "...", e);
				throw new RuntimeException("Invalid token encryption", e);
			}
			log.error("Failed to decrypt JWT token", e);
			throw new RuntimeException("Failed to decrypt JWT", e);
		}
	}

	public boolean validateToken(String token) {
		try {
			if (token == null || token.isEmpty()) {
				return false;
			}

			EncryptedJWT jwt = parseAndDecrypt(token);
			JWTClaimsSet claims = jwt.getJWTClaimsSet();
			Date now = new Date();

			return claims.getIssuer().equals(JWT_ISSUER) &&
				   claims.getAudience().equals(JWT_AUDIENCE) &&
				   now.before(claims.getExpirationTime()) &&
				   now.after(claims.getNotBeforeTime());

		} catch (Exception e) {
			log.error("Failed to validate token: {}", e.getMessage());
			return false;
		}
	}

	public String getUserRole(String token) {
		try {
			EncryptedJWT jwt = parseAndDecrypt(token);
			JWTClaimsSet claims = jwt.getJWTClaimsSet();
			return claims.getStringClaim("role");
		} catch (Exception e) {
			log.error("Failed to get user role from token", e);
			return null;
		}
	}

	public String getUserId(String token) {
		try {
			EncryptedJWT jwt = parseAndDecrypt(token);
			JWTClaimsSet claims = jwt.getJWTClaimsSet();
			return claims.getSubject();
		} catch (Exception e) {
			log.error("Failed to get user ID from token", e);
			return null;
		}
	}

	public String getClaimOfToken(String token, String typeOfClaim) {
		try {
			if (!validateToken(token)) {
				throw new RuntimeException("Invalid or expired token");
			}

			EncryptedJWT jwt = parseAndDecrypt(token);
			JWTClaimsSet claims = jwt.getJWTClaimsSet();
			Object claimValue = claims.getClaim(typeOfClaim);

			return claimValue != null ? claimValue.toString() : null;

		} catch (Exception e) {
			log.error("Failed to get claim from token: {}", e.getMessage());
			throw new RuntimeException("Failed to get claim from token", e);
		}
	}
}

