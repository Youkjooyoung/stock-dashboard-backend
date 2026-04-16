package com.stock.dashboard.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.stock.dashboard.dto.UserDto;

@Testcontainers
@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(
        named = "RUN_INTEGRATION_TESTS",
        matches = "true",
        disabledReason = "Testcontainers 통합 테스트는 CI(Ubuntu) 또는 RUN_INTEGRATION_TESTS=true 환경에서만 실행")
@DisplayName("UserDao 통합 테스트 — Testcontainers (MySQL 8)")
class UserDaoIntegrationTest {

    @Autowired
    private UserDao userDao;

    private UserDto newUser(String email, String nickname) {
        UserDto dto = new UserDto();
        dto.setEmail(email);
        dto.setPassword("encoded-password");
        dto.setName("홍길동");
        dto.setNickname(nickname);
        dto.setPhone("01012345678");
        dto.setAddress("서울");
        dto.setAddressDetail("101호");
        dto.setResidentNo("encrypted");
        dto.setEmailVerifyToken(UUID.randomUUID().toString());
        return dto;
    }

    @BeforeEach
    void cleanDb() {
        // MybatisTest 는 기본적으로 각 테스트 후 롤백. 추가 정리 불필요.
    }

    @Test
    @DisplayName("insertUser 후 findByEmail 로 다시 조회할 수 있다")
    void insertAndFindByEmail() {
        String email = "alice-" + UUID.randomUUID() + "@test.com";
        userDao.insertUser(newUser(email, "alice" + System.nanoTime() % 10000));

        UserDto loaded = userDao.findByEmail(email);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getEmail()).isEqualTo(email);
        assertThat(loaded.getEmailVerified()).isEqualTo("N");
        assertThat(loaded.getAccountLocked()).isEqualTo("N");
    }

    @Test
    @DisplayName("checkEmailExists 는 활성 계정만 카운트한다 (softDelete 계정 제외)")
    void checkEmailExists_excludesSoftDeleted() {
        String email = "bob-" + UUID.randomUUID() + "@test.com";
        userDao.insertUser(newUser(email, "bob" + System.nanoTime() % 10000));

        assertThat(userDao.checkEmailExists(email)).isEqualTo(1);

        UserDto user = userDao.findByEmail(email);
        userDao.softDeleteUser(user.getUserId(), "테스트 탈퇴");

        assertThat(userDao.checkEmailExists(email)).isEqualTo(0);
        assertThat(userDao.findByEmail(email)).isNull();
    }

    @Test
    @DisplayName("softDelete → findByEmailIncludeDeleted → restore 전체 플로우")
    void softDelete_includeDeleted_restore() {
        String email = "carol-" + UUID.randomUUID() + "@test.com";
        userDao.insertUser(newUser(email, "carol" + System.nanoTime() % 10000));

        UserDto user = userDao.findByEmail(email);
        int userId = user.getUserId();

        userDao.softDeleteUser(userId, "단순 변심");

        UserDto deleted = userDao.findByEmailIncludeDeleted(email);
        assertThat(deleted).isNotNull();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getDeleteReason()).isEqualTo("단순 변심");

        userDao.restoreUser(userId);

        UserDto restored = userDao.findByEmailIncludeDeleted(email);
        assertThat(restored.getDeletedAt()).isNull();
        assertThat(restored.getDeleteReason()).isNull();
        assertThat(userDao.findByEmail(email)).isNotNull();
    }

    @Test
    @DisplayName("updateForcePwChange 로 Y/N 전환이 정상 동작한다")
    void updateForcePwChange_toggle() {
        String email = "dave-" + UUID.randomUUID() + "@test.com";
        userDao.insertUser(newUser(email, "dave" + System.nanoTime() % 10000));
        int userId = userDao.findByEmail(email).getUserId();

        userDao.updateForcePwChange(userId, "Y");
        assertThat(userDao.findByEmail(email).getForcePwChange()).isEqualTo("Y");

        userDao.updateForcePwChange(userId, "N");
        assertThat(userDao.findByEmail(email).getForcePwChange()).isEqualTo("N");
    }

    @Test
    @DisplayName("updateLoginFail 5회 누적 시 ACCOUNT_LOCKED 가 Y 로 전환된다")
    void updateLoginFail_locksAccountAtFive() {
        String email = "eve-" + UUID.randomUUID() + "@test.com";
        userDao.insertUser(newUser(email, "eve" + System.nanoTime() % 10000));

        for (int i = 0; i < 5; i++) {
            userDao.updateLoginFail(email);
        }

        UserDto user = userDao.findByEmail(email);
        assertThat(user.getLoginFailCnt()).isEqualTo(5);
        assertThat(user.getAccountLocked()).isEqualTo("Y");
    }

    @Test
    @DisplayName("resetLoginFail 호출 시 카운트와 잠금이 초기화된다")
    void resetLoginFail_clears() {
        String email = "frank-" + UUID.randomUUID() + "@test.com";
        userDao.insertUser(newUser(email, "frank" + System.nanoTime() % 10000));

        for (int i = 0; i < 3; i++) userDao.updateLoginFail(email);
        userDao.resetLoginFail(email);

        UserDto user = userDao.findByEmail(email);
        assertThat(user.getLoginFailCnt()).isZero();
        assertThat(user.getAccountLocked()).isEqualTo("N");
    }

    @Test
    @DisplayName("findByEmailVerifyToken 후 updateEmailVerified 호출 시 Y로 전환")
    void emailVerification_flow() {
        String email = "grace-" + UUID.randomUUID() + "@test.com";
        UserDto dto = newUser(email, "grace" + System.nanoTime() % 10000);
        String verifyToken = dto.getEmailVerifyToken();
        userDao.insertUser(dto);

        UserDto found = userDao.findByEmailVerifyToken(verifyToken);
        assertThat(found).isNotNull();
        assertThat(found.getEmail()).isEqualTo(email);

        userDao.updateEmailVerified(verifyToken);

        UserDto verified = userDao.findByEmail(email);
        assertThat(verified.getEmailVerified()).isEqualTo("Y");
    }
}
