# UTEB Burrito

Website đặt món burrito và quản lý nhiều cửa hàng, được xây dựng cho đồ án môn học. Hệ thống hỗ trợ tùy chọn nguyên liệu, giỏ hàng, thanh toán COD/VNPay, giao hàng và chăm sóc khách sau mua. Trợ lý AI gợi ý món theo nhu cầu bằng mô hình chạy trên máy.

Bản chạy chính sử dụng **SQL Server**. Admin quản lý các tham số nghiệp vụ trực tiếp trên giao diện; thông tin kết nối dịch vụ được lưu riêng trên từng máy.

## Thành viên

| Họ và tên | Mã số sinh viên |
| :--- | :---: |
| Thái Nhựt Huy | 24110227 |
| Dương Thành Đạt | 24110191 |
| Ngô Duy Khang | 24110243 |

## Công nghệ

| Thành phần | Công nghệ |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5.6, Spring Web |
| Xác thực và phân quyền | Spring Security, BCrypt, phiên đăng nhập và JWT |
| Truy cập dữ liệu | Spring Data JPA, Hibernate, Microsoft JDBC Driver |
| Cơ sở dữ liệu | SQL Server 2019 trở lên; H2 cho kiểm thử cô lập và profile demo riêng |
| Giao diện | Thymeleaf, HTML, CSS, JavaScript, Bootstrap |
| Cập nhật thời gian thực | WebSocket, STOMP |
| Email và media | Spring Mail/SMTP, Cloudinary |
| Thanh toán | COD, VNPay Sandbox |
| Bản đồ | Leaflet và nền bản đồ OpenStreetMap |
| Trợ lý AI | Ollama, mô hình `qwen3:4b-instruct` |
| Build và kiểm thử | Maven Wrapper, JUnit, Spring Boot Test, Mockito, Node.js Test Runner |

## Chức năng theo vai trò

| Vai trò | Chức năng chính |
| --- | --- |
| Guest | Trang chủ, món bán chạy, thực đơn, chi tiết món, thông tin cửa hàng và bản đồ; đăng ký, xác thực email và đăng nhập |
| User / `CUSTOMER` | Hồ sơ, nhiều địa chỉ, tìm kiếm và phân trang món, yêu thích, đã xem, giỏ hàng lưu database, COD/VNPay, lịch sử đơn, hủy và trả hàng, đánh giá kèm ảnh/video, voucher, Rewards, đặt tiệc và đặt nhóm |
| Vendor | Có chức năng khách hàng; đăng ký shop chờ duyệt; quản lý trang cửa hàng, sản phẩm, ảnh, tồn kho, khuyến mãi, đơn và doanh thu của shop |
| Manager | Quản lý chi nhánh được giao: nhân sự và khách liên quan, sản phẩm, danh mục, nhà vận chuyển, phí giao, hoa hồng, khuyến mãi và đơn hàng |
| Admin | Quản lý toàn hệ thống: tài khoản, vai trò, cửa hàng, duyệt shop, danh mục, nguyên liệu, sản phẩm, vận chuyển, ưu đãi, nhật ký và cấu hình |
| Staff | Xử lý đơn của chi nhánh được giao theo quyền và trạng thái hợp lệ |
| Shipper | Xem đơn được phân công, cập nhật giao hàng, xác nhận thu COD và thống kê đơn của mình |

Tài khoản khách hàng sử dụng role `CUSTOMER`. Quyền được kiểm tra ở backend theo vai trò, chủ sở hữu và chi nhánh.

### Luồng đặt hàng và hậu mãi

1. Khách chọn cửa hàng, món, nguyên liệu và thêm vào giỏ.
2. Chọn nhận tại cửa hàng hoặc giao tận nơi, địa chỉ, nhà vận chuyển, thời gian và voucher.
3. Backend kiểm tra tồn kho, điều kiện ưu đãi và tính lại giá, phí giao, tổng tiền.
4. Khách đặt đơn bằng COD hoặc thanh toán VNPay Sandbox.
5. Cửa hàng xác nhận, chuẩn bị và phân công shipper với đơn giao tận nơi.
6. Shipper cập nhật lấy hàng, đang giao, hoàn tất; đơn COD cần xác nhận thu tiền.
7. Khách xem lịch sử, nhận điểm, đánh giá món đủ điều kiện; hủy hoặc trả hàng đi qua các bước xét duyệt và đối soát.

