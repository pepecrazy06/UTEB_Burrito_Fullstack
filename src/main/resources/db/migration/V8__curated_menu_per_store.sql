SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF OBJECT_ID(N'dbo.schema_migrations', N'U') IS NULL
        THROW 50010, 'Apply V1-V7 migrations first.', 1;
    IF (SELECT COUNT(*) FROM dbo.schema_migrations WHERE version_no IN (1,2,3,4,5,6,7)) <> 7
        THROW 50011, 'Incomplete V1-V7 history. Do not change the catalog.', 1;

    IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE version_no = 8)
    BEGIN
        DECLARE @curated TABLE (source_id bigint PRIMARY KEY, name nvarchar(255));
        INSERT @curated (source_id,name) VALUES
            (1,N'Burrito Gà nướng'),(2,N'Burrito Bò BBQ'),(3,N'Burrito Heo xé'),
            (4,N'Burrito Chay'),(5,N'Burrito Gà cay'),(6,N'Burrito Bò đặc biệt'),
            (7,N'Bowl Gà nướng'),(8,N'Bowl Bò BBQ'),
            (13,N'Tacos Gà nướng'),(16,N'Tacos Chay'),
            (19,N'Quesadilla Gà nướng'),(20,N'Quesadilla Bò BBQ'),
            (25,N'Salad Gà nướng'),(28,N'Salad Chay'),
            (31,N'Kids Gà nướng'),(32,N'Kids Bò BBQ'),
            (37,N'Chips & Salsa'),(38,N'Chips & Guacamole'),(39,N'Chips & Queso'),
            (43,N'Trà chanh'),(46,N'Nước suối'),(47,N'Cola');

        -- Hide only unchanged V3 sample products outside the curated menu.
        -- Keep rows and order snapshots so historical orders remain intact.
        UPDATE p SET p.available = 0, p.version = p.version + 1
        FROM dbo.products AS p
        JOIN (VALUES
            (9,N'Bowl Heo xé'),(10,N'Bowl Chay'),(11,N'Bowl Gà cay'),(12,N'Bowl Bò đặc biệt'),
            (14,N'Tacos Bò BBQ'),(15,N'Tacos Heo xé'),(17,N'Tacos Gà cay'),(18,N'Tacos Bò đặc biệt'),
            (21,N'Quesadilla Heo xé'),(22,N'Quesadilla Chay'),(23,N'Quesadilla Gà cay'),(24,N'Quesadilla Bò đặc biệt'),
            (26,N'Salad Bò BBQ'),(27,N'Salad Heo xé'),(29,N'Salad Gà cay'),(30,N'Salad Bò đặc biệt'),
            (33,N'Kids Heo xé'),(34,N'Kids Chay'),(35,N'Kids Gà cay'),(36,N'Kids Bò đặc biệt'),
            (40,N'Khoai nướng'),(41,N'Salsa tươi'),(42,N'Guacamole'),
            (44,N'Soda chanh'),(45,N'Trà đào'),(48,N'Nước cam')
        ) AS retired(id,name) ON p.id = retired.id AND p.name = retired.name
        WHERE p.available = 1;

        -- Update only known V7 sample photos, preserving administrator replacements.
        UPDATE p SET p.image = photos.new_image, p.version = p.version + 1
        FROM dbo.products AS p
        JOIN @curated AS c ON p.id = c.source_id AND p.name = c.name
        JOIN (VALUES
            (1,N'/assets/menu/burrito-grilled-chicken-full-plate.jpg',N'/assets/menu/burrito-grilled-chicken-cutout.png'),
            (2,N'/assets/menu/burrito-bbq-beef-full-plate.jpg',N'/assets/menu/burrito-bbq-beef-cutout.png'),
            (3,N'/assets/menu/burrito-pulled-pork-full-plate.jpg',N'/assets/menu/burrito-pulled-pork-cutout.png'),
            (4,N'/assets/menu/burrito-vegetarian-full-plate.jpg',N'/assets/menu/burrito-vegetarian-cutout.png'),
            (5,N'/assets/menu/burrito-spicy-chicken-full-plate.jpg',N'/assets/menu/burrito-spicy-chicken-cutout.png'),
            (6,N'/assets/menu/burrito-premium-beef-full-plate.jpg',N'/assets/menu/burrito-premium-beef-cutout.png'),
            (7,N'/assets/menu/bowl-grilled-chicken-wide.jpg',N'/assets/menu/bowl-grilled-chicken-cutout.png'),
            (8,N'/assets/menu/bowl-bbq-beef-wide.jpg',N'/assets/menu/bowl-bbq-beef-cutout.png'),
            (13,N'/assets/menu/tacos-grilled-chicken-full-plate.jpg',N'/assets/menu/tacos-grilled-chicken-cutout.png'),
            (16,N'/assets/menu/tacos-vegetarian-full-plate.jpg',N'/assets/menu/tacos-vegetarian-cutout.png'),
            (19,N'/assets/menu/quesadilla-grilled-chicken-full-plate.jpg',N'/assets/menu/quesadilla-grilled-chicken-cutout.png'),
            (20,N'/assets/menu/quesadilla-bbq-beef-full-plate.jpg',N'/assets/menu/quesadilla-bbq-beef-cutout.png'),
            (25,N'/assets/menu/salad-grilled-chicken-wide.jpg',N'/assets/menu/salad-grilled-chicken-cutout.png'),
            (28,N'/assets/menu/salad-vegetarian-wide.jpg',N'/assets/menu/salad-vegetarian-cutout.png'),
            (31,N'/assets/menu/kids-grilled-chicken-full-plate.jpg',N'/assets/menu/kids-grilled-chicken-cutout.png'),
            (32,N'/assets/menu/kids-bbq-beef-full-plate.jpg',N'/assets/menu/kids-bbq-beef-cutout.png'),
            (37,N'/assets/menu/chips-salsa-full-plate.jpg',N'/assets/menu/chips-salsa-cutout.png'),
            (38,N'/assets/menu/chips-guacamole-full-plate.jpg',N'/assets/menu/chips-guacamole-cutout.png'),
            (39,N'/assets/menu/chips-queso-full-plate.jpg',N'/assets/menu/chips-queso-cutout.png'),
            (43,N'/assets/menu/lemon-iced-tea-wide.jpg',N'/assets/menu/lemon-iced-tea-cutout.png'),
            (46,N'/assets/menu/mineral-water-wide.jpg',N'/assets/menu/mineral-water-cutout.png'),
            (47,N'/assets/menu/cola-wide.jpg',N'/assets/menu/cola-cutout.png')
        ) AS photos(id,old_image,new_image) ON p.id = photos.id AND p.image = photos.old_image;

        -- Copy each selected sample product to branches where it does not exist.
        -- Read current source values so administrator edits to price/photo are retained.
        INSERT dbo.products
            (name,category,description,image,store_id,price,calories,stock,available,customizable,created_at,version)
        SELECT p.name,p.category,p.description,p.image,st.id,p.price,p.calories,p.stock,p.available,p.customizable,SYSDATETIME(),0
        FROM @curated AS c
        JOIN dbo.products AS p ON p.id = c.source_id AND p.name = c.name
        CROSS JOIN dbo.stores AS st
        WHERE NOT EXISTS (
            SELECT 1 FROM dbo.products AS existing
            WHERE existing.store_id = st.id AND existing.name = p.name
        );

        -- New fixed-recipe Burritos have their own images and estimated kcal.
        INSERT dbo.products
            (name,category,description,image,store_id,price,calories,stock,available,customizable,created_at,version)
        SELECT recipe.name,N'Burrito',recipe.description,recipe.image,st.id,recipe.price,recipe.calories,100,1,0,SYSDATETIME(),0
        FROM (VALUES
            (N'Burrito Trứng phô mai',N'Burrito nhân trứng, phô mai, cơm, salsa cà chua và rau; khẩu phần cố định.',N'/assets/menu/burrito-egg-cheese-cutout.png',89000,650),
            (N'Burrito Gà guacamole',N'Burrito gà nướng, guacamole, cơm, đậu đen, salsa cà chua và rau; khẩu phần cố định.',N'/assets/menu/burrito-chicken-guacamole-cutout.png',105000,780)
        ) AS recipe(name,description,image,price,calories)
        CROSS JOIN dbo.stores AS st
        WHERE NOT EXISTS (
            SELECT 1 FROM dbo.products AS existing
            WHERE existing.store_id = st.id AND existing.name = recipe.name
        );

        INSERT dbo.schema_migrations(version_no,name)
        VALUES (8,N'V8__curated_menu_per_store');
    END;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
