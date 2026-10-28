# Nâng cấp chức năng bán hàng — V11

## Chạy bản mới

1. Sao lưu database hiện có trước khi nâng cấp.
2. Trong thư mục dự án, chạy migration bằng tài khoản Windows có quyền quản lý database:

   ```powershell
   .\sql\Apply-Schema.ps1 -Server localhost -Database uteb -Apply
   ```

   Nếu database có tên khác, thay `uteb` bằng đúng tên. Nếu dùng SQL Authentication, đặt riêng `DB_USER`, `DB_PASSWORD` trên máy và thêm `-Authentication sql`. Không đưa thông tin đăng nhập vào Git.
3. Chạy web bằng cấu hình riêng đã lưu trên máy:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

4. Mở `http://localhost:8080/` và nhấn Ctrl+F5. Mỗi thành viên cập nhật code và chạy V11 trên database riêng một lần. Script có thể chạy lại mà không nhân đôi dữ liệu.

V11 thêm thông tin trang shop, phạm vi danh mục, nhà vận chuyển, sản phẩm áp dụng voucher và dữ liệu trả hàng. Migration giữ tài khoản, giỏ hàng, sản phẩm, tồn kho, đơn và giao dịch cũ. Không đổi khóa SMTP, Cloudinary, VNPay hay cấu hình Ollama.

## Chức năng và cách kiểm tra

| Khu vực | Kiểm tra |
|---|---|
| Trang chủ | Món của các shop xếp theo số phần bán giảm dần; mục bán hơn 10 phần chỉ hiện dữ liệu đủ điều kiện. Không tạo lượt bán giả khi chưa có đơn. |
| Thực đơn | Mỗi trang 20 món. Chọn 20 món mới, bán chạy, đánh giá cao hoặc được thích nhất trong đúng cửa hàng/danh mục. Tìm có dấu hoặc không dấu. |
| Chi tiết món | Bấm ảnh/tên món để mở `#product/ID`; kiểm tra giá, calories, shop, đánh giá phân trang và mã ưu đãi của món. Nút đặt vẫn mở tùy chọn nguyên liệu. |
| Shop | Mở `#shop/ID`. Vendor sửa giới thiệu, logo, ảnh bìa trong Chi nhánh; ảnh chọn trực tiếp từ máy và tải lên Cloudinary khi lưu. |
| Tài khoản quản trị | Tìm theo tên/email/điện thoại. Manager thấy nhân sự cùng chi nhánh và khách đã mua tại chi nhánh. Với khách, Manager sửa tên và số liên hệ; Admin quản lý quyền, mật khẩu và khóa tài khoản toàn hệ thống. |
| Danh mục | Admin quản lý danh mục chung hoặc riêng; Manager tạo/sửa danh mục riêng của chi nhánh. Không sửa danh mục chung của các shop khác. |
| Hoa hồng | Admin chỉnh mọi shop; Manager chỉnh chi nhánh được giao. Vendor không tự chỉnh hoa hồng. Tỷ lệ đã lưu trong đơn cũ không thay đổi. |
| Nhà vận chuyển | Admin/Manager thêm tên, phí, trạng thái đúng chi nhánh. Khách chọn tại bước giao tận nơi. Phí/tên được chốt vào đơn; ẩn dịch vụ không xóa lịch sử. |
| Khuyến mãi | Chọn giảm % hoặc số tiền cho toàn phạm vi/một món; hoặc giảm phí giao. Mỗi checkout dùng một mã, kiểm tra hạn, lượt dùng, người sở hữu, điều kiện đơn và chi nhánh trên server. |
| Lịch sử đơn | Khách và nhân sự lọc theo trạng thái, gồm đã lấy hàng, trả hàng và đã hoàn tiền. Shipper chỉ tiếp tục đơn được phân công. |
| Trả hàng | Khách gửi yêu cầu sau khi nhận và thanh toán; shop duyệt kèm hướng dẫn; khách xác nhận đã gửi; shop xác nhận nhận hàng; sau đó mới xác nhận hoàn tiền thủ công có bằng chứng. Không tự chuyển tiền qua VNPay. |
| Hủy đơn trả trước | Admin, Manager hoặc Vendor đúng shop xét duyệt và lưu chứng từ hoàn tiền. Nhân sự/shipper không có quyền này. |
| Thống kê | Shipper chỉ thống kê đơn được giao, tách đơn tự mua; có số đơn theo trạng thái. Doanh thu/lượt bán giữ đến khi tiền thực sự được hoàn, không giảm ngay lúc khách gửi yêu cầu trả. |
| Đã xem | Mở lại món cũ đưa món đó lên đầu lịch sử xem. |

## Kiểm thử

```powershell
.\mvnw.cmd test
node --test src/test/javascript/*.test.cjs
.\sql\Test-CommerceMigration.ps1
```

Kiểm thử Java dùng H2 riêng để cô lập dữ liệu. Bản chạy chung vẫn dùng SQL Server với `ddl-auto=validate`. Kiểm thử migration tạo database SQL Server tạm, kiểm tra giữ lịch sử và tự dọn đúng các database thử đã tạo.
