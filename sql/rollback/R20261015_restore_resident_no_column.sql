-- V20261015_drop_resident_no.sql 되돌리기.
-- 옛 애플리케이션 버전으로 되돌려야 할 때만 쓴다. 빈 컬럼만 다시 만들며, 지운 주민등록번호는 복구되지 않는다(의도된 동작).
ALTER TABLE USERS ADD COLUMN RESIDENT_NO VARCHAR(255) DEFAULT NULL;
