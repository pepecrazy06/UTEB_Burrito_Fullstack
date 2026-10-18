# Email OTP khi chạy SQL Server

SQL Server và SMTP là hai kết nối độc lập. Cấu hình hiện tại dùng biến môi trường,
không dùng mật khẩu ghi trong source. `MAIL_ENABLED` mặc định là `false`;
host mẫu `smtp.example.com` không gửi được email. Dự án không tự đọc file `.env`.

Nếu mật khẩu SMTP/App Password từng được push lên GitHub, hãy thu hồi mật khẩu đó
ở nhà cung cấp email và tạo mật khẩu mới. Xóa khỏi file hiện tại không xóa lịch sử Git.
Không đặt mật khẩu mới trong source, commit, ảnh chụp hoặc nội dung PR.

## PowerShell trong IntelliJ

1. Mở Terminal tại thư mục có `pom.xml`, chọn PowerShell.
2. Giữ nguyên các biến SQL Server đã dùng để chạy thành công.
3. Chạy lệnh dưới đây (có dấu chấm và khoảng trắng đầu lệnh). Script hỏi host,
   email gửi và mật khẩu ẩn; chỉ đặt biến cho terminal hiện tại, không ghi ra file:

```powershell
. .\sql\Enable-Smtp.ps1
.\mvnw.cmd spring-boot:run
```

Cấu hình hiện tại dùng SMTP có đăng nhập và STARTTLS, mặc định cổng 587.
Điền host/cổng và mật khẩu SMTP theo nhà cung cấp email của nhóm.
Nếu tài khoản dùng Gmail với xác minh hai bước, dùng App Password mới.
[Hướng dẫn App Password của Google](https://support.google.com/accounts/answer/185833).
Hộp thư `MAIL_FROM` mặc định trùng `SMTP_USER`; nếu dùng địa chỉ khác,
nhà cung cấp cần cho phép gửi bằng địa chỉ đó.

Các biến cần có: `MAIL_ENABLED=true`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`,
`SMTP_PASSWORD`; tùy chọn `MAIL_FROM`. Đừng đặt `MAIL_FROM=noreply@example.com`.

## Nút Run của IntelliJ

Run → Edit Configurations → cấu hình chạy Spring Boot → Environment variables.
Thêm cùng các biến trên vào cấu hình đang dùng (giữ nguyên các biến SQL).
Biến đặt trong Terminal không tự truyền vào nút Run của IntelliJ và ngược lại.
Khởi động lại ứng dụng sau khi sửa cấu hình. Không chia sẻ Run Configuration chứa mật khẩu.

## Kiểm tra nhận email

1. Đăng ký bằng email có thể nhận thư: tài khoản chưa xác thực chưa được đăng nhập.
2. Kiểm tra Inbox/Spam, nhập mã 6 số trong 5 phút, rồi đăng nhập.
3. Thử mã sai và mã đã dùng: phải bị từ chối. Gửi lại trước 60 giây phải bị chặn.
4. Nếu đóng trang trước khi xác thực, dùng liên kết “Xác thực lại email đăng ký”
   ở trang đăng nhập để gửi lại, không cần đăng ký lần nữa.
5. Quên mật khẩu → gửi OTP → nhập OTP và mật khẩu mới. Mật khẩu cũ không đăng nhập
   được; mã đã dùng không dùng lại được.
6. Khi SMTP sai, giao diện phải báo chưa gửi được thư; đăng ký thất bại không để lại
   tài khoản dở dang. Lỗi gửi lại không thay mã cũ, thời hạn hoặc thời gian chờ.

Profile SQL không ghi OTP ra `data/mailbox`. Thư mục này chỉ dùng cho profile demo.
Các kiểm thử tự động dùng cơ sở dữ liệu riêng và mail giả lập; nhận thư qua nhà
cung cấp thật cần chạy các bước trên bằng cấu hình riêng của nhóm.
