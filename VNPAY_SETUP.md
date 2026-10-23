# VNPay Sandbox cho UTEB Burrito

Nhánh: `feat/vnpay-payment`. Đây là kết nối cổng Sandbox, dùng thẻ/tài khoản
thử nghiệm do VNPay cung cấp. MoMo sẽ được hoàn thiện riêng.

## 1. Chuẩn bị tài khoản

Đăng ký tại [VNPay Sandbox](https://sandbox.vnpayment.vn/devreg/), mở email để
kích hoạt tài khoản và lấy `vnp_TmnCode` cùng `vnp_HashSecret`. Mật khẩu đăng nhập
trang merchant và HashSecret là hai thông tin khác nhau.

## 2. Thêm bảng thanh toán vào SQL Server

Dừng ứng dụng bằng Ctrl+C, giữ tunnel chạy. Trong PowerShell đã có các biến SQL:

```powershell
# Xem kế hoạch trước; Database phải là database đang dùng trong DB_URL.
.\sql\Apply-Schema.ps1 -Server 'localhost,1433' -Database uteb -Authentication sql

# Nếu kế hoạch chỉ có V5 (hoặc V4 + V5 khi máy chưa có ngày sinh), thực hiện:
.\sql\Apply-Schema.ps1 -Server 'localhost,1433' -Database uteb -Authentication sql -Apply
```

V5 thêm `dbo.payment_attempts` và ghi lịch sử migration. Không xóa bảng hoặc dữ
liệu đang có. File SQL cũng được giữ tại `src/main/resources/db/migration/` để
đối chiếu; ứng dụng dùng `ddl-auto=validate`, không tự chạy các migration này.
Mỗi máy của thành viên nhóm cần chạy migration V5 sau khi lấy code mới.

## 3. Nhập thông tin riêng trên máy

Trong cùng PowerShell đã có DB_URL, DB_USER, DB_PASSWORD và JWT_SECRET:

```powershell
. .\.env.smtp.ps1
. .\sql\Enable-Vnpay.ps1 -PublicBaseUrl 'https://YOUR-CURRENT-TUNNEL.trycloudflare.com'
.\mvnw.cmd spring-boot:run
```

Script hỏi TmnCode và nhập HashSecret ẩn, đặt BASE_URL theo URL HTTPS của bạn.
Khóa chỉ nằm trong môi trường của terminal hiện tại; không ghi vào source hoặc
file. Terminal mới cần nạp lại biến. Nút Run IntelliJ dùng môi trường riêng:
thêm VNPAY_TMN_CODE, VNPAY_HASH_SECRET, BASE_URL và COOKIE_SECURE=true vào đúng
Run Configuration nếu chạy bằng nút đó. Không chia sẻ cấu hình chứa khóa.

URL website cần tiếp tục mở được trong lúc test. Khi tunnel thay địa chỉ, cập
nhật cấu hình tương ứng tại VNPay và BASE_URL, rồi khởi động lại ứng dụng.

## 4. Khai báo URL với VNPay

Với `BASE_URL=https://YOUR-CURRENT-TUNNEL.trycloudflare.com`:

- Return URL: `https://YOUR-CURRENT-TUNNEL.trycloudflare.com/payments/vnpay/return`
- IPN URL: `https://YOUR-CURRENT-TUNNEL.trycloudflare.com/payments/vnpay/ipn`

Khai báo/đề nghị VNPay thiết lập IPN URL cho merchant theo hướng dẫn trong email
kết nối. Không thêm mật khẩu truy cập tunnel vì máy chủ VNPay phải gọi được IPN.
Mở IPN trực tiếp không kèm dữ liệu sẽ trả RspCode 97: đó là việc từ chối yêu cầu
thiếu chữ ký, không phải bằng chứng kết nối thật đã được thiết lập.

## 5. Kiểm tra thanh toán

1. Đăng nhập khách hàng, thêm món từ một chi nhánh, chọn VNPay Sandbox và đặt đơn.
   Website chuyển sang cổng VNPay. Số tiền được tính từ database, không nhận tổng
   tiền do trình duyệt gửi lên. Đơn nhiều chi nhánh thanh toán riêng từng đơn.
2. Dùng thông tin thử nghiệm VNPay cung cấp, hoàn thành giao dịch.
3. Khi quay về, trang có thể hiện đang xác nhận và tự cập nhật khoảng 30 giây.
   PAID chỉ được ghi sau IPN hợp lệ; Return URL không sửa trạng thái thanh toán.
4. Xem đơn: trạng thái Đã thanh toán, mã giao dịch VNPay; kiểm tra SQL bên dưới.
5. Thử hủy/thất bại: đơn chưa được PAID. Có thể thử lại trong hạn 15 phút; mỗi
   lần mới sau thất bại có reference riêng. Bấm lặp khi còn chờ dùng lại cùng lần.
6. Callback lặp không thêm lần xác nhận/thông báo. Đơn hết hạn trả tồn kho đúng
   một lần. Nếu VNPay báo thu tiền sau khi đơn đã hủy/hết hạn, cần đối soát;
   hệ thống lưu REVIEW_REQUIRED, không mở lại đơn hoặc tự giả định đã hoàn tiền.

```sql
SELECT TOP (20) id, code, status, payment_method, payment_status, total, transaction_ref
FROM dbo.meal_orders ORDER BY id DESC;

SELECT TOP (20) order_id, reference, amount, state, transaction_no, response_code,
       transaction_status, created_at, expires_at
FROM dbo.payment_attempts ORDER BY id DESC;
```

Nếu quay về mãi chờ xác nhận, kiểm tra IPN URL đã được VNPay cấu hình, tunnel
còn chạy và BASE_URL đúng. Không sửa PAID thủ công để làm giao diện báo thành công.

Quy tắc giao tiếp tham chiếu [tài liệu PAY chính thức của VNPay](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).
Kiểm thử tự động dùng khóa và database riêng; giao dịch qua cổng thật cần kiểm
tra tiếp bằng tài khoản Sandbox và cấu hình riêng của nhóm.
