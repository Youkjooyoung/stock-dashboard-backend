# 주식 대시보드 프로젝트

포트폴리오용 주식 정보 대시보드 웹 애플리케이션
개발 기간: 3개월 | 개발자 2명, 디자이너 1명

---

## 프로젝트 버전 정보

| 항목 | 실제 버전 |
|------|-----------|
| Java | 21 (pom.xml `java.version`) |
| Spring Boot | 3.3.10 (spring-boot-starter-parent) |
| MyBatis Spring Boot Starter | 3.0.3 |
| jjwt (api/impl/jackson) | 0.12.6 |
| AWS S3 SDK | 2.25.16 |
| MySQL Connector | Spring Boot BOM 관리 |
| Lombok | Spring Boot BOM 관리 |
| 패키징 | WAR (ServletInitializer 포함) |

---

## Tech Stack

**Backend**
- Java 21, Spring Boot 3.3.10
- MyBatis 3.0.3 (ORM)
- MySQL 8.0
- Spring Security + JWT (jjwt 0.12.6, Access/Refresh Token)
- WebSocket/STOMP (실시간 시세)
- OAuth2 (Kakao, Google)
- PortOne V1 (본인인증)
- Resend API (이메일 인증)
- AES-256 (주민등록번호 암호화)
- AWS S3 SDK 2.25.16 (프로필 이미지)
- Anthropic API (AI 분석)

**Frontend**
- React 19.2, Vite 8.0
- Zustand 5.0 (전역 상태)
- React Query (@tanstack/react-query 5.95)
- Axios 1.13 (HTTP)
- Chart.js 4.5 / react-chartjs-2 5.3 / lightweight-charts 5.1
- react-router-dom 7.13
- motion 12.0 (Framer Motion)
- @stomp/stompjs 7.3 + sockjs-client 1.6
- CSS Modules

**Infra**
- AWS EC2 (프론트/백 분리 서버)
- Nginx (리버스 프록시)
- Let's Encrypt SSL
- Route53 DNS
- Docker Compose (로컬 개발용)

---

## Project Structure

**Backend**
```
src/main/java/com/stock/dashboard/
├── config/
│   ├── CacheConfig.java
│   └── GlobalExceptionHandler.java
├── controller/
│   ├── AdminController.java
│   ├── AiAnalysisController.java
│   ├── AuthController.java
│   ├── ChatController.java
│   ├── NewsController.java
│   ├── PortfolioController.java
│   ├── PriceAlertController.java
│   ├── StockController.java
│   └── UserController.java
├── dao/
│   ├── AdminDao.java
│   ├── ChatDao.java
│   ├── PortfolioDao.java
│   ├── PriceAlertDao.java
│   ├── RefreshTokenDao.java
│   ├── StockDao.java
│   ├── UserDao.java
│   └── UserSocialDao.java
├── dto/
│   ├── AdminUserDto.java
│   ├── ChatMessageDto.java
│   ├── NewsDto.java
│   ├── PortfolioDto.java
│   ├── PriceAlertDto.java
│   ├── RefreshTokenDto.java
│   ├── StockItemDto.java
│   ├── StockPriceDto.java
│   ├── UserDto.java
│   └── UserSocialDto.java
├── scheduler/
│   └── StockScheduler.java
├── service/
│   ├── AdminService.java
│   ├── AiAnalysisService.java
│   ├── EmailService.java
│   ├── NewsService.java
│   ├── PortfolioService.java
│   ├── PortoneService.java
│   ├── PriceAlertService.java
│   ├── S3Service.java
│   ├── StockService.java
│   └── UserService.java
├── util/
│   └── AesEncryptor.java
├── InputValidator.java
├── JwtAuthFilter.java
├── JwtUtil.java
├── SecurityConfig.java
└── WebSocketConfig.java

src/main/resources/
├── mapper/
│   ├── AdminMapper.xml
│   ├── ChatMapper.xml
│   ├── PortfolioMapper.xml
│   ├── PriceAlertMapper.xml
│   ├── RefreshTokenMapper.xml
│   ├── StockMapper.xml
│   ├── UserMapper.xml
│   └── UserSocialMapper.xml
├── application.properties
├── application-local.properties   (gitignore)
└── application-prod.properties    (gitignore)
```

