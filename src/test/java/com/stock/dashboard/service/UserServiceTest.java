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
import com.stock.dashboard.dao.CertificationUseDao;
import com.stock.dashboard.dao.RefreshTokenDao;
import com.stock.dashboard.dao.StockDao;
import com.stock.dashboard.dao.UserDao;
import com.stock.dashboard.dao.UserSocialDao;
import com.stock.dashboard.dto.UserDto;
import com.stock.dashboard.dto.UserIdentityResultDto;
import com.stock.dashboard.dto.UserLoginRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 단위 테스트")
class UserServiceTest {

    @Mock private CertificationUseDao certificationUseDao;
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
    @DisplayName("verifyCertification()")
    class VerifyCertificationTest {

        private Map<String, String> certResult() {
            return Map.of(
                "name",  "홍길동",
                "phone", "010-1234-5678",
                "birth", "1990-01-01"
            );
        }

        private UserIdentityResultDto identity(String email, String provider) {
            UserIdentityResultDto dto = new UserIdentityResultDto();
            dto.setUserId(7L);
            dto.setEmail(email);
            dto.setProvider(provider);
            return dto;
        }

        @Test
        @DisplayName("이메일 가입 활성 회원 → existingMember=true, provider=EMAIL, maskedEmail 마스킹")
        void verify_existingEmailMember() throws Exception {
            when(portoneService.getCertification("imp_1")).thenReturn(certResult());
            when(userDao.findActiveMemberByIdentity("홍길동", "01012345678"))
                    .thenReturn(identity("hong.gildong@naver.com", null));

            Map<String, Object> res = userService.verifyCertification("imp_1");

            assertThat(res.get("existingMember")).isEqualTo(true);
            assertThat(res.get("existingProvider")).isEqualTo("EMAIL");
            assertThat(res.get("existingMaskedEmail")).isEqualTo("ho***@naver.com");
        }

        @Test
        @DisplayName("카카오 자동생성 이메일 회원 → provider=KAKAO, maskedEmail=null")
        void verify_existingKakaoMember() throws Exception {
            when(portoneService.getCertification("imp_2")).thenReturn(certResult());
            when(userDao.findActiveMemberByIdentity("홍길동", "01012345678"))
                    .thenReturn(identity("kakao_98765@kakao.com", "KAKAO"));

            Map<String, Object> res = userService.verifyCertification("imp_2");

            assertThat(res.get("existingMember")).isEqualTo(true);
            assertThat(res.get("existingProvider")).isEqualTo("KAKAO");
            assertThat(res.get("existingMaskedEmail")).isNull();
        }

        @Test
        @DisplayName("카카오 후 이메일 추가 연동: 가장 최근 매핑(KAKAO) + 일반 이메일이면 마스킹")
        void verify_kakaoThenEmailLink() throws Exception {
            when(portoneService.getCertification("imp_3")).thenReturn(certResult());
            when(userDao.findActiveMemberByIdentity("홍길동", "01012345678"))
                    .thenReturn(identity("hong@naver.com", "KAKAO"));

            Map<String, Object> res = userService.verifyCertification("imp_3");

            assertThat(res.get("existingProvider")).isEqualTo("KAKAO");
            assertThat(res.get("existingMaskedEmail")).isEqualTo("ho***@naver.com");
        }

        @Test
        @DisplayName("신규 미가입 → existingMember=false, 나머지 null")
        void verify_newUser() throws Exception {
            when(portoneService.getCertification("imp_4")).thenReturn(certResult());
            when(userDao.findActiveMemberByIdentity(anyString(), anyString())).thenReturn(null);

            Map<String, Object> res = userService.verifyCertification("imp_4");

            assertThat(res.get("existingMember")).isEqualTo(false);
            assertThat(res.get("existingProvider")).isNull();
            assertThat(res.get("existingMaskedEmail")).isNull();
        }

