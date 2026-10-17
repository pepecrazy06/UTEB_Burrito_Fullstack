# Chạy bản dùng chung trên SQL Server

## Trạng thái và phạm vi

Mặc định `sqlserver`, `demo=false`, `seed=false`, Hibernate `validate`, SQL init `never`.
Ứng dụng không tự chạy migration và không fallback sang H2 khi SQL Server lỗi.
Profile `demo` chỉ dùng khi chọn rõ; test dùng H2 trong bộ nhớ.

Nhánh `feat/sqlserver-runtime` đã fast-forward lên `origin/main` tại `218a9ab`,
giữ nguyên giao diện/ngày sinh từ main. Không sửa Cart, Checkout, Order hoặc Payment.
Tài liệu ở gốc vì `docs/` và `README.md` bị ignore; không thay quy tắc ignore.

Môi trường đã kiểm tra ngày 18/10/2026:

| Thành phần | Giá trị |
| --- | --- |
| Java | Test/JDBC: JDK 21.0.10 x64; web nghiệm thu: JDK 21.0.12.1 x64 |
| Spring Boot | 3.5.6 |
| JDBC | Microsoft 12.10.1.jre11 (dùng được với JDK 21) |
| SQL Server | 17.0.1000.7, Standard Developer Edition 64-bit |
| Server / instance | localhost / MSSQLSERVER (default instance) |
| TCP | 1433, đã kiểm tra hoạt động |
| Database | UTE_Burrito |
| Collation | Latin1_General_100_CI_AS_SC_UTF8 |
| Xác thực local | Windows Authentication của tài khoản chạy Java |

JDBC thật bằng JDK 21 đã SELECT thành công đúng database sau khi nạp DLL.
V1–V4 đã chạy sau phê duyệt: 24 bảng gồm history, `accounts.birth_date` là nullable date.
Ngay sau migration có 0 accounts và 0 orders; V3 chỉ chứa catalog mẫu.
Trạng thái nghiệm thu web, restart và Git được ghi riêng trong `SQLSERVER_ACCEPTANCE.md`.
Không coi test H2 là bằng chứng web ghi SQL Server.

## Biến và xác thực

`.env.example` chỉ là mẫu. Dự án KHÔNG tự nạp `.env`; đặt biến trong terminal hoặc
IntelliJ Run Configuration. Biến đặt trong terminal không truyền ngược vào nút Run.

| Biến | Ý nghĩa |
| --- | --- |
| SPRING_PROFILES_ACTIVE | sqlserver, cũng là mặc định |
| DB_AUTH | windows hoặc sql; mặc định sql |
| DB_URL | URL JDBC bắt buộc, trỏ đúng instance/database |
| DB_USER / DB_PASSWORD | Chỉ bắt buộc với DB_AUTH=sql; bỏ khi dùng windows |
| JWT_SECRET | Chuỗi riêng ngẫu nhiên ít nhất 32 ký tự |
| COOKIE_SECURE | false với HTTP local, true với HTTPS |
| MAIL_ENABLED | false trong đợt chuyển database |
| ADMIN_EMAIL / ADMIN_PASSWORD | Chỉ dùng khởi tạo khi accounts trống |

Windows Authentication local:

```text
jdbc:sqlserver://localhost:1433;databaseName=UTE_Burrito;integratedSecurity=true;authenticationScheme=NativeAuthentication;encrypt=true;trustServerCertificate=true
```

SQL Authentication (máy thành viên có thể dùng login SQL riêng):

```text
jdbc:sqlserver://SQL_HOST:1433;databaseName=UTE_Burrito;encrypt=true;trustServerCertificate=true
```

Với SQL Authentication đặt DB_AUTH=sql, DB_USER và DB_PASSWORD riêng. Không đưa mật
khẩu vào URL hoặc file cấu hình chia sẻ. Máy khác phải thay localhost bằng hostname/IP
máy SQL Server; Windows Authentication dùng tài khoản Windows của máy chạy Java,
không mặc định được cấp quyền trên server khác. Nhóm phải thống nhất quyền kết nối.
`trustServerCertificate=true` dùng cho chứng chỉ tự ký trong môi trường phát triển.

## Leader cấu hình trên máy khác

