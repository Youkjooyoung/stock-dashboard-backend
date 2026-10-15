-- 로그인 잠금 자동 해제 + 본인인증 결과 1회 사용 기록 (MySQL 8.x)
--
-- 순서가 중요하다. 이번에는 "마이그레이션 먼저, 배포 나중"이다.
--   새 애플리케이션은 USERS.LOCKED_UNTIL 과 CERTIFICATION_USE 를 사용한다.
--   예전 애플리케이션은 둘 다 모르지만, 컬럼·테이블이 추가돼도 문제없이 동작한다.
--
-- 롤백: sql/rollback/R20261016_drop_lock_until_and_certification_use.sql

-- 1) 잠금이 풀리는 시각. NULL 이면 잠겨 있어도 자동으로 풀리지 않는다(관리자 해제 대상).
ALTER TABLE USERS ADD COLUMN LOCKED_UNTIL DATETIME NULL DEFAULT NULL;

-- 2) 지금 잠겨 있는 계정은 모두 로그인 실패로 잠긴 것이므로 15분 뒤 자동 해제되게 맞춘다.
UPDATE USERS
   SET LOCKED_UNTIL = DATE_ADD(NOW(), INTERVAL 15 MINUTE)
 WHERE ACCOUNT_LOCKED = 'Y' AND LOCKED_UNTIL IS NULL;

-- 3) 한 번 쓴 PortOne 본인인증(imp_uid)을 다시 쓰지 못하게 기록한다.
CREATE TABLE IF NOT EXISTS CERTIFICATION_USE (
    IMP_UID    VARCHAR(64)  NOT NULL PRIMARY KEY,
    USER_EMAIL VARCHAR(120),
    PURPOSE    VARCHAR(20)  NOT NULL,
    USED_AT    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