**Frontend**
```
src/
├── api/
│   ├── axiosInstance.js
│   ├── profileApi.js
│   └── services.js
├── components/
│   ├── AddressSearch.jsx
│   ├── AiAnalysis.jsx
│   ├── AlertNotification.jsx
│   ├── AlertSetter.jsx
│   ├── AnimatedNumber.jsx
│   ├── AppLayout.jsx
│   ├── CandlestickChart.jsx
│   ├── EmailVerifyStep.jsx
│   ├── ErrorBoundary.jsx
│   ├── FloatingAiChat.jsx
│   ├── Header.jsx
│   ├── NewsSection.jsx
│   ├── PhoneVerifyStep.jsx
│   ├── ProfileImageUpload.jsx
│   ├── SecureKeypad.jsx
│   ├── SignupFormStep.jsx
│   ├── StockCharts.jsx
│   ├── StockChat.jsx
│   ├── StockListSkeleton.jsx
│   ├── StockModal.jsx
│   ├── StockModalSkeleton.jsx
│   ├── StockTable.jsx
│   ├── StockTicker.jsx
│   ├── SummaryCards.jsx
│   └── Toast.jsx
├── hooks/
│   ├── useAlertSocket.js
│   ├── useDarkMode.js
│   ├── useQueries.js
│   └── useToast.js
├── pages/
│   ├── AdminPage.jsx
│   ├── ComparePage.jsx
│   ├── DashboardPage.jsx
│   ├── ForgotPasswordPage.jsx
│   ├── LoginPage.jsx
│   ├── OAuthCallbackPage.jsx
│   ├── OAuthLinkCallbackPage.jsx
│   ├── ProfilePage.jsx
│   ├── ResetPasswordPage.jsx
│   ├── SignupPage.jsx
│   └── VerifyEmailPage.jsx
├── router/
│   └── index.jsx
├── store/
│   ├── authStore.js
│   └── alertStore.js
└── styles/
    ├── global.css
    ├── components/
    └── pages/
```

---

## Server Info

| 구분 | 도메인 | IP |
|------|--------|----|
| Frontend | jyyouk.shop | 52.79.153.252 |
| Backend | api.jyyouk.shop | 3.37.153.11 |

**로컬 개발**
- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080` (로컬), `https://localhost:8443` (SSL)
- DB: `localhost:3306/stock_dashboard`

**환경변수 파일**
- 운영: `.env` (`VITE_API_BASE_URL=https://api.jyyouk.shop`)
- 로컬: `.env.local` (`VITE_API_BASE_URL=https://localhost:8443`)

---

## Commands

**Backend**
```bash
# 로컬 실행
./mvnw spring-boot:run

# 빌드
./mvnw clean package -DskipTests

# 운영 배포 (MobaXterm)
cd ~/stock-dashboard-backend
git pull origin main
./mvnw clean package -DskipTests
sudo systemctl restart stock-dashboard
```

**Frontend**
```bash
# 로컬 실행
npm run dev

# 빌드
npm run build

# 운영 배포 (MobaXterm) — nginx root: /var/www/stock-dashboard/
cd ~/stock-dashboard-frontend
git pull origin main
npm install
npm run build
sudo cp -r ~/stock-dashboard-frontend/dist/* /var/www/stock-dashboard/
sudo systemctl reload nginx
```

**Docker (로컬 전체 기동)**
```bash
docker-compose up --build
```

**DB**
```bash
mysql -u root -p stock_dashboard
```

---

## 코딩 컨벤션

### 공통 규칙

- **주석 금지**: 코드에 주석을 작성하지 않는다. 단, CSS 파일의 섹션 구분용 블록 주석과 `useQueries.js`의 구분선 주석은 허용
  ```css
  /* === StockTable — 주식 테이블 (네이버 Finance 스타일) === */
  ```
  ```js
  // ── Query Keys ─────
  ```
- **에러 메시지 언어**: 한국어 (`"이메일 인증이 필요합니다."`, `"존재하지 않는 이메일입니다."`)
- **커밋 메시지 언어**: 한국어 prefix 컨벤션 (`feat: 소셜 계정 연동 기능 추가`)

### 백엔드 규칙

