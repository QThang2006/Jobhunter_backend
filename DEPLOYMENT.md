# Deploy bản sửa BakeJobs

## Render: biến bắt buộc

Bản sửa loại bỏ credential viết cố định trong source. Trước khi deploy, đặt các biến sau trong Render → Environment; không đưa giá trị thật vào Git:

| Biến | Nội dung |
|---|---|
| `SPRING_DATASOURCE_URL` | JDBC PostgreSQL URL của Supabase; giữ tùy chọn pooler như `prepareThreshold=0` nếu đang dùng |
| `SPRING_DATASOURCE_USERNAME` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `JWT_BASE64_SECRET` | Khóa ngẫu nhiên tối thiểu 64 byte, mã hóa Base64 để ký HS512 |
| `SUPABASE_URL` | URL project Supabase |
| `SUPABASE_KEY` | API key có quyền storage phù hợp |
| `SPRING_MAIL_USERNAME` | Brevo SMTP username |
| `SPRING_MAIL_PASSWORD` | Brevo SMTP password |
| `GOOGLE_CLIENT_ID` | Google OAuth client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret |
| `N8N_INTERVIEW_MEET_WEBHOOK` | Production webhook của workflow phỏng vấn |

Không cần gửi giá trị secret vào chat. Các credential từng nằm trong repository cần được thay ở nhà cung cấp; xóa khỏi source mới không xóa lịch sử Git. Đổi JWT secret sẽ yêu cầu đăng nhập lại.

## Cấu hình tùy chọn

| Biến | Mặc định / mục đích |
|---|---|
| `FRONTEND_URL` | `https://jobhunter-fe-one.vercel.app`; URL gốc không có dấu `/` cuối |
| `CORS_ALLOWED_ORIGINS` | Origin Vercel trên và localhost/127.0.0.1 cổng 3000, 3001; danh sách phân cách bằng dấu phẩy |
| `AUTH_COOKIE_SECURE` | `true` cho production HTTPS; chỉ đặt `false` khi chạy local HTTP |
| `ABSTRACT_API_KEY` | Tùy chọn kiểm tra deliverability email; nếu thiếu thì chỉ kiểm tra định dạng email |
| `BOOTSTRAP_ADMIN_PASSWORD` | Chỉ dùng khi database chưa có user để tạo `admin@gmail.com`; database đã có dữ liệu không tạo lại admin |

Nếu đổi domain frontend, cập nhật cả `FRONTEND_URL` và `CORS_ALLOWED_ORIGINS`. Google Console phải cho phép callback `https://<backend-host>/login/oauth2/code/google`. Cookie refresh dùng `SameSite=None; Secure` khi chạy HTTPS; trình duyệt chặn cookie bên thứ ba vẫn có thể ảnh hưởng đăng nhập lâu dài giữa hai site.

## Thứ tự triển khai

1. Kiểm tra và lưu các biến Render nêu trên.
2. Deploy backend từ commit mới trên `main`; Dockerfile chạy `clean bootJar`. Xem log khởi động và kiểm tra `GET /` trả `OK`.
3. Trên Vercel, giữ `VITE_BACKEND_URL` trỏ về URL gốc backend; build command `npm run build`, output `dist`. Deploy frontend từ commit mới trên `main`.
4. Đăng nhập lại, thử hồ sơ cá nhân, thông báo, sửa/bật/tắt tin, ứng tuyển, phỏng vấn và phân ca bằng tài khoản phù hợp. Frontend mới dùng `GET/PUT /api/v1/users/me` nên cần backend mới trước.
5. Kiểm tra role HR có permissions theo URL/method cần thiết và company. Các API trước đây bỏ qua quyền nay thực thi quyền; tài khoản không có quyền sẽ nhận 403.

Không đổi schema bằng migration trong bản sửa này. Khi startup, initializer bổ sung role `USER` cho tài khoản chưa có role và đồng bộ permissions cho `SUPER_ADMIN`; mật khẩu/tài khoản hiện có được giữ nguyên.

## Kiểm thử trước deploy

```bash
./gradlew test bootJar
```

Tests dùng H2 trong bộ nhớ, không truy cập database production. Frontend có `npm test` và `npm run build`. OAuth, SMTP, Supabase thật và workflow n8n vẫn cần kiểm tra sau triển khai.
