-- Stock Dashboard query indexes (MySQL 8.x)
--
-- Safe to run more than once. The helper resolves the actual table-name casing
-- and skips indexes that already exist. Take a database snapshot before applying
-- this file in production and run the matching rollback script if needed.

DROP PROCEDURE IF EXISTS add_index_if_missing;

DELIMITER $$
CREATE PROCEDURE add_index_if_missing(
    IN requested_table VARCHAR(64),
    IN requested_index VARCHAR(64),
    IN index_columns VARCHAR(512)
)
BEGIN
    DECLARE actual_table VARCHAR(64) DEFAULT NULL;

    SELECT MAX(TABLE_NAME)
      INTO actual_table
      FROM INFORMATION_SCHEMA.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND LOWER(TABLE_NAME) = LOWER(requested_table);

    IF actual_table IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
             FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND LOWER(TABLE_NAME) = LOWER(actual_table)
              AND INDEX_NAME = requested_index
       ) THEN
        SET @index_sql = CONCAT(
            'ALTER TABLE `', REPLACE(actual_table, '`', '``'),
            '` ADD INDEX `', REPLACE(requested_index, '`', '``'),
            '` (', index_columns, ')'
        );
        PREPARE index_statement FROM @index_sql;
        EXECUTE index_statement;
        DEALLOCATE PREPARE index_statement;
    END IF;
END$$
DELIMITER ;

CALL add_index_if_missing('STOCK_PRICE', 'idx_stock_price_trade_item', '`TRADE_DATE`, `ITEM_ID`');
CALL add_index_if_missing('STOCK_PRICE', 'idx_stock_price_item_trade', '`ITEM_ID`, `TRADE_DATE`');
CALL add_index_if_missing('USER_WATCHLIST', 'idx_watchlist_user_item', '`USER_ID`, `ITEM_ID`');
CALL add_index_if_missing('USER_WATCHLIST', 'idx_watchlist_item_user', '`ITEM_ID`, `USER_ID`');
CALL add_index_if_missing('PRICE_ALERT', 'idx_price_alert_user_created', '`USER_ID`, `CREATED_AT`');
CALL add_index_if_missing('PRICE_ALERT', 'idx_price_alert_triggered', '`IS_TRIGGERED`, `ALERT_ID`');
CALL add_index_if_missing('PORTFOLIO', 'idx_portfolio_user_created', '`USER_ID`, `CREATED_AT`');
CALL add_index_if_missing('REFRESH_TOKEN', 'idx_refresh_token_token', '`TOKEN`(191)');
CALL add_index_if_missing('CHAT_MESSAGE', 'idx_chat_message_ticker_created', '`TICKER`, `CREATED_AT`');

DROP PROCEDURE IF EXISTS add_index_if_missing;
