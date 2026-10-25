SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF OBJECT_ID(N'dbo.schema_migrations', N'U') IS NULL
        THROW 50008, 'Apply V1-V5 migrations first.', 1;
    IF (SELECT COUNT(*) FROM dbo.schema_migrations WHERE version_no IN (1,2,3,4,5)) <> 5
        THROW 50009, 'Incomplete V1-V5 history. Do not change the catalog.', 1;

    IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE version_no = 6)
    BEGIN
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

        INSERT dbo.schema_migrations(version_no, name)
        VALUES (6, N'V6__menu_images');
    END;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
