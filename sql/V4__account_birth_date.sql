-- Run manually in SSMS on the approved database that already has V1-V3.
-- No USE statement: verify DB_NAME() before execution.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF OBJECT_ID(N'dbo.accounts', N'U') IS NULL
        THROW 50001, 'Missing dbo.accounts. Verify the selected database and V1-V3 first.', 1;
    IF OBJECT_ID(N'dbo.schema_migrations', N'U') IS NULL
        THROW 50002, 'Missing dbo.schema_migrations. Verify V1-V3 first.', 1;
    IF (SELECT COUNT(*) FROM dbo.schema_migrations WHERE version_no IN (1, 2, 3)) <> 3
        THROW 50003, 'V1-V3 migration history is incomplete. Do not recreate existing data.', 1;
    IF COL_LENGTH(N'dbo.accounts', N'birth_date') IS NULL
        ALTER TABLE dbo.accounts ADD birth_date date NULL;
    IF NOT EXISTS (
        SELECT 1 FROM sys.columns
        WHERE object_id = OBJECT_ID(N'dbo.accounts') AND name = N'birth_date'
          AND system_type_id = TYPE_ID(N'date') AND is_nullable = 1
    )
        THROW 50004, 'Existing birth_date must be nullable date. Review the schema manually.', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE version_no = 4)
        INSERT INTO dbo.schema_migrations(version_no, name) VALUES (4, N'V4__account_birth_date');
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
