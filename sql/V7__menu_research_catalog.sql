SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF OBJECT_ID(N'dbo.schema_migrations', N'U') IS NULL
        THROW 50010, 'Apply V1-V6 migrations first.', 1;
    IF (SELECT COUNT(*) FROM dbo.schema_migrations WHERE version_no IN (1,2,3,4,5,6)) <> 6
        THROW 50011, 'Incomplete V1-V6 history. Do not change the catalog.', 1;

    IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE version_no = 7)
    BEGIN
        -- Suggested prices and estimated base calories, researched for the demo menu.
        -- Change only unchanged V3 sample fields. Keep administrator edits, stock, store, and order snapshots.
        UPDATE p
        SET p.price = CASE WHEN p.price = catalog.old_price THEN catalog.new_price ELSE p.price END,
            p.calories = CASE WHEN p.calories = catalog.old_calories THEN catalog.new_calories ELSE p.calories END,
            p.version = p.version + 1
        FROM dbo.products AS p
        JOIN (VALUES
            (1,N'Burrito Gà nướng',69000,89000,220,320),
            (2,N'Burrito Bò BBQ',72000,99000,220,320),
            (3,N'Burrito Heo xé',75000,94000,220,320),
            (4,N'Burrito Chay',78000,84000,220,320),
            (5,N'Burrito Gà cay',81000,92000,220,320),
            (6,N'Burrito Bò đặc biệt',84000,109000,220,320),
            (7,N'Bowl Gà nướng',73000,89000,245,35),
            (8,N'Bowl Bò BBQ',76000,99000,245,35),
            (9,N'Bowl Heo xé',79000,94000,245,35),
            (10,N'Bowl Chay',82000,84000,245,35),
            (11,N'Bowl Gà cay',85000,92000,245,35),
            (12,N'Bowl Bò đặc biệt',88000,109000,245,35),
            (13,N'Tacos Gà nướng',77000,79000,270,210),
            (14,N'Tacos Bò BBQ',80000,89000,270,210),
            (15,N'Tacos Heo xé',83000,84000,270,210),
            (16,N'Tacos Chay',86000,74000,270,210),
            (17,N'Tacos Gà cay',89000,82000,270,210),
            (18,N'Tacos Bò đặc biệt',92000,99000,270,210),
            (19,N'Quesadilla Gà nướng',81000,99000,295,430),
            (20,N'Quesadilla Bò BBQ',84000,109000,295,430),
            (21,N'Quesadilla Heo xé',87000,104000,295,430),
            (22,N'Quesadilla Chay',90000,94000,295,430),
            (23,N'Quesadilla Gà cay',93000,102000,295,430),
            (24,N'Quesadilla Bò đặc biệt',96000,119000,295,430),
            (25,N'Salad Gà nướng',85000,89000,320,80),
            (26,N'Salad Bò BBQ',88000,99000,320,80),
            (27,N'Salad Heo xé',91000,94000,320,80),
            (28,N'Salad Chay',94000,84000,320,80),
            (29,N'Salad Gà cay',97000,92000,320,80),
            (30,N'Salad Bò đặc biệt',100000,109000,320,80),
            (31,N'Kids Gà nướng',89000,55000,345,150),
            (32,N'Kids Bò BBQ',92000,65000,345,150),
            (33,N'Kids Heo xé',95000,60000,345,150),
            (34,N'Kids Chay',98000,50000,345,150),
            (35,N'Kids Gà cay',101000,58000,345,150),
            (36,N'Kids Bò đặc biệt',104000,70000,345,150),
            (37,N'Chips & Salsa',25000,39000,180,300),
            (38,N'Chips & Guacamole',28000,49000,180,385),
            (39,N'Chips & Queso',31000,49000,180,390),
            (40,N'Khoai nướng',34000,35000,180,220),
            (41,N'Salsa tươi',37000,25000,180,25),
            (42,N'Guacamole',40000,39000,180,230),
            (43,N'Trà chanh',15000,25000,90,80),
            (44,N'Soda chanh',17000,29000,90,120),
            (45,N'Trà đào',19000,29000,90,110),
            (46,N'Nước suối',21000,15000,90,0),
            (47,N'Cola',23000,20000,90,139),
            (48,N'Nước cam',25000,30000,90,120)
        ) AS catalog(id,name,old_price,new_price,old_calories,new_calories)
          ON p.id = catalog.id AND p.name = catalog.name
        WHERE p.price = catalog.old_price OR p.calories = catalog.old_calories;

        -- Replace only unchanged V6 menu photos; preserve any photo uploaded by an administrator.
        UPDATE p
        SET p.image = photos.new_image,
            p.version = p.version + 1
        FROM dbo.products AS p
        JOIN (VALUES
            (1,N'Burrito Gà nướng',N'/assets/menu/burrito-grilled-chicken.jpg',N'/assets/menu/burrito-grilled-chicken-full-plate.jpg'),
            (2,N'Burrito Bò BBQ',N'/assets/menu/burrito-bbq-beef.jpg',N'/assets/menu/burrito-bbq-beef-full-plate.jpg'),
            (3,N'Burrito Heo xé',N'/assets/menu/burrito-pulled-pork.jpg',N'/assets/menu/burrito-pulled-pork-full-plate.jpg'),
            (4,N'Burrito Chay',N'/assets/menu/burrito-vegetarian.jpg',N'/assets/menu/burrito-vegetarian-full-plate.jpg'),
            (5,N'Burrito Gà cay',N'/assets/menu/burrito-spicy-chicken.jpg',N'/assets/menu/burrito-spicy-chicken-full-plate.jpg'),
            (6,N'Burrito Bò đặc biệt',N'/assets/menu/burrito-premium-beef.jpg',N'/assets/menu/burrito-premium-beef-full-plate.jpg'),
            (7,N'Bowl Gà nướng',N'/assets/menu/bowl-grilled-chicken.jpg',N'/assets/menu/bowl-grilled-chicken-wide.jpg'),
            (8,N'Bowl Bò BBQ',N'/assets/menu/bowl-bbq-beef.jpg',N'/assets/menu/bowl-bbq-beef-wide.jpg'),
            (9,N'Bowl Heo xé',N'/assets/menu/bowl-pulled-pork.jpg',N'/assets/menu/bowl-pulled-pork-wide.jpg'),
            (10,N'Bowl Chay',N'/assets/menu/bowl-vegetarian.jpg',N'/assets/menu/bowl-vegetarian-wide.jpg'),
            (11,N'Bowl Gà cay',N'/assets/menu/bowl-spicy-chicken.jpg',N'/assets/menu/bowl-spicy-chicken-wide.jpg'),
            (12,N'Bowl Bò đặc biệt',N'/assets/menu/bowl-premium-beef.jpg',N'/assets/menu/bowl-premium-beef-wide.jpg'),
            (13,N'Tacos Gà nướng',N'/assets/menu/tacos-grilled-chicken.jpg',N'/assets/menu/tacos-grilled-chicken-full-plate.jpg'),
            (14,N'Tacos Bò BBQ',N'/assets/menu/tacos-bbq-beef.jpg',N'/assets/menu/tacos-bbq-beef-full-plate.jpg'),
            (15,N'Tacos Heo xé',N'/assets/menu/tacos-pulled-pork.jpg',N'/assets/menu/tacos-pulled-pork-full-plate.jpg'),
            (16,N'Tacos Chay',N'/assets/menu/tacos-vegetarian.jpg',N'/assets/menu/tacos-vegetarian-full-plate.jpg'),
            (17,N'Tacos Gà cay',N'/assets/menu/tacos-spicy-chicken.jpg',N'/assets/menu/tacos-spicy-chicken-full-plate.jpg'),
            (18,N'Tacos Bò đặc biệt',N'/assets/menu/tacos-premium-beef.jpg',N'/assets/menu/tacos-premium-beef-full-plate.jpg'),
            (19,N'Quesadilla Gà nướng',N'/assets/menu/quesadilla-grilled-chicken.jpg',N'/assets/menu/quesadilla-grilled-chicken-full-plate.jpg'),
            (20,N'Quesadilla Bò BBQ',N'/assets/menu/quesadilla-bbq-beef.jpg',N'/assets/menu/quesadilla-bbq-beef-full-plate.jpg'),
            (21,N'Quesadilla Heo xé',N'/assets/menu/quesadilla-pulled-pork.jpg',N'/assets/menu/quesadilla-pulled-pork-full-plate.jpg'),
            (22,N'Quesadilla Chay',N'/assets/menu/quesadilla-vegetarian.jpg',N'/assets/menu/quesadilla-vegetarian-full-plate.jpg'),
            (23,N'Quesadilla Gà cay',N'/assets/menu/quesadilla-spicy-chicken.jpg',N'/assets/menu/quesadilla-spicy-chicken-full-plate.jpg'),
            (24,N'Quesadilla Bò đặc biệt',N'/assets/menu/quesadilla-premium-beef.jpg',N'/assets/menu/quesadilla-premium-beef-full-plate.jpg'),
            (25,N'Salad Gà nướng',N'/assets/menu/salad-grilled-chicken.jpg',N'/assets/menu/salad-grilled-chicken-wide.jpg'),
            (26,N'Salad Bò BBQ',N'/assets/menu/salad-bbq-beef.jpg',N'/assets/menu/salad-bbq-beef-wide.jpg'),
            (27,N'Salad Heo xé',N'/assets/menu/salad-pulled-pork.jpg',N'/assets/menu/salad-pulled-pork-wide.jpg'),
            (28,N'Salad Chay',N'/assets/menu/salad-vegetarian.jpg',N'/assets/menu/salad-vegetarian-wide.jpg'),
            (29,N'Salad Gà cay',N'/assets/menu/salad-spicy-chicken.jpg',N'/assets/menu/salad-spicy-chicken-wide.jpg'),
            (30,N'Salad Bò đặc biệt',N'/assets/menu/salad-premium-beef.jpg',N'/assets/menu/salad-premium-beef-wide.jpg'),
            (31,N'Kids Gà nướng',N'/assets/menu/kids-grilled-chicken.jpg',N'/assets/menu/kids-grilled-chicken-full-plate.jpg'),
            (32,N'Kids Bò BBQ',N'/assets/menu/kids-bbq-beef.jpg',N'/assets/menu/kids-bbq-beef-full-plate.jpg'),
            (33,N'Kids Heo xé',N'/assets/menu/kids-pulled-pork.jpg',N'/assets/menu/kids-pulled-pork-full-plate.jpg'),
            (34,N'Kids Chay',N'/assets/menu/kids-vegetarian.jpg',N'/assets/menu/kids-vegetarian-full-plate.jpg'),
            (35,N'Kids Gà cay',N'/assets/menu/kids-spicy-chicken.jpg',N'/assets/menu/kids-spicy-chicken-full-plate.jpg'),
            (36,N'Kids Bò đặc biệt',N'/assets/menu/kids-premium-beef.jpg',N'/assets/menu/kids-premium-beef-full-plate.jpg'),
            (37,N'Chips & Salsa',N'/assets/menu/chips-salsa.jpg',N'/assets/menu/chips-salsa-full-plate.jpg'),
            (38,N'Chips & Guacamole',N'/assets/menu/chips-guacamole.jpg',N'/assets/menu/chips-guacamole-full-plate.jpg'),
            (39,N'Chips & Queso',N'/assets/menu/chips-queso.jpg',N'/assets/menu/chips-queso-full-plate.jpg'),
            (40,N'Khoai nướng',N'/assets/menu/roasted-potatoes.jpg',N'/assets/menu/roasted-potatoes-full-plate.jpg'),
            (41,N'Salsa tươi',N'/assets/menu/fresh-salsa.jpg',N'/assets/menu/fresh-salsa-wide.jpg'),
            (42,N'Guacamole',N'/assets/menu/guacamole.jpg',N'/assets/menu/guacamole-wide.jpg'),
            (43,N'Trà chanh',N'/assets/menu/lemon-iced-tea.jpg',N'/assets/menu/lemon-iced-tea-wide.jpg'),
            (44,N'Soda chanh',N'/assets/menu/lime-soda.jpg',N'/assets/menu/lime-soda-wide.jpg'),
            (45,N'Trà đào',N'/assets/menu/peach-iced-tea.jpg',N'/assets/menu/peach-iced-tea-wide.jpg'),
            (46,N'Nước suối',N'/assets/menu/mineral-water.jpg',N'/assets/menu/mineral-water-wide.jpg'),
            (47,N'Cola',N'/assets/menu/cola.jpg',N'/assets/menu/cola-wide.jpg'),
            (48,N'Nước cam',N'/assets/menu/orange-juice.jpg',N'/assets/menu/orange-juice-wide.jpg')
        ) AS photos(id,name,old_image,new_image)
          ON p.id = photos.id AND p.name = photos.name
        WHERE p.image = photos.old_image;

        INSERT dbo.schema_migrations(version_no, name)
        VALUES (7, N'V7__menu_research_catalog');
    END;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
