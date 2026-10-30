-- Additive upgrade: preserve existing accounts, catalog, orders and payment records.
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 IF NOT EXISTS(SELECT 1 FROM dbo.schema_migrations WHERE version_no=11 AND name=N'V11__commerce_completion')
  THROW 50120,'Apply V11 before V12.',1;
 IF EXISTS(SELECT 1 FROM dbo.schema_migrations WHERE version_no=12 AND name<>N'V12__business_rules_and_catalog_metadata')
  THROW 50121,'V12 belongs to another migration.',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.schema_migrations WHERE version_no=12)
 BEGIN
  ALTER TABLE dbo.products ADD
   required_groups varchar(255) NOT NULL CONSTRAINT df_product_required DEFAULT('PROTEIN,RICE,BEANS,SAUCE') WITH VALUES,
   optional_groups varchar(255) NOT NULL CONSTRAINT df_product_optional DEFAULT('TOPPING,EXTRA') WITH VALUES,
   suggested_ingredient_ids varchar(255) NOT NULL CONSTRAINT df_product_suggestions DEFAULT('') WITH VALUES,
   ingredient_calorie_factor numeric(38,2) NOT NULL CONSTRAINT df_product_factor DEFAULT(1) WITH VALUES,
   vegetarian bit NOT NULL CONSTRAINT df_product_vegetarian DEFAULT(0) WITH VALUES,
   included_protein_id bigint NULL CONSTRAINT fk_product_included_protein REFERENCES dbo.ingredients(id);
  ALTER TABLE dbo.ingredients ADD vegetarian bit NOT NULL CONSTRAINT df_ingredient_vegetarian DEFAULT(1) WITH VALUES;
  ALTER TABLE dbo.meal_orders ADD
   points_unit numeric(38,2) NOT NULL CONSTRAINT df_order_points_unit DEFAULT(1000) WITH VALUES,
   points_earned int NOT NULL CONSTRAINT df_order_points_earned DEFAULT(0) WITH VALUES;
  -- Interpret the old catalog once, before names become independent of recipe rules.
  EXEC(N'UPDATE dbo.products SET required_groups=CASE category WHEN ''Tacos'' THEN ''PROTEIN,SAUCE'' WHEN ''Quesadilla'' THEN ''PROTEIN,SAUCE'' WHEN ''Salad'' THEN ''PROTEIN,BEANS,SAUCE'' WHEN ''Kids'' THEN ''PROTEIN,RICE,SAUCE'' ELSE ''PROTEIN,RICE,BEANS,SAUCE'' END,
   optional_groups=CASE category WHEN ''Tacos'' THEN ''RICE,BEANS,TOPPING,EXTRA'' WHEN ''Quesadilla'' THEN ''RICE,BEANS,TOPPING,EXTRA'' WHEN ''Salad'' THEN ''RICE,TOPPING,EXTRA'' WHEN ''Kids'' THEN ''BEANS,TOPPING,EXTRA'' ELSE ''TOPPING,EXTRA'' END,
   ingredient_calorie_factor=CASE WHEN category=''Kids'' THEN 0.5 ELSE 1 END,
   vegetarian=CASE WHEN name LIKE N''% Chay'' THEN 1 ELSE 0 END;
   UPDATE dbo.ingredients SET vegetarian=0 WHERE (group_name=''PROTEIN'' AND name<>N''Đậu hũ chay'') OR name=N''Thêm thịt'';
   UPDATE p SET included_protein_id=(SELECT MIN(i.id) FROM dbo.ingredients i WHERE i.group_name=''PROTEIN'' AND i.name=CASE WHEN p.name LIKE N''% Bò BBQ'' OR p.name LIKE N''% Bò đặc biệt'' THEN N''Bò BBQ'' WHEN p.name LIKE N''% Heo xé'' THEN N''Heo xé'' END) FROM dbo.products p WHERE p.customizable=1;
   UPDATE p SET suggested_ingredient_ids=COALESCE((SELECT STRING_AGG(CAST(chosen.id AS varchar(max)), '','') WITHIN GROUP(ORDER BY chosen.id) FROM
    (SELECT MIN(i.id) AS id FROM dbo.ingredients i WHERE CHARINDEX('',''+i.group_name+'','', '',''+p.required_groups+'','')>0 AND
      (i.group_name<>''PROTEIN'' OR i.name=CASE WHEN p.name LIKE N''% Bò BBQ'' OR p.name LIKE N''% Bò đặc biệt'' THEN N''Bò BBQ'' WHEN p.name LIKE N''% Heo xé'' THEN N''Heo xé'' WHEN p.vegetarian=1 THEN N''Đậu hũ chay'' ELSE N''Gà nướng'' END) AND
      (i.group_name<>''SAUCE'' OR p.name NOT LIKE N''% Gà cay'' OR i.name=N''Sốt cay'') GROUP BY i.group_name) chosen),'''') FROM dbo.products p;
   UPDATE dbo.meal_orders SET points_earned=CAST(FLOOR(total/points_unit) AS int) WHERE points_awarded=1;');
  CREATE TABLE dbo.reward_offers(
   id bigint IDENTITY NOT NULL PRIMARY KEY,version bigint NOT NULL DEFAULT(0),code varchar(255) NOT NULL UNIQUE,name varchar(255) NOT NULL,
   points int NOT NULL,valid_days int NOT NULL,amount numeric(38,2) NOT NULL,active bit NOT NULL DEFAULT(1),
   CONSTRAINT ck_reward_offer CHECK(points BETWEEN 1 AND 1000000 AND valid_days BETWEEN 1 AND 365 AND amount>0 AND amount<=10000000));
  CREATE TABLE dbo.catering_packages(
   id bigint IDENTITY NOT NULL PRIMARY KEY,version bigint NOT NULL DEFAULT(0),code varchar(255) NOT NULL UNIQUE,name varchar(255) NOT NULL,description varchar(1000) NOT NULL,
   price_per_person numeric(38,2) NOT NULL,min_people int NOT NULL,max_people int NOT NULL,lead_hours int NOT NULL,active bit NOT NULL DEFAULT(1),
   CONSTRAINT ck_catering_package CHECK(price_per_person>0 AND price_per_person<=10000000 AND min_people>=1 AND max_people>=min_people AND max_people<=10000 AND lead_hours BETWEEN 1 AND 720));
  INSERT dbo.reward_offers(code,name,points,valid_days,amount) VALUES('DRINK',N'Thức uống',500,90,25000),('CHIPS',N'Chips & salsa',800,90,35000),('BURRITO',N'Một chiếc burrito',1200,90,89000);
  INSERT dbo.catering_packages(code,name,description,price_per_person,min_people,max_people,lead_hours) VALUES
   ('BOX',N'Burrito Box',N'Burrito cuộn sẵn, dễ chia phần.',99000,10,500,24),
   ('PARTY',N'Build Your Own Party',N'Cùng phối nhân, cơm và topping tại bàn tiệc.',99000,10,500,24),
   ('CHIPS',N'Chips & Salsa',N'Món ăn kèm để chia sẻ cùng cả nhóm.',45000,10,500,24);
  INSERT dbo.settings(version,name,setting_value)
   SELECT 0,v.name,v.value FROM (VALUES('brand',N'UTEB Burrito'),('brand_short',N'UTEB'),('free_shipping_threshold',N'150000'),('points_unit',N'1000'),('payment_expiry_minutes',N'15'),('schedule_lead_minutes',N'30'),('schedule_max_days',N'7')) v(name,value)
   WHERE NOT EXISTS(SELECT 1 FROM dbo.settings s WHERE s.name=v.name);
  INSERT dbo.schema_migrations(version_no,name) VALUES(12,N'V12__business_rules_and_catalog_metadata');
 END;
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
