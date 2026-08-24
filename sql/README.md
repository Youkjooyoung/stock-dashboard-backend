# Stock Dashboard database migration

`V20260824_add_query_indexes.sql` adds indexes for the read paths used by the
dashboard, watchlist, alerts, portfolio, refresh tokens, and chat. The script is
idempotent and resolves table-name casing through `INFORMATION_SCHEMA`.

## Production procedure

1. Confirm the target database and capture a backup before changing indexes.

   ```bash
   mysqldump --single-transaction --routines --triggers stock_dashboard > stock_dashboard_before_20260824.sql
   ```

2. Review current indexes and available disk space.

   ```sql
   SELECT TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME
   FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE()
   ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;
   ```

3. Apply during a low-traffic maintenance window.

   ```bash
   mysql stock_dashboard < sql/V20260824_add_query_indexes.sql
   ```

4. Re-run the index query and compare `EXPLAIN` output for the affected mapper
   queries. Monitor database CPU, write latency, and disk usage.

5. If an index causes a regression, run
   `sql/rollback/R20260824_drop_query_indexes.sql`. The rollback removes only
   indexes introduced by this migration; data is not deleted.

Do not run this migration automatically from the application deployment. A
failed online index build must not replace a healthy application release.