Giá món, thành phần, địa chỉ, phí, hoa hồng và các dữ liệu cần đối chiếu được chốt vào đơn. Thay đổi cấu hình sau đó không tính lại giá đơn cũ.

### Cấu hình trực tiếp trên Admin

- Tên thương hiệu, ngưỡng miễn phí giao, thời hạn thanh toán và tỷ lệ tích điểm.
- Thời gian chuẩn bị tối thiểu và số ngày được đặt trước.
- Phần thưởng: điểm cần đổi, giá trị voucher, hạn dùng và trạng thái.
- Gói tiệc: giá mỗi người, giới hạn số khách và thời gian đặt trước.
- Quy tắc nguyên liệu của món: nhóm bắt buộc/tùy chọn, lựa chọn mặc định và món chay.

Chi tiết tại [BUSINESS_RULES.md](BUSINESS_RULES.md).

## Cài đặt và chạy

### 1. Chuẩn bị

- JDK **21**, Git, SQL Server và SQL Server Management Studio (SSMS).
- Biết đúng máy chủ, cổng, database và tài khoản kết nối SQL Server.
- Internet ở lần build đầu để Maven Wrapper tải dependency; không cần cài Maven riêng.
- Node.js nếu chạy kiểm thử JavaScript; không bắt buộc để chạy web.
- Ollama nếu dùng trợ lý; tài khoản SMTP, Cloudinary và VNPay Sandbox để thử đầy đủ các tích hợp.

### 2. Lấy mã nguồn

```powershell
git clone https://github.com/pepecrazy06/UTEB_Burrito_Fullstack.git
cd UTEB_Burrito_Fullstack
```

Chạy các lệnh dưới đây tại thư mục có `pom.xml`. Nếu dùng bản tính năng trên nhánh riêng, chuyển sang nhánh đó trước khi cấu hình.

### 3. Khởi tạo hoặc nâng cấp database

**Database mới:** trong SSMS, chạy [sql/00_create_database.sql](sql/00_create_database.sql) để tạo `uteb` với collation UTF-8. Nếu chọn tên khác, dùng đúng tên đó trong lệnh migration và cấu hình ứng dụng.

**Database đã có dữ liệu:** dừng ứng dụng và sao lưu trước khi nâng cấp. Không chạy lại V1 hoặc xóa bảng để sửa lỗi schema.

Runner mặc định dùng Windows Authentication của người chạy PowerShell, cần quyền tương ứng trên database:

```powershell
# Xem các migration còn thiếu, chưa ghi thay đổi.
.\sql\Apply-Schema.ps1 -Server 'localhost,1433' -Database 'uteb'

# Áp dụng các migration đã liệt kê.
.\sql\Apply-Schema.ps1 -Server 'localhost,1433' -Database 'uteb' -Apply
```

Nếu dùng **SQL Authentication**, nhập thông tin riêng và thêm `-Authentication sql`:

```powershell
$env:DB_USER = Read-Host 'SQL login'
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'SQL password' -AsSecureString)).Password

.\sql\Apply-Schema.ps1 -Server 'localhost,1433' -Database 'uteb' -Authentication sql
.\sql\Apply-Schema.ps1 -Server 'localhost,1433' -Database 'uteb' -Authentication sql -Apply
```

**Sau khi runner hoàn tất, chạy riêng [V12__business_rules_and_catalog_metadata.sql](sql/V12__business_rules_and_catalog_metadata.sql) trong SSMS trên đúng database.** Runner hiện áp dụng đến V11, chưa tự thêm V12. V12 yêu cầu V11 và bổ sung cấu hình, phần thưởng, gói tiệc cùng metadata món.

Các tệp V6–V8 có tên từ những nhánh lịch sử; không chạy tất cả file theo thứ tự tên. Dùng runner chọn đúng migration rồi áp dụng V12 như trên. Đối chiếu lịch sử:

