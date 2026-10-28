# Kết quả nghiệm thu chuyển SQL Server

Ngày nghiệm thu local: 18/10/2026. Nhánh feat/sqlserver-runtime đã tích hợp main
218a9ab. Báo cáo khôi phục từ kết quả kiểm tra và bằng chứng local đã lưu; không
chứa tài khoản đăng nhập thật, mật khẩu, JWT hoặc token.

## Kết quả đã xác minh

| Hạng mục | Kết quả |
| --- | --- |
| Test/package | 240 test đạt: 204 Commerce, 9 Birthday, 27 cấu hình/SeedData/BootstrapAdmin; Maven package BUILD SUCCESS |
| JDBC thật | JDK 21, driver Microsoft 12.10.1, DLL x64, Windows Authentication kết nối thành công |
| Đích nghiệm thu | localhost:1433, default instance MSSQLSERVER, database UTE_Burrito |
| SQL Server | 17.0.1000.7, Standard Developer Edition 64-bit |
| Schema | V1–V4 áp dụng sau phê duyệt, 24 bảng gồm history; Hibernate validate thành công |
| Unicode | Database trống được đổi UTF8 sau phê duyệt; catalog tiếng Việt đọc đúng |
| Birth date | accounts.birth_date là date NULL; V4 giữ đúng tên từ main, hai bản script đồng nhất |
| Mặc định runtime | sqlserver, demo=false, seed=false, ddl-auto=validate, sql.init.mode=never; không fallback H2 |
| ADMIN | Người dùng nhập password kín qua biến môi trường; BootstrapAdmin tạo đúng 1 verified ADMIN, không tạo trùng sau restart |
| Web tạo | ID 49, SQLTEST-Khang-20261018-0400, store_id=1, giá 123000, stock=7, version=0 |
| Web sửa | Cùng ID 49, giá 124000, stock=9, version=1; SELECT SQL Server khớp |
| Restart | Tiến trình trước restart PID 7092, sau restart PID 31556, Started 04:08:39; dữ liệu SQLTEST giữ nguyên |
| Website sau restart | Reload thực đơn, tìm SQLTEST; tên/mô tả đã sửa và 124.000đ hiển thị, không cần đăng nhập |
| Không seed lại | Tổng 49 sản phẩm gồm 48 catalog gốc + 1 SQLTEST; đúng 1 ADMIN; 0 đơn, 0 reward_logs, 0 order_events |
| Không sửa sản phẩm gốc | 48 sản phẩm ID 1–48 còn nguyên, tất cả version=0 |
| Audit | 2 audit_records do thao tác tạo/sửa thật, không phải dữ liệu demo |
| H2 | SHA256 trước/sau khớp, 110592 byte; không mở hoặc chuyển dữ liệu H2 |

SHA256 file data/uteb.mv.db trước và sau nghiệm thu:

```text
1BDAAFCC9CC7340C28698F1EDFD97F2D9B95CCCCBEFE2F5EBDB7F8B0A7EC6257
```

History sau restart vẫn chỉ có version 1, 2, 3, 4 với tên V1__schema,
V2__constraints, V3__catalog_seed, V4__account_birth_date. Runner kế hoạch báo
No pending V1-V4 migrations. Không chạy lại V3, không chạy script tạo database cũ.

Không dùng INSERT SQL để chứng minh web ghi dữ liệu. Không tạo đơn, không thêm
vào giỏ, không thanh toán và không vô hiệu hóa SQLTEST. Sản phẩm kiểm thử còn
nhãn rõ để nhóm nhận diện. Không xóa hoặc reset dữ liệu SQL/H2 hiện có.

## Sửa lỗi tối thiểu đã được duyệt

Form sửa có input ẩn name=id che thuộc tính form.id, khiến switch(form.id) không
đi vào nhánh admin-form. Người dùng đã duyệt đúng một dòng trong app.js:

```javascript
switch(form.getAttribute('id'))
```

Sau thay đổi, chạy lại 240 test/package thành công và nghiệm thu sửa qua website
đã cập nhật SQL version 0 thành 1. Không sửa bố cục hoặc module nghiệp vụ khác.

