package com.stock.dashboard.config;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.stock.dashboard.exception.BusinessException;

import lombok.extern.slf4j.Slf4j;

/** 컨트롤러 예외를 {"message": ...} 응답으로 바꾼다. BusinessException 등 사용자용 메시지만 그대로 보내고, 그 밖의 예외는 고정 문구로 감춘다. */
@Slf4j
@RestControllerAdvice(basePackages = "com.stock.dashboard.controller")
public class GlobalExceptionHandler {

    private static final String SERVER_ERROR_MESSAGE = "서버 오류가 발생했습니다.";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, String>> handleBusiness(BusinessException e) {
        log.warn("[경고] 처리된 예외: {}", e.getMessage());
        return message(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return message(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleSecurity(SecurityException e) {
        return message(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    // 중복 키 등 DB 제약 위반: SQL 문장이 응답에 섞이지 않도록 고정 문구로 응답한다.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("[경고] DB 제약 위반: {}", e.getMostSpecificCause().getMessage());
        return message(HttpStatus.CONFLICT, "이미 사용 중이거나 허용되지 않는 값입니다.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingParameter(MissingServletRequestParameterException e) {
        return message(HttpStatus.BAD_REQUEST, "필수 값이 없습니다: " + e.getParameterName());
    }

    @ExceptionHandler({
        MissingRequestHeaderException.class,
        MissingServletRequestPartException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception e) {
        return message(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleUploadSize(MaxUploadSizeExceededException e) {
        return message(HttpStatus.PAYLOAD_TOO_LARGE, "파일 크기가 너무 큽니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception e) {
        // 405, 415 같은 Spring MVC 예외는 원래 상태 코드를 유지한다.
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            return ResponseEntity.status(status).body(Map.of("message", "요청을 처리할 수 없습니다."));
        }
        log.error("[오류] 처리되지 않은 예외: {}", e.getMessage(), e);
        return message(HttpStatus.INTERNAL_SERVER_ERROR, SERVER_ERROR_MESSAGE);
    }

    private ResponseEntity<Map<String, String>> message(HttpStatusCode status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message != null ? message : SERVER_ERROR_MESSAGE));
    }
}
