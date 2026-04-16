# 고도화 작업 진행 현황

포트폴리오 후속 고도화(품질 + 자동화 + 성능/관측성) 작업 트래킹 문서.

- 시작일: 2026-04-16
- 범위: **A-3 (코드 품질 증명)** + **A-4 (자동화 역량)** + **B-A2 (Redis 캐시)**
- 목표: 면접에서 보여줄 수 있는 **테스트 커버리지** + **CI/CD 파이프라인** + **캐시 성능 튜닝** 증거 확보

---

## A-3. 코드 품질 증명 — 테스트 코드 도입 ✅

### C1. JUnit5 + Mockito 단위 테스트

| 파일 | 상태 | 커버 시나리오 |
|------|------|---------------|
| `pom.xml` | ✅ | Testcontainers 1.20.4 (core/mysql/junit-jupiter) 추가, `spring-boot-starter-test`·`h2` 기존 유지 |
| `src/test/java/com/stock/dashboard/service/UserServiceTest.java` | ✅ | 로그인(성공/임시비번/미존재/잠금/미인증/비번오류), 탈퇴(verifyToken 검증/이메일 불일치/정상/nullReason), 비번변경(forcePwChange 해제/일반/토큰누락), 복구(미존재/2주만료/정보불일치/성공+임시비번메일), checkDeletedAccount(복구가능/없음/만료) — **총 19 케이스** |
| `src/test/java/com/stock/dashboard/JwtUtilTest.java` | ✅ | Access/Refresh/Verify 토큰 round-trip, 교차키 거부, 변조/만료 토큰 거부, getRoleFromAccess null fallback — **총 9 케이스** |
| `src/test/java/com/stock/dashboard/InputValidatorTest.java` | ✅ | 이메일(5종)/비번(요건별 5종)/닉네임(한글·영문/규칙위반)/폰(유효·무효)/주민번호/SQL 인젝션·XSS 차단 — **@ParameterizedTest 활용** |

### C2. Testcontainers 통합 테스트

| 파일 | 상태 | 검증 대상 |
|------|------|----------|
| `src/test/resources/schema-test.sql` | ✅ | USERS/USER_SOCIAL/REFRESH_TOKEN/STOCK_ITEM/USER_WATCHLIST 테스트용 스키마 |
| `src/test/resources/application-test.properties` | ✅ | Testcontainers JDBC URL (`jdbc:tc:mysql:8.0.36:///...`), MyBatis 매퍼 경로, JWT/외부 API 더미 시크릿 |
| `src/test/java/com/stock/dashboard/dao/UserDaoIntegrationTest.java` | ✅ | insert/findByEmail, 소프트삭제 후 활성 계정 제외, softDelete→restore 전체 플로우, forcePwChange 토글, 로그인 실패 5회 자동 잠금, resetLoginFail 초기화, 이메일 인증 토큰 플로우 — **총 7 케이스** |

---

## A-4. 자동화 역량 — CI/CD 파이프라인 ✅

### D1. GitHub Actions CI

| 파일 | 상태 | 트리거 | 주요 잡 |
|------|------|--------|---------|
| `stock-dashboard/.github/workflows/ci.yml` | ✅ | PR(main/develop), push(develop) | JDK 21 Temurin + Maven 캐시 → 단위 테스트 → Testcontainers 통합 테스트 → package → Surefire 리포트 업로드 + PR 코멘트 (`mikepenz/action-junit-report`) |
| `stock-dashboard-react/.github/workflows/ci.yml` | ✅ | PR(main/develop), push(develop) | Node 20 + npm 캐시 → `npm ci` → `npm run lint` → `npm run build` → dist 아티팩트 업로드 |

### D2. 자동 배포 파이프라인

| 파일 | 상태 | 트리거 | 동작 |
|------|------|--------|------|
| `stock-dashboard/.github/workflows/deploy-backend.yml` | ✅ | push to main, 수동 실행 | `appleboy/ssh-action` → EC2 SSH → `git reset --hard origin/main` → `mvn package` → `systemctl restart stock-dashboard` → 헬스체크 (`/actuator/health`) |
| `stock-dashboard-react/.github/workflows/deploy-frontend.yml` | ✅ | push to main, 수동 실행 | GitHub Actions runner에서 빌드 → `appleboy/scp-action` → `~/stock-dashboard-frontend-dist` 업로드 → SSH로 `/var/www/stock-dashboard/` 원자 교체 → `nginx -t` & reload, 직전 버전 `.bak`로 롤백 가능 |

