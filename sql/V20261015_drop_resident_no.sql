-- 주민등록번호 수집 중단: 저장된 값을 지우고 USERS.RESIDENT_NO 컬럼을 없앤다. (MySQL 8.x)
--
-- 근거: 개인정보 보호법 제24조의2 — 법령 근거 없이 주민등록번호를 처리할 수 없다.
--       본인 확인은 PortOne 본인인증 결과(이름·휴대폰·생년월일)로 충분하다.
--
-- 순서가 중요하다.
--   1) RESIDENT_NO 를 쓰지 않는 애플리케이션 버전을 먼저 배포한다.
--      (옛 버전은 INSERT/SELECT 에 이 컬럼을 쓰므로, 컬럼을 먼저 지우면 회원가입·로그인이 깨진다)
--   2) 남은 값 개수를 확인한다.
--        SELECT COUNT(*) FROM USERS WHERE RESIDENT_NO IS NOT NULL;
--   3) 이 파일을 실행한다.
--   4) 이 작업 전에 만든 DB 백업 파일·스냅샷에는 (암호화된) 주민등록번호가 남아 있다.
--      적용 결과를 확인한 뒤 그 백업은 안전하게 삭제하고, 서버 환경변수 AES_SECRET_KEY 도 지운다.
--
-- 롤백: sql/rollback/R20261015_restore_resident_no_column.sql (빈 컬럼만 되살린다. 지운 값은 복구되지 않는다.)

UPDATE USERS SET RESIDENT_NO = NULL WHERE RESIDENT_NO IS NOT NULL;
ALTER TABLE USERS DROP COLUMN RESIDENT_NO;
