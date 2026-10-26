-- Apply only to the explicitly selected, approved database. No database switch.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF OBJECT_ID(N'dbo.schema_migrations', N'U') IS NULL
        THROW 50008, 'Apply V1-V5 before order cancellations.', 1;
    IF (SELECT COUNT(*) FROM dbo.schema_migrations WHERE version_no IN (1,2,3,4,5)) <> 5
        THROW 50009, 'Incomplete V1-V5 migration history.', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE version_no=6)
    BEGIN
        IF OBJECT_ID(N'dbo.order_cancellations', N'U') IS NOT NULL
            THROW 50010, 'Existing order_cancellations needs a manual schema audit.', 1;
        CREATE TABLE dbo.order_cancellations (
            id bigint IDENTITY PRIMARY KEY,
            version bigint NOT NULL,
            order_id bigint NOT NULL,
            user_id bigint NOT NULL,
            store_id bigint NOT NULL,
            status varchar(32) NOT NULL,
            reason nvarchar(2000) NOT NULL,
            amount numeric(38,2) NOT NULL,
            confirmed_amount numeric(38,2) NULL,
            payment_method varchar(32) NOT NULL,
            payment_reference nvarchar(255) NOT NULL,
            decision_note nvarchar(1000) NULL,
            refund_note nvarchar(1000) NULL,
            refund_reference nvarchar(120) NULL,
            evidence nvarchar(255) NULL,
            decided_by bigint NULL,
            refunded_by bigint NULL,
            created_at datetime2(6) NOT NULL,
            decided_at datetime2(6) NULL,
            refunded_at datetime2(6) NULL,
            CONSTRAINT UQ_cancellation_order UNIQUE (order_id),
            CONSTRAINT FK_cancellation_order FOREIGN KEY(order_id) REFERENCES dbo.meal_orders(id),
            CONSTRAINT FK_cancellation_user FOREIGN KEY(user_id) REFERENCES dbo.accounts(id),
            CONSTRAINT FK_cancellation_store FOREIGN KEY(store_id) REFERENCES dbo.stores(id),
            CONSTRAINT FK_cancellation_decider FOREIGN KEY(decided_by) REFERENCES dbo.accounts(id),
            CONSTRAINT FK_cancellation_refunder FOREIGN KEY(refunded_by) REFERENCES dbo.accounts(id),
            CONSTRAINT CK_cancellation_status CHECK(status IN ('REQUESTED','APPROVED','REJECTED','REFUNDED')),
            CONSTRAINT CK_cancellation_amount CHECK(amount>0),
            CONSTRAINT CK_cancellation_payment CHECK(payment_method IN ('VNPAY','MOMO')),
            CONSTRAINT CK_cancellation_decision CHECK(status='REQUESTED' OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
            CONSTRAINT CK_cancellation_rejection CHECK(status<>'REJECTED' OR LEN(LTRIM(RTRIM(decision_note)))>=5 AND decision_note IS NOT NULL),
            CONSTRAINT CK_cancellation_refund CHECK(status<>'REFUNDED' OR (
                confirmed_amount IS NOT NULL AND confirmed_amount=amount AND refunded_by IS NOT NULL
                AND refunded_at IS NOT NULL AND refund_reference IS NOT NULL AND LEN(refund_reference)>=3
                AND evidence IS NOT NULL AND LEN(evidence)>=8))
        );
        -- SQL Server needs a filtered index to allow multiple unconfirmed (NULL) references.
        CREATE UNIQUE INDEX UQ_cancellation_refund_reference ON dbo.order_cancellations(refund_reference) WHERE refund_reference IS NOT NULL;
        CREATE INDEX IX_cancellation_store_status ON dbo.order_cancellations(store_id,status);
        INSERT dbo.schema_migrations(version_no,name) VALUES(6,N'V6__order_cancellations');
    END;
    IF OBJECT_ID(N'dbo.order_cancellations', N'U') IS NULL
        THROW 50011, 'V6 history exists but order_cancellations is missing.', 1;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