#### 패키지 구조
```
config/       → 설정 클래스 (CacheConfig, GlobalExceptionHandler)
controller/   → REST API 엔드포인트
dao/          → MyBatis @Mapper 인터페이스
dto/          → 데이터 전송 객체 (@Data 롬복)
scheduler/    → 스케줄링 작업
service/      → 비즈니스 로직
util/         → 유틸리티 (AesEncryptor)
루트 패키지    → 필터/설정 (JwtUtil, JwtAuthFilter, SecurityConfig, WebSocketConfig)
```

#### 네이밍 컨벤션
- **클래스명**: PascalCase + 역할 접미사 (`AuthController`, `UserService`, `StockDao`, `UserDto`)
- **DAO 메소드 네이밍**: `동사 + 대상` 패턴
  - 조회: `findBy...`, `select...`, `check...Exists`
  - 삽입: `insert...`
  - 수정: `update...`, `reset...`, `clear...`
  - 삭제: `delete...`
  ```java
  // UserDao.java
  UserDto findByEmail(String email);
  int insertUser(UserDto dto);
  void updateNickname(UserDto dto);
  void deleteUser(int userId);
  ```
- **DTO 필드명**: camelCase (`userId`, `emailVerified`, `loginFailCnt`)
- 외부 API DTO는 해당 API 필드명 그대로 사용 가능 (`basDt`, `clpr`, `hipr` 등 — 공공데이터 포털)

#### DI 패턴
- `@RequiredArgsConstructor` + `private final` 필드 (Controller, Service 공통)
  ```java
  @RestController
  @RequestMapping("/api/auth")
  @RequiredArgsConstructor
  public class AuthController {
      private final UserService userService;
  ```
- `BCryptPasswordEncoder`처럼 Spring Bean이 아닌 것은 인라인 `new` 초기화 허용
  ```java
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  ```

#### Controller 패턴
- **어노테이션**: `@RestController` + `@RequestMapping("/api/...")` + `@RequiredArgsConstructor`
- **응답 형식**: 모든 엔드포인트 `ResponseEntity<T>`로 감싸서 반환
  ```java
  return ResponseEntity.ok(Map.of("message", "성공"));
  return ResponseEntity.ok(Map.<String, Object>of("success", true));
  ```
- **파라미터 수신**: `@RequestBody Map<String, String>` 또는 `@RequestBody UserDto` (별도 Request DTO 클래스 없이 Map 직접 사용)
- **인증 토큰 추출**: `@RequestHeader("Authorization")` + `extractToken()` 헬퍼 메소드로 "Bearer " 제거
- **OAuth 리다이렉트**: `void` 반환 + `HttpServletResponse.sendRedirect()`
- **로깅**: 필요한 Controller/Service에만 `@Slf4j` 추가

#### 에러 처리
- **GlobalExceptionHandler** (`@RestControllerAdvice`)로 중앙 처리
  | 예외 타입 | HTTP 상태 | 로깅 |
  |-----------|-----------|------|
  | `IllegalArgumentException` | 400 | 없음 |
  | `RuntimeException` | 400 | `log.warn` |
  | `SecurityException` | 401 | 없음 |
  | `Exception` (기타) | 500 | `log.error` + 스택트레이스 |
- 응답 형식 통일: `{"message": "에러 메시지"}`
- 500 에러 시 고정 메시지 반환 (`"서버 오류가 발생했습니다."`)
- 커스텀 예외 클래스 없음 — `RuntimeException` / `IllegalArgumentException` 직접 throw
  ```java
  throw new RuntimeException("이메일 인증이 필요합니다.");
  throw new IllegalArgumentException("잘못된 요청입니다.");
  ```

#### Service 패턴
- **메소드 정렬**: 기능 그룹별 정렬 (인증 > 소셜 > 조회 > 수정 > 삭제)
- **private 헬퍼 메소드**: 클래스 하단에 배치
  ```java
  // UserService.java 하단
  private Map<String, String> issueTokens(UserDto user) { ... }
  private String fetchAccessToken(String code) { ... }
  private Map<String, String> fetchUserInfo(String accessToken) { ... }
  ```
