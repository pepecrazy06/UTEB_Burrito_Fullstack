# Thực đơn và hình ảnh của Đạt

Nhánh tích hợp lấy nội dung từ `cb73587`, `70228eb`, `ddffc67`, giữ tác giả Dương Thành Đạt trong các commit cục bộ tương ứng. Phần quản lý xóa ảnh của PR7 được giữ riêng.

Mỗi chi nhánh có cùng 24 món đang bán: 8 Burrito; Bowl, Tacos, Quesadilla, Salad và Kids mỗi loại 2; Sides và Drinks mỗi loại 3. Hai Burrito Trứng phô mai và Gà guacamole có khẩu phần cố định. Ảnh đang bán dùng bộ PNG tách nền của Đạt. Giá/calo và nhóm nguyên liệu theo từng loại món được ghép cùng backend và giao diện để tính nhất quán.

## SQL Server

Sao lưu database trước khi cập nhật môi trường khác. Dùng helper `sql/Apply-Schema.ps1` để xem kế hoạch rồi áp dụng. Bản tích hợp chạy V1–V5, V8 hủy/hoàn tiền, V9 tọa độ và **V10 thực đơn**. Không chạy riêng V6/V7/V8 menu cũ: số V6/V8 có thể đã thuộc lịch sử hủy đơn. Không xóa hay đổi tên các dòng lịch sử migration đã dùng.

V10 ghép các cập nhật dữ liệu của ba migration menu của Đạt dưới một số phiên bản mới. Vì không phụ thuộc số V6 cũ, database có V6 hủy đơn vẫn nhận được ảnh mới. Database có V6/V7 menu cũng được nâng cấp. Database từng dùng V8 cho menu phải được kiểm tra riêng; helper dừng thay vì giả định V8 là hủy đơn.

Các bản ghi món cũ được giữ, chỉ ẩn những món mẫu không còn trong danh sách chọn lọc; lịch sử đơn, ảnh chụp đơn, tồn kho cũ và ID sản phẩm không bị dựng lại. Theo lựa chọn dùng trọn bộ ảnh của Đạt, ảnh của 24 tên món chuẩn được đổi sang PNG tách nền mới; tệp/MediaRecord Cloudinary và ảnh đã chụp trong đơn cũ không bị xóa. Giá đã được quản trị chỉnh được giữ theo điều kiện của migration. Nếu món mẫu đã đổi tên thì migration không đoán và ghi đè tên mới. Các chi nhánh được tạo mới sau lần nâng cấp cần bổ sung thực đơn tương ứng.

Đổi dữ liệu SQL xong cần tải lại trang. Sau khi ghép code, phải khởi động lại Spring Boot để backend tính giá và tùy chọn theo thực đơn mới. Chỉ dừng tiến trình Spring Boot, giữ tunnel Cloudflared đang chạy để tránh đổi URL VNPay. Cấu hình SQL/SMTP/Cloudinary/VNPay riêng vẫn ở `.env.local.properties` đã được Git bỏ qua.
