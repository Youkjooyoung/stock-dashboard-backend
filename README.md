# 📈 Stock Dashboard - Backend

> 실시간 주식 데이터 조회, AI 분석, JWT 인증 기반 주식 대시보드 백엔드 서버

---

## 📌 프로젝트 소개

개인 포트폴리오 프로젝트로, 실시간 주식 데이터 조회 및 AI 분석 기능을 제공하는 RESTful API 서버입니다.
Spring Boot 기반으로 구현되었으며 JWT 인증, WebSocket 실시간 통신, MyBatis ORM을 활용합니다.

---

## 🛠 기술 스택

- **Language** : Java 17
- **Framework** : Spring Boot 3.x
- **Security** : Spring Security, JWT
- **ORM** : MyBatis
- **Database** : MySQL
- **Build Tool** : Maven
- **실시간 통신** : WebSocket (STOMP)
- **기타** : Spring Cache, Spring Mail

---

## ✨ 주요 기능

### 🔐 JWT 로그인 / 회원가입
- Access Token / Refresh Token 이중 인증 구조
- Spring Security 기반 인증 필터 (`JwtAuthFilter`)
- AES 암호화를 통한 민감 정보 보호 (`AesEncryptor`)
- OAuth 소셜 로그인 지원
- 이메일 인증, 휴대폰 인증

### 📊 주식 실시간 조회 및 차트
- 실시간 주식 가격 데이터 조회
- WebSocket을 통한 실시간 데이터 스트리밍
- 주식 스케줄러를 통한 자동 데이터 갱신 (`StockScheduler`)
- 주식 종목 비교 기능

### 🤖 AI 분석
- 주식 데이터 기반 AI 분석 리포트 제공
- AI 분석 결과 캐싱 처리 (`CacheConfig`)

### 💼 포트폴리오 관리
- 보유 종목 등록 / 수정 / 삭제
- 포트폴리오 수익률 계산

### 🔔 가격 알림
- 목표 주가 도달 시 이메일 알림 발송
- 알림 조건 설정 / 관리

### 📰 뉴스 조회
- 주식 관련 최신 뉴스 조회

### 💬 실시간 채팅
- WebSocket 기반 실시간 채팅

---

## 📁 프로젝트 구조

```
src/main/java/com/stock/dashboard/
├── config/
│   ├── CacheConfig.java
│   └── GlobalExceptionHandler.java
├── controller/
│   ├── AiAnalysisController.java
│   ├── AuthController.java
│   ├── ChatController.java
│   ├── NewsController.java
│   ├── PortfolioController.java
│   ├── PriceAlertController.java
│   ├── StockController.java
│   └── UserController.java
├── dao/
├── dto/
├── scheduler/
│   └── StockScheduler.java
├── service/
├── util/
│   └── AesEncryptor.java
├── InputValidator.java
├── JwtAuthFilter.java
├── JwtUtil.java
├── SecurityConfig.java
└── WebSocketConfig.java
```

---

##  실행 방법

### 사전 요구사항
- Java 17 이상
- MySQL 8.0 이상
- Maven 3.x

### 1. 레포지토리 클론
```bash
git clone https://github.com/Youkjooyoung/stock-dashboard-backend.git
cd stock-dashboard-backend
```

### 2. 환경 설정
`src/main/resources/application.properties.example` 파일을 복사하여 `application.properties` 생성 후 값 입력:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/stock_dashboard
spring.datasource.username=your_username
spring.datasource.password=your_password

jwt.secret=your_jwt_secret_key
```

### 3. DB 테이블 생성
```bash
src/main/resources/sql/create_portfolio.sql 실행
```

### 4. 실행
```bash
./mvnw spring-boot:run
```

서버 실행 후 `http://localhost:8080` 에서 확인

---

##  관련 레포지토리

- **Frontend**: [stock-dashboard-frontend](https://github.com/Youkjooyoung/stock-dashboard-frontend)

---

##  개발자

| 이름 | GitHub |
|------|--------|
| Youkjooyoung | [@Youkjooyoung](https://github.com/Youkjooyoung) |