- **트랜잭션**: `@Transactional` 미사용 (현재 프로젝트 패턴)
- **캐싱**: StockService에서 `@Cacheable`, `@CacheEvict` 활용
- **비동기**: `@Async`로 대량 작업 처리, `AtomicInteger`/`volatile`로 진행 상태 추적
- **HTTP 클라이언트**: `java.net.http.HttpClient` 직접 사용 (RestTemplate/WebClient 미사용)

#### DTO 패턴
- 모든 DTO에 `@Data` (Lombok) 사용, 빌더/생성자 없음
- 외부 API 매핑용 DTO에만 `@JsonIgnoreProperties(ignoreUnknown = true)` 추가
- Java record는 Service 내부 응답 전용으로만 사용
  ```java
  public record BulkStatusDto(String status, int current, int total) {}
  ```

#### SQL/Mapper 스타일
- **SQL 키워드/테이블명/컬럼명**: 대문자 (`SELECT USER_ID, EMAIL FROM USERS WHERE ...`)
- **resultType 사용**: `resultMap` 미사용, FQCN 지정
  ```xml
  <select id="findByEmail" parameterType="String"
          resultType="com.stock.dashboard.dto.UserDto">
      SELECT USER_ID, EMAIL, PASSWORD, NAME, NICKNAME, PHONE,
             EMAIL_VERIFIED, EMAIL_VERIFY_TOKEN,
             LOGIN_FAIL_CNT, ACCOUNT_LOCKED, CREATED_AT
      FROM USERS WHERE EMAIL = #{_parameter}
  </select>
  ```
- **파라미터**: 단일값 `#{_parameter}`, DTO 필드 `#{email}`, 다중 `@Param` + `#{paramName}`
- **동적 SQL**: `<foreach>` 태그, `INSERT IGNORE INTO`, `NOT EXISTS` 중복 방지
- **XML 이스케이프**: `&gt;`, `&lt;` 사용

#### 보안 (JWT/인증)
- **SecurityFilterChain** Bean 방식 (Spring Security 6.x Lambda DSL)
  ```java
  http
      .cors(cors -> cors.configurationSource(corsConfigurationSource()))
      .csrf(csrf -> csrf.disable())
      .authorizeHttpRequests(auth -> auth
          .requestMatchers("/api/auth/**", "/api/stock/**", "/ws/**").permitAll()
          .anyRequest().authenticated())
      .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
  ```
- **CORS**: 허용 Origin 상수로 관리 (`ALLOWED_ORIGINS`, `ALLOWED_METHODS`)
- **JWT**: Access Token + Refresh Token 이중 토큰 방식

### 프론트엔드 규칙

#### import 순서
```jsx
// 1. React 훅
import { useState, useEffect, useRef } from 'react';
// 2. react-router-dom
import { useNavigate } from 'react-router-dom';
// 3. 서드파티 라이브러리
import { useQueryClient } from '@tanstack/react-query';
// 4. 내부 컴포넌트
import StockTable from '../components/StockTable';
// 5. 커스텀 훅
import { useStockPrices, useWatchlist } from '../hooks/useQueries';
// 6. API 모듈
import api from '../api/axiosInstance';
// 7. Store
import useAuthStore from '../store/authStore';
// 8. CSS Modules (항상 마지막)
import styles from '../styles/pages/DashboardPage.module.css';
```

#### 컴포넌트 구조 및 코드 배치 순서
- **선언 방식**: `export default function ComponentName()` (화살표 함수 미사용)
- **코드 배치 순서**:
  1. 라우터 훅 (`useNavigate`, `useOutletContext`)
  2. Store/Context (`useAuthStore`, `useQueryClient`)
  3. React Query 데이터 훅 (`useStockPrices()`, `useWatchlist()`)
  4. 로컬 useState (UI 상태)
  5. useEffect (사이드 이펙트)
  6. 이벤트 핸들러 함수
  7. 파생 데이터 (computed values)
  8. return JSX

```jsx
export default function DashboardPage() {
  const navigate = useNavigate();                          // 1. 라우터 훅
  const { autoRefresh } = useOutletContext();
  const queryClient = useQueryClient();                    // 2. Store/Context
  const { data: stocks } = useStockPrices();               // 3. React Query
  const [search, setSearch] = useState('');                 // 4. useState
  useEffect(() => { ... }, []);                            // 5. useEffect
  const handleSearch = () => { ... };                      // 6. 핸들러
  const filtered = stocks?.filter(...);                    // 7. 파생 데이터
  return <div>...</div>;                                   // 8. JSX
}
```

