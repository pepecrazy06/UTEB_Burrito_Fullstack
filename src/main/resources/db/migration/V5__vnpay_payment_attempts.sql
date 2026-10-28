SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF OBJECT_ID(N'dbo.schema_migrations', N'U') IS NULL
        THROW 50005, 'Apply the existing V1-V4 migrations first.', 1;
    IF (SELECT COUNT(*) FROM dbo.schema_migrations WHERE version_no IN (1,2,3,4)) <> 4
        THROW 50006, 'Incomplete V1-V4 history. Do not recreate existing data.', 1;
    IF OBJECT_ID(N'dbo.payment_attempts', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.payment_attempts (
            id bigint IDENTITY PRIMARY KEY,
            version bigint NOT NULL,
            order_id bigint NOT NULL,
            reference varchar(64) NOT NULL,
            amount numeric(38,2) NOT NULL,
            state varchar(32) NOT NULL,
            transaction_no varchar(255) NULL,
            response_code varchar(2) NULL,
            transaction_status varchar(2) NULL,
            created_at datetime2(6) NOT NULL,
            expires_at datetime2(6) NOT NULL,
            CONSTRAINT UQ_payment_attempt_reference UNIQUE (reference),
            CONSTRAINT FK_payment_attempt_order FOREIGN KEY (order_id) REFERENCES dbo.meal_orders(id),
            CONSTRAINT CK_payment_attempt_amount CHECK (amount > 0),
            CONSTRAINT CK_payment_attempt_state CHECK (state IN ('PENDING','PAID','FAILED','REVIEW_REQUIRED'))
        );
        CREATE INDEX IX_payment_attempt_order ON dbo.payment_attempts(order_id);
    END;
    IF ISNULL(COL_LENGTH(N'dbo.payment_attempts', N'reference'), 0) <> 64
        OR COL_LENGTH(N'dbo.payment_attempts', N'order_id') IS NULL
        OR COL_LENGTH(N'dbo.payment_attempts', N'amount') IS NULL
        OR COL_LENGTH(N'dbo.payment_attempts', N'state') IS NULL
        THROW 50007, 'Existing payment_attempts schema needs manual review.', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE version_no=5)
        INSERT dbo.schema_migrations(version_no, name) VALUES (5, N'V5__vnpay_payment_attempts');
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