Leader chọn rõ một trong ba cách dưới đây, không sao chép secret hoặc Run Configuration
cá nhân của người khác. `localhost` luôn là máy đang chạy Java, không phải máy của Khang.

| Cách chạy | Cấu hình và điều kiện |
| --- | --- |
| SQL Server cài trên chính máy leader | DB_AUTH=windows, URL localhost:1433, UTE_Burrito; cấp quyền database cho tài khoản Windows đang chạy IntelliJ/Java trên máy leader; cần DLL x64 khớp driver |
| Kết nối server nhóm bằng SQL login | DB_AUTH=sql, URL dùng hostname/IP server thật; DB_USER/DB_PASSWORD do quản trị server cấp riêng; không cần DLL Windows Authentication |
| Kết nối server nhóm bằng Windows Authentication | DB_AUTH=windows, URL dùng hostname/IP server thật; quản trị server phải cấp quyền cho danh tính Windows/domain được SQL Server nhận diện; tài khoản local trên máy leader không tự có quyền từ xa |

Trước khi chạy, leader xác nhận với người quản trị: hostname, cổng TCP, instance,
tên database, phương thức xác thực và quyền đọc/ghi của ứng dụng. SQL login cần
server cho phép SQL Server Authentication; chỉ quản trị server cấu hình/cấp login
qua kênh riêng. Không tạo login hoặc thay cấu hình server bằng script của PR này.
Máy chủ dùng chung cần TCP/IP và đường mạng/firewall cho phép đúng cổng đã thống nhất.

Trình tự trên máy leader:

1. Cài JDK 21 x64, chọn đúng Project SDK/JRE trong IntelliJ. Lấy source có phần
   chuyển SQL Server, chạy `.\mvnw.cmd package` để có driver và JAR local.
2. Kiểm tra database đích trong SSMS. Database nhóm có V1–V4 thì không chạy lại
   migration, không tạo ADMIN mới. Nếu mới hoàn toàn, xin phép khởi tạo database
   với UTF8, dùng runner kiểm tra kế hoạch rồi mới áp dụng schema theo mục migration.
   Không chạy thẳng script `00_create_database.sql` cũ vì nó chọn tên `uteb`.
3. Đặt biến riêng trong Run Configuration hoặc terminal. Với SQL login, ví dụ:

   ```powershell
   $env:SPRING_PROFILES_ACTIVE = 'sqlserver'
   $env:DB_AUTH = 'sql'
   $env:DB_URL = 'jdbc:sqlserver://SQL_HOST:1433;databaseName=UTE_Burrito;encrypt=true;trustServerCertificate=true'
   $env:DB_USER = Read-Host 'SQL login duoc cap'
   $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'SQL password' -AsSecureString)).Password
   $env:JWT_SECRET = [System.Net.NetworkCredential]::new('', (Read-Host 'JWT secret >=32 ky tu' -AsSecureString)).Password
   $env:COOKIE_SECURE = 'false'
   $env:MAIL_ENABLED = 'false'
   ```

   Thay SQL_HOST bằng máy chủ thật. Nếu dùng HTTPS đặt COOKIE_SECURE=true.
   Với Windows Authentication dùng URL integratedSecurity/NativeAuthentication,
   bỏ DB_USER/DB_PASSWORD và nạp DLL như phần dưới. Cấu hình chứng chỉ theo nhóm;
   trustServerCertificate=true chỉ dành cho môi trường phát triển dùng chứng chỉ tự ký.
4. Chạy `sql/Check-Connection.java` theo mục kiểm tra JDBC. Đối chiếu server và
   DB_NAME() với cửa sổ SSMS. Kiểm tra migration history bằng SELECT và kế hoạch
   `Apply-Schema.ps1 -Server 'SQL_HOST,1433' -Database UTE_Burrito` không có -Apply.
5. Chạy `.\mvnw.cmd spring-boot:run` hoặc main class trong IntelliJ bằng chính các
   biến đã đặt. Chờ Hibernate validate và Started, rồi đăng nhập ADMIN có sẵn.
   Chỉ nhập biến ADMIN để khởi tạo khi accounts trống và đã được phép.
6. Leader thực hiện lại kết nối và nghiệm thu SQLTEST có nhãn riêng sau khi được
   phép, ghi kết quả server/database/ID/giá trước-sau/restart, không chụp secret.

