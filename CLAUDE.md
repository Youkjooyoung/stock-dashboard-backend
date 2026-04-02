# 주식 대시보드 프로젝트

포트폴리오용 주식 정보 대시보드 웹 애플리케이션
개발 기간: 3개월 | 개발자 2명, 디자이너 1명

---

## Tech Stack

**Backend**
- Java 17, Spring Boot 3.3
- MyBatis (ORM)
- MySQL 8.0
- Spring Security + JWT (Access/Refresh Token)
- WebSocket/STOMP (실시간 시세)
- OAuth2 (Kakao, Google)
- PortOne V1 (본인인증)
- Resend API (이메일 인증)
- AES-256 (주민등록번호 암호화)
- AWS S3 (프로필 이미지)
- Anthropic API (AI 분석)

**Frontend**
- React 18, Vite
- Zustand (전역 상태)
- React Query (@tanstack/react-query)
- Axios (HTTP)
- Chart.js / react-chartjs-2
- CSS Modules

**Infra**
- AWS EC2 (프론트/백 분리 서버)
- Nginx (리버스 프록시)
- Let's Encrypt SSL
- Route53 DNS

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
│   └── profileApi.js
├── components/
│   ├── AddressSearch.jsx
│   ├── AiAnalysis.jsx
│   ├── AlertNotification.jsx
│   ├── AlertSetter.jsx
│   ├── AppLayout.jsx
│   ├── CandlestickChart.jsx
│   ├── EmailVerifyStep.jsx
│   ├── ErrorBoundary.jsx
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
│   └── useQueries.js
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
│   └── authStore.js
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

# 운영 배포 (MobaXterm)
cd ~/stock-dashboard-frontend
git pull origin main
npm install
npm run build
sudo systemctl restart nginx
```

**DB**
```bash
# 로컬 MySQL 접속
mysql -u root -p stock_dashboard
```

---

## Code Style

**공통 규칙**
- 주석 사용 금지
- 메소드명 알파벳 순서 정렬
- CSS, JS, JSX 파일 모듈화 유지

**Backend (Java)**
- CRUD 순서로 메소드 정렬 (Create → Read → Update → Delete)
- private 헬퍼 메소드는 하단에 배치
- DTO는 @Data 롬복 사용

**Frontend (React)**
- CSS Modules 사용 (`styles.className`), 인라인 스타일 금지
- 전역 스타일은 `global.css`, 컴포넌트별 스타일은 `모듈명.module.css`
- React Query 훅은 `useQueries.js`에 집중 관리
- Zustand store는 `store/` 디렉토리

---

## DB Schema

```sql
USERS
  USER_ID, EMAIL, PASSWORD, NAME, NICKNAME, PHONE,
  EMAIL_VERIFIED, EMAIL_VERIFY_TOKEN,
  ACCOUNT_LOCKED, LOGIN_FAIL_CNT,
  ROLE,           -- USER / ADMIN
  PW_RESET_TOKEN, PW_RESET_EXPIRES,
  PROFILE_IMAGE_URL, CREATED_AT

USER_SOCIAL
  ID, USER_ID, PROVIDER, PROVIDER_EMAIL, CREATED_AT

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
POST /api/auth/portone/verify      # PortOne 본인인증
POST /api/auth/forgot-password     # 비밀번호 재설정 메일 발송
POST /api/auth/reset-password      # 토큰으로 비밀번호 변경

# OAuth2 소셜 로그인
GET  /api/auth/kakao/login
GET  /api/auth/google/login
GET  /api/auth/kakao/callback
GET  /api/auth/google/callback

# 소셜 계정 연동
GET  /api/auth/kakao/link
GET  /api/auth/google/link
GET  /api/auth/kakao/link/callback
GET  /api/auth/google/link/callback

# 사용자
GET  /api/user/profile
PUT  /api/user/profile
POST /api/user/profile-image       # S3 이미지 업로드
GET  /api/user/social              # 연동 목록
POST /api/user/social/link
DEL  /api/user/social/unlink/{provider}

# 주식
GET  /api/stock/prices
GET  /api/stock/prices/{ticker}

# 즐겨찾기
GET    /api/user/watchlist
POST   /api/user/watchlist
DELETE /api/user/watchlist/{itemId}

# 포트폴리오
GET    /api/user/portfolio
POST   /api/user/portfolio
PUT    /api/user/portfolio/{id}
DELETE /api/user/portfolio/{id}

# 목표가 알림
GET    /api/alert
POST   /api/alert
DELETE /api/alert/{alertId}

# AI / 뉴스 / 채팅
POST /api/ai/analyze
GET  /api/news/{ticker}
WS   /ws/stock                     # WebSocket 실시간 시세

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
```

---

## 구현 완료

**인증/회원**
- [x] 회원가입 (PortOne 본인인증 → 주민번호 교차검증 → 정보입력 → 이메일인증)
- [x] 보안 키패드 (주민번호 뒷자리 가상 키패드, 랜덤 배치, 마스킹)
- [x] 로그인/로그아웃 (JWT Access/Refresh Token)
- [x] 이메일 미인증 로그인 차단 + 재발송 버튼 (60초 쿨타임)
- [x] 비밀번호 찾기/재설정 (이메일 링크 방식, 1시간 유효)
- [x] 카카오/구글 소셜 로그인
- [x] 소셜 계정 연동/해제 (USER_SOCIAL)
- [x] 프로필 이미지 업로드 (AWS S3)

**주식 기능**
- [x] 주식 시세 조회 / 즐겨찾기
- [x] 포트폴리오 관리
- [x] 목표가 알림 (PRICE_ALERT)
- [x] WebSocket 실시간 시세
- [x] AI 종목 분석 (Anthropic API)
- [x] 뉴스 조회

**UI**
- [x] 네이버페이 스타일 UI 리디자인 (CSS 변수 기반)
- [x] 스켈레톤 UI (종목 리스트, 상세 모달)
- [x] 토스트 알림
- [x] ErrorBoundary
- [x] 번들 최적화 (코드 스플리팅)

**관리자**
- [x] 로그인 시 ADMIN → /admin 자동 이동
- [x] 통계 탭 (전체회원/오늘가입/이메일인증/계정잠금 카드, 즐겨찾기 TOP5)
- [x] 회원 관리 탭 (잠금해제, 메일재발송, 권한변경)
- [x] 주식 관리 탭 (STOCK_ITEM 종목 목록)
- [x] 알림 관리 탭 (PRICE_ALERT 전체 목록)
- [x] AI 채팅 이력 탭 (CHAT_MESSAGE 최신 500건)

## 진행 중 / 예정

- [ ] develop → main 머지 후 운영 배포 (관리자 탭 3개 추가분)
- [ ] 소셜 연동 운영 배포 검증
