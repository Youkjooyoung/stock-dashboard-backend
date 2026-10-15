-- V20261016_lock_until_and_certification_use.sql 되돌리기.
-- 예전 애플리케이션으로 되돌린 뒤에 실행한다(새 애플리케이션은 이 컬럼·테이블을 사용한다).
DROP TABLE IF EXISTS CERTIFICATION_USE;
ALTER TABLE USERS DROP COLUMN LOCKED_UNTIL;
