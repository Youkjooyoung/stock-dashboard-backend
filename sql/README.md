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

## V20261015_unique_social_provider_email.sql

Adds `UNIQUE (PROVIDER, PROVIDER_EMAIL)` to `USER_SOCIAL` so one social account
cannot be linked to two members. Run the duplicate check query written at the top
of the script first; the `ALTER` fails while duplicates exist. Back up the database
before applying, and use `sql/rollback/R20261015_drop_unique_social_provider_email.sql`
to revert. Like the index migration, apply it manually, not from the deployment.

## V20261015_drop_resident_no.sql

Clears and drops `USERS.RESIDENT_NO`. Deploy the application version that no longer
reads or writes the column first; the previous version fails on signup and login
without it. Count remaining values, take a backup, run the script, then delete the
backup and any older snapshots that still contain the data. The rollback
(`sql/rollback/R20261015_restore_resident_no_column.sql`) only recreates an empty column.

## V20261016_lock_until_and_certification_use.sql

Adds `USERS.LOCKED_UNTIL` (time-based unlock after 15 minutes) and the
`CERTIFICATION_USE` table (single-use PortOne certifications). Unlike the
resident-number migration, apply this one **before** deploying: the new application
reads both, while the previous version simply ignores them. Accounts that are locked
when the script runs get `LOCKED_UNTIL = NOW() + 15 minutes`. Rollback:
`sql/rollback/R20261016_drop_lock_until_and_certification_use.sql` (after reverting the application).