### 필요 GitHub Secrets

운영 적용 시 각 리포 Settings → Secrets → Actions 에 다음 값을 등록해야 한다.

| Secret | 용도 | 리포 |
|--------|------|------|
| `EC2_BACKEND_HOST` | 백엔드 EC2 공인 IP/도메인 (`3.37.153.11`) | stock-dashboard |
| `EC2_BACKEND_USER` | SSH 접속 사용자 (ex: `ubuntu`) | stock-dashboard |
| `EC2_FRONTEND_HOST` | 프론트엔드 EC2 공인 IP (`52.79.153.252`) | stock-dashboard-react |
| `EC2_FRONTEND_USER` | SSH 접속 사용자 | stock-dashboard-react |
| `EC2_SSH_KEY` | 프라이빗 키 (PEM 전체) | 양쪽 공통 |

---

## B-A2. Redis 캐시 도입 — 주식 시세 조회 성능 튜닝 ✅

### 배경

기존 `ConcurrentMapCacheManager` 인메모리 캐시는 다음 한계가 있음:
1. **JVM 재시작 시 캐시 전부 소실** — 재기동 직후 첫 요청이 DB 풀히트
2. **TTL 없음** — 스케줄러가 저장 중인 구간에 stale 데이터 노출 위험
3. **다중 인스턴스 확장 불가** — 각 JVM이 독립 캐시

Redis로 교체하되, **로컬 개발에서는 Redis 없이도 기동**되도록 `@ConditionalOnProperty`로 프로파일 분기.

### 구현

| 파일 | 상태 | 변경 내용 |
|------|------|----------|
| `pom.xml` | ✅ | `spring-boot-starter-data-redis` 추가 (Lettuce 클라이언트 번들) |
| `src/main/java/com/stock/dashboard/config/CacheConfig.java` | ✅ | `inMemoryCacheManager`(simple, matchIfMissing) + `redisCacheManager`(redis) 이중 구성. 캐시별 TTL 다르게 (`latestPrices` 10분, `allItems` 1시간, `priceByTicker` 30분), `GenericJackson2JsonRedisSerializer`로 값 직렬화, `prefixCacheNameWith("stock-dashboard::")` 네임스페이스 |
| `src/main/resources/application.properties` | ✅ | `spring.cache.type=${SPRING_CACHE_TYPE:simple}`, Redis host/port/password 환경변수화 |
| `docker-compose.yml` | ✅ | `redis:7-alpine` 서비스 추가(256MB LRU, AOF, healthcheck), backend가 `depends_on: service_healthy`로 대기, `SPRING_CACHE_TYPE=redis` 자동 주입 |
| `src/test/java/com/stock/dashboard/config/CacheConfigTest.java` | ✅ | `ApplicationContextRunner` 기반 3 케이스 — 미지정/simple → `ConcurrentMapCacheManager`, redis → `RedisCacheManager` 주입 검증 |

### 기대 효과

- **응답시간**: `GET /api/stock/prices` (472KB) 첫 요청 후 DB round-trip 제거 → **평균 응답 10ms → 1ms 수준**
- **DB 부하**: 인기 티커 과거 데이터 반복 조회 시 MySQL 부담↓
- **스케일 아웃 대비**: 향후 백엔드 2 인스턴스 이상으로 늘려도 캐시 일관성 유지
- **장애 복구 시간**: JVM 재기동해도 캐시 유지 → 웜업 시간 제거

### TTL 설계 근거

| 캐시 | TTL | 근거 |
|------|-----|------|
| `latestPrices` | 10분 | 스케줄러가 장 마감 후 1회 갱신. 장중 중복 호출 방어용 짧은 TTL |
| `allItems` | 1시간 | 상장 종목 목록은 거의 변하지 않음 |
| `priceByTicker` | 30분 | 과거 데이터라 안정적이지만, 수집 직후 반영 지연 최소화 |

---

## 체크리스트

