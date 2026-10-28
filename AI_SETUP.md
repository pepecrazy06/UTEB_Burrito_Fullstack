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

Tài liệu: https://docs.ollama.com/capabilities/structured-outputs và https://ollama.com/library/qwen3:4b-instruct.
