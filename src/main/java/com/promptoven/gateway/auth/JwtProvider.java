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

	public static class TokenInfo {
		private final JWTClaimsSet claims;

		private TokenInfo(JWTClaimsSet claims) {
			this.claims = claims;
		}

		public String getRole() {
			try {
				return claims.getStringClaim("role");
			} catch (ParseException e) {
				return null;
			}
		}

		public String getUserId() {
			return claims.getSubject();
		}

		public String getClaim(String claimName) {
			try {
				Object claim = claims.getClaim(claimName);
				return claim != null ? claim.toString() : null;
			} catch (Exception e) {
				return null;
			}
		}
	}

	/**
	 * Decrypts the token and returns the claims set
	 */
	private JWTClaimsSet decryptToken(String token) throws ParseException, JOSEException {
		EncryptedJWT jwt = EncryptedJWT.parse(token);
		jwt.decrypt(decrypter);
		return jwt.getJWTClaimsSet();
	}

	/**
	 * Validates the basic token claims (issuer, audience, expiration)
	 */
	private boolean validateClaims(JWTClaimsSet claims) {
		try {
			Date now = new Date();
			return claims.getIssuer().equals(JWT_ISSUER) &&
				   claims.getAudience().equals(JWT_AUDIENCE) &&
				   now.before(claims.getExpirationTime()) &&
				   now.after(claims.getNotBeforeTime());
		} catch (Exception e) {
			log.error("Token validation failed: {}", e.getMessage());
			return false;
		}
	}

	/**
	 * Decrypts and validates token, returns TokenInfo if valid
	 */
	public TokenInfo validateAndDecryptToken(String token) {
		try {
			JWTClaimsSet claims = decryptToken(token);
			return validateClaims(claims) ? new TokenInfo(claims) : null;
		} catch (Exception e) {
			log.error("Token processing failed: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Only decrypts token and returns claims without validation
	 */
	public TokenInfo decryptTokenOnly(String token) {
		try {
			JWTClaimsSet claims = decryptToken(token);
			return new TokenInfo(claims);
		} catch (Exception e) {
			log.error("Token decryption failed: {}", e.getMessage());
			return null;
		}
	}
}