```sql
USE [uteb];
SELECT version_no, name, applied_at
FROM dbo.schema_migrations
ORDER BY version_no;
```

Hibernate dùng `ddl-auto=validate`: **không tự chạy migration, không tạo lại schema và không tự chuyển sang H2 khi SQL Server lỗi**.

### 4. Lưu kết nối riêng trên máy

Với SQL login, thay `TEN_SQL_LOGIN` bằng login thực tế:

```powershell
.\sql\Save-LocalConfig.ps1 -Section sql -SqlLogin 'TEN_SQL_LOGIN' -Database 'uteb'
```

Helper hỏi mật khẩu bằng cách nhập ẩn, tạo `.env.local.properties` và sinh `JWT_SECRET` nếu chưa có. Thêm `-SqlServer 'SQL_HOST:PORT'` khi dùng địa chỉ khác. Profile mặc định `sqlserver` tự nạp file này, kể cả trong terminal mới.

- `.env.example` chỉ mô tả biến; ứng dụng không tự nạp file này hoặc file `.env` bất kỳ.
- Biến môi trường trong Terminal/IntelliJ được ưu tiên hơn file đã lưu.
- Mỗi thành viên cấu hình trên máy mình. `.env.local.properties` chứa thông tin riêng và được Git bỏ qua.
- Helper trên dùng SQL Authentication; cách dùng Windows Authentication nằm trong [SQLSERVER_RUNTIME.md](SQLSERVER_RUNTIME.md).

Nếu PowerShell chặn helper, có thể gọi riêng lần chạy đó bằng:

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\sql\Save-LocalConfig.ps1 -Section sql -SqlLogin 'TEN_SQL_LOGIN' -Database 'uteb'
```

### 5. Tạo Admin lần đầu và khởi động

Chỉ khi database chưa có tài khoản, đặt thông tin khởi tạo trong terminal:

```powershell
$env:ADMIN_EMAIL = Read-Host 'Email Admin khoi tao'
$env:ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Mat khau Admin' -AsSecureString)).Password
```

Ứng dụng chỉ tạo Admin khi bảng tài khoản trống. Database đang có dữ liệu dùng tài khoản đã tạo; không có mật khẩu Admin chung cho bản SQL Server.

```powershell
.\mvnw.cmd spring-boot:run
```

Chờ dòng `Started UtebApplication`, mở **http://localhost:8080/**. Nhấn **Ctrl+C** tại terminal để dừng.

Trong IntelliJ IDEA, mở dự án Maven, chọn JDK 21 và chạy `UtebApplication`. Working directory phải có `pom.xml` và file cấu hình riêng. Biến của Terminal và nút Run thuộc hai môi trường khác nhau.

Chi tiết: [LOCAL_SETUP.md](LOCAL_SETUP.md).

## Các dịch vụ tích hợp

### Email OTP

Cấu hình bằng thông tin nhà cung cấp:

| Biến | Nội dung |
| --- | --- |
| `MAIL_ENABLED` | `true` khi đã cấu hình SMTP |
| `SMTP_HOST`, `SMTP_PORT` | Máy chủ và cổng SMTP, dùng STARTTLS |
| `SMTP_USER`, `SMTP_PASSWORD` | Tài khoản gửi và mật khẩu SMTP/App Password |
| `MAIL_FROM` | Địa chỉ được phép gửi; mặc định theo `SMTP_USER` |

Nạp các biến vào terminal hiện tại:

```powershell
. .\sql\Enable-Smtp.ps1
```

Khởi động web trong cùng terminal để thử gửi thư. Để lưu cho lần sau, chạy lại helper lưu cấu hình SQL ở bước 4 trong terminal này; các biến SMTP đang có cũng được lưu kèm.

OTP hết hạn sau 5 phút, tối đa 5 lần sai, chờ 60 giây trước khi gửi lại. Profile SQL không ghi OTP ra hộp thư giả lập. Chi tiết: [SMTP_SETUP.md](SMTP_SETUP.md).

### Cloudinary

```powershell
.\sql\Save-LocalConfig.ps1 -Section cloudinary
```

Nhập **Cloud name**, **API Key**, **API Secret**. Form sản phẩm, cửa hàng và đánh giá cho phép chọn ảnh/video từ máy để tải lên. Backend kiểm tra định dạng; mỗi tệp tối đa 10 MB theo cấu hình hiện tại.

### VNPay Sandbox

Website hỗ trợ **COD và VNPay**. MoMo không nằm trong bản hiện tại; không có nút giả lập xác nhận thanh toán.

Sau khi có TmnCode và HashSecret, lưu cùng HTTPS URL đang sử dụng:

```powershell
.\sql\Save-LocalConfig.ps1 -Section vnpay -PublicBaseUrl 'https://YOUR_PUBLIC_DOMAIN'
```

| URL | Đường dẫn |
| --- | --- |
| Return | `https://YOUR_PUBLIC_DOMAIN/payments/vnpay/return` |
| IPN | `https://YOUR_PUBLIC_DOMAIN/payments/vnpay/ipn` |

