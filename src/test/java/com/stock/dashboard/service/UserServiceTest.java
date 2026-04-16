package com.stock.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.stock.dashboard.InputValidator;
import com.stock.dashboard.JwtUtil;
import com.stock.dashboard.dao.RefreshTokenDao;
import com.stock.dashboard.dao.StockDao;
import com.stock.dashboard.dao.UserDao;
import com.stock.dashboard.dao.UserSocialDao;
import com.stock.dashboard.dto.UserDto;
import com.stock.dashboard.dto.UserLoginRequest;
import com.stock.dashboard.util.AesEncryptor;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 단위 테스트")
class UserServiceTest {

    @Mock private AesEncryptor    aesEncryptor;
    @Mock private EmailService    emailService;
    @Mock private InputValidator  validator;
    @Mock private JwtUtil         jwtUtil;
    @Mock private PortoneService  portoneService;
    @Mock private RefreshTokenDao refreshTokenDao;
    @Mock private StockDao        stockDao;
    @Mock private UserDao         userDao;
    @Mock private UserSocialDao   userSocialDao;
    @Mock private S3Service       s3Service;

    @InjectMocks
    private UserService userService;

    private final BCryptPasswordEncoder realEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "appBaseUrl",         "http://localhost:5173");
        ReflectionTestUtils.setField(userService, "kakaoClientId",      "test-kakao-id");
        ReflectionTestUtils.setField(userService, "kakaoClientSecret",  "test-kakao-secret");
        ReflectionTestUtils.setField(userService, "kakaoRedirectUri",   "http://localhost/kakao");
        ReflectionTestUtils.setField(userService, "googleClientId",     "test-google-id");
        ReflectionTestUtils.setField(userService, "googleClientSecret", "test-google-secret");
        ReflectionTestUtils.setField(userService, "googleRedirectUri",  "http://localhost/google");
    }

    private UserDto activeUser(String email, String rawPassword) {
        UserDto u = new UserDto();
        u.setUserId(1);
        u.setEmail(email);
        u.setPassword(realEncoder.encode(rawPassword));
        u.setEmailVerified("Y");
        u.setAccountLocked("N");
        u.setRole("USER");
        u.setNickname("닉네임");
        u.setName("홍길동");
        u.setPhone("01012345678");
        u.setForcePwChange("N");
        return u;
    }

    @Nested
    @DisplayName("login()")
    class LoginTest {

        @Test
        @DisplayName("정상 로그인 시 accessToken/refreshToken/role을 반환한다")
        void login_success() {
            UserDto user = activeUser("me@test.com", "P@ssw0rd!");
            when(userDao.findByEmail("me@test.com")).thenReturn(user);
            when(jwtUtil.generateAccessToken("me@test.com",  "USER")).thenReturn("access-jwt");
            when(jwtUtil.generateRefreshToken("me@test.com")).thenReturn("refresh-jwt");
            when(jwtUtil.getRefreshExpiredAt()).thenReturn(new Date(System.currentTimeMillis() + 604800000L));

            UserLoginRequest req = new UserLoginRequest();
            req.setEmail("me@test.com");
            req.setPassword("P@ssw0rd!");

            Map<String, String> tokens = userService.login(req);

            assertThat(tokens.get("accessToken")).isEqualTo("access-jwt");
            assertThat(tokens.get("refreshToken")).isEqualTo("refresh-jwt");
            assertThat(tokens.get("role")).isEqualTo("USER");
            assertThat(tokens.get("userId")).isEqualTo("1");
            assertThat(tokens).doesNotContainKey("forcePwChange");
            verify(userDao).resetLoginFail("me@test.com");
            verify(refreshTokenDao).insertRefreshToken(any());
        }

        @Test
        @DisplayName("임시 비밀번호 계정이면 forcePwChange=Y 플래그가 포함된다")
        void login_forcePwChange() {
            UserDto user = activeUser("me@test.com", "TempPw12!");
            user.setForcePwChange("Y");
            when(userDao.findByEmail(anyString())).thenReturn(user);
            when(jwtUtil.generateAccessToken(anyString(), anyString())).thenReturn("a");
            when(jwtUtil.generateRefreshToken(anyString())).thenReturn("r");
            when(jwtUtil.getRefreshExpiredAt()).thenReturn(new Date());

            UserLoginRequest req = new UserLoginRequest();
            req.setEmail("me@test.com");
            req.setPassword("TempPw12!");

            Map<String, String> tokens = userService.login(req);

            assertThat(tokens.get("forcePwChange")).isEqualTo("Y");
        }

        @Test
        @DisplayName("존재하지 않는 이메일이면 예외를 던진다")
        void login_emailNotFound() {
            when(userDao.findByEmail("nouser@test.com")).thenReturn(null);

            UserLoginRequest req = new UserLoginRequest();
            req.setEmail("nouser@test.com");
            req.setPassword("P@ssw0rd!");

            assertThatThrownBy(() -> userService.login(req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("이메일 또는 비밀번호");
        }

        @Test
        @DisplayName("계정 잠금 상태면 예외를 던진다")
        void login_accountLocked() {
            UserDto user = activeUser("me@test.com", "P@ssw0rd!");
            user.setAccountLocked("Y");
            when(userDao.findByEmail("me@test.com")).thenReturn(user);

            UserLoginRequest req = new UserLoginRequest();
            req.setEmail("me@test.com");
            req.setPassword("P@ssw0rd!");

            assertThatThrownBy(() -> userService.login(req))
                    .hasMessageContaining("계정이 잠겼습니다");
        }

        @Test
        @DisplayName("이메일 미인증이면 예외를 던진다")
        void login_emailNotVerified() {
            UserDto user = activeUser("me@test.com", "P@ssw0rd!");
            user.setEmailVerified("N");
            when(userDao.findByEmail("me@test.com")).thenReturn(user);

            UserLoginRequest req = new UserLoginRequest();
            req.setEmail("me@test.com");
            req.setPassword("P@ssw0rd!");

            assertThatThrownBy(() -> userService.login(req))
                    .hasMessageContaining("이메일 인증");
        }

        @Test
        @DisplayName("비밀번호 불일치 시 updateLoginFail 호출 후 남은 시도 횟수 메시지 반환")
        void login_wrongPassword() {
            UserDto user = activeUser("me@test.com", "P@ssw0rd!");
            user.setLoginFailCnt(1);
            when(userDao.findByEmail("me@test.com")).thenReturn(user);

            UserLoginRequest req = new UserLoginRequest();
            req.setEmail("me@test.com");
            req.setPassword("WrongPass!");

            assertThatThrownBy(() -> userService.login(req))
                    .hasMessageContaining("남은 시도");

            verify(userDao).updateLoginFail("me@test.com");
            verify(userDao, never()).resetLoginFail(anyString());
        }
    }

    @Nested
    @DisplayName("deleteAccount()")
    class DeleteAccountTest {

        @Test
        @DisplayName("verifyToken 누락 시 본인인증 예외")
        void deleteAccount_noVerifyToken() {
            assertThatThrownBy(() -> userService.deleteAccount("me@test.com", "", "탈퇴"))
                    .hasMessageContaining("본인인증이 필요합니다");
        }

        @Test
        @DisplayName("만료된 verifyToken 이면 재인증 예외")
        void deleteAccount_expiredVerifyToken() {
            when(jwtUtil.validateVerifyToken("expired")).thenReturn(false);
            assertThatThrownBy(() -> userService.deleteAccount("me@test.com", "expired", "탈퇴"))
                    .hasMessageContaining("본인인증이 만료");
        }

        @Test
        @DisplayName("verifyToken의 이메일과 요청 이메일이 불일치하면 예외")
        void deleteAccount_emailMismatch() {
            when(jwtUtil.validateVerifyToken("vt")).thenReturn(true);
            when(jwtUtil.getEmailFromVerifyToken("vt")).thenReturn("other@test.com");
            UserDto user = activeUser("me@test.com", "P@ssw0rd!");
            when(userDao.findByEmail("me@test.com")).thenReturn(user);

            assertThatThrownBy(() -> userService.deleteAccount("me@test.com", "vt", "탈퇴"))
                    .hasMessageContaining("본인인증 정보가 일치하지 않");
        }

        @Test
        @DisplayName("정상 탈퇴 시 refreshToken 삭제 + softDelete 호출")
        void deleteAccount_success() {
            when(jwtUtil.validateVerifyToken("vt")).thenReturn(true);
            when(jwtUtil.getEmailFromVerifyToken("vt")).thenReturn("me@test.com");
            UserDto user = activeUser("me@test.com", "P@ssw0rd!");
            when(userDao.findByEmail("me@test.com")).thenReturn(user);

            userService.deleteAccount("me@test.com", "vt", "단순 변심");

            verify(refreshTokenDao).deleteByUserId(1);
            verify(userDao).softDeleteUser(1, "단순 변심");
        }

        @Test
        @DisplayName("deleteReason이 null이면 빈 문자열로 저장")
        void deleteAccount_nullReasonBecomesEmpty() {
            when(jwtUtil.validateVerifyToken("vt")).thenReturn(true);
            when(jwtUtil.getEmailFromVerifyToken("vt")).thenReturn("me@test.com");
            UserDto user = activeUser("me@test.com", "P@ssw0rd!");
            when(userDao.findByEmail("me@test.com")).thenReturn(user);

            userService.deleteAccount("me@test.com", "vt", null);

            verify(userDao).softDeleteUser(1, "");
        }
    }

    @Nested
    @DisplayName("changePassword()")
    class ChangePasswordTest {

        @Test
        @DisplayName("정상 변경 시 updatePassword 호출, forcePwChange=Y 였다면 N 으로 전환")
        void changePassword_success_clearsForcePwChange() {
            when(jwtUtil.validateVerifyToken("vt")).thenReturn(true);
            when(jwtUtil.getEmailFromVerifyToken("vt")).thenReturn("me@test.com");
            UserDto user = activeUser("me@test.com", "Old@1234");
            user.setForcePwChange("Y");
            when(userDao.findByEmail("me@test.com")).thenReturn(user);

            userService.changePassword("me@test.com", "vt", "NewPass@1");

            verify(validator).validatePassword("NewPass@1");
            verify(userDao).updatePassword(user);
            verify(userDao).updateForcePwChange(1, "N");
        }

        @Test
        @DisplayName("일반 비밀번호 변경(forcePwChange=N) 은 updateForcePwChange를 호출하지 않는다")
        void changePassword_success_normalPath() {
            when(jwtUtil.validateVerifyToken("vt")).thenReturn(true);
            when(jwtUtil.getEmailFromVerifyToken("vt")).thenReturn("me@test.com");
            UserDto user = activeUser("me@test.com", "Old@1234");
            when(userDao.findByEmail("me@test.com")).thenReturn(user);

            userService.changePassword("me@test.com", "vt", "NewPass@1");

            verify(userDao).updatePassword(user);
            verify(userDao, never()).updateForcePwChange(anyInt(), anyString());
        }

        @Test
        @DisplayName("verifyToken 누락 시 예외")
        void changePassword_noVerifyToken() {
            assertThatThrownBy(() -> userService.changePassword("me@test.com", null, "NewPass@1"))
                    .hasMessageContaining("본인인증이 필요합니다");
        }
    }

    @Nested
    @DisplayName("recoverAccount()")
    class RecoverAccountTest {

        @Test
        @DisplayName("복구 가능 계정 없음 → 예외")
        void recover_noDeletedAccount() {
            when(userDao.findByEmailIncludeDeleted("me@test.com")).thenReturn(null);

            assertThatThrownBy(() -> userService.recoverAccount("me@test.com", "홍길동", "01012345678"))
                    .hasMessageContaining("복구 가능한 계정");
        }

        @Test
        @DisplayName("2주 경과한 계정은 복구 불가")
        void recover_expired() {
            UserDto deleted = activeUser("me@test.com", "P@ssw0rd!");
            deleted.setDeletedAt(new Date(System.currentTimeMillis() - 15L * 24 * 60 * 60 * 1000));
            when(userDao.findByEmailIncludeDeleted("me@test.com")).thenReturn(deleted);

            assertThatThrownBy(() -> userService.recoverAccount("me@test.com", "홍길동", "01012345678"))
                    .hasMessageContaining("복구 기간");
        }

        @Test
        @DisplayName("이름/전화 불일치 시 복구 거부")
        void recover_wrongIdentity() {
            UserDto deleted = activeUser("me@test.com", "P@ssw0rd!");
            deleted.setDeletedAt(new Date(System.currentTimeMillis() - 1000L));
            when(userDao.findByEmailIncludeDeleted("me@test.com")).thenReturn(deleted);

            assertThatThrownBy(() -> userService.recoverAccount("me@test.com", "가짜이름", "01012345678"))
                    .hasMessageContaining("탈퇴 계정 정보와 일치");
        }

        @Test
        @DisplayName("정상 복구 시 임시 비밀번호 저장 + restore + forcePwChange=Y + 이메일 발송")
        void recover_success() throws Exception {
            UserDto deleted = activeUser("me@test.com", "P@ssw0rd!");
            deleted.setDeletedAt(new Date(System.currentTimeMillis() - 1000L));
            when(userDao.findByEmailIncludeDeleted("me@test.com")).thenReturn(deleted);

            Map<String, String> res = userService.recoverAccount("me@test.com", "홍길동", "01012345678");

            assertThat(res.get("message")).contains("복구");
            verify(userDao).updatePassword(deleted);
            verify(userDao).restoreUser(1);
            verify(userDao).updateForcePwChange(1, "Y");
            verify(emailService, times(1)).sendTempPasswordEmail(eq("me@test.com"), anyString());
        }
    }

    @Nested
    @DisplayName("checkDeletedAccount()")
    class CheckDeletedAccountTest {

        @Test
        @DisplayName("2주 이내 탈퇴 계정은 recoverable=true")
        void checkDeleted_recoverable() {
            UserDto deleted = activeUser("me@test.com", "P@ssw0rd!");
            deleted.setDeletedAt(new Date(System.currentTimeMillis() - 1000L));
            when(userDao.findByEmailIncludeDeleted("me@test.com")).thenReturn(deleted);

            Map<String, Object> res = userService.checkDeletedAccount("me@test.com");

            assertThat(res.get("deleted")).isEqualTo(true);
            assertThat(res.get("recoverable")).isEqualTo(true);
        }

        @Test
        @DisplayName("존재하지 않는 이메일은 deleted=false")
        void checkDeleted_none() {
            when(userDao.findByEmailIncludeDeleted("me@test.com")).thenReturn(null);

            Map<String, Object> res = userService.checkDeletedAccount("me@test.com");

            assertThat(res.get("deleted")).isEqualTo(false);
            assertThat(res.get("recoverable")).isEqualTo(false);
        }

        @Test
        @DisplayName("2주 초과 탈퇴는 recoverable=false")
        void checkDeleted_expired() {
            UserDto deleted = activeUser("me@test.com", "P@ssw0rd!");
            deleted.setDeletedAt(new Date(System.currentTimeMillis() - 15L * 24 * 60 * 60 * 1000));
            when(userDao.findByEmailIncludeDeleted("me@test.com")).thenReturn(deleted);

            Map<String, Object> res = userService.checkDeletedAccount("me@test.com");

            assertThat(res.get("deleted")).isEqualTo(false);
            assertThat(res.get("recoverable")).isEqualTo(false);
        }
    }
}
