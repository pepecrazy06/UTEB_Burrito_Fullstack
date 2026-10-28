-- Integrates Dat's menu commits cb73587, 70228eb and ddffc67.
-- Use a new identity: V6 may already mean cancellations, V8 does, and V9 fixes locations.
-- Original menu migrations remain as provenance; run this bridge instead of those files.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF OBJECT_ID(N'dbo.schema_migrations',N'U') IS NULL
        THROW 50040, 'Apply the base schema before the integrated menu.', 1;
    IF (SELECT COUNT(*) FROM dbo.schema_migrations WHERE version_no IN (1,2,3,4,5)) <> 5
        THROW 50041, 'Incomplete V1-V5 history; do not modify the menu.', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE version_no=8 AND name=N'V8__order_cancellations')
        OR OBJECT_ID(N'dbo.order_cancellations',N'U') IS NULL
        THROW 50042, 'Apply/verify cancellation V8 before the integrated menu. Do not reuse a menu V8.', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE version_no=9 AND name=N'V9__store_coordinates')
        THROW 50043, 'Apply store coordinates V9 before the integrated menu.', 1;
    IF EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE version_no=10 AND name<>N'V10__integrated_dat_menu')
        THROW 50044, 'V10 belongs to a different migration; manual audit required.', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE version_no=10)
    BEGIN
        -- Data update from Dat's V6__menu_images; old history is untouched.
