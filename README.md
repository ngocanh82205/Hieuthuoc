# VinaPharma – Website nhà thuốc trực tuyến (Java Spring Boot)

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
- **Tìm kiếm:** theo tên thuốc, hoạt chất, công dụng, triệu chứng. Lọc theo danh mục, loại thuốc, **thương hiệu, xuất xứ, dạng bào chế**, giá và tình trạng còn hàng; có sắp xếp.
- **Trang chi tiết thuốc:** đầy đủ thông tin dược, **giá theo từng đơn vị tính** (VD: Vỉ và Hộp = 15 vỉ), thuốc cùng hoạt chất, đánh giá, **hỏi đáp với dược sĩ**, **yêu cầu dược sĩ gọi lại**.
- **Tài khoản:**
  - Đăng ký bằng **SĐT** (email không bắt buộc); đăng nhập bằng email hoặc SĐT.
  - **Quên mật khẩu:** khách gửi yêu cầu, admin gọi điện xác minh rồi cấp mật khẩu tạm.
  - Hồ sơ cá nhân, sổ địa chỉ, hồ sơ sức khỏe.
- **Giỏ hàng và thanh toán:**
  - Chọn đơn vị tính, áp mã giảm giá hoặc voucher trong kho, **dùng điểm tích lũy** để trừ tiền.
  - Thanh toán COD, **chuyển khoản có mã VietQR**, hoặc VNPay/MoMo (cổng giả lập).
- **Thuốc kê đơn:**
  - Bắt buộc tải ảnh đơn thuốc khi mua; dược sĩ duyệt hoặc từ chối, khách tải lại được.
  - **Gửi đơn thuốc mà không cần chọn sản phẩm:** dược sĩ lên đơn, gửi báo giá, khách xác nhận.
  - Dược sĩ có thể **đề xuất thuốc thay thế cùng hoạt chất**; khi đơn bị điều chỉnh, khách phải **xác nhận lại**.
- **Sau mua hàng:**
  - Theo dõi đơn; **hủy đơn được đến trước bước Đang giao** (tự hoàn kho, hoàn điểm).
  - Đổi trả, đánh giá, mua lại nhanh.
- **Chăm sóc khách hàng:**
  - **Hạng thành viên** Đồng / Bạc / Vàng / Kim cương, hạng càng cao càng được nhân nhiều điểm.
  - **Kho voucher**, **danh sách yêu thích**, **báo khi có hàng trở lại**.
  - **Nhắc lịch uống thuốc và nhắc mua lại:** thông báo hiện ngay trên web, có cửa sổ nhỏ nổi lên ở góc màn hình.
  - Chat tư vấn với dược sĩ (gửi được ảnh).

### Dược sĩ / Nhân viên (`/staff`)
- **Tư vấn (chat):**
  - Hội thoại mới được **tự phân cho dược sĩ đang online** ít việc nhất; dược sĩ có thể nhận, chuyển cho người khác hoặc đóng.
  - Hiện **cảnh báo an toàn**: dị ứng (kể cả nhóm chéo như penicillin/beta-lactam), bệnh nền, thai kỳ, trùng hoạt chất, tương tác thuốc; đối chiếu cả thuốc khách mua trong 90 ngày.
  - **Gửi giỏ hàng tư vấn** kèm lời dặn; khách bấm một nút là thêm cả giỏ.
  - Trả lời hỏi đáp sản phẩm, xử lý yêu cầu gọi lại.
- **Duyệt đơn thuốc:**
  - Hàng chờ xếp theo thời gian; xem ảnh đơn cùng hồ sơ sức khỏe và cảnh báo.
  - **Checklist bắt buộc**: đơn hợp lệ, còn hạn (mặc định 5 ngày, cấu hình được), có chữ ký/dấu, khớp thuốc.
  - Duyệt, giảm số lượng, hoặc **thay thuốc cùng hoạt chất và cùng hàm lượng**; từ chối bắt buộc có lý do.
  - Ghi **sổ bán thuốc kê đơn** kèm người duyệt và thời điểm.
