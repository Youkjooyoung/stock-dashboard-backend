package com.stock.dashboard;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

	private static final long ACCESS_EXPIRATION       = 1000L * 60 * 60;          // 1시간
	private static final long REFRESH_EXPIRATION      = 1000L * 60 * 60 * 24 * 7; // 7일
	private static final long VERIFY_TOKEN_EXPIRATION = 1000L * 60 * 5;           // 5분

	// 같은 키로 서명하는 토큰끼리 서로 대신 쓰이지 않도록 용도를 표시한다.
	private static final String TYPE_CLAIM  = "typ";
	private static final String TYPE_ACCESS = "access";
	private static final String TYPE_VERIFY = "verify";

	private final SecretKey accessKey;
	private final SecretKey refreshKey;

	public JwtUtil(
			@Value("${jwt.secret.access}")  String accessSecret,
			@Value("${jwt.secret.refresh}") String refreshSecret) {
		this.accessKey  = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
		this.refreshKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
	}

	public String generateAccessToken(String email, String role) {
		return Jwts.builder()
				.subject(email)
				.claim(TYPE_CLAIM, TYPE_ACCESS)
				.claim("role", role)
				.issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + ACCESS_EXPIRATION))
				.signWith(accessKey)
				.compact();
	}

	public String generateRefreshToken(String email) {
		return Jwts.builder().subject(email).issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + REFRESH_EXPIRATION)).signWith(refreshKey).compact();
	}

	public String generateVerifyToken(String email) {
		return Jwts.builder()
				.subject(email)
				.claim(TYPE_CLAIM, TYPE_VERIFY)
				.issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + VERIFY_TOKEN_EXPIRATION))
				.signWith(accessKey)
				.compact();
	}

	public String getEmailFromAccess(String token) {
		return parse(token, accessKey, TYPE_ACCESS).getSubject();
	}

	public String getEmailFromRefresh(String token) {
		return parse(token, refreshKey, null).getSubject();
	}

	public String getEmailFromVerifyToken(String token) {
		return parse(token, accessKey, TYPE_VERIFY).getSubject();
	}

	public String getRoleFromAccess(String token) {
		try {
			return parse(token, accessKey, TYPE_ACCESS).get("role", String.class);
		} catch (JwtException | IllegalArgumentException e) {
			return null;
		}
	}

	public Date getRefreshExpiredAt() {
		return new Date(System.currentTimeMillis() + REFRESH_EXPIRATION);
	}

	public boolean validateAccessToken(String token) {
		return isValid(token, accessKey, TYPE_ACCESS);
	}

	public boolean validateRefreshToken(String token) {
		return isValid(token, refreshKey, null);
	}

	public boolean validateVerifyToken(String token) {
		return isValid(token, accessKey, TYPE_VERIFY);
	}

	public String stripBearer(String header) {
		return header != null && header.startsWith("Bearer ") ? header.substring(7) : header;
	}

	/** 서명·만료를 검사하고, expectedType 이 있으면 typ 클레임까지 일치해야 통과한다. */
	private Claims parse(String token, SecretKey key, String expectedType) {
		Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
		if (expectedType != null && !expectedType.equals(claims.get(TYPE_CLAIM, String.class))) {
			throw new JwtException("토큰 용도가 올바르지 않습니다.");
		}
		return claims;
	}

	private boolean isValid(String token, SecretKey key, String expectedType) {
		try {
			parse(token, key, expectedType);
			return true;
		} catch (JwtException | IllegalArgumentException e) {
			return false;
		}
	}
}
