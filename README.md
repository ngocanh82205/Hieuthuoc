# Hiếu Thuốc – Website nhà thuốc trực tuyến (Java Spring Boot)

Website bán thuốc có 3 tác nhân: **Khách hàng**, **Dược sĩ / Nhân viên** và **Admin**. Hệ thống làm theo đúng nghiệp vụ nhà thuốc GPP:
thuốc kê đơn phải được dược sĩ duyệt, không bán online thuốc kiểm soát đặc biệt, kho quản lý theo lô/hạn dùng và xuất theo FEFO.

## Công nghệ

| Thành phần | Công nghệ |
|---|---|
| Backend | Java 17+, Spring Boot 3.5 (Web MVC, Data JPA, Security, Validation) |
| Giao diện | Thymeleaf, Bootstrap 5, Bootstrap Icons, Chart.js (đóng gói qua WebJars, chạy offline được) |
| Cơ sở dữ liệu | H2 dạng file (mặc định, không cần cài) hoặc MySQL 8 |
| Khác | Lombok, BCrypt, JUnit 5 |

## Chạy dự án

Yêu cầu: **JDK 17 trở lên**. Không cần cài Maven vì dự án có sẵn Maven Wrapper.

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

Mở trình duyệt tại <http://localhost:8080>. Lần chạy đầu tiên, hệ thống tự tạo dữ liệu mẫu: 26 sản phẩm, lô hàng, khoảng 55 đơn hàng trong 28 ngày, đơn thuốc chờ duyệt, hội thoại tư vấn...

| Vai trò | Email | Mật khẩu |
|---|---|---|
| Admin | `admin@hieuthuoc.vn` | `admin123` |
| Dược sĩ | `duocsi@hieuthuoc.vn` | `duocsi123` |
| Khách hàng | `khachhang@gmail.com` | `123456` |

- Muốn làm lại dữ liệu mẫu từ đầu: dừng ứng dụng, xóa thư mục `data/` và `uploads/`, rồi chạy lại.
- Chạy test: `./mvnw test`
- Mở bằng IntelliJ IDEA: *Open* thư mục dự án, rồi chạy class `HieuThuocApplication`. Cần bật Annotation Processing cho Lombok.

### Dùng MySQL

```sql
CREATE DATABASE hieuthuoc CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Sửa user/password trong `src/main/resources/application-mysql.properties`, sau đó chạy:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql
```

Bảng được tự tạo khi chạy (`spring.jpa.hibernate.ddl-auto=update`).

## Chức năng theo tác nhân

### Khách hàng
- Xem, tìm kiếm sản phẩm theo tên, hoạt chất, công dụng. Lọc theo danh mục, loại thuốc, giá, còn hàng; sắp xếp.
- Trang chi tiết: hoạt chất, hàm lượng, số đăng ký, công dụng, liều dùng, chống chỉ định, sản phẩm **cùng hoạt chất**, đánh giá.
- Giỏ hàng (lưu theo session), mã giảm giá, giới hạn số lượng mỗi đơn.
- Đặt hàng: giao tận nơi hoặc nhận tại nhà thuốc; thanh toán COD hoặc online. Thanh toán online là **cổng giả lập** để demo.
- **Thuốc kê đơn**: bắt buộc tải ảnh đơn thuốc. Đơn chờ dược sĩ duyệt; nếu bị từ chối thì tải lại được.
- Theo dõi đơn theo dòng thời gian, hủy đơn khi chưa soạn hàng, **đổi/trả** trong N ngày, **mua lại** nhanh.
- Hồ sơ cá nhân, **hồ sơ sức khỏe** (dị ứng, bệnh nền, mang thai), sổ địa chỉ, điểm tích lũy, thông báo.
- **Chat tư vấn** với dược sĩ, gửi được ảnh.

