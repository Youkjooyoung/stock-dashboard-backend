package com.stock.dashboard.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.stock.dashboard.JwtAuthFilter;
import com.stock.dashboard.config.GlobalExceptionHandler;
import com.stock.dashboard.service.StockService;

@WebMvcTest(StockController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class StockControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StockService stockService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    void rejectsImpossibleCalendarDate() throws Exception {
        mockMvc.perform(post("/api/stock/collect")
                        .param("basDt", "20260230"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("날짜는 YYYYMMDD 형식의 실제 날짜여야 합니다."));

        verifyNoInteractions(stockService);
    }

    @Test
    void rejectsReversedDateRange() throws Exception {
        mockMvc.perform(get("/api/stock/prices/005930/range")
                        .param("startDate", "20260824")
                        .param("endDate", "20260801"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("시작일은 종료일보다 늦을 수 없습니다."));

        verifyNoInteractions(stockService);
    }

    @Test
    void rejectsInvalidTicker() throws Exception {
        mockMvc.perform(get("/api/stock/prices/not-valid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("종목코드 형식이 올바르지 않습니다."));

        verifyNoInteractions(stockService);
    }
}
