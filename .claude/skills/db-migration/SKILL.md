---
name: db-migration
description: Use when changing the PostgreSQL schema of the ScreenSort backend (new table, column, constraint, index, or a status value in a CHECK constraint). Covers Flyway naming, rules and verification.
---

# Database schema change (Flyway)

1. Find the highest existing version in `src/main/resources/db/migration/` (`V<n>__*.sql`).
2. Add a new file `V<n+1>__<snake_case_description>.sql`. Never edit or rename an applied migration,
   even to fix a typo; write a new migration instead.
3. Write plain PostgreSQL. Keep each migration to one logical change.
4. Changing allowed status values: drop and re-create the CHECK constraint in the new migration
   (see `V9__add_running_state_to_organization_export_item.sql` for the pattern), then update every
   Java enum or string set that writes that column.
5. Update the JPA entities/records and stores that read the table. Search for the table name in
   `src/main/java` to find every user.
6. Verify:
   - `./mvnw -q test -Dtest=<StoreOrRepositoryTest>` (Testcontainers, Docker must be running)
   - Optional native check on a disposable database: `scripts/verify-native-tests.ps1`
7. In the review doc or PR, state that the migration must reach the database before the new code
   is deployed.
