-- Apply only to the explicitly selected, approved database. No database switch.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF OBJECT_ID(N'dbo.schema_migrations', N'U') IS NULL
        THROW 50008, 'Apply V1-V5 before order cancellations.', 1;
    IF (SELECT COUNT(*) FROM dbo.schema_migrations WHERE version_no IN (1,2,3,4,5)) <> 5
        THROW 50009, 'Incomplete V1-V5 migration history.', 1;
    -- V6/V7 are reserved for catalog changes. Leave historical V6 rows intact.
    IF EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE version_no=8 AND name<>N'V8__order_cancellations')
        THROW 50012, 'V8 belongs to a different migration; manual review required.', 1;
    IF OBJECT_ID(N'dbo.order_cancellations', N'U') IS NULL AND EXISTS (
        SELECT 1 FROM dbo.schema_migrations WHERE version_no=8 OR (version_no=6 AND name=N'V6__order_cancellations'))
        THROW 50013, 'Cancellation history exists but its table is missing; restore/audit instead of recreating data.', 1;
    IF OBJECT_ID(N'dbo.order_cancellations', N'U') IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM dbo.schema_migrations WHERE (version_no=8 AND name=N'V8__order_cancellations')
            OR (version_no=6 AND name=N'V6__order_cancellations'))
        THROW 50014, 'Existing order_cancellations has no recognized history; manual schema audit required.', 1;
    IF OBJECT_ID(N'dbo.order_cancellations', N'U') IS NULL
    BEGIN
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
    END;
    -- Validate an existing legacy V6 table before recording V8; never drop rows.
    IF EXISTS (
        SELECT 1 FROM (VALUES
            (N'id',N'bigint',8,19,0,0,1),(N'version',N'bigint',8,19,0,0,0),
            (N'order_id',N'bigint',8,19,0,0,0),(N'user_id',N'bigint',8,19,0,0,0),(N'store_id',N'bigint',8,19,0,0,0),
            (N'status',N'varchar',32,0,0,0,0),(N'reason',N'nvarchar',4000,0,0,0,0),
            (N'amount',N'numeric',17,38,2,0,0),(N'confirmed_amount',N'numeric',17,38,2,1,0),
            (N'payment_method',N'varchar',32,0,0,0,0),(N'payment_reference',N'nvarchar',510,0,0,0,0),
            (N'decision_note',N'nvarchar',2000,0,0,1,0),(N'refund_note',N'nvarchar',2000,0,0,1,0),
            (N'refund_reference',N'nvarchar',240,0,0,1,0),(N'evidence',N'nvarchar',510,0,0,1,0),
            (N'decided_by',N'bigint',8,19,0,1,0),(N'refunded_by',N'bigint',8,19,0,1,0),
            (N'created_at',N'datetime2',8,26,6,0,0),(N'decided_at',N'datetime2',8,26,6,1,0),(N'refunded_at',N'datetime2',8,26,6,1,0)
        ) expected(name,type_name,max_length,precision,scale,is_nullable,is_identity)
        LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.order_cancellations') AND c.name=expected.name
        WHERE c.column_id IS NULL OR TYPE_NAME(c.user_type_id)<>expected.type_name OR c.max_length<>expected.max_length
            OR c.precision<>expected.precision OR c.scale<>expected.scale OR c.is_nullable<>expected.is_nullable OR c.is_identity<>expected.is_identity
    ) THROW 50015, 'Cancellation columns differ from the approved schema; manual audit required.', 1;
    IF (SELECT COUNT(*) FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.order_cancellations')
        AND name IN (N'CK_cancellation_status',N'CK_cancellation_amount',N'CK_cancellation_payment',N'CK_cancellation_decision',N'CK_cancellation_rejection',N'CK_cancellation_refund')
        AND is_disabled=0 AND is_not_trusted=0)<>6
        THROW 50016, 'Missing or disabled cancellation constraints; manual audit required.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.order_cancellations')
        AND name=N'UQ_cancellation_refund_reference' AND is_unique=1 AND has_filter=1 AND is_disabled=0)
        THROW 50017, 'Missing cancellation refund uniqueness guard; manual audit required.', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE version_no=8)
        INSERT dbo.schema_migrations(version_no,name) VALUES(8,N'V8__order_cancellations');
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