- **Xử lý đơn hàng:**
  - Lọc theo trạng thái, ngày, phương thức thanh toán, kênh (online/POS).
  - Đơn **COD giá trị lớn** (mặc định ≥ 1.000.000 đ) phải **gọi xác minh** trước khi xác nhận.
  - **Soạn hàng**: hệ thống gợi ý lô theo FEFO, nhân viên có thể chọn lô khác hoặc quét/nhập số lô.
  - In **phiếu giao hàng**, **hóa đơn**, **hướng dẫn sử dụng**; nhập **đơn vị vận chuyển và mã vận đơn**.
  - Hủy đơn đã thanh toán / hoàn tiền / duyệt trả hàng cần quyền **Hoàn tiền**. Hàng trả không nhập lại kho được ghi vào **kho hủy**.
- **Bán tại quầy (POS):** dùng chung tồn kho với web, trừ kho theo FEFO; tìm khách theo SĐT để **cộng điểm**; thuốc kê đơn phải nhập thông tin đơn (ghi sổ); tính tiền thối.
- **Kho:** tồn theo lô (thực tế / giữ chỗ / khả dụng), cảnh báo cận hạn, hết hạn, dưới định mức; **kiểm kê** nhập số đếm thực tế (hệ thống ghi chênh lệch); hủy thuốc; **thu hồi** (khóa lô, truy vết khách, gửi thông báo); tạo phiếu nhập.
- **Nội dung:** viết bài "Góc sức khỏe", sửa **thông tin chuyên môn sản phẩm**, kiểm duyệt đánh giá và hỏi đáp.

**Phân quyền nhân viên** (admin tick trong trang sửa nhân viên; admin luôn có toàn quyền):

| Quyền | Cho phép |
|---|---|
| Hoàn tiền | Hủy đơn đã thanh toán, duyệt trả hàng/hoàn tiền |
| Duyệt phiếu nhập | Duyệt phiếu nhập kho |
| Điều chỉnh kho | Điều chỉnh tăng tồn, kiểm kê ghi tăng |
| Bán tại quầy | Dùng màn hình POS |
| Nội dung | Bài viết, thông tin sản phẩm, kiểm duyệt đánh giá |

Tài khoản mẫu `duocsi@hieuthuoc.vn` có đủ các quyền; `duocsi2@hieuthuoc.vn` (mật khẩu `duocsi123`) chỉ có quyền Bán tại quầy và Nội dung.

### Admin (`/admin`, dùng được cả toàn bộ chức năng dược sĩ)
- Bảng điều khiển: doanh thu hôm nay/tháng, biểu đồ 14 ngày, trạng thái đơn, top bán chạy.
- **Báo cáo**: doanh thu, lợi nhuận gộp (giá vốn theo lô), theo ngày/danh mục, top sản phẩm, hàng tồn chậm, tỷ lệ hủy, **hiệu suất dược sĩ**, giá trị tồn kho; xuất CSV.
- Quản lý sản phẩm (phân loại OTC / kê đơn / kiểm soát đặc biệt / TPCN / dụng cụ / mỹ phẩm, ảnh, giới hạn mua), danh mục, nhà cung cấp, mã giảm giá.
- Quản lý nhân viên và phân quyền, số chứng chỉ hành nghề; khóa/mở khóa khách hàng, điều chỉnh điểm.
- Duyệt phiếu nhập; cấu hình hệ thống (thông tin pháp lý GPP, phí ship, ngưỡng cận hạn, thời hạn đổi trả, hạn đơn thuốc, ngưỡng gọi xác minh COD...); nhật ký thao tác.

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

> **Nâng cấp từ bản cũ:** cấu trúc dữ liệu đã thay đổi. Nếu đã chạy bản trước, hãy xóa thư mục `data/` và `uploads/` trước khi chạy lại.

## Giới hạn và hướng phát triển
- Thanh toán online đang là cổng giả lập. Khi triển khai thật cần tích hợp API VNPay/MoMo (IPN).
- Chưa có OTP và chưa gửi email/SMS vì cần dịch vụ gửi thật; hiện dùng thông báo ngay trong web. Chat tự cập nhật mỗi 4 giây (polling); có thể nâng cấp lên WebSocket.
- Mã vận đơn nhập tay, chưa kết nối API đơn vị vận chuyển; chưa liên thông Dược Quốc gia. Trạng thái online của dược sĩ tính theo hoạt động trong 5 phút gần nhất.