        @Test
        @DisplayName("phone 하이픈 제거 후 정규화된 값으로 DAO 호출")
        void verify_phoneNormalization() throws Exception {
            when(portoneService.getCertification("imp_5")).thenReturn(certResult());
            when(userDao.findActiveMemberByIdentity(anyString(), anyString())).thenReturn(null);

            userService.verifyCertification("imp_5");

            verify(userDao).findActiveMemberByIdentity("홍길동", "01012345678");
        }

        @Test
        @DisplayName("기존 응답 필드(name/phone/birth) 보존")
        void verify_basicFieldsPreserved() throws Exception {
            when(portoneService.getCertification("imp_6")).thenReturn(certResult());
            when(userDao.findActiveMemberByIdentity(anyString(), anyString())).thenReturn(null);

            Map<String, Object> res = userService.verifyCertification("imp_6");

            assertThat(res.get("name")).isEqualTo("홍길동");
            assertThat(res.get("phone")).isEqualTo("010-1234-5678");
            assertThat(res.get("birth")).isEqualTo("1990-01-01");
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

    @Nested
    @DisplayName("[보안] 본인인증·닉네임·가입 검증")
    class SecurityRegressionTest {

        private Map<String, String> cert(String name, String phone) {
            return Map.of("name", name, "phone", phone, "birth", "1990-01-01");
        }

        @Test
        @DisplayName("등록된 이름·전화번호와 본인인증 결과가 다르면 verifyToken 을 발급하지 않는다")
        void verifyIdentity_rejectsDifferentPerson() throws Exception {
            when(userDao.findByEmail("me@test.com")).thenReturn(activeUser("me@test.com", "P@ssw0rd!"));
            when(portoneService.getCertification("imp_1")).thenReturn(cert("김철수", "01099998888"));

            assertThatThrownBy(() -> userService.verifyIdentityForAction("me@test.com", "imp_1"))
                    .hasMessageContaining("일치하지 않습니다");
            verify(jwtUtil, never()).generateVerifyToken(anyString());
        }

        @Test
        @DisplayName("본인인증 결과가 회원 정보와 같으면 verifyToken 을 발급한다(하이픈 무시)")
        void verifyIdentity_issuesTokenForSamePerson() throws Exception {
            when(userDao.findByEmail("me@test.com")).thenReturn(activeUser("me@test.com", "P@ssw0rd!"));
            when(portoneService.getCertification("imp_1")).thenReturn(cert("홍길동", "010-1234-5678"));
            when(jwtUtil.generateVerifyToken("me@test.com")).thenReturn("vt");

            assertThat(userService.verifyIdentityForAction("me@test.com", "imp_1")).containsEntry("verifyToken", "vt");
        }

        @Test
        @DisplayName("PortOne 이 '완료되지 않은 인증'으로 판단하면 소셜 가입자도 통과하지 못한다")
        void verifyIdentity_socialUserNeedsCompletedCertification() throws Exception {
            UserDto social = activeUser("kakao_1@kakao.com", "x");
            social.setName(null);
            social.setPhone(null);
            when(userDao.findByEmail("kakao_1@kakao.com")).thenReturn(social);
            when(portoneService.getCertification("imp_bogus"))
                    .thenThrow(new com.stock.dashboard.exception.BusinessException("본인인증이 완료되지 않았습니다."));

            assertThatThrownBy(() -> userService.verifyIdentityForAction("kakao_1@kakao.com", "imp_bogus"))
                    .hasMessageContaining("본인인증이 완료되지 않았습니다");
            verify(jwtUtil, never()).generateVerifyToken(anyString());
        }

        @Test
        @DisplayName("다른 회원이 쓰는 닉네임으로 바꾸면 SQL 오류 대신 안내 메시지를 준다")
        void updateNickname_duplicate() {
            when(userDao.findByEmail("me@test.com")).thenReturn(activeUser("me@test.com", "P@ssw0rd!"));
            when(userDao.checkNicknameExists("alice01")).thenReturn(1);

            assertThatThrownBy(() -> userService.updateNickname("me@test.com", "alice01"))
                    .hasMessageContaining("이미 사용 중인 닉네임");
            verify(userDao, never()).updateNickname(any());
        }

        @Test
        @DisplayName("가입 요청의 이름·전화번호가 본인인증 결과와 다르면 가입을 막는다")
        void signup_rejectsIdentityMismatch() throws Exception {
            com.stock.dashboard.dto.UserSignupRequest req = new com.stock.dashboard.dto.UserSignupRequest();
            req.setEmail("new@test.com");
            req.setPassword("P@ssw0rd!");
            req.setNickname("newbie");
            req.setName("다른사람");
            req.setPhone("01012345678");
            req.setImpUid("imp_1");
            when(portoneService.getCertification("imp_1")).thenReturn(cert("홍길동", "01012345678"));

            assertThatThrownBy(() -> userService.signup(req))
                    .hasMessageContaining("본인인증 정보와 입력한");
            verify(userDao, never()).insertUser(any());
        }

        @Test
        @DisplayName("주민등록번호 없이 가입되고, 이름·전화번호는 본인인증 결과로 저장된다")
        void signup_withoutResidentNumber() throws Exception {
            com.stock.dashboard.dto.UserSignupRequest req = new com.stock.dashboard.dto.UserSignupRequest();
            req.setEmail("new@test.com");
            req.setPassword("P@ssw0rd!");
            req.setNickname("newbie");
            req.setName("홍길동");
            req.setPhone("01012345678");
            req.setImpUid("imp_1");
            when(portoneService.getCertification("imp_1")).thenReturn(cert("홍길동", "010-1234-5678"));

            userService.signup(req);

            org.mockito.ArgumentCaptor<UserDto> saved = org.mockito.ArgumentCaptor.forClass(UserDto.class);
            verify(userDao).insertUser(saved.capture());
            assertThat(saved.getValue().getName()).isEqualTo("홍길동");
            assertThat(saved.getValue().getPhone()).isEqualTo("01012345678");
            verify(emailService).sendVerificationEmail(eq("new@test.com"), anyString());
        }

        @Test
        @DisplayName("로그인 사용자 정보가 없으면 NPE 대신 다시 로그인 안내")
        void missingUser_returnsUnauthorizedMessage() {
            when(userDao.findByEmail("gone@test.com")).thenReturn(null);

            assertThatThrownBy(() -> userService.getWatchlist("gone@test.com"))
                    .isInstanceOf(com.stock.dashboard.exception.BusinessException.class)
                    .hasMessageContaining("다시 로그인");
        }
    }

    @Nested
    @DisplayName("[보안] 로그인 잠금 자동 해제·본인인증 1회 사용·소셜 본인정보 등록")
    class LockAndIdentityTest {

        private Map<String, String> cert(String name, String phone) {
            return Map.of("name", name, "phone", phone, "birth", "1990-01-01");
        }

        private UserLoginRequest loginReq(String email, String pw) {
            UserLoginRequest req = new UserLoginRequest();
            req.setEmail(email);
            req.setPassword(pw);
            return req;
        }

        @Test
        @DisplayName("잠금 시간이 지나면 자동으로 풀리고 로그인된다")
        void lockExpired_autoUnlocks() {
            UserDto user = activeUser("me@test.com", "P@ssw0rd!");
            user.setAccountLocked("Y");
            user.setLoginFailCnt(5);
            user.setLockedUntil(new Date(System.currentTimeMillis() - 1000));
            when(userDao.findByEmail("me@test.com")).thenReturn(user);
            when(jwtUtil.generateAccessToken(anyString(), anyString())).thenReturn("a");
            when(jwtUtil.generateRefreshToken(anyString())).thenReturn("r");
            when(jwtUtil.getRefreshExpiredAt()).thenReturn(new Date());

            Map<String, String> tokens = userService.login(loginReq("me@test.com", "P@ssw0rd!"));

            assertThat(tokens).containsEntry("accessToken", "a");
            verify(userDao, times(2)).resetLoginFail("me@test.com"); // 잠금 해제 1번 + 로그인 성공 1번
        }

        @Test
        @DisplayName("잠금 시간 안이면 올바른 비밀번호라도 15분 제한 안내")
        void lockActive_rejects() {
            UserDto user = activeUser("me@test.com", "P@ssw0rd!");
            user.setAccountLocked("Y");
            user.setLockedUntil(new Date(System.currentTimeMillis() + 60_000));
            when(userDao.findByEmail("me@test.com")).thenReturn(user);

            assertThatThrownBy(() -> userService.login(loginReq("me@test.com", "P@ssw0rd!")))
                    .hasMessageContaining("15분간");
            verify(userDao, never()).resetLoginFail(anyString());
        }

        @Test
        @DisplayName("5번째로 틀리면 잠금 안내(남은 시도 0회라고 하지 않는다)")
        void fifthFailure_locks() {
            UserDto user = activeUser("me@test.com", "P@ssw0rd!");
            user.setLoginFailCnt(4);
            when(userDao.findByEmail("me@test.com")).thenReturn(user);

            assertThatThrownBy(() -> userService.login(loginReq("me@test.com", "wrong")))
                    .hasMessageContaining("15분간");
            verify(userDao).updateLoginFail("me@test.com");
        }

        @Test
        @DisplayName("이미 사용한 본인인증(imp_uid)으로는 verifyToken 을 받을 수 없다")
        void verifyIdentity_rejectsReusedCertification() throws Exception {
            when(userDao.findByEmail("me@test.com")).thenReturn(activeUser("me@test.com", "P@ssw0rd!"));
            when(portoneService.getCertification("imp_1")).thenReturn(cert("홍길동", "01012345678"));
            when(certificationUseDao.insertUse("imp_1", "me@test.com", "VERIFY"))
                    .thenThrow(new org.springframework.dao.DuplicateKeyException("dup"));

            assertThatThrownBy(() -> userService.verifyIdentityForAction("me@test.com", "imp_1"))
                    .hasMessageContaining("이미 사용된 본인인증");
            verify(jwtUtil, never()).generateVerifyToken(anyString());
        }

        @Test
        @DisplayName("소셜 가입자는 첫 본인인증 결과가 회원 정보로 등록된다")
        void verifyIdentity_socialUserBindsIdentity() throws Exception {
            UserDto social = activeUser("kakao_1@kakao.com", "x");
            social.setName(null);
            social.setPhone(null);
            when(userDao.findByEmail("kakao_1@kakao.com")).thenReturn(social);
            when(portoneService.getCertification("imp_2")).thenReturn(cert("홍길동", "010-1234-5678"));
            when(jwtUtil.generateVerifyToken("kakao_1@kakao.com")).thenReturn("vt");

            assertThat(userService.verifyIdentityForAction("kakao_1@kakao.com", "imp_2"))
                    .containsEntry("verifyToken", "vt");
            verify(userDao).updateIdentity(1, "홍길동", "01012345678");
            verify(certificationUseDao).insertUse("imp_2", "kakao_1@kakao.com", "VERIFY");
        }

        @Test
        @DisplayName("다른 회원의 본인인증 정보로는 소셜 가입자 정보를 등록할 수 없다")
        void verifyIdentity_socialUserRejectsOthersIdentity() throws Exception {
            UserDto social = activeUser("kakao_1@kakao.com", "x");
            social.setName(null);
            social.setPhone(null);
            when(userDao.findByEmail("kakao_1@kakao.com")).thenReturn(social);
            when(portoneService.getCertification("imp_3")).thenReturn(cert("홍길동", "01012345678"));
            UserIdentityResultDto other = new UserIdentityResultDto();
            other.setUserId(99L);
            other.setEmail("other@test.com");
            when(userDao.findActiveMemberByIdentity("홍길동", "01012345678")).thenReturn(other);

            assertThatThrownBy(() -> userService.verifyIdentityForAction("kakao_1@kakao.com", "imp_3"))
                    .hasMessageContaining("다른 계정");
            verify(userDao, never()).updateIdentity(anyInt(), anyString(), anyString());
        }
    }
}
