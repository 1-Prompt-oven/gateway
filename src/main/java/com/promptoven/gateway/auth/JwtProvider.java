package com.promptoven.gateway.auth;

import java.security.interfaces.RSAPrivateKey;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;

import jakarta.annotation.PostConstruct;

@Component
public class JwtProvider {

	// nimbusds's jwt claim default set's data
	String jwtissuer = "Prompt Oven Service development group";
	List<String> jwtaudience = List.of("prompt oven service");
	//Hint of JWT token Encryption type
	// RSA-OAEP-512 + ASE/GCM 256
	JWEHeader header = new JWEHeader(
		JWEAlgorithm.RSA_OAEP_512,
		EncryptionMethod.A256GCM
	);
	@Value("${jwt.expiration.refresh}")
	long refreshExpiration;
	@Value("${jwt.expiration.access}")
	long accessTokenExpiration;
	@Autowired
	private JwtSecret jwtSecret;
	private RSAPrivateKey privateKey;


	@PostConstruct
	public void init() {
		this.privateKey = jwtSecret.getPrivateKey();
	}

	// parse serialized token value to token object
	private EncryptedJWT parseToken(String serializedJWT) {
		EncryptedJWT candidateToken = null;
		try {
			candidateToken = EncryptedJWT.parse(serializedJWT);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}

		return candidateToken;
	}

	// decrypt token
	private EncryptedJWT decryptToken(EncryptedJWT token) {

		RSADecrypter decrypter = new RSADecrypter(privateKey);

		try {
			token.decrypt(decrypter);
		} catch (JOSEException e) {
			throw new RuntimeException(e);
		}
		return token;
	}

	private boolean validateTokenInfo(JWTClaimsSet claims) {
		boolean vaildation = false;

		String issuer = claims.getIssuer();
		Date expire = claims.getExpirationTime();
		Date now = new Date();
		List<String> audience = claims.getAudience();
		if (issuer.equals(jwtissuer) && audience.equals(jwtaudience) && now.before(expire)) {
			vaildation = true;
		}
		return vaildation;
	}

	public boolean validateToken(String token) {
		try {
			EncryptedJWT targetToken = decryptToken(parseToken(token));
			JWTClaimsSet claimsSet = targetToken.getJWTClaimsSet();
			return validateTokenInfo(claimsSet);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	//get values of token
	public String getClaimOfToken(String recievedToken, String typeOfClaim) {
		try {
			EncryptedJWT targetToken = decryptToken(parseToken(recievedToken));
			JWTClaimsSet claimsSet = targetToken.getJWTClaimsSet();
			if (validateTokenInfo(claimsSet)) {
				System.out.println(claimsSet);
				return claimsSet.getClaim(typeOfClaim).toString();
			} else {
				throw new RuntimeException("token expired");
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public Date getTokenExpiration(String recievedToken) {
		try {
			EncryptedJWT targetToken = decryptToken(parseToken(recievedToken));
			JWTClaimsSet claimsSet = targetToken.getJWTClaimsSet();
			if (validateTokenInfo(claimsSet)) {
				return claimsSet.getExpirationTime();
			} else {
				throw new RuntimeException("token expired");
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}