-- Upgrade sample images and descriptions field by field; preserve uploaded media and order history.
        -- Preserve each manually edited field, including Cloudinary images and availability.
        UPDATE p
        SET p.image = CASE WHEN p.image IN (N'/assets/food.png',N'/assets/menu/photo-pending.png') THEN catalog.image ELSE p.image END,
            p.description = CASE WHEN p.description IN (
                N'Chuẩn bị khi bạn đặt. Tự chọn nhân, cơm, đậu và sốt theo khẩu vị.',
                N'Món ăn kèm cho bữa ăn thêm trọn vị.',
                N'Thức uống mát lạnh, dùng cùng món bạn yêu thích.'
            ) THEN catalog.description ELSE p.description END,
            p.version = p.version + 1
        FROM dbo.products AS p
        JOIN (VALUES
            (1,N'Burrito Gà nướng',N'/assets/menu/burrito-grilled-chicken.jpg',N'Burrito cuộn bánh tortilla, gợi ý nhân gà nướng. Chọn thành phần khi đặt.'),
            (2,N'Burrito Bò BBQ',N'/assets/menu/burrito-bbq-beef.jpg',N'Burrito cuộn bánh tortilla, gợi ý nhân bò bbq. Chọn thành phần khi đặt.'),
            (3,N'Burrito Heo xé',N'/assets/menu/burrito-pulled-pork.jpg',N'Burrito cuộn bánh tortilla, gợi ý nhân heo xé. Chọn thành phần khi đặt.'),
            (4,N'Burrito Chay',N'/assets/menu/burrito-vegetarian.jpg',N'Burrito cuộn bánh tortilla, gợi ý nhân chay. Chọn thành phần khi đặt.'),
            (5,N'Burrito Gà cay',N'/assets/menu/burrito-spicy-chicken.jpg',N'Burrito cuộn bánh tortilla, gợi ý nhân gà cay. Chọn thành phần khi đặt.'),
            (6,N'Burrito Bò đặc biệt',N'/assets/menu/burrito-premium-beef.jpg',N'Burrito cuộn bánh tortilla, gợi ý nhân bò đặc biệt. Chọn thành phần khi đặt.'),
            (7,N'Bowl Gà nướng',N'/assets/menu/bowl-grilled-chicken.jpg',N'Bowl phục vụ trong tô, gợi ý nhân gà nướng. Chọn thành phần khi đặt.'),
            (8,N'Bowl Bò BBQ',N'/assets/menu/bowl-bbq-beef.jpg',N'Bowl phục vụ trong tô, gợi ý nhân bò bbq. Chọn thành phần khi đặt.'),
            (9,N'Bowl Heo xé',N'/assets/menu/bowl-pulled-pork.jpg',N'Bowl phục vụ trong tô, gợi ý nhân heo xé. Chọn thành phần khi đặt.'),
            (10,N'Bowl Chay',N'/assets/menu/bowl-vegetarian.jpg',N'Bowl phục vụ trong tô, gợi ý nhân chay. Chọn thành phần khi đặt.'),
            (11,N'Bowl Gà cay',N'/assets/menu/bowl-spicy-chicken.jpg',N'Bowl phục vụ trong tô, gợi ý nhân gà cay. Chọn thành phần khi đặt.'),
            (12,N'Bowl Bò đặc biệt',N'/assets/menu/bowl-premium-beef.jpg',N'Bowl phục vụ trong tô, gợi ý nhân bò đặc biệt. Chọn thành phần khi đặt.'),
            (13,N'Tacos Gà nướng',N'/assets/menu/tacos-grilled-chicken.jpg',N'Tacos bánh tortilla, gợi ý nhân gà nướng. Chọn thành phần khi đặt.'),
            (14,N'Tacos Bò BBQ',N'/assets/menu/tacos-bbq-beef.jpg',N'Tacos bánh tortilla, gợi ý nhân bò bbq. Chọn thành phần khi đặt.'),
            (15,N'Tacos Heo xé',N'/assets/menu/tacos-pulled-pork.jpg',N'Tacos bánh tortilla, gợi ý nhân heo xé. Chọn thành phần khi đặt.'),
            (16,N'Tacos Chay',N'/assets/menu/tacos-vegetarian.jpg',N'Tacos bánh tortilla, gợi ý nhân chay. Chọn thành phần khi đặt.'),
            (17,N'Tacos Gà cay',N'/assets/menu/tacos-spicy-chicken.jpg',N'Tacos bánh tortilla, gợi ý nhân gà cay. Chọn thành phần khi đặt.'),
            (18,N'Tacos Bò đặc biệt',N'/assets/menu/tacos-premium-beef.jpg',N'Tacos bánh tortilla, gợi ý nhân bò đặc biệt. Chọn thành phần khi đặt.'),
            (19,N'Quesadilla Gà nướng',N'/assets/menu/quesadilla-grilled-chicken.jpg',N'Quesadilla áp chảo, gợi ý nhân gà nướng. Chọn thành phần khi đặt.'),
            (20,N'Quesadilla Bò BBQ',N'/assets/menu/quesadilla-bbq-beef.jpg',N'Quesadilla áp chảo, gợi ý nhân bò bbq. Chọn thành phần khi đặt.'),
            (21,N'Quesadilla Heo xé',N'/assets/menu/quesadilla-pulled-pork.jpg',N'Quesadilla áp chảo, gợi ý nhân heo xé. Chọn thành phần khi đặt.'),
            (22,N'Quesadilla Chay',N'/assets/menu/quesadilla-vegetarian.jpg',N'Quesadilla áp chảo, gợi ý nhân chay. Chọn thành phần khi đặt.'),
            (23,N'Quesadilla Gà cay',N'/assets/menu/quesadilla-spicy-chicken.jpg',N'Quesadilla áp chảo, gợi ý nhân gà cay. Chọn thành phần khi đặt.'),
            (24,N'Quesadilla Bò đặc biệt',N'/assets/menu/quesadilla-premium-beef.jpg',N'Quesadilla áp chảo, gợi ý nhân bò đặc biệt. Chọn thành phần khi đặt.'),
            (25,N'Salad Gà nướng',N'/assets/menu/salad-grilled-chicken.jpg',N'Salad rau, gợi ý nhân gà nướng. Chọn thành phần khi đặt.'),
            (26,N'Salad Bò BBQ',N'/assets/menu/salad-bbq-beef.jpg',N'Salad rau, gợi ý nhân bò bbq. Chọn thành phần khi đặt.'),
            (27,N'Salad Heo xé',N'/assets/menu/salad-pulled-pork.jpg',N'Salad rau, gợi ý nhân heo xé. Chọn thành phần khi đặt.'),
            (28,N'Salad Chay',N'/assets/menu/salad-vegetarian.jpg',N'Salad rau, gợi ý nhân chay. Chọn thành phần khi đặt.'),
            (29,N'Salad Gà cay',N'/assets/menu/salad-spicy-chicken.jpg',N'Salad rau, gợi ý nhân gà cay. Chọn thành phần khi đặt.'),
            (30,N'Salad Bò đặc biệt',N'/assets/menu/salad-premium-beef.jpg',N'Salad rau, gợi ý nhân bò đặc biệt. Chọn thành phần khi đặt.'),
            (31,N'Kids Gà nướng',N'/assets/menu/kids-grilled-chicken.jpg',N'Khẩu phần trẻ em, gợi ý nhân gà nướng. Chọn thành phần khi đặt.'),
            (32,N'Kids Bò BBQ',N'/assets/menu/kids-bbq-beef.jpg',N'Khẩu phần trẻ em, gợi ý nhân bò bbq. Chọn thành phần khi đặt.'),
            (33,N'Kids Heo xé',N'/assets/menu/kids-pulled-pork.jpg',N'Khẩu phần trẻ em, gợi ý nhân heo xé. Chọn thành phần khi đặt.'),
            (34,N'Kids Chay',N'/assets/menu/kids-vegetarian.jpg',N'Khẩu phần trẻ em, gợi ý nhân chay. Chọn thành phần khi đặt.'),
            (35,N'Kids Gà cay',N'/assets/menu/kids-spicy-chicken.jpg',N'Khẩu phần trẻ em, gợi ý nhân gà cay. Chọn thành phần khi đặt.'),
            (36,N'Kids Bò đặc biệt',N'/assets/menu/kids-premium-beef.jpg',N'Khẩu phần trẻ em, gợi ý nhân bò đặc biệt. Chọn thành phần khi đặt.'),
            (37,N'Chips & Salsa',N'/assets/menu/chips-salsa.jpg',N'Bánh tortilla giòn ăn kèm salsa cà chua.'),
            (38,N'Chips & Guacamole',N'/assets/menu/chips-guacamole.jpg',N'Bánh tortilla giòn ăn kèm sốt bơ guacamole.'),
            (39,N'Chips & Queso',N'/assets/menu/chips-queso.jpg',N'Bánh tortilla giòn ăn kèm sốt phô mai queso.'),
            (40,N'Khoai nướng',N'/assets/menu/roasted-potatoes.jpg',N'Khoai tây nướng dùng như món ăn kèm.'),
            (41,N'Salsa tươi',N'/assets/menu/fresh-salsa.jpg',N'Salsa cà chua tươi dùng kèm món chính.'),
            (42,N'Guacamole',N'/assets/menu/guacamole.jpg',N'Sốt bơ guacamole dùng kèm món chính.'),
            (43,N'Trà chanh',N'/assets/menu/lemon-iced-tea.jpg',N'Trà chanh dùng lạnh.'),
            (44,N'Soda chanh',N'/assets/menu/lime-soda.jpg',N'Soda chanh có ga dùng lạnh.'),
            (45,N'Trà đào',N'/assets/menu/peach-iced-tea.jpg',N'Trà đào dùng lạnh.'),
            (46,N'Nước suối',N'/assets/menu/mineral-water.jpg',N'Nước suối đóng chai.'),
            (47,N'Cola',N'/assets/menu/cola.jpg',N'Nước ngọt cola có ga.'),
            (48,N'Nước cam',N'/assets/menu/orange-juice.jpg',N'Nước cam dùng lạnh.')
        ) AS catalog(id,name,image,description) ON p.id = catalog.id AND p.name = catalog.name
        WHERE p.image IN (N'/assets/food.png',N'/assets/menu/photo-pending.png')
           OR p.description IN (
               N'Chuẩn bị khi bạn đặt. Tự chọn nhân, cơm, đậu và sốt theo khẩu vị.',
               N'Món ăn kèm cho bữa ăn thêm trọn vị.',
               N'Thức uống mát lạnh, dùng cùng món bạn yêu thích.'
           );

        -- Data update from Dat's V7__menu_research_catalog; old history is untouched.
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

        -- Data update from Dat's V8__curated_menu_per_store; old history is untouched.
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
        -- The user selected Dat's complete photo set. Replace photos of these named
        -- menu items only; keep Cloudinary media records and order photo snapshots.
        UPDATE p SET image=photos.image,version=p.version+1
        FROM dbo.products p JOIN (VALUES
            (N'Burrito Gà nướng',N'/assets/menu/burrito-grilled-chicken-cutout.png'),
            (N'Burrito Bò BBQ',N'/assets/menu/burrito-bbq-beef-cutout.png'),
            (N'Burrito Heo xé',N'/assets/menu/burrito-pulled-pork-cutout.png'),
            (N'Burrito Chay',N'/assets/menu/burrito-vegetarian-cutout.png'),
            (N'Burrito Gà cay',N'/assets/menu/burrito-spicy-chicken-cutout.png'),
            (N'Burrito Bò đặc biệt',N'/assets/menu/burrito-premium-beef-cutout.png'),
            (N'Burrito Trứng phô mai',N'/assets/menu/burrito-egg-cheese-cutout.png'),
            (N'Burrito Gà guacamole',N'/assets/menu/burrito-chicken-guacamole-cutout.png'),
            (N'Bowl Gà nướng',N'/assets/menu/bowl-grilled-chicken-cutout.png'),
            (N'Bowl Bò BBQ',N'/assets/menu/bowl-bbq-beef-cutout.png'),
            (N'Tacos Gà nướng',N'/assets/menu/tacos-grilled-chicken-cutout.png'),
            (N'Tacos Chay',N'/assets/menu/tacos-vegetarian-cutout.png'),
            (N'Quesadilla Gà nướng',N'/assets/menu/quesadilla-grilled-chicken-cutout.png'),
            (N'Quesadilla Bò BBQ',N'/assets/menu/quesadilla-bbq-beef-cutout.png'),
            (N'Salad Gà nướng',N'/assets/menu/salad-grilled-chicken-cutout.png'),
            (N'Salad Chay',N'/assets/menu/salad-vegetarian-cutout.png'),
            (N'Kids Gà nướng',N'/assets/menu/kids-grilled-chicken-cutout.png'),
            (N'Kids Bò BBQ',N'/assets/menu/kids-bbq-beef-cutout.png'),
            (N'Chips & Salsa',N'/assets/menu/chips-salsa-cutout.png'),
            (N'Chips & Guacamole',N'/assets/menu/chips-guacamole-cutout.png'),
            (N'Chips & Queso',N'/assets/menu/chips-queso-cutout.png'),
            (N'Trà chanh',N'/assets/menu/lemon-iced-tea-cutout.png'),
            (N'Nước suối',N'/assets/menu/mineral-water-cutout.png'),
            (N'Cola',N'/assets/menu/cola-cutout.png')
        ) AS photos(name,image) ON p.name=photos.name
        WHERE p.image IS NULL OR p.image<>photos.image;

        INSERT dbo.schema_migrations(version_no,name) VALUES(10,N'V10__integrated_dat_menu');
    END;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
