# Checklist 48 món thực đơn mẫu

Ảnh: 9 ảnh có sẵn và 39 ảnh mới tạo cho những món từng dùng ảnh chờ. Cả 48 ảnh menu đã được mở rộng thành khung 4:3: 28 món trên đĩa thấy trọn viền đĩa, 20 món trong tô/chén hoặc ly được mở rộng nền bàn ở hai bên. Ảnh mới là **minh họa AI**, chưa phải ảnh món chụp tại cửa hàng. Mô tả, giá và kcal là **đề xuất cho thực đơn demo**, đối chiếu với các nguồn ở dưới; không phải số liệu đo từ món UTEB. Tồn kho và chi nhánh giữ từ dữ liệu mẫu `sql/V3__catalog_seed.sql`; nhóm chưa xác nhận số lượng bán thực tế. Không dùng checklist này làm thực đơn chính thức nếu chưa duyệt.

Mỗi món hiện thuộc **một chi nhánh**. Phân bố mẫu: Võ Văn Ngân 16, Quận 1 16, Bình Thạnh 8, Thủ Đức 8. `stock` là tồn kho theo món; project chưa có tồn kho nguyên liệu hoặc công thức cố định theo từng món.

| ID | Tên món | Mô tả đề xuất | Danh mục | Ảnh | Giá đề xuất | kcal nền/ước tính | Kho mẫu | Chi nhánh mẫu | Tự chọn | Nhóm đã chốt dữ liệu |
|---:|---|---|---|---|---:|---:|---:|---|---|---|
| 1 | Burrito Gà nướng | Burrito cuộn bánh tortilla, gợi ý nhân gà nướng. Chọn thành phần khi đặt. | Burrito | [Viền đĩa trọn](src/main/resources/static/assets/menu/burrito-grilled-chicken-full-plate.jpg) | 89.000đ | 320 | 100 | Võ Văn Ngân | Có | ☐ |
| 2 | Burrito Bò BBQ | Burrito cuộn bánh tortilla, gợi ý nhân bò bbq. Chọn thành phần khi đặt. | Burrito | [Viền đĩa trọn](src/main/resources/static/assets/menu/burrito-bbq-beef-full-plate.jpg) | 99.000đ | 320 | 100 | Quận 1 | Có | ☐ |
| 3 | Burrito Heo xé | Burrito cuộn bánh tortilla, gợi ý nhân heo xé. Chọn thành phần khi đặt. | Burrito | [Viền đĩa trọn](src/main/resources/static/assets/menu/burrito-pulled-pork-full-plate.jpg) | 94.000đ | 320 | 100 | Bình Thạnh | Có | ☐ |
| 4 | Burrito Chay | Burrito cuộn bánh tortilla, gợi ý nhân chay. Chọn thành phần khi đặt. | Burrito | [Viền đĩa trọn](src/main/resources/static/assets/menu/burrito-vegetarian-full-plate.jpg) | 84.000đ | 320 | 100 | Thủ Đức | Có | ☐ |
| 5 | Burrito Gà cay | Burrito cuộn bánh tortilla, gợi ý nhân gà cay. Chọn thành phần khi đặt. | Burrito | [Viền đĩa trọn](src/main/resources/static/assets/menu/burrito-spicy-chicken-full-plate.jpg) | 92.000đ | 320 | 100 | Võ Văn Ngân | Có | ☐ |
| 6 | Burrito Bò đặc biệt | Burrito cuộn bánh tortilla, gợi ý nhân bò đặc biệt. Chọn thành phần khi đặt. | Burrito | [Viền đĩa trọn](src/main/resources/static/assets/menu/burrito-premium-beef-full-plate.jpg) | 109.000đ | 320 | 100 | Quận 1 | Có | ☐ |
| 7 | Bowl Gà nướng | Bowl phục vụ trong tô, gợi ý nhân gà nướng. Chọn thành phần khi đặt. | Bowl | [Nền mở rộng](src/main/resources/static/assets/menu/bowl-grilled-chicken-wide.jpg) | 89.000đ | 35 | 100 | Võ Văn Ngân | Có | ☐ |
| 8 | Bowl Bò BBQ | Bowl phục vụ trong tô, gợi ý nhân bò bbq. Chọn thành phần khi đặt. | Bowl | [Nền mở rộng](src/main/resources/static/assets/menu/bowl-bbq-beef-wide.jpg) | 99.000đ | 35 | 100 | Quận 1 | Có | ☐ |
| 9 | Bowl Heo xé | Bowl phục vụ trong tô, gợi ý nhân heo xé. Chọn thành phần khi đặt. | Bowl | [Nền mở rộng](src/main/resources/static/assets/menu/bowl-pulled-pork-wide.jpg) | 94.000đ | 35 | 100 | Bình Thạnh | Có | ☐ |
| 10 | Bowl Chay | Bowl phục vụ trong tô, gợi ý nhân chay. Chọn thành phần khi đặt. | Bowl | [Nền mở rộng](src/main/resources/static/assets/menu/bowl-vegetarian-wide.jpg) | 84.000đ | 35 | 100 | Thủ Đức | Có | ☐ |
| 11 | Bowl Gà cay | Bowl phục vụ trong tô, gợi ý nhân gà cay. Chọn thành phần khi đặt. | Bowl | [Nền mở rộng](src/main/resources/static/assets/menu/bowl-spicy-chicken-wide.jpg) | 92.000đ | 35 | 100 | Võ Văn Ngân | Có | ☐ |
| 12 | Bowl Bò đặc biệt | Bowl phục vụ trong tô, gợi ý nhân bò đặc biệt. Chọn thành phần khi đặt. | Bowl | [Nền mở rộng](src/main/resources/static/assets/menu/bowl-premium-beef-wide.jpg) | 109.000đ | 35 | 100 | Quận 1 | Có | ☐ |
| 13 | Tacos Gà nướng | Tacos bánh tortilla, gợi ý nhân gà nướng. Chọn thành phần khi đặt. | Tacos | [Viền đĩa trọn](src/main/resources/static/assets/menu/tacos-grilled-chicken-full-plate.jpg) | 79.000đ | 210 | 100 | Võ Văn Ngân | Có | ☐ |
| 14 | Tacos Bò BBQ | Tacos bánh tortilla, gợi ý nhân bò bbq. Chọn thành phần khi đặt. | Tacos | [Viền đĩa trọn](src/main/resources/static/assets/menu/tacos-bbq-beef-full-plate.jpg) | 89.000đ | 210 | 100 | Quận 1 | Có | ☐ |
| 15 | Tacos Heo xé | Tacos bánh tortilla, gợi ý nhân heo xé. Chọn thành phần khi đặt. | Tacos | [Viền đĩa trọn](src/main/resources/static/assets/menu/tacos-pulled-pork-full-plate.jpg) | 84.000đ | 210 | 100 | Bình Thạnh | Có | ☐ |
| 16 | Tacos Chay | Tacos bánh tortilla, gợi ý nhân chay. Chọn thành phần khi đặt. | Tacos | [Viền đĩa trọn](src/main/resources/static/assets/menu/tacos-vegetarian-full-plate.jpg) | 74.000đ | 210 | 100 | Thủ Đức | Có | ☐ |
| 17 | Tacos Gà cay | Tacos bánh tortilla, gợi ý nhân gà cay. Chọn thành phần khi đặt. | Tacos | [Viền đĩa trọn](src/main/resources/static/assets/menu/tacos-spicy-chicken-full-plate.jpg) | 82.000đ | 210 | 100 | Võ Văn Ngân | Có | ☐ |
| 18 | Tacos Bò đặc biệt | Tacos bánh tortilla, gợi ý nhân bò đặc biệt. Chọn thành phần khi đặt. | Tacos | [Viền đĩa trọn](src/main/resources/static/assets/menu/tacos-premium-beef-full-plate.jpg) | 99.000đ | 210 | 100 | Quận 1 | Có | ☐ |
| 19 | Quesadilla Gà nướng | Quesadilla áp chảo, gợi ý nhân gà nướng. Chọn thành phần khi đặt. | Quesadilla | [Viền đĩa trọn](src/main/resources/static/assets/menu/quesadilla-grilled-chicken-full-plate.jpg) | 99.000đ | 430 | 100 | Võ Văn Ngân | Có | ☐ |
| 20 | Quesadilla Bò BBQ | Quesadilla áp chảo, gợi ý nhân bò bbq. Chọn thành phần khi đặt. | Quesadilla | [Viền đĩa trọn](src/main/resources/static/assets/menu/quesadilla-bbq-beef-full-plate.jpg) | 109.000đ | 430 | 100 | Quận 1 | Có | ☐ |
| 21 | Quesadilla Heo xé | Quesadilla áp chảo, gợi ý nhân heo xé. Chọn thành phần khi đặt. | Quesadilla | [Viền đĩa trọn](src/main/resources/static/assets/menu/quesadilla-pulled-pork-full-plate.jpg) | 104.000đ | 430 | 100 | Bình Thạnh | Có | ☐ |
| 22 | Quesadilla Chay | Quesadilla áp chảo, gợi ý nhân chay. Chọn thành phần khi đặt. | Quesadilla | [Viền đĩa trọn](src/main/resources/static/assets/menu/quesadilla-vegetarian-full-plate.jpg) | 94.000đ | 430 | 100 | Thủ Đức | Có | ☐ |
| 23 | Quesadilla Gà cay | Quesadilla áp chảo, gợi ý nhân gà cay. Chọn thành phần khi đặt. | Quesadilla | [Viền đĩa trọn](src/main/resources/static/assets/menu/quesadilla-spicy-chicken-full-plate.jpg) | 102.000đ | 430 | 100 | Võ Văn Ngân | Có | ☐ |
| 24 | Quesadilla Bò đặc biệt | Quesadilla áp chảo, gợi ý nhân bò đặc biệt. Chọn thành phần khi đặt. | Quesadilla | [Viền đĩa trọn](src/main/resources/static/assets/menu/quesadilla-premium-beef-full-plate.jpg) | 119.000đ | 430 | 100 | Quận 1 | Có | ☐ |
| 25 | Salad Gà nướng | Salad rau, gợi ý nhân gà nướng. Chọn thành phần khi đặt. | Salad | [Nền mở rộng](src/main/resources/static/assets/menu/salad-grilled-chicken-wide.jpg) | 89.000đ | 80 | 100 | Võ Văn Ngân | Có | ☐ |
| 26 | Salad Bò BBQ | Salad rau, gợi ý nhân bò bbq. Chọn thành phần khi đặt. | Salad | [Nền mở rộng](src/main/resources/static/assets/menu/salad-bbq-beef-wide.jpg) | 99.000đ | 80 | 100 | Quận 1 | Có | ☐ |
| 27 | Salad Heo xé | Salad rau, gợi ý nhân heo xé. Chọn thành phần khi đặt. | Salad | [Nền mở rộng](src/main/resources/static/assets/menu/salad-pulled-pork-wide.jpg) | 94.000đ | 80 | 100 | Bình Thạnh | Có | ☐ |
| 28 | Salad Chay | Salad rau, gợi ý nhân chay. Chọn thành phần khi đặt. | Salad | [Nền mở rộng](src/main/resources/static/assets/menu/salad-vegetarian-wide.jpg) | 84.000đ | 80 | 100 | Thủ Đức | Có | ☐ |
| 29 | Salad Gà cay | Salad rau, gợi ý nhân gà cay. Chọn thành phần khi đặt. | Salad | [Nền mở rộng](src/main/resources/static/assets/menu/salad-spicy-chicken-wide.jpg) | 92.000đ | 80 | 100 | Võ Văn Ngân | Có | ☐ |
| 30 | Salad Bò đặc biệt | Salad rau, gợi ý nhân bò đặc biệt. Chọn thành phần khi đặt. | Salad | [Nền mở rộng](src/main/resources/static/assets/menu/salad-premium-beef-wide.jpg) | 109.000đ | 80 | 100 | Quận 1 | Có | ☐ |
| 31 | Kids Gà nướng | Khẩu phần trẻ em, gợi ý nhân gà nướng. Chọn thành phần khi đặt. | Kids | [Viền đĩa trọn](src/main/resources/static/assets/menu/kids-grilled-chicken-full-plate.jpg) | 55.000đ | 150 | 100 | Võ Văn Ngân | Có | ☐ |
| 32 | Kids Bò BBQ | Khẩu phần trẻ em, gợi ý nhân bò bbq. Chọn thành phần khi đặt. | Kids | [Viền đĩa trọn](src/main/resources/static/assets/menu/kids-bbq-beef-full-plate.jpg) | 65.000đ | 150 | 100 | Quận 1 | Có | ☐ |
| 33 | Kids Heo xé | Khẩu phần trẻ em, gợi ý nhân heo xé. Chọn thành phần khi đặt. | Kids | [Viền đĩa trọn](src/main/resources/static/assets/menu/kids-pulled-pork-full-plate.jpg) | 60.000đ | 150 | 100 | Bình Thạnh | Có | ☐ |
| 34 | Kids Chay | Khẩu phần trẻ em, gợi ý nhân chay. Chọn thành phần khi đặt. | Kids | [Viền đĩa trọn](src/main/resources/static/assets/menu/kids-vegetarian-full-plate.jpg) | 50.000đ | 150 | 100 | Thủ Đức | Có | ☐ |
| 35 | Kids Gà cay | Khẩu phần trẻ em, gợi ý nhân gà cay. Chọn thành phần khi đặt. | Kids | [Viền đĩa trọn](src/main/resources/static/assets/menu/kids-spicy-chicken-full-plate.jpg) | 58.000đ | 150 | 100 | Võ Văn Ngân | Có | ☐ |
| 36 | Kids Bò đặc biệt | Khẩu phần trẻ em, gợi ý nhân bò đặc biệt. Chọn thành phần khi đặt. | Kids | [Viền đĩa trọn](src/main/resources/static/assets/menu/kids-premium-beef-full-plate.jpg) | 70.000đ | 150 | 100 | Quận 1 | Có | ☐ |
| 37 | Chips & Salsa | Bánh tortilla giòn ăn kèm salsa cà chua. | Sides | [Viền đĩa trọn](src/main/resources/static/assets/menu/chips-salsa-full-plate.jpg) | 39.000đ | 300 | 100 | Võ Văn Ngân | Không | ☐ |
| 38 | Chips & Guacamole | Bánh tortilla giòn ăn kèm sốt bơ guacamole. | Sides | [Viền đĩa trọn](src/main/resources/static/assets/menu/chips-guacamole-full-plate.jpg) | 49.000đ | 385 | 100 | Quận 1 | Không | ☐ |
| 39 | Chips & Queso | Bánh tortilla giòn ăn kèm sốt phô mai queso. | Sides | [Viền đĩa trọn](src/main/resources/static/assets/menu/chips-queso-full-plate.jpg) | 49.000đ | 390 | 100 | Bình Thạnh | Không | ☐ |
| 40 | Khoai nướng | Khoai tây nướng dùng như món ăn kèm. | Sides | [Viền đĩa trọn](src/main/resources/static/assets/menu/roasted-potatoes-full-plate.jpg) | 35.000đ | 220 | 100 | Thủ Đức | Không | ☐ |
| 41 | Salsa tươi | Salsa cà chua tươi dùng kèm món chính. | Sides | [Nền mở rộng](src/main/resources/static/assets/menu/fresh-salsa-wide.jpg) | 25.000đ | 25 | 100 | Võ Văn Ngân | Không | ☐ |
| 42 | Guacamole | Sốt bơ guacamole dùng kèm món chính. | Sides | [Nền mở rộng](src/main/resources/static/assets/menu/guacamole-wide.jpg) | 39.000đ | 230 | 100 | Quận 1 | Không | ☐ |
| 43 | Trà chanh | Trà chanh dùng lạnh. | Drinks | [Nền mở rộng](src/main/resources/static/assets/menu/lemon-iced-tea-wide.jpg) | 25.000đ | 80 | 100 | Võ Văn Ngân | Không | ☐ |
| 44 | Soda chanh | Soda chanh có ga dùng lạnh. | Drinks | [Nền mở rộng](src/main/resources/static/assets/menu/lime-soda-wide.jpg) | 29.000đ | 120 | 100 | Quận 1 | Không | ☐ |
| 45 | Trà đào | Trà đào dùng lạnh. | Drinks | [Nền mở rộng](src/main/resources/static/assets/menu/peach-iced-tea-wide.jpg) | 29.000đ | 110 | 100 | Bình Thạnh | Không | ☐ |
| 46 | Nước suối | Nước suối đóng chai. | Drinks | [Nền mở rộng](src/main/resources/static/assets/menu/mineral-water-wide.jpg) | 15.000đ | 0 | 100 | Thủ Đức | Không | ☐ |
| 47 | Cola | Nước ngọt cola có ga. | Drinks | [Nền mở rộng](src/main/resources/static/assets/menu/cola-wide.jpg) | 20.000đ | 139 | 100 | Võ Văn Ngân | Không | ☐ |
| 48 | Nước cam | Nước cam dùng lạnh. | Drinks | [Nền mở rộng](src/main/resources/static/assets/menu/orange-juice-wide.jpg) | 30.000đ | 120 | 100 | Quận 1 | Không | ☐ |

