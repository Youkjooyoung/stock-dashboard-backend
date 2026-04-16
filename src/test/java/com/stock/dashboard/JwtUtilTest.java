package com.stock.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@DisplayName("JwtUtil 단위 테스트")
class JwtUtilTest {

    private static final String ACCESS_SECRET  = "jwt-access-secret-key-for-testing-only-32chars!!";
    private static final String REFRESH_SECRET = "jwt-refresh-secret-key-for-testing-only-32chars!!";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(ACCESS_SECRET, REFRESH_SECRET);
    }

    @Test
    @DisplayName("Access 토큰 생성 후 이메일/역할이 동일하게 파싱된다")
    void accessToken_roundtrip() {
        String token = jwtUtil.generateAccessToken("user@test.com", "ADMIN");

        assertThat(jwtUtil.validateAccessToken(token)).isTrue();
        assertThat(jwtUtil.getEmailFromAccess(token)).isEqualTo("user@test.com");
        assertThat(jwtUtil.getRoleFromAccess(token)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Refresh 토큰 생성/검증/이메일 파싱이 일관되게 동작한다")
    void refreshToken_roundtrip() {
        String token = jwtUtil.generateRefreshToken("user@test.com");

        assertThat(jwtUtil.validateRefreshToken(token)).isTrue();
        assertThat(jwtUtil.getEmailFromRefresh(token)).isEqualTo("user@test.com");
    }

    @Test
    @DisplayName("Verify 토큰은 Access 키로 서명되며 validateVerifyToken이 통과한다")
    void verifyToken_roundtrip() {
        String token = jwtUtil.generateVerifyToken("user@test.com");

        assertThat(jwtUtil.validateVerifyToken(token)).isTrue();
        assertThat(jwtUtil.getEmailFromVerifyToken(token)).isEqualTo("user@test.com");
    }

    @Test
    @DisplayName("refreshKey로 서명된 토큰은 validateAccessToken에서 거부된다")
    void accessValidation_rejectsRefreshToken() {
        String refreshToken = jwtUtil.generateRefreshToken("user@test.com");

        assertThat(jwtUtil.validateAccessToken(refreshToken)).isFalse();
    }

    @Test
    @DisplayName("변조된 토큰은 validate 가 false를 반환한다")
    void validate_rejectsTamperedToken() {
        String token = jwtUtil.generateAccessToken("user@test.com", "USER");
        String tampered = token.substring(0, token.length() - 5) + "xxxxx";

        assertThat(jwtUtil.validateAccessToken(tampered)).isFalse();
    }

    @Test
    @DisplayName("만료된 토큰은 validate=false 를 반환한다")
    void validate_rejectsExpiredToken() {
        String expired = Jwts.builder()
                .subject("user@test.com")
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 1_000))
                .signWith(Keys.hmacShaKeyFor(ACCESS_SECRET.getBytes()))
                .compact();

        assertThat(jwtUtil.validateAccessToken(expired)).isFalse();
    }

    @Test
    @DisplayName("getRoleFromAccess 는 변조 토큰에 대해 null을 반환한다")
    void getRoleFromAccess_returnsNullOnInvalid() {
        assertThat(jwtUtil.getRoleFromAccess("not.a.jwt")).isNull();
    }

    @Test
    @DisplayName("getRefreshExpiredAt 은 현재 시각 이후의 Date를 반환한다")
    void getRefreshExpiredAt_isInFuture() {
        Date expiry = jwtUtil.getRefreshExpiredAt();
        assertThat(expiry).isAfter(new Date());
    }

    @Test
    @DisplayName("다른 secret으로 만든 JwtUtil 인스턴스 토큰은 교차 검증에 실패한다")
    void crossInstance_tokensDoNotValidate() {
        JwtUtil other = new JwtUtil(
                "different-access-secret-key-testing-32chars!!!!!!",
                "different-refresh-secret-key-testing-32chars!!!!!");
        String token = other.generateAccessToken("user@test.com", "USER");

        assertThat(jwtUtil.validateAccessToken(token)).isFalse();
        // 필드 참조만으로도 재접근 가능한지 확인 (테스트 용도)
        ReflectionTestUtils.getField(jwtUtil, "accessKey");
    }
}
