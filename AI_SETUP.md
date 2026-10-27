# Gợi ý món bằng AI chạy trên máy

Trợ lý dùng Ollama và Qwen3, không gọi API trả phí, không cần API key hoặc tự train. Backend đọc nhu cầu bằng mô hình đã tải, rồi lọc món đang bán trong SQL theo chi nhánh. Giá và kcal được kiểm lại bằng hàm tính giá dùng ở giỏ hàng/checkout. Giới hạn calo là cả phần ăn theo cấu hình nguyên liệu, không phải calo phần nền.

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

5. Mở `http://localhost:8080/#assistant`, hoặc chọn “Gợi ý món với AI” ở thực đơn.

Mặc định `AI_ENABLED=true`, `OLLAMA_URL=http://127.0.0.1:11434`, `OLLAMA_MODEL=qwen3:4b-instruct`. Không cần chỉnh cấu hình nếu dùng đúng mô hình này. Muốn lưu lựa chọn trên máy, chạy `powershell -ExecutionPolicy RemoteSigned -File .\sql\Save-LocalConfig.ps1 -Section ai`; helper không yêu cầu key.

## Kiểm tra nhanh

- “Gà dưới 600 kcal, không cay”: món/nhân gà, cả phần ăn dưới 600 kcal.
- “Thèm bò, tối đa 100.000đ”: giá cấu hình không vượt ngân sách.
- “Ăn chay, ưu tiên ít calo”: món chay, nhân đậu hũ.
- “Bowl không cơm, không đậu”: mở cấu hình để thấy đúng lựa chọn được giữ lại.
- “Dưới 10 kcal”: báo không tìm được món, không tự nới giới hạn.
- Khi dừng Ollama: web/thực đơn vẫn dùng được, trợ lý báo chưa sẵn sàng.

Lần gọi đầu có thể chậm vì nạp mô hình vào RAM. Không đẩy tệp mô hình lên GitHub. Hai thành viên khác cần cài/tải mô hình trên máy họ để dùng AI. Khi demo bằng Cloudflared trên máy này, giữ Spring Boot và Ollama chạy; không mở cổng Ollama ra Internet.

API không tự thêm giỏ hoặc đặt đơn: khách xem cấu hình, chỉnh nếu muốn rồi tự thêm vào giỏ. Dữ liệu AI chỉ giữ trong phiên trang, không lưu lịch sử nhu cầu vào database. Trợ lý chưa khẳng định món an toàn với dị ứng vì dữ liệu phần nền chưa có bảng chất gây dị ứng đầy đủ.

Tài liệu: https://docs.ollama.com/capabilities/structured-outputs và https://ollama.com/library/qwen3:4b-instruct.