#### 상태 관리 (전역 vs 서버 상태)
- **Zustand** (전역 상태): 인증 정보만 (`token`, `user`, `role`) + 간단한 UI 상태 (`badgeCount`)
  ```js
  // authStore.js — 단일 플랫 객체, localStorage 동기화
  const useAuthStore = create((set) => ({
    token: localStorage.getItem('accessToken') || null,
    user:  localStorage.getItem('userEmail')   || null,
    role:  localStorage.getItem('userRole')    || 'USER',
    setAuth: (email, accessToken, refreshToken, userId, role) => { ... },
    logout: async () => { ... },
  }));
  ```
- **React Query** (서버 상태): API에서 가져오는 모든 데이터
- Store는 2개만 유지 (`authStore`, `alertStore`), 서버 데이터는 전부 `useQueries.js` 훅으로 관리
- **React Query 키**: 중앙 `QUERY_KEYS` 객체로 관리
  ```js
  export const QUERY_KEYS = {
    stocks:      ['stocks'],
    stockDetail: (ticker) => ['stocks', ticker],
    watchlist:   ['watchlist'],
    alerts:      ['alerts'],
    userInfo:    ['user', 'info'],
    portfolio:   ['portfolio'],
    socialLinks: ['user', 'social'],
  };
  ```
- **Mutation 후 캐시 무효화**: `onSuccess`에서 `queryClient.invalidateQueries()` 호출

#### API 호출 패턴
- **Axios 인스턴스**: `axiosInstance.js`에서 단일 인스턴스 생성
  - `baseURL`: `${import.meta.env.VITE_API_BASE_URL}/api`
  - `timeout`: 10000ms
  - `withCredentials`: true
- **Request 인터셉터**: `localStorage.getItem('accessToken')`으로 JWT 자동 첨부
- **Response 인터셉터**: 401 시 Refresh Token 자동 갱신 (동시 요청 큐 패턴: `isRefreshing` + `failedQueue`)
- **환경변수**: `import.meta.env.VITE_*` 패턴
- **에러 추출**: `err.response?.data?.message`

#### 스타일링
- **CSS Modules 전용**: `*.module.css` 파일 사용, 인라인 스타일 금지
- **파일 위치**: `styles/components/`, `styles/pages/` 디렉토리에 분리
- **클래스 네이밍**: kebab-case 우선 (`login-page`, `dashboard-main`), JSX에서 `styles['login-page']`로 접근
  ```jsx
  <div className={styles['login-page']}>
    <div className={styles['login-card']}>
  ```
- **전역 스타일**: `global.css`에 CSS 변수 정의 (`--surface`, `--border`, `--primary`, `--radius-lg`)

#### 라우팅
- **코드 스플리팅**: 모든 페이지 `lazy()` + `import()`로 동적 로딩, `Suspense` 래핑
  ```jsx
  const LoginPage     = lazy(() => import('../pages/LoginPage'));
  const DashboardPage = lazy(() => import('../pages/DashboardPage'));
  ```
- **레이아웃 패턴**: `AppLayout`을 레이아웃 라우트로 사용 (인증 가드 역할)
  - 인증 필요 페이지: `<Route element={<AppLayout />}>` 내부에 중첩
  - 인증 불필요 페이지: 레이아웃 바깥에 단독 배치
- **ErrorBoundary**: 인증 페이지에 개별 래핑
  ```jsx
  <Route element={<AppLayout />}>
    <Route path="/" element={<ErrorBoundary><DashboardPage /></ErrorBoundary>} />
  </Route>
  ```
- **번들 최적화** (vite.config.js `manualChunks`):
  - `vendor-react`: react, react-dom, react-router-dom
  - `vendor-chart`: chart.js, react-chartjs-2
  - `vendor-stomp`: @stomp, sockjs
  - `vendor`: 나머지 node_modules

### 파일/변수 네이밍

