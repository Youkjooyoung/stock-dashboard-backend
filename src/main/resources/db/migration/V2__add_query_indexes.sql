-- V2: 조회 경로용 인덱스.
-- 예전 수동 스크립트(sql/V20260824_add_query_indexes.sql)와 같은 내용이지만 운영에는 적용된 적이 없었다
-- (2026-10-15 운영 구조 덤프로 확인). 테이블 이름 대소문자는 운영 DB 기준이다.

-- 종목별 기간 시세 조회, 최신 거래일 조회
CREATE INDEX idx_stock_price_item_trade ON STOCK_PRICE (ITEM_ID, TRADE_DATE);
CREATE INDEX idx_stock_price_trade_item ON STOCK_PRICE (TRADE_DATE, ITEM_ID);

-- 관심종목: 내 목록 조회 / 종목을 관심 등록한 사용자 조회(급등락 알림)
CREATE INDEX idx_watchlist_user_item ON USER_WATCHLIST (USER_ID, ITEM_ID);
CREATE INDEX idx_watchlist_item_user ON USER_WATCHLIST (ITEM_ID, USER_ID);

-- 가격 알림: 내 알림 목록 / 1분마다 도는 미발동 알림 검사
CREATE INDEX idx_price_alert_user_created ON PRICE_ALERT (USER_ID, CREATED_AT);
CREATE INDEX idx_price_alert_triggered ON PRICE_ALERT (IS_TRIGGERED, ALERT_ID);

-- 포트폴리오 목록(최신순)
CREATE INDEX idx_portfolio_user_created ON portfolio (user_id, created_at);

-- 토큰 재발급 때 토큰 문자열로 조회
CREATE INDEX idx_refresh_token_token ON REFRESH_TOKEN (TOKEN(191));

-- 종목 채팅방 최근 메시지
CREATE INDEX idx_chat_message_ticker_created ON CHAT_MESSAGE (TICKER, CREATED_AT);