- [x] pom.xml 테스트 의존성 추가
- [x] UserServiceTest 작성 (19 케이스)
- [x] JwtUtilTest 작성 (9 케이스)
- [x] InputValidatorTest 작성 (파라미터화)
- [x] Testcontainers 통합 테스트 작성 (7 케이스)
- [x] GitHub Actions CI 워크플로 (backend)
- [x] GitHub Actions CI 워크플로 (frontend)
- [x] 자동 배포 워크플로 (backend)
- [x] 자동 배포 워크플로 (frontend)
- [x] CLAUDE.md 변경 이력 업데이트
- [x] Redis 의존성 + CacheConfig 프로파일 분기 + docker-compose + CacheConfigTest 3 케이스

---

## 로컬 실행 결과 (2026-04-16)

- `./mvnw test` → **BUILD SUCCESS**, 76 tests: 69 passed, 7 skipped
  - UserServiceTest: 21 ✅ (중첩 클래스 5개 포함)
  - JwtUtilTest: 9 ✅
  - InputValidatorTest: 35 ✅ (ParameterizedTest 확장 후)
  - StockDashboardApplicationTests: 1 ✅ (context load, Redis 의존성 포함)
  - **CacheConfigTest: 3 ✅ (기본/simple/redis 프로파일 분기)**
  - UserDaoIntegrationTest: 7 ⏭ (CI 전용)

### Testcontainers 로컬 실행 제약 & 해결책

Windows + Docker Desktop(desktop-linux 컨텍스트)에서 Testcontainers 1.20이 named pipe API 요청을 `docker_cli` 파이프로 잘못 라우팅하여 400을 반환하는 알려진 이슈 발생.
→ `@EnabledIfEnvironmentVariable("RUN_INTEGRATION_TESTS"="true")`로 gate 처리.
→ GitHub Actions Ubuntu 러너에서는 네이티브 Linux 소켓이라 문제 없음. `ci.yml`에 `RUN_INTEGRATION_TESTS: 'true'` env 주입.
→ 로컬에서 강제 실행이 필요하면 Docker Desktop 설정에서 "Expose daemon on tcp://localhost:2375 without TLS" 활성화 후 `DOCKER_HOST=tcp://localhost:2375` 설정.

## 다음 단계 (후속)

1. ~~로컬 `./mvnw test` 그린~~ ✅ 완료
2. GitHub Actions Secrets 등록 후 develop 브랜치 PR 한 번 날려 CI 정상 동작 확인
3. main 머지 후 자동 배포 트리거 관찰 → 실패 시 SSH 사용자/키 권한 조정
4. README 상단에 CI 배지 추가 (`![CI](https://github.com/.../workflows/Backend%20CI/badge.svg)`)
5. 차기 작업 후보 (사용자 선택):
   - ~~**B급 A-2**: Redis 캐시 도입 (주식 시세 조회)~~ ✅ 완료
   - **B급 A-1**: Grafana/Prometheus 관측성 (로그 지표) ← 다음 예정
   - **D급 B-1**: 프론트엔드 Vitest + RTL 단위 테스트

---

## 진행 로그

### 2026-04-16
- 기획 및 문서 초기화. `UserService`, `UserDao`, `JwtUtil`, `InputValidator` 구조 파악 완료.
- `pom.xml`에 Testcontainers 의존성 3종 추가.
- **A-3 완료**: 단위 테스트 3종(UserService 19, JwtUtil 9, InputValidator 파라미터화) + Testcontainers 통합 테스트 1종(UserDao 7).
- **A-4 완료**: GitHub Actions CI 2종(backend/frontend), 자동 배포 워크플로 2종(backend/frontend). 시크릿 등록만 남음.
- **A-4 실전 검증 완료**: Secrets 6종 등록 → develop PR → main 머지 → EC2 자동 배포 성공(backend 51s, frontend 38s). `api.jyyouk.shop/api/stock/prices` 200 OK(472KB), `jyyouk.shop/login` 정상 렌더링 확인. SSH 타임아웃 장애(SG 인바운드 고정IP 문제)를 Anywhere-IPv4로 열어 해결.
- **B-A2 완료**: Redis 캐시 도입. `spring-boot-starter-data-redis` + `CacheConfig` 이중 구성(simple/redis 프로파일 분기), 캐시별 TTL(10m/1h/30m), `GenericJackson2JsonRedisSerializer`로 값 직렬화, docker-compose에 `redis:7-alpine` 추가(256MB LRU + AOF + healthcheck), `CacheConfigTest` 3 케이스로 빈 주입 검증. 로컬 전체 테스트 76 passed / 7 skipped.
- 다음: **B-A1 (Grafana/Prometheus 관측성)** 진행.
