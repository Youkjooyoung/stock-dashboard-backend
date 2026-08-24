-- Rollback for V20260824_add_query_indexes.sql (MySQL 8.x)

DROP PROCEDURE IF EXISTS drop_index_if_present;

DELIMITER $$
CREATE PROCEDURE drop_index_if_present(
    IN requested_table VARCHAR(64),
    IN requested_index VARCHAR(64)
)
BEGIN
    DECLARE actual_table VARCHAR(64) DEFAULT NULL;

    SELECT MAX(TABLE_NAME)
      INTO actual_table
      FROM INFORMATION_SCHEMA.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND LOWER(TABLE_NAME) = LOWER(requested_table);

    IF actual_table IS NOT NULL
       AND EXISTS (
           SELECT 1
             FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND LOWER(TABLE_NAME) = LOWER(actual_table)
              AND INDEX_NAME = requested_index
       ) THEN
        SET @index_sql = CONCAT(
            'ALTER TABLE `', REPLACE(actual_table, '`', '``'),
            '` DROP INDEX `', REPLACE(requested_index, '`', '``'), '`'
        );
        PREPARE index_statement FROM @index_sql;
        EXECUTE index_statement;
        DEALLOCATE PREPARE index_statement;
    END IF;
END$$
DELIMITER ;

CALL drop_index_if_present('STOCK_PRICE', 'idx_stock_price_trade_item');
CALL drop_index_if_present('STOCK_PRICE', 'idx_stock_price_item_trade');
CALL drop_index_if_present('USER_WATCHLIST', 'idx_watchlist_user_item');
CALL drop_index_if_present('USER_WATCHLIST', 'idx_watchlist_item_user');
CALL drop_index_if_present('PRICE_ALERT', 'idx_price_alert_user_created');
CALL drop_index_if_present('PRICE_ALERT', 'idx_price_alert_triggered');
CALL drop_index_if_present('PORTFOLIO', 'idx_portfolio_user_created');
CALL drop_index_if_present('REFRESH_TOKEN', 'idx_refresh_token_token');
CALL drop_index_if_present('CHAT_MESSAGE', 'idx_chat_message_ticker_created');

DROP PROCEDURE IF EXISTS drop_index_if_present;
