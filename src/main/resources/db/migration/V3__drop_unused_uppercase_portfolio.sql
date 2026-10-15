-- V3: 코드에서 쓰지 않는 대문자 PORTFOLIO 테이블 정리.
--
-- 운영(리눅스 MySQL, 테이블 이름 대소문자 구분)에는 예전 스키마 파일로 만든 대문자 PORTFOLIO 와
-- 실제로 쓰는 소문자 portfolio 가 함께 있다. 2026-10-15 확인 결과 대문자 쪽은 0건이다.
--
-- 주의: 대소문자를 구분하지 않는 DB(Windows, macOS)에서 그냥 DROP TABLE PORTFOLIO 를 실행하면
-- 소문자 portfolio 가 지워진다. 그래서 이름이 정확히 'PORTFOLIO' 인 테이블이 따로 있고,
-- 비어 있을 때만 지운다. 데이터가 있으면 아무것도 하지 않는다.

SET @has_upper = (
    SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema = DATABASE() AND BINARY table_name = 'PORTFOLIO'
);

SET @count_sql = IF(@has_upper = 1, 'SELECT COUNT(*) INTO @upper_rows FROM `PORTFOLIO`', 'SET @upper_rows = -1');
PREPARE count_stmt FROM @count_sql;
EXECUTE count_stmt;
DEALLOCATE PREPARE count_stmt;

SET @drop_sql = IF(@upper_rows = 0, 'DROP TABLE `PORTFOLIO`', 'DO 0');
PREPARE drop_stmt FROM @drop_sql;
EXECUTE drop_stmt;
DEALLOCATE PREPARE drop_stmt;