## Bằng chứng và vị trí đề xuất cho PR

Bằng chứng gốc hiện ở target/sqlserver-evidence (Git ignore):

- web-after-restart.jpg: website tìm thấy SQLTEST và giá 124.000đ sau reload.
- web-after-restart.txt: DOM của thực đơn sau restart.
- sql-after-web-update.txt: SELECT ngay sau sửa sản phẩm qua web.
- sql-after-restart.txt: SELECT và số lượng dữ liệu sau restart.
- first-startup.log, before-final-restart.log, final-startup.log: log SQL Server,
  Hibernate và PID trước/sau. Log thô chỉ giữ local.

Đề xuất đính kèm ảnh website và kết quả SELECT đã lược bỏ thông tin máy/tài khoản
vào nội dung PR sau khi được phép xuất bản. Nếu nhóm muốn lưu cùng repository,
đề xuất thư mục review-evidence/sqlserver/2026-10-18/ cho ảnh và kết quả SELECT đã
kiểm tra, nằm ngoài target/ và docs/. Chỉ chọn từng file cần thiết; không copy toàn
bộ target, log thô, cookie/session, DLL, thông tin đăng nhập hoặc dữ liệu database.
Chưa tạo/stage thư mục bằng chứng này; danh sách staged vẫn chỉ có 17 file dưới đây.
Không sửa .gitignore. Maven clean sẽ xóa bằng chứng còn trong target, nên cần
sao lưu các file đã được nhóm chọn trước khi clean.

## Giới hạn thực tế

- Chưa tái lập trên máy thành viên/leader khác. Hướng dẫn ba trường hợp local
  Windows, SQL login dùng chung và Windows/domain từ xa ở SQLSERVER_RUNTIME.md.
- SELECT bằng sqlcmd trên đúng instance/database; chưa chụp cửa sổ SSMS.
- SQL login được kiểm tra bằng test cấu hình, kết nối thật phiên này dùng Windows.
- Tomcat khởi chạy từ công cụ Codex gặp lỗi UnixDomainSockets/PipeImpl khi mở cổng,
  nên người dùng chạy app và các lần restart từ terminal riêng. JDBC/Hibernate
  vẫn kết nối và validate; không đổi schema hay nới bảo mật để xử lý lỗi JVM đó.
- V3 vẫn là catalog mẫu cần thay dần (ảnh, giá, tọa độ, voucher), không phải dữ liệu
  kinh doanh đã xác minh. Chưa kiểm kê dữ liệu nhập tay H2; không tự sao chép H2.
- OTP/email thật, VNPay/MoMo/refund, Cloudinary, bản đồ và production ngoài phạm vi.
- Secret SMTP cũ đã được bỏ khỏi cấu hình nhưng còn trong lịch sử Git; chủ tài
  khoản cần thu hồi/đổi secret đó. Không sửa lịch sử Git trong phần bàn giao này.

## Danh sách 17 file chuẩn bị commit

1. .env.example
2. SQLSERVER_RUNTIME.md
3. SQLSERVER_ACCEPTANCE.md
4. sql/Apply-Schema.ps1
5. sql/Check-Connection.java
6. sql/Start-LocalSqlServer.ps1
7. sql/V4__account_birth_date.sql
8. src/main/java/vn/uteb/SeedData.java
9. src/main/java/vn/uteb/SqlServerEnvironment.java
10. src/main/resources/application.properties
11. src/main/resources/application-sqlserver.properties
12. src/main/resources/META-INF/spring.factories
13. src/main/resources/db/migration/V4__account_birth_date.sql
14. src/main/resources/static/app.js
15. src/test/java/vn/uteb/CommerceTest.java
16. src/test/java/vn/uteb/RegistrationBirthdayTest.java
17. src/test/java/vn/uteb/SqlServerEnvironmentTest.java

Commit dự kiến: config: switch shared runtime to SQL Server.
Chỉ stage 17 file để người dùng duyệt; chưa commit, push hoặc tạo PR.
