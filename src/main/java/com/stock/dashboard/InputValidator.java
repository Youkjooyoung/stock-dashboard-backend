package com.stock.dashboard;

import org.springframework.stereotype.Component;

@Component
public class InputValidator {

    private static final String EMAIL_PATTERN      = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$";
    private static final String NICKNAME_PATTERN   = "^[가-힣a-zA-Z0-9]{2,8}$";
    private static final String PASSWORD_PATTERN   = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*]).{8,}$";
    private static final String PHONE_PATTERN      = "^01[0-9]{8,9}$";
    private static final String RESIDENT_PATTERN   = "^\\d{13}$";
    private static final String SQL_INJECT_PATTERN = "(?i)(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|EXEC|UNION|--|;|/\\*)";
    private static final String XSS_PATTERN        = ".*[<>\"'&].*";

    public void validateEmail(String email) {
        if (email == null || !email.matches(EMAIL_PATTERN))
            throw new RuntimeException("올바른 이메일 형식이 아닙니다.");
        validateInput(email);
    }

    public void validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank())
            throw new RuntimeException("닉네임을 입력해 주세요.");
        if (!nickname.matches(NICKNAME_PATTERN))
            throw new RuntimeException("닉네임은 한글 2~8자 또는 영문·숫자 혼합 2~8자만 가능합니다.");
        validateInput(nickname);
    }

    public void validatePassword(String password) {
        if (password == null || !password.matches(PASSWORD_PATTERN))
            throw new RuntimeException("비밀번호는 8자 이상, 대/소문자, 숫자, 특수문자(!@#$%^&*)를 포함해야 합니다.");
        validateInput(password);
    }

    public void validatePhone(String phone) {
        if (phone == null || !phone.matches(PHONE_PATTERN))
            throw new RuntimeException("유효하지 않은 휴대폰 번호입니다.");
    }

    public void validateResidentNo(String residentNo) {
        if (residentNo == null || !residentNo.matches(RESIDENT_PATTERN))
            throw new RuntimeException("주민등록번호 형식이 올바르지 않습니다.");
    }

    private void validateInput(String value) {
        if (value.matches(".*" + SQL_INJECT_PATTERN + ".*"))
            throw new RuntimeException("허용되지 않는 문자가 포함되어 있습니다.");
        if (value.matches(XSS_PATTERN))
            throw new RuntimeException("허용되지 않는 문자가 포함되어 있습니다.");
    }
}