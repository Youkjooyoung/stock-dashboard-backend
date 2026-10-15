package com.stock.dashboard.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stock.dashboard.config.GlobalExceptionHandler;
import com.stock.dashboard.exception.BusinessException;

@DisplayName("[보안] 전역 예외 응답")
class GlobalExceptionHandlerTest {

    // GlobalExceptionHandler 는 controller 패키지에만 적용되므로 테스트 컨트롤러도 같은 패키지에 둔다.
    @RestController // 테스트 클래스 안의 중첩 클래스라 @SpringBootTest 컴포넌트 스캔에서는 제외된다
    static class FailingController {
        @GetMapping("/test/duplicate")
        String duplicate() {
            throw new DuplicateKeyException("### SQL: UPDATE USERS SET NICKNAME = ? WHERE USER_ID = ?");
        }

        @GetMapping("/test/npe")
        String npe() {
            throw new NullPointerException("Cannot invoke \"com.stock.dashboard.dto.UserDto.getUserId()\"");
        }

        @GetMapping("/test/business")
        String business() {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");
        }

        @GetMapping("/test/param")
        String param(@RequestParam String startDate) {
            return startDate;
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("DB 제약 위반은 409 + 고정 문구, SQL 문장은 응답에 없다")
    void duplicateKey_hidesSql() throws Exception {
        mockMvc.perform(get("/test/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("SQL"))));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 500 + 고정 문구, 클래스 이름이 응답에 없다")
    void unexpectedException_hidesInternals() throws Exception {
        mockMvc.perform(get("/test/npe"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));
    }

    @Test
    @DisplayName("업무 오류는 지정한 상태 코드와 메시지를 그대로 준다")
    void businessException_keepsMessage() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 사용 중인 닉네임입니다."));
    }

    @Test
    @DisplayName("필수 파라미터가 없으면 500 이 아니라 400 을 준다")
    void missingParameter_isBadRequest() throws Exception {
        mockMvc.perform(get("/test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("필수 값이 없습니다: startDate"));
    }
}
