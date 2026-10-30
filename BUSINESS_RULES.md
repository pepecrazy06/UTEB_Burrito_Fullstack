# Cấu hình nghiệp vụ và kiểm thử theo tiêu chí đồ án

## Chạy bản mới

1. Dừng web trước khi nâng cấp database.
2. Sao lưu SQL Server, chọn đúng database `uteb`, chạy `sql/V12__business_rules_and_catalog_metadata.sql` trong SSMS. Script yêu cầu đã có V11, chạy trong transaction và có thể chạy lại.
3. Mở PowerShell trong thư mục có `pom.xml`, chạy `./mvnw.cmd spring-boot:run`, sau đó tải lại trình duyệt bằng Ctrl + F5.
4. Mỗi thành viên cập nhật code và chạy V12 trên database riêng. Không đưa file cấu hình riêng, backup hoặc tài khoản SMTP/API lên GitHub.

V12 bổ sung bảng chương trình đổi thưởng, bảng gói tiệc, các tham số công khai, quy tắc nguyên liệu theo món và dữ liệu chốt tỷ lệ tích điểm theo đơn. Giá, nội dung và lịch sử đơn cũ được giữ nguyên. Tên danh mục/món chỉ được diễn giải một lần lúc chuyển dữ liệu cũ sang metadata; lúc vận hành không dùng tên để xác định công thức.

## Admin có thể thay đổi gì?

Trong **Quản trị → Cấu hình**, nhấn Sửa ở từng tham số:

| Tham số | Áp dụng |
|---|---|
| Tên thương hiệu và tên ngắn cạnh logo | Tiêu đề trang, nhận diện đầu trang và cuối trang sau khi nạp dữ liệu |
| Đơn tối thiểu miễn phí giao | Các lần xem trước và đặt đơn tiếp theo, tính riêng mỗi chi nhánh |
| Số tiền tương ứng 1 điểm | Đơn mới; tỷ lệ được chốt khi đặt đơn |
| Thời hạn thanh toán | Hạn của đơn mới; không đổi hạn đã ghi nhận |
| Thời gian chuẩn bị tối thiểu | Kiểm tra giờ hẹn ở giao diện và backend |
| Số ngày được đặt trước | Kiểm tra giờ hẹn ở giao diện và backend |

Chỉ Admin sửa được các tham số này. Backend kiểm tra tên khóa, kiểu dữ liệu, giới hạn và chống trùng. Mật khẩu, JWT secret, SMTP, VNPay và Cloudinary tiếp tục ở cấu hình riêng, không xuất hiện trong API công khai hoặc bảng này.

**Chương trình đổi thưởng:** thêm/sửa tên, mã, điểm cần đổi, giá trị voucher, số ngày hiệu lực và bật/tắt. Khách thấy danh sách đang áp dụng. Voucher đã đổi giữ giá trị và hạn của lúc đổi.

**Gói đặt tiệc:** thêm/sửa tên, mô tả, giá mỗi người, số khách tối thiểu/tối đa, giờ đặt trước và bật/tắt. Báo giá đã gửi được giữ nguyên khi sửa giá gói.

**Thực đơn & tồn kho → Sửa món:** chọn nhóm nguyên liệu bắt buộc/tùy chọn, hệ số calories, món chay, nhân đã bao gồm trong giá và nguyên liệu chọn sẵn. Không cần nhập ID. Đổi tên món/danh mục không đổi quy tắc này. Trong Nguyên liệu, Admin đánh dấu nguyên liệu có dùng được cho món chay hay không.

Giá, phí, công thức và calories vẫn được backend tính lại. Nội dung hỗ trợ về phí giao và trợ lý đọc cùng ngưỡng miễn phí giao đang cấu hình. Thay đổi cấu hình không làm định giá lại đơn cũ; hoàn tiền thu hồi theo số điểm thực tế đã cộng.

## Bảo mật và validation bổ sung

- Đăng nhập có giới hạn mặc định 5 lần sai/tài khoản và 30 lần/IP trong cửa sổ 15 phút, dùng chung cho `/api` và `/shop-api`. Trả HTTP 429 khi hết lượt. Không tin IP do client tự ghi vào header proxy.
- Điều chỉnh ngưỡng bằng `LOGIN_ACCOUNT_LIMIT`, `LOGIN_IP_LIMIT`, `LOGIN_WINDOW_MINUTES` trong file cấu hình riêng. Đây là bộ giới hạn trong bộ nhớ của một tiến trình; nếu triển khai nhiều máy chủ cần kho giới hạn dùng chung. Khởi động lại tiến trình làm mất bộ đếm hiện tại.
- Tạo/sửa tài khoản từ Admin kiểm tra tên, email, điện thoại và giới hạn BCrypt 72 byte UTF-8; đăng ký shop kiểm tra thông tin liên hệ và yêu cầu đang chờ duyệt.
- Số lượng, sao đánh giá, tồn kho và các số nguyên nghiệp vụ được kiểm tra trước khi chuyển sang `int`, chặn trường hợp số rất lớn bị tràn thành số nhỏ hợp lệ.
- Tài khoản mới chưa có ID không đi vào bước thu hồi WebSocket; chỉnh sửa tài khoản cũ vẫn thu hồi phiên/token theo cơ chế hiện có.
- Các khóa hoặc mật khẩu từng được commit phải được thay tại nhà cung cấp. Thêm `.gitignore` không thu hồi được thông tin đã lộ.

## Các luồng nên demo

1. **Admin → cấu hình → khách checkout:** đặt ngưỡng miễn phí giao 200.000đ; xem giỏ có tổng tiền món dưới ngưỡng. Hạ ngưỡng xuống 100.000đ, tính lại giỏ và kiểm tra phí giao đổi đúng. Đơn đã đặt không đổi tiền.
2. **User → cửa hàng → shipper:** đăng ký và xác thực OTP thật, đăng nhập, lưu địa chỉ, chọn món, chọn COD hoặc VNPay Sandbox. Nhân sự đúng cửa hàng xử lý theo thứ tự; shipper chỉ thao tác đơn được phân công. Xác nhận COD trước khi hoàn tất.
3. **Đổi thưởng và hoàn tiền:** hoàn tất đơn, kiểm tra điểm; đổi voucher. Sửa chương trình rồi đổi voucher mới, xác nhận voucher cũ không đổi. Khi hoàn tiền, kiểm tra số điểm thu hồi đúng tỷ lệ của đơn cũ. Hoàn tiền hiện được người phụ trách xác nhận bằng bằng chứng, không phải tự chuyển tiền từ VNPay.
4. **Admin → sản phẩm:** đổi tên danh mục/món và tên nhân, kiểm tra nhóm nguyên liệu, giá và calories vẫn giữ đúng cấu hình. Thử gắn nguyên liệu thịt vào món chay và kiểm tra bị chặn.
5. **Đặt tiệc:** sửa giá/số khách/giờ đặt trước, thử gửi yêu cầu hợp lệ và không hợp lệ; xác nhận báo giá cũ không bị thay đổi.
6. **Chặn truy cập:** dùng User hoặc Manager gửi API sửa cấu hình toàn hệ thống; sửa ID đơn/địa chỉ sang của người khác; đăng nhập sai liên tục; gửi số lượng `4294967297`. Kết quả cần là lỗi có kiểm soát và không làm hỏng dữ liệu.

Kiểm thử Java dùng database độc lập. Việc chạy được kiểm thử tự động không thay thế kiểm tra SMTP, Cloudinary và VNPay Sandbox trong cấu hình riêng của nhóm.