**Không dùng `Start-LocalSqlServer.ps1` cho server dùng chung hoặc SQL login**:
helper này cố định Windows Authentication, localhost:1433 và UTE_Burrito, đồng thời
ghi đè DB_URL/DB_AUTH và bỏ DB_USER/DB_PASSWORD. Dùng Run Configuration hoặc lệnh
Maven ở trên để giữ cấu hình server nhóm. Việc leader tái lập trên máy khác chưa
được kiểm chứng trong phiên nghiệm thu local của Khang.

## DLL cho Windows Authentication

Tải `mssql-jdbc_auth.zip` từ bản phát hành Microsoft chính thức:
https://github.com/microsoft/mssql-jdbc/releases/tag/v12.10.1

Giải nén và dùng `x64/mssql-jdbc_auth-12.10.1.x64.dll`. Phiên bản DLL phải khớp driver,
kiến trúc phải khớp JVM x64. Đợt kiểm tra này DLL nằm trong
`target/jdbc-auth-12.10.1/x64` (bị Git ignore). Maven clean sẽ xóa target;
nên lưu bản dùng lâu dài ở thư mục riêng, ví dụ `C:\sqljdbc\12.10.1\x64`.
Không chép DLL vào thư mục hệ thống hoặc commit vào Git.

IntelliJ: Run > Edit Configurations > chọn UtebApplication, JRE 21, đặt biến ở bảng
trên và thêm VM options `-Djava.library.path=C:\sqljdbc\12.10.1\x64`.
Với Maven Run Configuration, truyền đường dẫn qua JVM của ứng dụng hoặc PATH;
chỉ đặt VM option cho Maven không đảm bảo JVM Spring Boot con nhận được.

