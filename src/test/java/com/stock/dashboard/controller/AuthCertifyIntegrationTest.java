package com.stock.dashboard.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.stock.dashboard.service.PortoneService;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(
        named = "RUN_INTEGRATION_TESTS",
        matches = "true",
        disabledReason = "Testcontainers 통합 테스트는 CI(Ubuntu) 또는 RUN_INTEGRATION_TESTS=true 환경에서만 실행")
@DisplayName("AuthController.certify() 통합 테스트 — 본인인증 후 기존 회원 자동 감지")
class AuthCertifyIntegrationTest {

    private static final String CERT_NAME       = "홍길동";
    private static final String CERT_PHONE      = "010-1234-5678";
    private static final String CERT_PHONE_NORM = "01012345678";
    private static final String CERT_BIRTH      = "1990-01-01";

    @Autowired private MockMvc      mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private PortoneService portoneService;

    @BeforeEach
    void cleanDb() {
        jdbc.update("DELETE FROM USER_SOCIAL");
        jdbc.update("DELETE FROM USERS");
    }

    @Test
    @DisplayName("case 1: 이메일 가입 활성 회원 → existingMember=true, provider=EMAIL, maskedEmail 마스킹")
    void case1_existingEmailMember() throws Exception {
        stubCert("imp_case1");
        long userId = insertUser("hong.gildong@naver.com", CERT_NAME, CERT_PHONE_NORM, null);

        mockMvc.perform(post("/api/auth/certify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"impUid\":\"imp_case1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existingMember").value(true))
                .andExpect(jsonPath("$.existingProvider").value("EMAIL"))
                .andExpect(jsonPath("$.existingMaskedEmail").value("ho***@naver.com"))
                .andExpect(jsonPath("$.name").value(CERT_NAME))
                .andExpect(jsonPath("$.phone").value(CERT_PHONE))
                .andExpect(jsonPath("$.birth").value(CERT_BIRTH))
                .andExpect(content().string(Matchers.not(Matchers.containsString(String.valueOf(userId)))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("hong.gildong@naver.com"))));
    }

    @Test
    @DisplayName("case 2: 카카오 자동생성 이메일 회원 → provider=KAKAO, maskedEmail=null")
    void case2_existingKakaoMember_autoEmail() throws Exception {
        stubCert("imp_case2");
        long userId = insertUser("kakao_98765@stock-dashboard.local", CERT_NAME, CERT_PHONE_NORM, null);
        insertSocial(userId, "KAKAO", "kakao_98765@stock-dashboard.local", "2026-04-01 10:00:00");

        mockMvc.perform(post("/api/auth/certify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"impUid\":\"imp_case2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existingMember").value(true))
                .andExpect(jsonPath("$.existingProvider").value("KAKAO"))
                .andExpect(jsonPath("$.existingMaskedEmail").doesNotExist())
                .andExpect(content().string(Matchers.not(Matchers.containsString("kakao_98765"))));
    }

    @Test
    @DisplayName("case 3: 카카오 가입 후 GOOGLE 추가 연동 → 가장 최근 CREATED_AT 매핑(GOOGLE)이 우선")
    void case3_mostRecentSocialLinkWins() throws Exception {
        stubCert("imp_case3");
        long userId = insertUser("hong@naver.com", CERT_NAME, CERT_PHONE_NORM, null);
        insertSocial(userId, "KAKAO",  "kakao_111@stock-dashboard.local", "2026-01-01 09:00:00");
        insertSocial(userId, "GOOGLE", "hong@gmail.com",                  "2026-04-15 09:00:00");

        mockMvc.perform(post("/api/auth/certify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"impUid\":\"imp_case3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existingMember").value(true))
                .andExpect(jsonPath("$.existingProvider").value("GOOGLE"))
                .andExpect(jsonPath("$.existingMaskedEmail").value("ho***@naver.com"));
    }

    @Test
    @DisplayName("case 4: 신규 미가입 → existingMember=false, 나머지 null")
    void case4_newUser() throws Exception {
        stubCert("imp_case4");

        mockMvc.perform(post("/api/auth/certify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"impUid\":\"imp_case4\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existingMember").value(false))
                .andExpect(jsonPath("$.existingProvider").doesNotExist())
                .andExpect(jsonPath("$.existingMaskedEmail").doesNotExist())
                .andExpect(jsonPath("$.name").value(CERT_NAME))
                .andExpect(jsonPath("$.phone").value(CERT_PHONE))
                .andExpect(jsonPath("$.birth").value(CERT_BIRTH));
    }

    @Test
    @DisplayName("case 5: 탈퇴 보류(DELETED_AT NOT NULL) → existingMember=false")
    void case5_softDeletedUserExcluded() throws Exception {
        stubCert("imp_case5");
        long userId = insertUser("hong@naver.com", CERT_NAME, CERT_PHONE_NORM, "2026-04-29 10:00:00");
        insertSocial(userId, "KAKAO", "kakao_222@stock-dashboard.local", "2026-04-01 10:00:00");

        mockMvc.perform(post("/api/auth/certify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"impUid\":\"imp_case5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existingMember").value(false))
                .andExpect(jsonPath("$.existingProvider").doesNotExist())
                .andExpect(jsonPath("$.existingMaskedEmail").doesNotExist());
    }

    @Test
    @DisplayName("case 6: DB 전화번호 하이픈 보유 + 본인인증 정규화 형태 → REPLACE 정규화 매칭 성공")
    void case6_phoneNormalizationMatching() throws Exception {
        when(portoneService.getCertification(eq("imp_case6"))).thenReturn(Map.of(
                "name",  CERT_NAME,
                "phone", CERT_PHONE_NORM,
                "birth", CERT_BIRTH
        ));
        insertUser("hong@naver.com", CERT_NAME, "010-1234-5678", null);

        mockMvc.perform(post("/api/auth/certify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"impUid\":\"imp_case6\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existingMember").value(true))
                .andExpect(jsonPath("$.existingProvider").value("EMAIL"))
                .andExpect(jsonPath("$.existingMaskedEmail").value("ho***@naver.com"))
                .andExpect(jsonPath("$.phone").value(CERT_PHONE_NORM));
    }

    private void stubCert(String impUid) throws Exception {
        when(portoneService.getCertification(eq(impUid))).thenReturn(Map.of(
                "name",  CERT_NAME,
                "phone", CERT_PHONE,
                "birth", CERT_BIRTH
        ));
    }

    private long insertUser(String email, String name, String phone, String deletedAt) {
        jdbc.update(
            "INSERT INTO USERS (EMAIL, PASSWORD, NAME, PHONE, EMAIL_VERIFIED, DELETED_AT) " +
            "VALUES (?, ?, ?, ?, 'Y', ?)",
            email, "encoded", name, phone, deletedAt
        );
        Long id = jdbc.queryForObject(
            "SELECT USER_ID FROM USERS WHERE EMAIL = ?", Long.class, email
        );
        return id != null ? id : 0L;
    }

    private void insertSocial(long userId, String provider, String providerEmail, String createdAt) {
        jdbc.update(
            "INSERT INTO USER_SOCIAL (USER_ID, PROVIDER, PROVIDER_EMAIL, CREATED_AT) VALUES (?, ?, ?, ?)",
            userId, provider, providerEmail, createdAt
        );
    }
}