| 대상 | 규칙 | 예시 |
|------|------|------|
| 컴포넌트/페이지 JSX | PascalCase | `DashboardPage.jsx`, `StockModal.jsx` |
| 훅 파일 | camelCase + `use` 접두어 | `useQueries.js` |
| Store 파일 | camelCase + `Store` 접미어 | `authStore.js`, `alertStore.js` |
| API 모듈 | camelCase | `axiosInstance.js`, `profileApi.js` |
| CSS Module | 연결된 컴포넌트/페이지명 | `LoginPage.module.css` |
| CSS 클래스 | kebab-case | `.login-page`, `.dashboard-main` |
| Java 클래스 | PascalCase + 역할 접미사 | `UserService`, `AuthController` |
| Java 변수/필드 | camelCase | `userId`, `emailVerified` |
| DB 테이블/컬럼 | UPPER_SNAKE_CASE | `USER_ID`, `STOCK_ITEM` |
| 환경변수 (Vite) | `VITE_` 접두어 + UPPER_SNAKE | `VITE_API_BASE_URL` |

### 환경변수

- **Backend**: `application.properties` (gitignore), 프로필별 분리 (`-local`, `-prod`)
- **Frontend**: `.env` (운영), `.env.local` (로컬) — 모두 gitignore 대상
- **Docker**: `docker-compose.yml`에서 환경변수 주입, DB는 `host.docker.internal:3306` 참조

---

## DB Schema

```sql
USERS
  USER_ID, EMAIL, PASSWORD, NAME, NICKNAME, PHONE,
  ADDRESS, ADDRESS_DETAIL, RESIDENT_NO,
  EMAIL_VERIFIED, EMAIL_VERIFY_TOKEN,
  ACCOUNT_LOCKED, LOGIN_FAIL_CNT,
  ROLE,           -- USER / ADMIN
  PW_RESET_TOKEN, PW_RESET_EXPIRES,
  PROFILE_IMAGE_URL, CREATED_AT

USER_SOCIAL
  SOCIAL_ID, USER_ID, PROVIDER, PROVIDER_EMAIL, CREATED_AT

STOCK_ITEM
  ITEM_ID, TICKER, ITEM_NM, MARKET, CREATED_AT

STOCK_PRICE
  PRICE_ID, ITEM_ID, OPEN_PRICE, CLOSE_PRICE,
  HIGH_PRICE, LOW_PRICE, VOLUME, TRADE_DATE

USER_WATCHLIST
  WATCHLIST_ID, USER_ID, ITEM_ID, CREATED_AT

PORTFOLIO
  PORTFOLIO_ID, USER_ID, ITEM_ID, TICKER, STOCK_NAME,
  BUY_PRICE, QUANTITY, BUY_DATE, CREATED_AT

PRICE_ALERT
  ALERT_ID, USER_ID, ITEM_ID, TICKER, STOCK_NAME,
  TARGET_PRICE, ALERT_TYPE, IS_TRIGGERED,
  CREATED_AT, TRIGGERED_AT

REFRESH_TOKEN
  ID, USER_ID, TOKEN, EXPIRES_AT, CREATED_AT

CHAT_MESSAGE
  MSG_ID, TICKER, USER_EMAIL, NICKNAME, CONTENT, CREATED_AT
```

---

## API Endpoints

```
# 인증
POST /api/auth/login
POST /api/auth/signup
POST /api/auth/logout
POST /api/auth/refresh
POST /api/auth/portone/verify
POST /api/auth/forgot-password
POST /api/auth/reset-password
POST /api/auth/resend-verify
GET  /api/auth/verify-email
GET  /api/auth/check-email
POST /api/auth/certify

# OAuth2 소셜 로그인
GET  /api/auth/kakao/login
GET  /api/auth/google/login
GET  /api/auth/kakao/callback
GET  /api/auth/google/callback

# 소셜 계정 연동 (state 파라미터에 JWT 인코딩)
GET  /api/auth/kakao/link?token=
GET  /api/auth/google/link?token=
GET  /api/auth/kakao/link/callback?code=&token=
GET  /api/auth/google/link/callback?code=&token=

# 사용자
GET  /api/user/profile
PUT  /api/user/profile
POST /api/user/profile-image
GET  /api/user/social
POST /api/user/social/link
DEL  /api/user/social/unlink/{provider}
GET  /api/user/kakao/**
GET  /api/user/google/**

# 주식 / 즐겨찾기 / 포트폴리오 / 알림
GET  /api/stock/prices
GET  /api/stock/prices/{ticker}
GET    /api/user/watchlist
POST   /api/user/watchlist
DELETE /api/user/watchlist/{itemId}
GET    /api/user/portfolio
POST   /api/user/portfolio
PUT    /api/user/portfolio/{id}
DELETE /api/user/portfolio/{id}
GET    /api/alert
POST   /api/alert
DELETE /api/alert/{alertId}

# AI / 뉴스 / 채팅
POST /api/ai/analyze
GET  /api/news/{ticker}
WS   /ws/stock

# 관리자
GET  /api/admin/stats
GET  /api/admin/users
GET  /api/admin/watchlist/top
GET  /api/admin/stocks
GET  /api/admin/alerts
GET  /api/admin/chats
POST /api/admin/users/{userId}/unlock
POST /api/admin/users/{userId}/resend-verify
POST /api/admin/users/{userId}/role
```

