package com.stock.dashboard;

import org.springframework.stereotype.Component;

import com.stock.dashboard.exception.BusinessException;

/**
 * 형식(허용 문자 패턴) 기반 입력 검증.
 * SQL 인젝션은 MyBatis #{} 바인딩이 막고, XSS는 화면 출력 시 이스케이프가 막는다.
 * 그래서 "SELECT", "DROP" 같은 금지어 검사는 하지 않는다(정상 값 walter@..., Dropbox1! 을 거부하던 원인).
 */
@Component
public class InputValidator {

    private static final String EMAIL_PATTERN    = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$";
    private static final String NICKNAME_PATTERN = "^[가-힣a-zA-Z0-9]{2,8}$";
    // BCrypt는 72바이트까지만 사용하므로 길이 상한을 둔다.
    private static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*]).{8,64}$";
    private static final String PHONE_PATTERN    = "^01[0-9]{8,9}$";
    private static final String RESIDENT_PATTERN = "^\\d{13}$";
    private static final int    EMAIL_MAX_LENGTH = 100;

    public void validateEmail(String email) {
        if (email == null || email.length() > EMAIL_MAX_LENGTH || !email.matches(EMAIL_PATTERN))
            throw new BusinessException("올바른 이메일 형식이 아닙니다.");
    }

    public void validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank())
            throw new BusinessException("닉네임을 입력해 주세요.");
        if (!nickname.matches(NICKNAME_PATTERN))
            throw new BusinessException("닉네임은 한글 2~8자 또는 영문·숫자 혼합 2~8자만 가능합니다.");
    }

    public void validatePassword(String password) {
        if (password == null || !password.matches(PASSWORD_PATTERN))
            throw new BusinessException("비밀번호는 8~64자이며 대/소문자, 숫자, 특수문자(!@#$%^&*)를 포함해야 합니다.");
    }

    public void validatePhone(String phone) {
        if (phone == null || !phone.matches(PHONE_PATTERN))
            throw new BusinessException("유효하지 않은 휴대폰 번호입니다.");
    }

    public void validateResidentNo(String residentNo) {
        if (residentNo == null || !residentNo.matches(RESIDENT_PATTERN))
            throw new BusinessException("주민등록번호 형식이 올바르지 않습니다.");
    }
}
