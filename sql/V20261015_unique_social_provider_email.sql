-- 같은 소셜 계정(PROVIDER + PROVIDER_EMAIL)이 두 회원에게 동시에 연결되지 않도록 막는다. (MySQL 8.x)
--
-- 배경: 예전 POST /api/user/social/link 는 검증 없이 아무 소셜 이메일이나 연결할 수 있었다.
-- 같은 소셜 이메일이 두 행 이상이면 소셜 로그인 때 다른 사람 계정으로 로그인되거나
-- MyBatis TooManyResultsException 으로 로그인이 막혔다.
--
-- 적용 전 반드시 중복부터 확인한다. 아래 조회 결과가 0행이어야 ALTER 가 성공한다.
--
--   SELECT PROVIDER, PROVIDER_EMAIL, COUNT(*) AS CNT, GROUP_CONCAT(USER_ID) AS USER_IDS
--     FROM USER_SOCIAL
--    GROUP BY PROVIDER, PROVIDER_EMAIL
--   HAVING COUNT(*) > 1;
--
-- 중복이 있으면 실제 주인이 누구인지 확인한 뒤 잘못 연결된 행만 지운다.
-- 롤백: sql/rollback/R20261015_drop_unique_social_provider_email.sql

ALTER TABLE USER_SOCIAL
    ADD CONSTRAINT uq_social_provider_email UNIQUE (PROVIDER, PROVIDER_EMAIL);
