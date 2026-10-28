# Nâng cấp chức năng hủy đơn

Migration mới của hủy đơn là **V8**, để V6/V7 dành cho dữ liệu thực đơn. Dùng `Apply-Schema.ps1`; không chạy V6 hủy đơn cho database mới.

Trong PowerShell tại thư mục dự án, xem kế hoạch trước rồi áp dụng lên đúng database:

```powershell
.\sql\Apply-Schema.ps1 -Server 'localhost,1433' -Database 'uteb'
.\sql\Apply-Schema.ps1 -Server 'localhost,1433' -Database 'uteb' -Apply
```

Mặc định dùng Windows Authentication. Nếu dùng SQL Authentication, đặt `DB_USER`, `DB_PASSWORD` riêng trong terminal và thêm `-Authentication sql`. Không commit mật khẩu.

- Database mới hoặc đang ở V5: helper chạy V1–V5 còn thiếu, rồi V8. Không cần V6/V7 để dùng hủy đơn.
- Đã có V6/V7 thực đơn: V8 bổ sung bảng hủy đơn, giữ lịch sử và dữ liệu thực đơn.
- Đã chạy V6 hủy đơn cũ: V8 kiểm tra cấu trúc bảng, giữ nguyên các yêu cầu hủy và lịch sử V6, rồi ghi nhận V8. Không xóa hay dựng lại bảng.
- Nếu lịch sử ghi đã có bảng nhưng bảng bị mất, cấu trúc sai, hoặc số V8 đã thuộc migration khác: dừng để kiểm tra/khôi phục. Không tự bỏ qua lỗi.

**Database đã có V6 hủy đơn cũ vẫn cần xử lý riêng khi nâng cấp nhánh thực đơn PR #12.** Helper cũ của PR #12 chỉ nhìn số phiên bản nên có thể bỏ qua V6 ảnh. Không coi việc chạy V8 hủy đơn là đã áp dụng dữ liệu thực đơn; PR #12 cần bản sửa tương ứng trước khi merge.

Các tệp V6 cũ được giữ nguyên để đối chiếu database đã chạy; helper mới không tự chạy chúng. Hai bản V8 tại `sql/` và `src/main/resources/db/migration/` phải giống nhau. Schema không tự cập nhật khi khởi động Spring Boot: áp dụng migration trước khi chạy ứng dụng đã cập nhật.

## Kiểm thử migration

```powershell
.\sql\Test-CancellationMigration.ps1 -Server 'localhost,1433'
```

Cần Windows login có quyền tạo database thử. Script chỉ tạo/xóa database tạm mang tiền tố ngẫu nhiên riêng, không sử dụng database `uteb`. Kiểm tra database mới, chạy lặp, giữ dữ liệu V6 cũ, từ chối schema sai/bảng mất/V8 bị chiếm.

Để kiểm tra cả hai thứ tự với migration thực đơn, thêm `-CatalogSqlDirectory` trỏ đến thư mục `sql` của nhánh PR #12 có V6 ảnh và V7 thực đơn.
