package com.stock.dashboard;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("InputValidator 단위 테스트")
class InputValidatorTest {

    private final InputValidator validator = new InputValidator();

    @Nested
    @DisplayName("validateEmail()")
    class EmailTest {
        @Test
        void 정상_이메일은_통과한다() {
            assertThatCode(() -> validator.validateEmail("user@example.com")).doesNotThrowAnyException();
            assertThatCode(() -> validator.validateEmail("a.b+c-d@sub.example.co")).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "잘못된 이메일: {0}")
        @ValueSource(strings = {"noat", "abc@", "@example.com", "user@", "user@com"})
        void 잘못된_형식은_예외(String invalid) {
            assertThatThrownBy(() -> validator.validateEmail(invalid))
                    .hasMessageContaining("올바른 이메일 형식");
        }

        @Test
        void null_은_예외() {
            assertThatThrownBy(() -> validator.validateEmail(null))
                    .hasMessageContaining("올바른 이메일 형식");
        }
    }

    @Nested
    @DisplayName("validatePassword()")
    class PasswordTest {
        @Test
        void 정상_비밀번호는_통과한다() {
            assertThatCode(() -> validator.validatePassword("Abcd123!")).doesNotThrowAnyException();
            assertThatCode(() -> validator.validatePassword("StrongP@ss99")).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "약한 비밀번호: {0}")
        @ValueSource(strings = {
                "short1!",          // 8자 미만
                "alllower123!",     // 대문자 없음
                "ALLUPPER123!",     // 소문자 없음
                "NoDigits!@#",      // 숫자 없음
                "NoSpecial123"      // 특수문자 없음
        })
        void 요건_미달_비밀번호는_예외(String invalid) {
            assertThatThrownBy(() -> validator.validatePassword(invalid))
                    .hasMessageContaining("비밀번호");
        }
    }

    @Nested
    @DisplayName("validateNickname()")
    class NicknameTest {
        @Test
        void 한글_2에서_8자_통과() {
            assertThatCode(() -> validator.validateNickname("홍길동"))
                    .doesNotThrowAnyException();
            assertThatCode(() -> validator.validateNickname("여덟자닉네임닉"))
                    .doesNotThrowAnyException();
        }

        @Test
        void 영문숫자_혼합_통과() {
            assertThatCode(() -> validator.validateNickname("User01")).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "잘못된 닉네임: {0}")
        @ValueSource(strings = {"", " ", "a", "한", "아주아주긴닉네임임임임임", "nick name", "nick!"})
        void 규칙_위반은_예외(String invalid) {
            assertThatThrownBy(() -> validator.validateNickname(invalid))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("validatePhone()")
    class PhoneTest {
        @ParameterizedTest
        @ValueSource(strings = {"01012345678", "0101234567"})
        void 유효한_핸드폰(String valid) {
            assertThatCode(() -> validator.validatePhone(valid)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(strings = {"010-1234-5678", "0212345678", "abc", "01"})
        void 잘못된_핸드폰은_예외(String invalid) {
            assertThatThrownBy(() -> validator.validatePhone(invalid))
                    .hasMessageContaining("휴대폰");
        }
    }

    @Nested
    @DisplayName("validateResidentNo()")
    class ResidentNoTest {
        @Test
        void _13자리_숫자_통과() {
            assertThatCode(() -> validator.validateResidentNo("9001011234567")).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(strings = {"900101-1234567", "abcdefghijklm", "12345", ""})
        void 형식_오류는_예외(String invalid) {
            assertThatThrownBy(() -> validator.validateResidentNo(invalid))
                    .hasMessageContaining("주민등록번호");
        }
    }

    @Nested
    @DisplayName("SQL Injection / XSS 차단")
    class MaliciousInputTest {
        @ParameterizedTest(name = "정상 값: {0}")
        @ValueSource(strings = {"walter@example.com", "drop-table@test.com", "select.kim@naver.com"})
        void SQL_단어가_들어간_정상_이메일은_통과한다(String email) {
            // 금지어 검사는 정상 사용자를 막기만 했다. SQL 인젝션은 MyBatis #{} 바인딩이 막는다.
            assertThatCode(() -> validator.validateEmail(email)).doesNotThrowAnyException();
        }

        @Test
        void SQL_단어가_들어간_정상_비밀번호는_통과한다() {
            assertThatCode(() -> validator.validatePassword("Dropbox1!")).doesNotThrowAnyException();
            assertThatCode(() -> validator.validatePassword("Tom&Jerry1")).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "형식 위반: {0}")
        @ValueSource(strings = {"a' OR 1=1 --@x.com", "<script>@x.com", "user;drop@x.com"})
        void 이메일_형식에_없는_문자는_예외(String invalid) {
            assertThatThrownBy(() -> validator.validateEmail(invalid))
                    .hasMessageContaining("올바른 이메일 형식");
        }

        @Test
        void XSS_특수문자_포함_닉네임은_예외() {
            assertThatThrownBy(() -> validator.validateNickname("<script>"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        void 너무_긴_비밀번호는_예외() {
            assertThatThrownBy(() -> validator.validatePassword("Aa1!" + "x".repeat(61)))
                    .hasMessageContaining("비밀번호");
        }
    }
}