## Nguồn và cách tính bản đề xuất (tra ngày 25/10/2026)

- [Lufina Mexican Grill](https://lufina.com/menu) và [Rico Mexican House](https://www.ricomexicanhouse.com/online-ordering) công bố giá và thành phần các nhóm burrito, bowl, taco, quesadilla, salad tại Việt Nam. Giá trong bảng là giá UTEB **đề xuất**, đặt trong vùng giá tham khảo và phân biệt nhân/khẩu phần; không phải giá của hai nhà hàng đó.
- [Bảng dinh dưỡng Chipotle](https://www.chipotle.com/content/dam/chipotle/menu/nutrition/US-Nutrition-Facts-Paper-Menu-3-2025.pdf?lv=true) nêu năng lượng theo khẩu phần cho tortilla, cơm, đậu, thịt, salsa, phô mai và món ăn kèm. `calories` của món tự chọn trong bảng chỉ là **phần nền** (ví dụ tortilla burrito 320 kcal); backend cộng thêm thành phần thực chọn. Kids dùng ước tính nửa khẩu phần thành phần. Các món không tự chọn là ước tính theo một phần bán giả định.
- [Coca-Cola Việt Nam](https://www.coca-cola.com/vn/vi/about-us/faq/m_t-lon-coca-cola-truyn-thng-330ml-cha-bao-nhieu-calo) công bố lon Coca-Cola 330 ml là 139 kcal. `Nước suối` không đường được đặt 0 kcal. Trà và nước trái cây cần chốt công thức/định lượng đường trước khi đưa ra thực đơn thật.
- Công thức tùy chọn hiện áp dụng: Burrito/Bowl chọn nhân, cơm, đậu, sốt; Tacos/Quesadilla chọn nhân, sốt; Salad chọn nhân, đậu, sốt; Kids chọn nhân, cơm, sốt. Phô mai của Quesadilla được tính trong phần nền. Giá món có tên Bò/Heo **đã bao gồm nhân tương ứng**; phụ phí chỉ phản ánh chênh lệch khi đổi nhân hoặc chọn topping/extra.

## Những mục nhóm cần xác nhận

- Danh sách 48 món này có phải thực đơn cuối cùng hay chỉ là seed demo? Nếu danh sách thay đổi, cần chốt tên, danh mục và chi nhánh trước khi công bố.
- Giá và kcal là đề xuất tham khảo đã sửa các điểm vô lý của seed cũ (`Nước suối = 0 kcal`, Kids rẻ hơn món lớn); **chưa đo trên khẩu phần UTEB**. Tất cả món vẫn có tồn kho mẫu 100, không phản ánh kho tại chi nhánh. Không suy ra kho thực tế từ nguồn trên mạng.
- Bộ nguyên liệu hiện dùng chung cho các món tự chọn: nhân (`Gà nướng`, `Bò BBQ`, `Heo xé`, `Đậu hũ chay`), cơm, đậu, sốt, topping, extra. Builder gợi ý nhân khớp với tên món; `Gà cay` gợi ý sốt cay. Món `Chay` chỉ nhận đậu hũ chay và không nhận phần thêm thịt, kể cả khi gọi API trực tiếp. Chưa có nguyên liệu riêng tên `Gà cay` hoặc `Bò đặc biệt`; nhóm vẫn cần chốt quy tắc chọn nguyên liệu cho từng loại món.
- Các ảnh AI mô tả món theo tên để nhận diện, không chứng minh thành phần thật, dị ứng hay giá trị dinh dưỡng. Cần đối chiếu ảnh với món bán thực tế trước khi dùng làm ảnh chính thức.
- V6 đã được áp dụng trên database `UTE_Burrito` vừa tạo của Đạt: 48 ảnh JPG, 0 mô tả cũ, 4 chi nhánh. V7 **chưa áp dụng**; nó thay giá/kcal còn đúng giá trị seed V3 và thay 48 ảnh thực đơn chỉ khi chúng vẫn là ảnh V6, giữ chỉnh sửa thủ công, tồn kho, chi nhánh và lịch sử đơn. Trên database khác phải kiểm tra lịch sử migration trước khi chạy.

## Đối soát trước khi dùng như thực đơn thật

1. Xác nhận mỗi món có bán tại chi nhánh trong bảng, và điều chỉnh số lượng tồn theo từng chi nhánh. Hiện một món chỉ thuộc một chi nhánh; đây là bố trí demo chứ không phải số liệu vận hành.
2. Chốt giá bán và phụ phí từ chi phí nguyên liệu của nhóm. Bản đề xuất đã tránh cộng trùng phụ phí của nhân được đặt tên trong món.
3. Chốt công thức, dị ứng và cách đặt tên món khi khách thay nhân. Đặc biệt kiểm tra `Chay`, `Gà cay`, `Bò đặc biệt` và khẩu phần `Kids`.
4. Cân phần ăn và lượng đường thực tế rồi tính lại kcal; con số từ món của nhà hàng khác chỉ là chuẩn tham khảo.
5. Kiểm tra kế hoạch V7 trên SQL Server, đối chiếu giá/kcal và 48 đường dẫn ảnh menu trước và sau khi chạy; chỉ áp dụng sau khi nhóm xem diff. V7 không tự thay stock hoặc storeId.

## Bộ prompt ảnh mới

Tạo bằng công cụ imagegen built-in, mỗi ảnh một lời nhắc riêng. Cấu trúc chung: `Use case: product-mockup; unique square catalog photograph; photorealistic premium restaurant food photography; one ivory ceramic plate or bowl on a warm sandy-beige tabletop; close three-quarter view; soft warm studio daylight; accurate named item and protein; no people, labels, text, logo, watermark or duplicate dishes.` Chủ thể cụ thể:

| ID | Món | Chủ thể ảnh |
|---:|---|---|
| 3 | Burrito Heo xé | two cut halves of a toasted wheat tortilla burrito filled prominently with tender pulled pork, rice, black beans, tomato salsa and lettuce, cut sides facing the camera |
| 5 | Burrito Gà cay | two cut halves of a toasted wheat tortilla burrito filled prominently with chili-glazed spicy grilled chicken with a visible red sauce, rice, black beans, tomato salsa and lettuce, cut sides facing the camera |
| 6 | Burrito Bò đặc biệt | two cut halves of a toasted wheat tortilla burrito filled prominently with premium seared beef strips with avocado and roasted peppers, rice, black beans, tomato salsa and lettuce, cut sides facing the camera |
| 8 | Bowl Bò BBQ | one ceramic bowl of cilantro rice, black beans, corn, tomato salsa and crisp lettuce, generously topped with smoky barbecue beef strips |
| 9 | Bowl Heo xé | one ceramic bowl of cilantro rice, black beans, corn, tomato salsa and crisp lettuce, generously topped with tender pulled pork |
| 10 | Bowl Chay | one ceramic bowl of cilantro rice, black beans, corn, tomato salsa and crisp lettuce, generously topped with grilled tofu, roasted peppers and mushrooms, entirely vegetarian with no meat |
| 11 | Bowl Gà cay | one ceramic bowl of cilantro rice, black beans, corn, tomato salsa and crisp lettuce, generously topped with chili-glazed spicy grilled chicken with a visible red sauce |
| 12 | Bowl Bò đặc biệt | one ceramic bowl of cilantro rice, black beans, corn, tomato salsa and crisp lettuce, generously topped with premium seared beef strips with avocado and roasted peppers |
| 13 | Tacos Gà nướng | three small toasted corn tortillas filled with grilled chicken, finely chopped onion, cilantro and tomato salsa |
| 14 | Tacos Bò BBQ | three small toasted corn tortillas filled with smoky barbecue beef strips, finely chopped onion, cilantro and tomato salsa |
| 16 | Tacos Chay | three small toasted corn tortillas filled with grilled tofu, roasted peppers and mushrooms, entirely vegetarian with no meat, finely chopped onion, cilantro and tomato salsa |
| 17 | Tacos Gà cay | three small toasted corn tortillas filled with chili-glazed spicy grilled chicken with a visible red sauce, finely chopped onion, cilantro and tomato salsa |
| 18 | Tacos Bò đặc biệt | three small toasted corn tortillas filled with premium seared beef strips with avocado and roasted peppers, finely chopped onion, cilantro and tomato salsa |
| 20 | Quesadilla Bò BBQ | four golden grilled tortilla quesadilla wedges with melted cheese and smoky barbecue beef strips, filling visible at the cut edges |
| 21 | Quesadilla Heo xé | four golden grilled tortilla quesadilla wedges with melted cheese and tender pulled pork, filling visible at the cut edges |
| 22 | Quesadilla Chay | four golden grilled tortilla quesadilla wedges with melted cheese and grilled tofu, roasted peppers and mushrooms, entirely vegetarian with no meat, filling visible at the cut edges |
| 23 | Quesadilla Gà cay | four golden grilled tortilla quesadilla wedges with melted cheese and chili-glazed spicy grilled chicken with a visible red sauce, filling visible at the cut edges |
| 24 | Quesadilla Bò đặc biệt | four golden grilled tortilla quesadilla wedges with melted cheese and premium seared beef strips with avocado and roasted peppers, filling visible at the cut edges |
| 26 | Salad Bò BBQ | one ceramic bowl of fresh lettuce, tomatoes, corn, black beans and avocado, generously topped with smoky barbecue beef strips |
| 27 | Salad Heo xé | one ceramic bowl of fresh lettuce, tomatoes, corn, black beans and avocado, generously topped with tender pulled pork |
| 28 | Salad Chay | one ceramic bowl of fresh lettuce, tomatoes, corn, black beans and avocado, generously topped with grilled tofu, roasted peppers and mushrooms, entirely vegetarian with no meat |
| 29 | Salad Gà cay | one ceramic bowl of fresh lettuce, tomatoes, corn, black beans and avocado, generously topped with chili-glazed spicy grilled chicken with a visible red sauce |
| 30 | Salad Bò đặc biệt | one ceramic bowl of fresh lettuce, tomatoes, corn, black beans and avocado, generously topped with premium seared beef strips with avocado and roasted peppers |
| 31 | Kids Gà nướng | a child-size meal of one small toasted tortilla wrap cut in half, a small side of rice and sweet corn, filled with grilled chicken, served in an appealing but realistic portion |
| 32 | Kids Bò BBQ | a child-size meal of one small toasted tortilla wrap cut in half, a small side of rice and sweet corn, filled with smoky barbecue beef strips, served in an appealing but realistic portion |
| 33 | Kids Heo xé | a child-size meal of one small toasted tortilla wrap cut in half, a small side of rice and sweet corn, filled with tender pulled pork, served in an appealing but realistic portion |
| 34 | Kids Chay | a child-size meal of one small toasted tortilla wrap cut in half, a small side of rice and sweet corn, filled with grilled tofu, roasted peppers and mushrooms, entirely vegetarian with no meat, served in an appealing but realistic portion |
| 35 | Kids Gà cay | a child-size meal of one small toasted tortilla wrap cut in half, a small side of rice and sweet corn, filled with chili-glazed spicy grilled chicken with a visible red sauce, served in an appealing but realistic portion |
| 36 | Kids Bò đặc biệt | a child-size meal of one small toasted tortilla wrap cut in half, a small side of rice and sweet corn, filled with premium seared beef strips with avocado and roasted peppers, served in an appealing but realistic portion |
| 37 | Chips & Salsa | a generous serving of golden crisp corn tortilla chips beside one small bowl of chunky fresh red tomato salsa |
| 39 | Chips & Queso | a generous serving of golden crisp corn tortilla chips beside one small bowl of warm creamy yellow queso cheese dip |
| 40 | Khoai nướng | one small plate of golden oven-roasted potato wedges with paprika and fresh herbs, crisp edges |
| 41 | Salsa tươi | one small ceramic bowl of freshly chopped tomato salsa with onion, cilantro and lime, vivid diced ingredients |
| 42 | Guacamole | one small ceramic bowl of chunky fresh avocado guacamole with lime and cilantro, vivid green texture |
| 43 | Trà chanh | one clear glass of amber iced lemon tea with ice cubes and a fresh lemon slice, condensation on the glass |
| 44 | Soda chanh | one clear glass of sparkling lime soda with visible bubbles, ice and fresh lime slices, condensation on the glass |
| 46 | Nước suối | one unbranded clear glass bottle of still mineral water beside a small clear drinking glass, subtle condensation |
| 47 | Cola | one clear glass filled with dark cola, ice cubes and tiny bubbles, subtle condensation, absolutely no brand marks |
| 48 | Nước cam | one clear glass of fresh orange juice with orange slices nearby, subtle condensation |

Các ảnh PNG gốc do imagegen lưu ngoài project đã được chuyển sang JPEG chất lượng 86 để trang tải nhanh; nội dung hình không bị thay đổi khi chuyển định dạng. 28 ảnh `*-full-plate.jpg` là bản chỉnh góc chụp rộng hơn bằng imagegen để nhìn thấy toàn bộ viền đĩa; ảnh cũ vẫn được giữ. Các ảnh JPEG được lưu trong `src/main/resources/static/assets/menu/`.