PowerShell trong thư mục chứa pom.xml:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;C:\sqljdbc\12.10.1\x64;$env:Path"
$env:SPRING_PROFILES_ACTIVE = 'sqlserver'
$env:DB_AUTH = 'windows'
$env:DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=UTE_Burrito;integratedSecurity=true;authenticationScheme=NativeAuthentication;encrypt=true;trustServerCertificate=true'
Remove-Item Env:DB_USER, Env:DB_PASSWORD -ErrorAction SilentlyContinue
$env:COOKIE_SECURE = 'false'
$env:MAIL_ENABLED = 'false'
$env:JWT_SECRET = [System.Net.NetworkCredential]::new('', (Read-Host 'JWT secret >=32 ky tu' -AsSecureString)).Password
java -version
```

Có thể lưu DB_AUTH, DB_URL, SPRING_PROFILES_ACTIVE trong **Edit environment variables
for your account**, rồi mở lại IntelliJ/terminal. Không cần lưu password cho Windows auth.

## Kiểm tra kết nối trước khi khởi động

Các lệnh dưới đây chỉ SELECT, không khởi chạy bootstrap/scheduler:

```powershell
sqlcmd -S tcp:localhost,1433 -d UTE_Burrito -E -C -l 5 -b -Q "SELECT DB_NAME(), ORIGINAL_LOGIN();"
$driver = "$env:USERPROFILE\.m2\repository\com\microsoft\sqlserver\mssql-jdbc\12.10.1.jre11\mssql-jdbc-12.10.1.jre11.jar"
java '-Djava.library.path=C:\sqljdbc\12.10.1\x64' --class-path $driver sql/Check-Connection.java
```

Helper JDBC đọc DB_URL/DB_AUTH và, khi dùng SQL login, DB_USER/DB_PASSWORD từ môi trường.
Nó in server/database/login và phiên bản driver, không in mật khẩu.

Nếu TCP chưa bật: Configuration Manager > Protocols for MSSQLSERVER > TCP/IP Enabled,
IPAll TCP Port 1433, bỏ Dynamic Ports khi dùng cổng tĩnh. Restart SQL Server khi được
phép vì sẽ ngắt kết nối hiện tại. Kết nối sqlcmd qua Shared Memory chưa chứng minh TCP/JDBC.

## Migration thủ công có kiểm tra

Nguồn chính là `sql/`; các bản sao V1–V4 trong resources không tự chạy lúc startup.
Chỉ giữ một tên V4: `V4__account_birth_date.sql`; bản resources khớp nội dung.
V4 thêm `birth_date date NULL`, kiểm tra V1–V3/history, không sửa hàng dữ liệu cũ.
Nếu V4 đã có history thì runner không chạy lại.

Chọn đúng database trong SSMS và kiểm tra trước:

```sql
SELECT @@SERVERNAME AS server_name, DB_NAME() AS database_name;
SELECT name, collation_name FROM sys.databases WHERE name=DB_NAME();
SELECT name FROM sys.tables;
-- Chỉ khi history đã tồn tại:
SELECT version_no,name,applied_at FROM dbo.schema_migrations ORDER BY version_no;
```

Chạy kế hoạch không ghi:

```powershell
.\sql\Apply-Schema.ps1 -Server 'localhost,1433' -Database UTE_Burrito
```

Runner mở chính database được chỉ định, chỉ loại câu USE uteb trong nội dung thực thi
của script cũ; không sửa V1–V3 trong Git, không chạy `00_create_database.sql`.
Nó dừng nếu có bảng mà thiếu history, history V1–V3 không đầy đủ, hoặc database mới
không dùng UTF8. Không tự dựng lại database hoặc thay collation.

- Database mới trống: sau khi được phép, xác nhận UTF8 trước V1, chạy V1→V2→V3→V4.
- Database đã có V1–V3: chỉ V4 còn thiếu, giữ nguyên dữ liệu/catalog.
- Database đã có V1–V4: không chạy gì.
- Database lỡ chạy một phần: kiểm kê và xử lý riêng; không xóa dữ liệu để chạy lại.

Chỉ sau phê duyệt ghi schema/catalog:

```powershell
.\sql\Apply-Schema.ps1 -Server 'localhost,1433' -Database UTE_Burrito -Apply
```

Với SQL login thêm `-Authentication sql`; runner dùng DB_USER/DB_PASSWORD riêng.
Các batch chạy cùng connection và transaction, rollback toàn bộ nhóm đang áp dụng
nếu lỗi. Script V1–V3 lịch sử có hardcode uteb: không mở chạy thẳng chúng trong SSMS
trên UTE_Burrito; dùng runner có chọn đích rõ ràng hoặc rà soát bản thực thi thủ công.
Database cũ không dùng UTF8 cần kế hoạch chuyển Unicode riêng, không tự đổi collation.

## Khởi tạo ADMIN và chạy ứng dụng

BootstrapAdmin hiện có chỉ tạo một ADMIN đã xác thực khi demo=false và accounts
hoàn toàn trống. Mật khẩu tối thiểu 8 ký tự được bcrypt trước khi lưu.
Có tài khoản rồi thì biến ADMIN không tạo thêm, không thay mật khẩu hoặc role.
Không tạo hàng loạt tài khoản. Xin phép trước khi khởi tạo ADMIN thật:

```powershell
$env:ADMIN_EMAIL = Read-Host 'Email ADMIN'
$env:ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'ADMIN password' -AsSecureString)).Password
.\mvnw.cmd spring-boot:run
```

Sau lần đầu bỏ ADMIN_EMAIL/ADMIN_PASSWORD khỏi cấu hình thường ngày. Hibernate
validate thiếu bảng/cột phải dừng; không đổi sang update/create để né lỗi.
Scheduler PaymentExpiry vẫn là nghiệp vụ hiện có, có thể cập nhật đơn hết hạn sau
khi app chạy; không dùng khởi động app làm kiểm tra chỉ đọc trên database có đơn thật.

Máy local trong phiên nghiệm thu có thể dùng helper đã chọn cố định
`localhost:1433 / UTE_Burrito`, JDK và DLL từ thư mục dự án:

```powershell
.\sql\Start-LocalSqlServer.ps1 -BootstrapAdmin
# Sau khi đã có ADMIN, Ctrl+C rồi chạy lại không cần nhập password khởi tạo:
.\sql\Start-LocalSqlServer.ps1
```

Helper hỏi email và password bằng Read-Host -AsSecureString, chỉ truyền password
qua môi trường tiến trình Java và xóa biến ADMIN khi tiến trình kết thúc. Không
lưu password/JWT vào file và không in ra màn hình. JWT được tạo ngẫu nhiên trong
bộ nhớ nếu chưa có biến; mở terminal khác sẽ cần JWT mới hoặc biến JWT riêng.
Có thể truyền `-JavaHome` và `-NativeAuthDirectory` nếu lưu JDK/DLL ở chỗ khác.
Mật khẩu ADMIN do người dùng tự nhập; Codex không cần biết mật khẩu. Người dùng
tự đăng nhập trên tab nghiệm thu trước khi Codex thao tác sản phẩm.
Log ứng dụng được ghi tại `target/sqlserver-acceptance-startup.log` (Git ignore),
không ghi prompt/password; tắt riêng cảnh báo mật khẩu security mặc định không dùng.

SeedData có chốt demo và enabled: bản SQL Server không sinh catalog, tài khoản,
đơn, điểm thưởng hay lịch sử. V3 là catalog thử (ảnh/giá/tọa độ/voucher ước tính)
được nạp thủ công một lần khi khởi tạo database trống. Không chạy lại V3.
Không xóa file H2, không tự sao chép demo H2; dữ liệu nhập tay trong H2 chưa kiểm kê.

## Nghiệm thu web và bàn giao

Sau khi được phép tạo ADMIN/dữ liệu SQLTEST:

1. Chạy app, xác nhận sqlserver, demo=false và Hibernate validate thành công.
2. Đăng nhập ADMIN, tạo sản phẩm `SQLTEST-<người>-<thời điểm>` qua quản trị hiện có.
3. Trong SSMS SELECT đúng server/database và sản phẩm vừa tạo.
4. Sửa sản phẩm thử qua web, SELECT lại giá và version.
5. Dừng/chạy lại app cùng biến, xác nhận dữ liệu còn trên web và SQL.
6. So sánh hash/thời điểm `data/uteb.mv.db` trước/sau; không thay đổi.
7. Thành viên khác cấu hình bằng biến riêng và thực hiện lại các bước kết nối.

```sql
SELECT id,name,store_id,price,stock,version
FROM dbo.products WHERE name LIKE 'SQLTEST-%' ORDER BY id DESC;
```

Không dùng INSERT SQL thay bằng chứng web. Không sửa/xóa sản phẩm thật để kiểm thử.
Giữ nhãn SQLTEST rõ ràng; nếu muốn vô hiệu hóa cũng cần phạm vi đã được đồng ý.

## Test và lỗi thường gặp

```powershell
.\mvnw.cmd test
git diff --check
```

Full suite sau tích hợp main: 240 test, 0 lỗi (204 Commerce, 9 Birthday, 27 cấu hình/
SeedData/BootstrapAdmin). Các test Spring Boot chọn demo và H2 in-memory riêng.

- Connection refused/timeout: kiểm tra TCP, port và instance, không fallback H2.
- Not configured for integrated authentication: kiểm tra DLL đúng phiên bản/x64
  và java.library.path/PATH của chính tiến trình Java.
- Login failed: xác nhận tài khoản Windows/SQL được cấp quyền trên đúng database.
- Thiếu DB_USER/DB_PASSWORD: dùng DB_AUTH=windows khi chọn Windows Authentication.
- JWT secret ngắn: đặt secret riêng >=32 ký tự.
- HTTP login không giữ phiên: COOKIE_SECURE=false khi chạy local HTTP.
- Thiếu bảng/cột: chạy đúng migration còn thiếu sau phê duyệt, giữ validate.
- Nếu Tomcat báo `Invalid argument: connect` tại `UnixDomainSockets/PipeImpl` khi
  chạy từ công cụ Codex nhưng JDBC đã kết nối được: chạy helper trực tiếp trong
  IntelliJ Terminal/PowerShell của người dùng. Phiên này đã gặp hạn chế đó;
  không đổi schema hoặc nới cấu hình SQL Server để xử lý lỗi socket JVM này.

OTP/email thật, VNPay/MoMo/refund, Cloudinary/upload, bản đồ và hoàn thiện nghiệp vụ
ngoài phạm vi đợt này. Mật khẩu SMTP cũ đã bỏ khỏi properties; chủ tài khoản cần
thu hồi/đổi secret đã xuất hiện trong lịch sử Git. Không sửa lịch sử Git trong đợt này.

Commit dự kiến `config: switch shared runtime to SQL Server`; chỉ stage các file
chuyển database, không stage target/, data/, DLL hoặc secret. Giữ danh tính Git của
người thực hiện. Commit, push và PR vào main chỉ thực hiện sau phê duyệt riêng.
