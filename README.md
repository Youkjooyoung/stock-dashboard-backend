# 📈 주식 대시보드 — 백엔드

국내 주식 시세 조회, 관심종목·포트폴리오 관리, 목표가 알림, 종목 채팅, AI 분석을 제공하는 주식 대시보드의 REST API·WebSocket 서버입니다.

- 서비스: https://jyyouk.store
- API: https://api.jyyouk.store
- 프론트엔드: [stock-dashboard-frontend](https://github.com/Youkjooyoung/stock-dashboard-frontend)

---

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| 언어·프레임워크 | Java 21, Spring Boot 3.3 |
| 데이터 | MySQL 8, MyBatis, Flyway |
| 인증·보안 | Spring Security, JWT(Access·Refresh), Kakao·Google OAuth, PortOne 본인인증 |
| 실시간 | WebSocket(STOMP + SockJS) |
| 캐시·운영 | Spring Cache(메모리 / Redis 선택), Actuator, Prometheus |
| 외부 연동 | 공공데이터포털 주식시세, 네이버 뉴스, OpenAI, Resend(메일), AWS S3 |
| 빌드·배포 | Maven, GitHub Actions, AWS EC2 + nginx |

---

## 주요 기능

| 기능 | 내용 |
|---|---|
| 회원 | 이메일 인증 가입(PortOne 본인인증), 카카오·구글 로그인과 계정 연동, 비밀번호 찾기·변경, 탈퇴 후 2주 내 복구 |
| 시세 | 공공데이터 API로 KOSPI·KOSDAQ 일별 시세를 매일 자동 수집, 종목별·기간별 조회 |
| 관심종목·포트폴리오 | 관심종목 등록, 보유 종목·매수가 관리 |
| 알림 | 목표가 도달 알림, 관심종목 급등락 알림 (로그인한 본인에게만 실시간 전달) |
| 종목 채팅 | 종목별 실시간 채팅방 |
| 뉴스·AI | 종목 관련 뉴스, OpenAI 기반 종목·포트폴리오 분석 |
| 관리자 | 회원·알림·채팅·종목 현황, 계정 잠금 해제 |

---

## 프로젝트 구조

```
src/main/java/com/stock/dashboard
├── config/          # 캐시, 예외 처리, 외부 HTTP, 스케줄러, 웹소켓 인증 설정
├── controller/      # REST API, STOMP 메시지 처리
├── service/         # 비즈니스 로직 (회원, 시세, 알림, AI, 외부 연동)
├── dao/  dto/       # MyBatis 매퍼 인터페이스, 요청·응답 객체
├── exception/       # BusinessException (사용자에게 보여줄 오류)
├── scheduler/       # 시세 수집, 가격 알림 검사
├── SecurityConfig.java  JwtUtil.java  JwtAuthFilter.java  WebSocketConfig.java
src/main/resources
├── mapper/          # MyBatis SQL
├── db/migration/    # Flyway 마이그레이션 (V1__baseline.sql ...)
└── application-prod.properties   # 모든 값은 환경변수로 주입
sql/                 # Flyway 도입 전 수동 마이그레이션 기록 (참고용)
```

---

## 로컬 실행

### 1. 준비
- JDK 21, MySQL 8 (빈 데이터베이스 `stock_dashboard`를 만들어 둡니다)
- 테이블은 앱을 처음 실행할 때 Flyway가 자동으로 만듭니다.

### 2. 환경변수
설정 파일은 `application-prod.properties` 하나이고, 값은 모두 환경변수로 넣습니다.
필요한 이름 목록은 [`application.properties.example`](src/main/resources/application.properties.example)에 있습니다.

로컬에서 꼭 필요한 값:

| 변수 | 예시 / 설명 |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `jdbc:mysql://localhost:3306/stock_dashboard?serverTimezone=Asia/Seoul` |
| `FLYWAY_ENABLED`, `FLYWAY_USER`, `FLYWAY_PASSWORD` | `true` / 로컬에서는 DB 계정과 같아도 됩니다 |
| `JWT_SECRET_ACCESS`, `JWT_SECRET_REFRESH` | 32자 이상의 서로 다른 임의 문자열 |
| `SERVER_SSL_ENABLED` | 로컬에서 인증서 없이 실행할 때 `false` |
| `STOCK_SCHEDULER_ENABLED` | 시세 API 키가 없으면 `false` |

OAuth, PortOne, OpenAI, 메일, S3 등은 해당 기능을 쓸 때만 실제 값이 필요합니다. 다만 `AWS_ACCESS_KEY_ID`·`AWS_SECRET_ACCESS_KEY`는 비어 있으면 시작할 때 오류가 나므로, 쓰지 않더라도 `dummy` 같은 임의 값을 넣어 둡니다.

### 3. 실행
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```
기본 포트는 `8443`입니다. 상태 확인: `GET /actuator/health`

---

## 테스트

```bash
# 단위 테스트
./mvnw test -Dtest='!*IntegrationTest'

# 통합 테스트 (Docker 필요, MySQL 8 컨테이너 + Flyway 마이그레이션으로 실행)
RUN_INTEGRATION_TESTS=true ./mvnw test -Dtest='*IntegrationTest'
```

---

## DB 구조 변경 (Flyway)

- 변경은 `src/main/resources/db/migration/V<번호>__<설명>.sql` 파일을 **새로 추가**해서만 합니다. 이미 적용된 파일은 고치지 않습니다.
- 앱이 시작될 때 아직 적용되지 않은 파일만 순서대로 실행됩니다.
- 앱은 권한이 작은 계정(`DB_USERNAME`)으로, 구조 변경은 마이그레이션 전용 계정(`FLYWAY_USER`)으로만 실행합니다.
- 운영 DB는 2026-10-15 구조를 `V1`(출발점)으로 기록해 두었습니다.

---

## 배포

`main`에 머지하면 GitHub Actions가 자동으로 배포합니다.

1. **Backend CI**: 단위·통합 테스트 → WAR 빌드
2. **Backend Auto Deploy**: EC2로 WAR 전송 → 서비스 재시작 → 헬스체크, 실패하면 이전 버전으로 자동 롤백

운영 서버에서는 nginx가 HTTPS와 인증 API 요청 횟수 제한을 맡고, 뒤의 Spring(8443)으로 요청을 넘깁니다.

---

## 보안 원칙

- 비밀값(DB, JWT, OAuth, PortOne, OpenAI, AWS 등)은 저장소에 넣지 않고 서버 환경변수로만 관리합니다.
- 주민등록번호는 수집하지 않습니다. 본인 확인은 PortOne 인증 결과로만 합니다.
- 코드 작성 규칙과 보안 규칙은 [`AGENTS.md`](AGENTS.md)에 정리되어 있습니다.

---

## 개발자

| 이름 | GitHub |
|---|---|
| 육주영 | [@Youkjooyoung](https://github.com/Youkjooyoung) |