### Dược sĩ / Nhân viên (`/staff`)
- Tổng quan công việc: đơn thuốc chờ duyệt, đơn cần xử lý, cảnh báo kho.
- **Duyệt đơn thuốc**: xem ảnh đơn cùng hồ sơ sức khỏe khách, chỉ được điều chỉnh giảm số lượng, ghi **sổ bán thuốc kê đơn** (bệnh nhân, bác sĩ, cơ sở khám), hoặc từ chối kèm lý do.
- **Xử lý đơn hàng**: Chờ xác nhận → Đã xác nhận → Đang chuẩn bị (**trừ kho theo FEFO, lưu số lô**) → Đang giao → Hoàn thành. Hủy đơn thì hoàn kho đúng lô. Xác nhận thanh toán, xử lý đổi/trả, in hóa đơn.
- **Kho theo lô**: tồn thực tế / giữ chỗ / khả dụng; cảnh báo cận hạn, hết hạn, dưới định mức; hủy thuốc, kiểm kê.
- **Thu hồi thuốc**: khóa lô, truy vết khách đã mua lô đó, gửi thông báo thu hồi.
- Tạo **phiếu nhập kho** (admin duyệt thì hàng mới vào kho).
- Tư vấn khách hàng (chat), kiểm duyệt đánh giá, viết bài "Góc sức khỏe".

### Admin (`/admin`, dùng được cả toàn bộ chức năng dược sĩ)
- Bảng điều khiển: doanh thu hôm nay/tháng, biểu đồ 14 ngày, trạng thái đơn, top bán chạy.
- **Báo cáo**: doanh thu, lợi nhuận gộp (giá vốn theo lô), theo ngày/danh mục, top sản phẩm, hàng tồn chậm, tỷ lệ hủy, **hiệu suất dược sĩ**, giá trị tồn kho; xuất CSV.
- Quản lý sản phẩm (phân loại OTC / kê đơn / kiểm soát đặc biệt / TPCN / dụng cụ / mỹ phẩm, ảnh, giới hạn mua), danh mục, nhà cung cấp, mã giảm giá.
- Quản lý nhân viên và phân quyền, số chứng chỉ hành nghề; khóa/mở khóa khách hàng, điều chỉnh điểm.
- Duyệt phiếu nhập; cấu hình hệ thống (thông tin pháp lý GPP, phí ship, ngưỡng cận hạn, thời hạn đổi trả...); nhật ký thao tác.

## Quy tắc nghiệp vụ đã cài đặt

1. Thuốc kê đơn không được thanh toán/xử lý khi chưa có dược sĩ duyệt đơn thuốc.
2. Thuốc kiểm soát đặc biệt (gây nghiện, hướng thần) **không bán online**.
3. Mã giảm giá chỉ tính trên phần hàng **không phải thuốc kê đơn**; admin không đặt giá khuyến mại được cho thuốc kê đơn.
4. Tồn kho: **giữ chỗ** khi đặt đơn, **trừ thật theo FEFO** khi soạn hàng, bỏ qua lô hết hạn hoặc bị khóa; hủy hoặc trả hàng thì hoàn về đúng lô.
5. Mỗi dòng đơn hàng lưu số lô đã xuất, phục vụ truy vết khi thu hồi.
6. Ảnh đơn thuốc và ảnh chat lưu **ngoài thư mục public**; chỉ chủ sở hữu hoặc dược sĩ/admin xem được.
7. Mọi thao tác quan trọng (duyệt đơn, đổi trạng thái, sửa giá, điều chỉnh kho...) đều ghi nhật ký.
8. Spring Security phân quyền theo vai trò, có CSRF, mật khẩu băm BCrypt; tài khoản bị khóa sẽ bị đăng xuất ngay.

## Cấu trúc mã nguồn

```
src/main/java/com/hieuthuoc
├── config/        # Security, dữ liệu mẫu (DataSeeder), xử lý lỗi, định dạng hiển thị
├── entity/        # Entity JPA + enum (Role, DrugType, OrderStatus...)
├── repository/    # Spring Data JPA
├── service/       # Nghiệp vụ: OrderService, StockService (FEFO), CartService, InventoryService, ReportService...
└── web/           # Controller: khách hàng | staff/ (dược sĩ) | admin/
src/main/resources
├── templates/     # Giao diện Thymeleaf (shop, account, consult, staff, admin)
└── static/        # CSS, JS
```

## Giới hạn và hướng phát triển
- Thanh toán online đang là cổng giả lập. Khi triển khai thật cần tích hợp API VNPay/MoMo (IPN).
- Chưa gửi email/SMS (hiện dùng thông báo trong hệ thống). Chat dùng polling 4 giây; có thể nâng cấp lên WebSocket.
- Mỗi sản phẩm bán theo 1 đơn vị tính; chưa quy đổi hộp/vỉ/viên.
- Chưa có bán tại quầy (POS), kết nối đơn vị vận chuyển hay liên thông Dược Quốc gia.
