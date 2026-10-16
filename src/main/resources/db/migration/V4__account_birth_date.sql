USE [uteb];
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH(N'dbo.accounts', N'birth_date') IS NULL
    ALTER TABLE dbo.accounts ADD birth_date date NULL;

IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE version_no = 4)
    INSERT INTO dbo.schema_migrations(version_no, name)
    VALUES (4, N'V4__account_birth_date');

COMMIT;
GO
