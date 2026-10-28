-- Data-only repair of the four original sample locations; no schema/order changes.
-- Match the original identity, name, address and coordinate pair. Custom locations survive.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF OBJECT_ID(N'dbo.schema_migrations', N'U') IS NULL OR OBJECT_ID(N'dbo.stores', N'U') IS NULL
        THROW 50030, 'Apply the catalog schema before repairing store locations.', 1;
    IF (SELECT COUNT(*) FROM dbo.schema_migrations WHERE version_no IN (1,2,3)) <> 3
        THROW 50031, 'Incomplete V1-V3 catalog history; review before repairing locations.', 1;
    IF EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE version_no=9 AND name<>N'V9__store_coordinates')
        THROW 50032, 'V9 belongs to a different migration; manual review required.', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE version_no=9)
    BEGIN
        DECLARE @fix TABLE (
            id bigint, name nvarchar(255), old_address nvarchar(255), new_address nvarchar(255),
            old_lat float, old_lon float, new_lat float, new_lon float
        );
        INSERT @fix VALUES
        (1,N'UTEB Võ Văn Ngân',N'1 Võ Văn Ngân, phường Linh Chiểu, TP. Thủ Đức',N'1 Võ Văn Ngân, phường Linh Chiểu, TP. Thủ Đức',10.85,106.771,10.8505000,106.7713056),
        (2,N'UTEB Quận 1',N'68 Nguyễn Huệ, phường Bến Nghé, Quận 1',N'68 Nguyễn Huệ, phường Bến Nghé, Quận 1',10.857,106.761,10.7745167,106.7031722),
        (3,N'UTEB Bình Thạnh',N'215 Điện Biên Phủ, phường 15, Bình Thạnh',N'215 Điện Biên Phủ, phường 15, Bình Thạnh',10.864,106.751,10.8000835,106.7064464),
        (4,N'UTEB Thủ Đức',N'88 Hoàng Diệu 2, phường Linh Trung, TP. Thủ Đức',N'88 Hoàng Diệu 2, phường Linh Chiểu, TP. Thủ Đức',10.871,106.741,10.8567000,106.7650860);

        UPDATE s SET latitude=f.new_lat, longitude=f.new_lon, address=f.new_address, version=s.version+1
        FROM dbo.stores s JOIN @fix f ON s.id=f.id AND s.name=f.name AND s.address=f.old_address
        WHERE ABS(s.latitude-f.old_lat)<0.000001 AND ABS(s.longitude-f.old_lon)<0.000001;

        INSERT dbo.schema_migrations(version_no,name) VALUES(9,N'V9__store_coordinates');
    END;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
