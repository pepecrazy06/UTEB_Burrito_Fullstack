# Tọa độ chi nhánh

V9 thay tọa độ mẫu trước đây (bốn ghim xếp thành đường chéo quanh Thủ Đức) bằng các điểm theo địa chỉ đang dùng trong đồ án. Đây là địa chỉ tham chiếu cho cửa hàng UTEB giả định, không phải xác nhận có cửa hàng UTEB ngoài thực tế.

| Chi nhánh | Vĩ độ | Kinh độ | Đối chiếu địa chỉ |
|---|---:|---:|---|
| Võ Văn Ngân | 10.8505000 | 106.7713056 | Số 01 Võ Văn Ngân, HCMUTE; tọa độ 10°51′01.8″N, 106°46′16.7″E trong [báo cáo JICA, bảng địa điểm số 62](https://openjicareport.jica.go.jp/pdf/12176566.pdf) |
| Quận 1 | 10.7745167 | 106.7031722 | Tòa nhà Vietcomreal, 68 Nguyễn Huệ; [liên hệ của đơn vị tại tòa nhà](https://anlawvietnam.com/vi/contact-us/) → [ghim địa điểm được liên kết](https://maps.app.goo.gl/umwSKQ76VDHCGWk47). Dùng tọa độ điểm `!3d...!4d...`, không dùng tâm camera `@...`. |
| Bình Thạnh | 10.8000835 | 106.7064464 | HIU, 215 Điện Biên Phủ; [dữ liệu tọa độ địa điểm](https://www.hotelinmap.com/country/vn/establishment/) và [địa chỉ trong tài liệu Bộ Văn hóa](https://bvhttdl.mediacdn.vn/document/2025/3/12/qd565-17417496805601279820315.pdf) |
| Thủ Đức | 10.8567000 | 106.7650860 | 88 Hoàng Diệu 2; `place:location:latitude/longitude` của [địa điểm cùng địa chỉ](https://www.foody.vn/ho-chi-minh/vua-mi-mi-bo-dai-loan-hoang-dieu-2). Sửa phường cũ Linh Trung thành Linh Chiểu để khớp địa chỉ. |

Đối chiếu ngày 27/10/2026. Các điểm xác định vị trí địa chỉ/tòa nhà, không phải khảo sát chính xác cửa ra vào hay lộ trình giao hàng. Nếu thay địa chỉ, cần cập nhật Vĩ độ/Kinh độ tương ứng trong quản trị.

## Áp dụng

`Apply-Schema.ps1` bổ sung V9 sau V8 cho cả database mới và database đã có dữ liệu. Không sửa lại V3 đã áp dụng; V6/V7 và dữ liệu đơn hàng giữ nguyên. Trước khi sửa database đang dùng, lưu ảnh chụp bốn dòng `stores` gồm id/name/address/latitude/longitude/version để có thể phục hồi.

V9 chỉ sửa dòng còn đúng id, tên, địa chỉ và cặp tọa độ mẫu ban đầu. Chi nhánh đã được đổi địa chỉ/tên/tọa độ sẽ được giữ nguyên; không suy đoán vị trí mới. Mỗi dòng được sửa tăng `version` để bảo vệ chỉnh sửa đồng thời. Chạy lại V9 không dịch ghim lần nữa và không ghi đè chỉnh sửa về sau. Nếu số V9 đã thuộc migration khác, dừng để kiểm tra.

Sau khi áp dụng, tải lại web (Ctrl+F5 hoặc mở lại trang) để `/shop-api/public/bootstrap` đọc tọa độ mới từ SQL. Chỉ khởi động lại ứng dụng mà chưa áp dụng V9 không sửa được database cũ.
