-- Additive upgrade. Existing products, accounts, orders and payment attempts are retained.
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 IF OBJECT_ID(N'dbo.schema_migrations',N'U') IS NULL OR NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE version_no=10 AND name=N'V10__integrated_dat_menu')
  THROW 50110,'Apply the integrated V10 catalog before V11.',1;
 IF EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE version_no=11 AND name<>N'V11__commerce_completion')
  THROW 50111,'V11 belongs to another migration. Review the schema.',1;
 IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE version_no=11)
 BEGIN
  ALTER TABLE dbo.stores ADD introduction varchar(2000) NOT NULL CONSTRAINT df_store_introduction DEFAULT('') WITH VALUES;
  ALTER TABLE dbo.stores ADD logo varchar(255) NOT NULL CONSTRAINT df_store_logo DEFAULT('/assets/uteb-logo.png') WITH VALUES;
  ALTER TABLE dbo.stores ADD banner varchar(255) NOT NULL CONSTRAINT df_store_banner DEFAULT('') WITH VALUES;
  ALTER TABLE dbo.categories ADD store_id bigint NULL CONSTRAINT fk_category_store REFERENCES dbo.stores(id);
  CREATE TABLE dbo.shipping_carriers (
   id bigint IDENTITY NOT NULL PRIMARY KEY, version bigint NOT NULL CONSTRAINT df_carrier_version DEFAULT(0),
   store_id bigint NOT NULL CONSTRAINT fk_carrier_store REFERENCES dbo.stores(id),
   name varchar(255) NOT NULL, fee numeric(38,2) NOT NULL, active bit NOT NULL CONSTRAINT df_carrier_active DEFAULT(1),
   CONSTRAINT ck_carrier_fee CHECK(fee>=0 AND fee<=1000000)
  );
  INSERT dbo.shipping_carriers(store_id,name,fee,active)
   SELECT id,N'Giao hàng UTEB',delivery_fee,1 FROM dbo.stores WHERE active=1 AND delivery=1;
  ALTER TABLE dbo.meal_orders ADD carrier_id bigint NULL CONSTRAINT fk_order_carrier REFERENCES dbo.shipping_carriers(id);
  ALTER TABLE dbo.meal_orders ADD carrier_name varchar(255) NOT NULL CONSTRAINT df_order_carrier_name DEFAULT('') WITH VALUES;
  ALTER TABLE dbo.meal_orders ADD shipping_discount numeric(38,2) NOT NULL CONSTRAINT df_order_shipping_discount DEFAULT(0) WITH VALUES;
  ALTER TABLE dbo.vouchers ADD product_id bigint NULL CONSTRAINT fk_voucher_product REFERENCES dbo.products(id);
  ALTER TABLE dbo.refund_requests ADD kind varchar(255) NOT NULL CONSTRAINT df_refund_kind DEFAULT('REFUND') WITH VALUES;
  ALTER TABLE dbo.refund_requests ADD return_instructions varchar(1000) NOT NULL CONSTRAINT df_return_instructions DEFAULT('') WITH VALUES;
  CREATE INDEX ix_carriers_store_active ON dbo.shipping_carriers(store_id,active);
  CREATE INDEX ix_marks_product_kind ON dbo.user_marks(product_id,kind);
  CREATE INDEX ix_products_store_created ON dbo.products(store_id,created_at);
  INSERT dbo.schema_migrations(version_no,name) VALUES(11,N'V11__commerce_completion');
 END;
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
