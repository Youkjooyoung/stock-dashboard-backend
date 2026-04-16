package com.stock.dashboard;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

	private static final long ACCESS_EXPIRATION       = 1000L * 60 * 60;          // 1시간
	private static final long REFRESH_EXPIRATION      = 1000L * 60 * 60 * 24 * 7; // 7일
	private static final long VERIFY_TOKEN_EXPIRATION = 1000L * 60 * 5;           // 5분

	private final SecretKey accessKey;
	private final SecretKey refreshKey;

	public JwtUtil(
			@Value("${jwt.secret.access}")  String accessSecret,
			@Value("${jwt.secret.refresh}") String refreshSecret) {
		this.accessKey  = Keys.hmacShaKeyFor(accessSecret.getBytes());
		this.refreshKey = Keys.hmacShaKeyFor(refreshSecret.getBytes());
	}

	public String generateAccessToken(String email) {
		return buildToken(email, ACCESS_EXPIRATION, accessKey);
	}

	public String generateRefreshToken(String email) {
		return buildToken(email, REFRESH_EXPIRATION, refreshKey);
	}

	public String getEmailFromAccess(String token) {
		return getSubject(token, accessKey);
	}

	public String getEmailFromRefresh(String token) {
		return getSubject(token, refreshKey);
	}

	public Date getRefreshExpiredAt() {
		return new Date(System.currentTimeMillis() + REFRESH_EXPIRATION);
	}

	public boolean validateAccessToken(String token) {
		return validate(token, accessKey);
	}

	public boolean validateRefreshToken(String token) {
		return validate(token, refreshKey);
	}

	public String generateVerifyToken(String email) {
		return buildToken(email, VERIFY_TOKEN_EXPIRATION, accessKey);
	}

	public String getEmailFromVerifyToken(String token) {
		return getSubject(token, accessKey);
	}

	public boolean validateVerifyToken(String token) {
		return validate(token, accessKey);
	}

	private String buildToken(String email, long expiration, SecretKey key) {
		return Jwts.builder().subject(email).issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + expiration)).signWith(key).compact();
	}

	private String getSubject(String token, SecretKey key) {
		return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
	}

	private boolean validate(String token, SecretKey key) {
		try {
			Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}