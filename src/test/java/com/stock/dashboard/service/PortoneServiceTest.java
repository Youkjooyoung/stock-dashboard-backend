package com.stock.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.stock.dashboard.exception.BusinessException;

@DisplayName("[보안] PortOne 본인인증 응답 검증")
class PortoneServiceTest {

    private HttpClient httpClient;
    private PortoneService portoneService;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        portoneService = new PortoneService(httpClient);
        ReflectionTestUtils.setField(portoneService, "apiKey", "key");
        ReflectionTestUtils.setField(portoneService, "apiSecret", "secret");
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body) {
        HttpResponse<String> res = mock(HttpResponse.class);
        when(res.statusCode()).thenReturn(status);
        when(res.body()).thenReturn(body);
        return res;
    }

    private void stub(HttpResponse<String> certification) throws Exception {
        HttpResponse<String> token = response(200, "{\"code\":0,\"response\":{\"access_token\":\"t\"}}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(token, certification);
    }

    @Test
    @DisplayName("완료된 인증이면 이름·전화번호·생일을 돌려준다")
    void certified_returnsIdentity() throws Exception {
        stub(response(200, "{\"code\":0,\"response\":{\"certified\":true,\"name\":\"홍길동\","
                + "\"phone\":\"01012345678\",\"birthday\":\"1990-01-01\"}}"));

        Map<String, String> cert = portoneService.getCertification("imp_123456789012");

        assertThat(cert).containsEntry("name", "홍길동").containsEntry("phone", "01012345678");
    }

    @Test
    @DisplayName("존재하지 않는 imp_uid(실패 응답)는 빈 값 대신 예외를 던진다")
    void notFound_throws() throws Exception {
        stub(response(404, "{\"code\":-1,\"message\":\"not found\",\"response\":null}"));

        assertThatThrownBy(() -> portoneService.getCertification("imp_000000000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("본인인증이 완료되지 않았습니다");
    }

    @Test
    @DisplayName("certified=false 인 인증은 통과하지 못한다")
    void notCertified_throws() throws Exception {
        stub(response(200, "{\"code\":0,\"response\":{\"certified\":false,\"name\":\"홍길동\",\"phone\":\"01012345678\"}}"));

        assertThatThrownBy(() -> portoneService.getCertification("imp_123456789012"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("imp_uid 형식이 아니면 외부 호출 없이 거부한다(경로 조작 방지)")
    void invalidImpUid_rejectedWithoutCall() {
        assertThatThrownBy(() -> portoneService.getCertification("../payments/imp_1"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(httpClient);
    }
}