Khi thử từ máy cá nhân, có thể dùng tunnel để VNPay gọi IPN:

```powershell
cloudflared tunnel --url http://localhost:8080
```

Khi tunnel đổi địa chỉ, cập nhật `BASE_URL` bằng helper VNPay, cập nhật IPN bên VNPay rồi khởi động lại web. HTTPS dùng `COOKIE_SECURE=true` và truy cập chính URL HTTPS đó; HTTP local dùng `false`.

Chỉ IPN hợp lệ mới xác nhận thanh toán; Return không tự đánh dấu đã trả tiền. Thiếu cấu hình VNPay, COD vẫn dùng được nhưng backend chặn tạo giao dịch VNPay. Dùng thông tin thử do VNPay cung cấp khi demo Sandbox.

### Trợ lý AI với Ollama

Cài Ollama, tải mô hình và kiểm tra:

```powershell
ollama pull qwen3:4b-instruct
ollama list
```

Giữ Ollama chạy tại `http://127.0.0.1:11434`. Nếu chưa chạy nền, mở terminal riêng và dùng `ollama serve`. Có thể lưu cấu hình bằng:

```powershell
.\sql\Save-LocalConfig.ps1 -Section ai
```

Nhấn biểu tượng trợ lý ở góc trang và thử hỏi:

- “Tôi muốn món gà, không cay, dưới 120.000đ.”
- “Đổi sang bò nhưng vẫn giữ ngân sách.”
- “Tôi muốn ăn nhẹ, hạn chế calories.”
- “Liệt kê các chi nhánh và giờ mở cửa.”

Trợ lý đọc thực đơn và dữ liệu công khai, kiểm tra lại giá/nguyên liệu bằng logic hệ thống. Mô hình chạy trên máy, không cần API key trả phí hoặc huấn luyện lại để cập nhật thông tin cửa hàng. AI không tự đặt đơn thay khách. Chi tiết: [AI_SETUP.md](AI_SETUP.md).

## Bảo mật và kiểm soát dữ liệu

- Mật khẩu dùng BCrypt; OTP lưu dạng băm, có hạn dùng và giới hạn thử.
- Luồng web dùng phiên, cookie HttpOnly và CSRF. API Bearer JWT có cơ chế xác thực riêng.
- Backend kiểm tra role, chi nhánh và chủ sở hữu của đơn, địa chỉ, giỏ và dữ liệu liên quan.
- Đổi/reset mật khẩu, khóa tài khoản hoặc đổi quyền kết hợp thu hồi phiên/token và kiểm tra WebSocket.
- Mặc định giới hạn đăng nhập sai 5 lần/tài khoản, 30 lần/IP trong 15 phút; có thể đổi qua cấu hình.
- Kiểm tra trường bắt buộc, email, điện thoại, số lượng, tồn kho, trạng thái, voucher và tệp upload ở backend.
- VNPay kiểm tra chữ ký và số tiền; checkout tính tiền lại tại server; xử lý lặp được kiểm soát để tránh ghi nhận hoặc cập nhật tồn nhiều lần.
- Khóa dịch vụ, file `.env.*`, database, log và backup không đưa vào Git. Admin chỉ công khai các tham số nghiệp vụ được cho phép.

