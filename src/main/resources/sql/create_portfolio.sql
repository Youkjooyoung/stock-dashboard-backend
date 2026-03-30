-- 포트폴리오 테이블 생성 (Oracle)
CREATE TABLE portfolio (
    portfolio_id  NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       NUMBER         NOT NULL,
    ticker        VARCHAR2(20)   NOT NULL,
    stock_name    VARCHAR2(100)  NOT NULL,
    quantity      NUMBER(12, 2)  NOT NULL,   -- 보유 수량 (소수점 허용)
    buy_price     NUMBER(15)     NOT NULL,   -- 매수가 (원)
    buy_date      VARCHAR2(8)    NOT NULL,   -- yyyyMMdd
    created_at    DATE           DEFAULT SYSDATE
);
