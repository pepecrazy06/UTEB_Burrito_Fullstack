# Gợi ý món bằng AI chạy trên máy

Trợ lý dùng Ollama và Qwen3, không gọi API trả phí, không cần API key hoặc tự train. Có thể hỏi theo món đang thèm, nhân gà/bò/heo/đậu hũ, nguyên liệu thêm hoặc muốn tránh, ăn cay, ăn no, ngân sách, nhóm người ăn, so sánh loại món, giờ cửa hàng và cách đặt hàng. Calo chỉ là một tiêu chí tùy chọn.

Backend đọc nhu cầu bằng mô hình đã tải, lọc món đang bán trong SQL theo chi nhánh, rồi tạo lời tư vấn dựa trên dữ liệu công khai của cửa hàng. Giá và kcal được kiểm lại bằng hàm tính giá dùng ở giỏ hàng/checkout. Không suy ra giới hạn calo từ số tiền. Khi hỏi so sánh loại món, dùng mô tả cách phục vụ đã biết, không tự khẳng định Bowl luôn ít calo hay ít no hơn Burrito.

## Chạy lần đầu trên mỗi máy

1. Cài Ollama Windows từ https://ollama.com/download/windows rồi mở terminal mới.
2. Tải mô hình khoảng 2,5 GB:

```powershell
ollama pull qwen3:4b-instruct
ollama list
```

3. Mở Ollama. Ứng dụng thường chạy nền ở `http://127.0.0.1:11434`; nếu chưa chạy, mở terminal riêng và dùng `ollama serve`.
4. Khởi động lại Spring Boot trong thư mục project:

```powershell
.\mvnw.cmd spring-boot:run
```

5. Bấm biểu tượng logo UTEB ở góc dưới bên phải để mở cửa sổ chat. Trang `http://localhost:8080/#assistant` cũng có nút mở cửa sổ. Trợ lý không chèn khối nội dung vào thực đơn và không thay đổi bố cục các trang cũ.

Cửa sổ dùng phong cách như phần chọn cửa hàng: logo và câu gợi ý ở bên trái, cuộc trò chuyện ở bên phải; điện thoại dùng một cột. Có thể soạn tin nhắn trong lúc AI chưa sẵn sàng. Kết nối được kiểm tra lại khi cửa sổ mở. Đóng/mở giữ lịch sử của phiên trang; đổi chi nhánh bắt đầu lại để tránh gợi ý nhầm cửa hàng. Bốn lượt gần nhất được gửi kèm cả câu hỏi, câu trả lời và tên món để hiểu câu tiếp nối. Enter gửi, Shift+Enter xuống dòng.

Mặc định `AI_ENABLED=true`, `OLLAMA_URL=http://127.0.0.1:11434`, `OLLAMA_MODEL=qwen3:4b-instruct`. Không cần chỉnh cấu hình nếu dùng đúng mô hình này. Muốn lưu lựa chọn trên máy, chạy `powershell -ExecutionPolicy RemoteSigned -File .\sql\Save-LocalConfig.ps1 -Section ai`; helper không yêu cầu key.

## Kiểm tra nhanh