## Build và kiểm thử

### Java

```powershell
.\mvnw.cmd test
```

Các test dùng H2 riêng để cô lập dữ liệu. Kết quả này không thay thế kiểm tra SQL Server, SMTP, Cloudinary và VNPay bằng cấu hình thực tế.

### JavaScript

```powershell
node --test src/test/javascript/*.test.cjs
```

### Đóng gói

```powershell
.\mvnw.cmd package
java -jar target/uteb-burrito-1.0.0.jar
```

Khi chạy JAR, giữ working directory chứa `.env.local.properties`, hoặc chỉ định file riêng qua `UTEB_LOCAL_CONFIG`. macOS/Linux dùng `sh ./mvnw test` hoặc `sh ./mvnw spring-boot:run`; cấu hình SQL và dịch vụ qua các biến tương ứng.

## Cấu trúc dự án

```text
.
├── pom.xml                         # Dependency và build Maven
├── mvnw / mvnw.cmd                 # Maven Wrapper
├── src/main/java/vn/uteb/          # API, nghiệp vụ, phân quyền và tích hợp
├── src/main/resources/
│   ├── application*.properties    # Cấu hình theo profile
│   ├── templates/                 # Trang HTML khởi tạo ứng dụng
│   ├── static/                    # JavaScript, CSS và ảnh
│   └── db/migration/              # Bản sao migration để đối chiếu
├── src/test/java/                 # Kiểm thử backend
├── src/test/javascript/           # Kiểm thử frontend
└── sql/                           # Migration và helper cấu hình
```

## Phạm vi và giới hạn

- VNPay hiện dùng **Sandbox**, chưa phải cấu hình thanh toán thương mại.
- Hoàn tiền được duyệt và ghi nhận bằng bằng chứng thủ công; chưa tự gọi API chuyển tiền hoàn của VNPay.
- Đặt tiệc là luồng gửi yêu cầu và báo giá, tách khỏi checkout món thông thường.
- Trợ lý chưa đủ dữ liệu để cam kết an toàn dị ứng hoặc tư vấn dinh dưỡng chuyên môn; hội thoại hiện giữ trong phiên trang.
- Giới hạn đăng nhập nằm trong bộ nhớ một tiến trình; triển khai nhiều máy chủ cần cơ chế chia sẻ phù hợp.
- Profile H2 demo tồn tại riêng để thử nghiệm, không phải nguồn dữ liệu của bản SQL Server.

## Xử lý lỗi thường gặp

| Hiện tượng | Kiểm tra |
| --- | --- |
| Không kết nối SQL Server | Host/cổng/database, TCP/IP, quyền của login, giá trị trong file riêng và biến môi trường |
| Hibernate báo thiếu bảng/cột | Lịch sử migration; áp dụng phần thiếu đến V11 và V12 riêng; giữ `ddl-auto=validate` |
| Database mới thiếu Admin | Đặt `ADMIN_EMAIL`, `ADMIN_PASSWORD` khi khởi động lần đầu và bảng tài khoản đang trống |
| HTTP local không giữ đăng nhập | `COOKIE_SECURE=false` cho HTTP; HTTPS cần đúng domain và cấu hình tương ứng |
| OTP không gửi | `MAIL_ENABLED`, thông tin SMTP, quyền gửi của `MAIL_FROM`, Inbox/Spam |
| Ảnh không tải lên | Đúng Cloud name/API Key/API Secret, định dạng và dung lượng tệp |
| VNPay chờ xác nhận | Bộ khóa, HTTPS URL, tunnel và IPN; không sửa trạng thái thanh toán bằng SQL |
| AI chưa sẵn sàng | Ollama, `ollama list`, model, URL; chờ nạp mô hình ở lần gọi đầu |
| Giao diện chưa đổi | Khởi động lại bản mới và tải lại trình duyệt bằng Ctrl+F5 |
| Cổng 8080 đang bận | Dừng web cũ hoặc chạy `./mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"`; cập nhật URL liên quan nếu đổi cổng |