---

## Git Workflow

```
main        # 운영 배포 브랜치
develop     # 개발 통합 브랜치
feat/*      # 기능 개발
fix/*       # 버그 수정
```

**커밋 컨벤션**
```
feat: 소셜 계정 연동 기능 추가
fix: 카카오 콜백 URL 오류 수정
refactor: UserService 메소드 정렬
style: ProfilePage CSS 수정
design: 네이버페이 스타일 UI 리디자인
chore: gitignore 추가
merge: Java 17 → 21 LTS 업그레이드 반영
```

---

## 구현 완료

**인증/회원**
- [x] 회원가입 (PortOne 본인인증 → 주민번호 교차검증 → 정보입력 → 이메일인증)
- [x] 보안 키패드 (주민번호 뒷자리 가상 키패드, 랜덤 배치, 마스킹)
- [x] 로그인/로그아웃 (JWT Access/Refresh Token)
- [x] 이메일 미인증 로그인 차단 + 재발송 버튼 (60초 쿨타임)
- [x] 비밀번호 찾기/재설정 (이메일 링크 방식, 1시간 유효)
- [x] 카카오/구글 소셜 로그인 (USER_SOCIAL 연동 계정 자동 매칭)
- [x] 소셜 계정 연동/해제 (USER_SOCIAL, state 파라미터 JWT 인코딩)
- [x] 프로필 이미지 업로드 (AWS S3)

**주식 기능**
- [x] 주식 시세 조회 / 즐겨찾기
- [x] 포트폴리오 관리
- [x] 목표가 알림 (PRICE_ALERT)
- [x] WebSocket 실시간 시세
- [x] AI 종목 분석 (Anthropic API)
- [x] 뉴스 조회

**UI/UX**
- [x] 네이버페이 스타일 UI 리디자인 (CSS 변수 기반)
- [x] 스켈레톤 UI (종목 리스트, 상세 모달)
- [x] 토스트 알림
- [x] ErrorBoundary
- [x] 번들 최적화 (코드 스플리팅, lazy import, manualChunks)
- [x] 로그인 페이지 소셜 버튼 공식 디자인 적용
- [x] 플로팅 AI 채팅 위젯 (FloatingAiChat) 전체 페이지 공통 적용
  - FAQ 탭: 23개 질문 4개 카테고리 아코디언
  - AI 종목 분석 탭: 종목코드 + 질문 입력

**관리자**
- [x] 로그인 시 ADMIN → /admin 자동 이동 (role 기반 라우터 분기)
- [x] 통계 탭 (전체회원/오늘가입/이메일인증/계정잠금, 즐겨찾기 TOP5)
- [x] 회원 관리 탭 (잠금해제, 메일재발송, 권한변경)
- [x] 주식 관리 탭 (STOCK_ITEM 종목 목록)
- [x] 알림 관리 탭 (PRICE_ALERT 전체 목록)
- [x] AI 채팅 이력 탭 (CHAT_MESSAGE 최신 500건)

## 진행 중 / 예정

- [ ] 소셜 로그인(OAuth) 시 role 미전달 버그 수정
- [ ] 관리자 회원 탭 검색/필터 기능 추가
- [ ] 포트폴리오 수익률 차트 강화