- “Thèm gà, thêm phô mai, không cay, tối đa 120.000đ một phần”: chọn đúng nhân, có thêm phô mai, giá cấu hình trong ngân sách.
- Hỏi tiếp “Đổi sang bò, vẫn giữ phô mai”: đổi nhân, nhớ ngân sách và yêu cầu không cay của lượt trước.
- “Đang đói, muốn ăn no”: ưu tiên cấu hình có đủ nhân và các thành phần chính; không tự chuyển sang chế độ ít calo.
- “Hai người ăn trong tổng 200.000đ”: mỗi thẻ là một phương án gồm hai phần cùng món, không phải tổng cả ba thẻ. Hiện chưa lập giỏ hỗn hợp nhiều món cho từng người. Tổng giá chưa gồm phí giao/ưu đãi; nút chọn món giữ số lượng hai khi mở cấu hình.
- “Burrito và Bowl khác nhau thế nào?”: giải thích dạng cuộn bánh và dạng tô, kèm các món đúng loại đang bán.
- “Có Burrito trứng phô mai không?” hoặc “Cửa hàng mở đến mấy giờ, có giao hàng không?”: đối chiếu thực đơn/cấu hình cửa hàng, không tạo món hoặc giờ mở cửa mới.
- “Gà dưới 600 kcal, không cay”: cả phần ăn theo cấu hình dưới 600 kcal, không phải riêng phần nền.
- “Thèm bò, tối đa 100.000đ”: giá cấu hình không vượt ngân sách.
- “Ăn chay, ưu tiên ít calo”: món chay, nhân đậu hũ.
- “Bowl không cơm, không đậu”: mở cấu hình để thấy đúng lựa chọn được giữ lại.
- “Dưới 10 kcal”: báo không tìm được món, không tự nới giới hạn.
- Khi dừng Ollama: web/thực đơn vẫn dùng được, trợ lý báo chưa sẵn sàng.

Lần gọi đầu có thể chậm vì nạp mô hình vào RAM. Không đẩy tệp mô hình lên GitHub. Hai thành viên khác cần cài/tải mô hình trên máy họ để dùng AI. Khi demo bằng Cloudflared trên máy này, giữ Spring Boot và Ollama chạy; không mở cổng Ollama ra Internet.

API không tự thêm giỏ hoặc đặt đơn: khách xem cấu hình, chỉnh nếu muốn rồi tự thêm vào giỏ. Dữ liệu AI chỉ giữ trong phiên trang, không lưu lịch sử nhu cầu vào database. Nếu mô hình không tạo được đoạn trả lời, phần gợi ý đã kiểm giá/nguyên liệu vẫn được giữ lại. Trợ lý chưa khẳng định món an toàn với dị ứng hoặc đủ gram protein vì dữ liệu phần nền chưa đầy đủ; cần xác nhận trực tiếp với cửa hàng.

## Thông tin cửa hàng và ngữ cảnh dị ứng

Các câu hỏi thông tin phổ biến đọc trực tiếp dữ liệu công khai đang có: danh sách chi nhánh hoạt động, địa chỉ, giờ phục vụ, điện thoại, phương thức nhận món, nhà vận chuyển và mã ưu đãi còn hiệu lực của cửa hàng đang chọn. Không cần tự train lại trọng số mô hình để cập nhật những dữ liệu này; thay đổi ở trang quản trị được đọc trong lần hỏi tiếp theo. Phần tư vấn món vẫn dùng Ollama miễn phí.

- Hỏi “dị ứng bò”, sau đó “đưa ra danh sách các chi nhánh”: câu sau trả danh sách cửa hàng, không lặp cảnh báo dị ứng.
- Hỏi “Chi nhánh Quận 1 ở đâu, số điện thoại và giờ mở cửa?”: thông tin đúng chi nhánh được hỏi, kể cả đang chọn Võ Văn Ngân.
- Hỏi về phí giao, COD/VNPay, cách đặt món, OTP hoặc mã giảm giá: trả đúng chức năng đang có. Mã cá nhân không được đưa vào danh sách ưu đãi công khai.
- Với dị ứng, chỉ đối chiếu những tên/nhãn nguyên liệu và mô tả món hiện có. Phân biệt bò với bơ; không biến món khách muốn ăn ở lượt sau thành chất gây dị ứng mới. Giữ hạn chế đã khai báo cho câu hỏi về món tiếp theo, nhưng không chặn câu hỏi khác về cửa hàng.
- Nếu khách chưa nói dị ứng nguyên liệu nào, hỏi rõ một lần. Nếu nguyên liệu chưa có nhãn, ghi nhận đúng tên và hướng dẫn xác nhận trực tiếp với cửa hàng. Không khẳng định món còn lại an toàn khi dữ liệu phần nền/cách chuẩn bị chưa đầy đủ.

Tài liệu: https://docs.ollama.com/capabilities/structured-outputs và https://ollama.com/library/qwen3:4b-instruct.
