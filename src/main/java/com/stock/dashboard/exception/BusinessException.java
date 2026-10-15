package com.stock.dashboard.exception;

import org.springframework.http.HttpStatus;

/**
 * 사용자에게 그대로 보여줘도 되는 업무 오류.
 * 이 예외의 메시지만 응답 본문에 담기고, 그 밖의 예외는 고정 문구로 감춘다.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
