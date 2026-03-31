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

**Backend** (`stock-dashboard`)
```
src/main/java/com/stock/dashboard/
├── controller/     # AuthController, UserController, StockController ...
├── service/        # UserService, StockService ...
├── dao/            # MyBatis Mapper 인터페이스
├── dto/            # DTO 클래스
└── config/         # SecurityConfig, WebSocketConfig ...

src/main/resources/
├── mapper/         # MyBatis XML
└── application.properties
```

**Frontend** (`stock-dashboard-react`)
```
src/
├── api/            # axiosInstance.js
├── components/     # 재사용 컴포넌트
├── hooks/          # useQueries.js (React Query 훅)
├── pages/          # 페이지 컴포넌트
├── router/         # index.jsx
├── store/          # authStore.js (Zustand)
└── styles/         # CSS Modules (pages/, components/)
```

---

## Server Info

| 구분 | 도메인 | IP |
|------|--------|----|
| Frontend | jyyouk.shop | 52.79.153.252 |
| Backend | api.jyyouk.shop | 3.37.153.11 |

**로컬 개발**
- Frontend: `http://localhost:5173`
- Backend: `https://localhost:8443`
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
- CSS Modules 사용 (`styles.className`)
- 전역 스타일은 `index.css`, 컴포넌트별 스타일은 `모듈명.module.css`
- React Query 훅은 `useQueries.js`에 집중 관리
- Zustand store는 `store/` 디렉토리

---

## DB Schema (주요 테이블)

```sql
USERS           -- 회원 (EMAIL, PASSWORD, NAME, NICKNAME ...)
USER_SOCIAL     -- 소셜 연동 (user_id, provider, provider_email)
STOCK_ITEM      -- 종목 기본정보
STOCK_PRICE     -- 일별 시세
USER_WATCHLIST  -- 즐겨찾기
PORTFOLIO       -- 포트폴리오
PRICE_ALERT     -- 목표가 알림
REFRESH_TOKEN   -- JWT 리프레시 토큰
CHAT_MESSAGE    -- AI 채팅 이력
```

---

## API Endpoints (주요)

```
POST /api/auth/login
POST /api/auth/signup
POST /api/auth/refresh
GET  /api/auth/kakao/login
GET  /api/auth/google/login
GET  /api/auth/kakao/link       # 소셜 연동용
GET  /api/auth/google/link      # 소셜 연동용

GET  /api/user/social           # 연동 목록 조회
POST /api/user/social/link      # 연동
DEL  /api/user/social/unlink/{provider}  # 연동 해제

GET  /api/stock/prices
GET  /api/user/watchlist
GET  /api/user/portfolio
GET  /api/alert
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
```

---

## 현재 구현 완료

- [x] 회원가입 (본인인증 → 정보입력 → 이메일인증)
- [x] 로그인/로그아웃 (JWT)
- [x] 카카오/구글 소셜 로그인
- [x] 소셜 계정 연동/해제 (USER_SOCIAL)
- [x] 주식 시세 조회/즐겨찾기
- [x] 포트폴리오 관리
- [x] 목표가 알림
- [x] WebSocket 실시간 시세
- [x] AI 분석 (Anthropic API)
- [x] 이메일 인증 (Resend)

## 진행 중 / 예정

- [ ] 소셜 계정 연동 로컬 테스트 완료 후 운영 배포
- [ ] 이메일 미인증 상태 로그인 차단 (백엔드 로그인 검증 추가 필요)