# Lưu kết nối một lần trên máy

Trong PowerShell tại thư mục có `pom.xml`, chạy:

```powershell
.\sql\Save-LocalConfig.ps1
```

Nhập mật khẩu SQL Server, Cloudinary Cloud name, API Key và API Secret một lần. Mặc định dùng SQL login `sa`, server `localhost:1433`, database `uteb`. Các giá trị đang có trong terminal sẽ được dùng lại, không hỏi lại.

Nếu Windows PowerShell báo chặn chạy script, dùng lệnh sau cho riêng lần chạy helper; không thay đổi policy lâu dài của máy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\sql\Save-LocalConfig.ps1
```

Helper tạo `.env.local.properties` riêng trên máy, được `.gitignore` loại khỏi Git. Tệp này chứa thông tin đăng nhập dạng văn bản: giữ trên máy, không gửi cho thành viên khác hoặc đính kèm báo cáo. Mỗi thành viên chạy helper với thông tin riêng.

Sau đó, kể cả trong terminal mới, chỉ chạy:

```powershell
.\mvnw.cmd spring-boot:run
```

Hoặc mở `UtebApplication` trong IntelliJ rồi Run. Working directory phải là thư mục dự án chứa `pom.xml` và `.env.local.properties`. Profile mặc định là `sqlserver`; chỉ profile này nạp tệp lưu riêng. Profile `demo` dùng cho kiểm thử không nạp tệp.

## Đổi thông tin đã lưu

```powershell
.\sql\Save-LocalConfig.ps1 -Section sql
.\sql\Save-LocalConfig.ps1 -Section cloudinary
```

Để dùng SQL login/database khác:

```powershell
.\sql\Save-LocalConfig.ps1 -SqlLogin 'your_login' -Database 'your_database'
```

Biến môi trường trong terminal hoặc IntelliJ được ưu tiên hơn tệp lưu. Nếu đã nhập sai ở terminal cũ, sửa/xóa biến cũ hoặc mở terminal mới trước khi lưu lại. Helper không thay đổi mật khẩu/tài khoản dịch vụ.

## Giữ cấu hình SMTP, VNPay đang dùng

Nếu terminal đã có cấu hình SMTP/VNPay, chạy helper tại terminal đó: các giá trị hiện có cũng được lưu lại. SMTP đang ở `.env.smtp.ps1` có thể nạp một lần trước khi lưu:

```powershell
. .\.env.smtp.ps1
.\sql\Save-LocalConfig.ps1
```

URL tunnel thay đổi vẫn cần cập nhật `BASE_URL` và URL callback bên VNPay. Không cần nhập lại mật khẩu SQL hay Cloudinary khi đổi URL tunnel. Tệp `.env.example` chỉ là tài liệu tham khảo; dự án tự nạp `.env.local.properties`, không tự nạp các tệp `.env`/PowerShell khác.

Để lưu VNPay riêng mà không nhập lại SQL/Cloudinary, chạy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\sql\Save-LocalConfig.ps1 -Section vnpay
```

Nhập TmnCode, HashSecret và HTTPS URL của website/tunnel hiện tại. Các giá trị được lưu vào tệp riêng, không lên GitHub. Nếu đã có thông tin VNPay trong tệp, khi đổi tunnel chỉ cần thêm `-PublicBaseUrl 'https://URL-MOI'`; bộ khóa đã lưu được giữ lại. Cập nhật IPN bên VNPay thành URL mới + `/payments/vnpay/ipn`, rồi khởi động lại ứng dụng. Nếu truy cập web qua HTTPS, dùng chính URL HTTPS đó để cookie đăng nhập được gửi đúng.

Checkout hỗ trợ COD và VNPay. Khi VNPay thiếu cấu hình, lựa chọn vẫn xuất hiện cùng thông báo và server chặn tạo đơn VNPay; COD vẫn dùng được. Hệ thống không tạo giao dịch MoMo hoặc xác nhận thanh toán giả lập.

Để dùng tệp ở vị trí khác, đặt `UTEB_LOCAL_CONFIG` bằng URI `file:/...` của tệp đó. Không đặt tệp chứa mật khẩu trong `src/main/resources`, vì thư mục này được đóng gói vào ứng dụng.

## Bản đồ cửa hàng

Mục Cửa hàng và hộp Chọn cửa hàng hiện sẵn các chi nhánh đang hoạt động, lọc theo Đến lấy/Giao tận nơi. Tên và địa chỉ lấy từ SQL; ghim dùng logo `/assets/uteb-logo.png`. Leaflet 1.9.4 được lưu trong `static/assets/leaflet` kèm giấy phép, không cần API key. Nền bản đồ cần Internet: OpenStreetMap, dự phòng OpenStreetMap France khi nguồn đầu không tải được. Giữ phần ghi nguồn hiển thị trên bản đồ; xem [chính sách OSM](https://operations.osmfoundation.org/policies/tiles/) và [OSM France](https://www.openstreetmap.fr/usage/) trước khi dùng ngoài đồ án.

Migration V9 sửa bốn tọa độ mẫu cũ theo địa chỉ chi nhánh; xem sql/STORE_LOCATIONS.md để đối chiếu nguồn và cách áp dụng. Các tọa độ do người dùng đã tự chỉnh được giữ nguyên. Vào Quản trị → Chi nhánh → Sửa để chỉnh Vĩ độ/Kinh độ khi đổi địa chỉ; để trống cả hai để bỏ vị trí. Chi nhánh chưa có tọa độ vẫn chọn được từ danh sách nhưng không có ghim. Nút Dùng vị trí của tôi chỉ hỏi quyền vị trí khi được bấm; khoảng cách hiển thị là đường thẳng, không phải quãng đường giao hàng. V9 chỉ cập nhật dữ liệu, không thêm hoặc xóa cột/bảng.
