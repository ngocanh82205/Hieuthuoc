# VinaPharma – Website nhà thuốc trực tuyến (Java Spring Boot)

Website bán thuốc có 3 tác nhân: **Khách hàng**, **Dược sĩ / Nhân viên** và **Admin**. Hệ thống làm theo đúng nghiệp vụ nhà thuốc GPP:
thuốc kê đơn phải được dược sĩ duyệt, không bán online thuốc kiểm soát đặc biệt, kho quản lý theo lô/hạn dùng và xuất theo FEFO.

## Công nghệ

| Thành phần | Công nghệ |
|---|---|
| Backend | Java 17+, Spring Boot 3.5 (Web MVC, Data JPA, Security, Validation) |
| Giao diện | Thymeleaf, Bootstrap 5, Bootstrap Icons, Chart.js (đóng gói qua WebJars, chạy offline được) |
| Cơ sở dữ liệu | H2 dạng file (mặc định, không cần cài) hoặc MySQL 8 |
| Khác | Lombok, BCrypt, Apache POI (Excel), JUnit 5 |

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
| Dược sĩ quản lý | `duocsi@hieuthuoc.vn` | `duocsi123` |
| Dược sĩ | `duocsi2@`, `duocsi3@`, `duocsi4@hieuthuoc.vn` | `duocsi123` |
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
  - **Chat tư vấn: trợ lý AI tiếp nhận trước**, chuyển dược sĩ khi cần (xem mục Trợ lý AI bên dưới). Gửi được ảnh.

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

**Phân quyền theo vai trò** (admin cấu hình tại Quản trị > Vai trò & phân quyền; admin luôn có toàn quyền):

| Quyền | Cho phép |
|---|---|
| Duyệt đơn thuốc | Duyệt / từ chối đơn thuốc, sổ thuốc kê đơn (bắt buộc có CCHN) |
| Đơn hàng | Xử lý đơn online: xác nhận, soạn hàng, giao hàng |
| Tư vấn / CSKH | Chat, hỏi đáp sản phẩm, yêu cầu gọi lại |
| Kho | Tồn kho, lô, phiếu nhập, kiểm kê, phiếu hủy, chuyển kho |
| Duyệt phiếu kho | Duyệt phiếu nhập, phiếu hủy, điều chỉnh kiểm kê |
| Bán tại quầy | Màn hình POS |
| Hoàn tiền | Hủy đơn đã thanh toán, đổi trả, duyệt hoàn tiền |
| Nội dung | Bài viết, thông tin sản phẩm, kiểm duyệt đánh giá |

Tài khoản mẫu (tất cả là dược sĩ có chứng chỉ hành nghề): `duocsi@` là **Dược sĩ quản lý** (đủ quyền, gồm duyệt phiếu kho, hoàn tiền, nội dung); `duocsi2@`, `duocsi3@`, `duocsi4@` là **Dược sĩ** (duyệt đơn thuốc, đơn hàng, tư vấn, bán quầy, kho). Mật khẩu đều là `duocsi123`.

### Admin (`/admin`, dùng được cả toàn bộ chức năng dược sĩ)
- **Người dùng & phân quyền (RBAC):**
  - Nhân viên: thêm/sửa, số CCHN, bằng cấp, ca làm; khóa tài khoản khi nghỉ việc (giữ lịch sử để truy vết).
  - **Vai trò** (Dược sĩ quản lý, Dược sĩ, Biên tập viên... thêm được vai trò mới) với **ma trận quyền theo chức năng**; có thể cấp thêm quyền riêng cho từng người.
  - Quyền được kiểm tra ở cả menu, đường dẫn và nghiệp vụ. Người có quyền duyệt đơn thuốc bắt buộc có CCHN.
  - Khách hàng: xem, khóa/mở khóa, lịch sử mua, điều chỉnh điểm.
  - **Nhật ký hoạt động** lọc theo nhân viên, nhóm hành động, nội dung, khoảng ngày.
- **Danh mục & sản phẩm:**
  - **Danh mục đa cấp** (VD: Thuốc > Tim mạch - Huyết áp > Thuốc huyết áp); lọc danh mục cha gồm cả sản phẩm của danh mục con.
  - Sản phẩm: thông tin dược, loại thuốc, ảnh, đa đơn vị tính, **SEO** (đường dẫn, tiêu đề, mô tả).
  - Danh mục **hoạt chất**, **thương hiệu / nhà sản xuất** (đổi tên tự cập nhật sản phẩm), nhà cung cấp.
  - Cấu hình **thuốc tương đương** (được phép thay khi duyệt đơn) và **quy tắc cảnh báo tương tác thuốc**.
  - **Nhập / xuất sản phẩm bằng Excel** (.xlsx; file có lỗi sẽ không nhập dòng nào).
- **Kho:**
  - Duyệt **phiếu nhập, phiếu hủy, phiếu điều chỉnh kiểm kê** (nhân viên kho lập phiếu, admin / quản lý duyệt mới đổi tồn).
  - **Nhiều kho / chi nhánh** và **phiếu chuyển kho** (kho dự trữ không tính vào hàng bán).
  - **Định mức tồn** hàng loạt, ngưỡng cảnh báo cận hạn.
  - **Công nợ nhà cung cấp**: hạn thanh toán, nợ quá hạn, phiếu chi.
- **Đơn hàng:** xem và can thiệp mọi đơn, **phân công** nhân viên xử lý, **duyệt hoàn tiền** (đơn đã thanh toán bị hủy / trả chuyển sang "Chờ hoàn tiền"), cấu hình chính sách đổi trả (mặc định không đổi trả thuốc kê đơn).
- **Marketing:**
  - Mã giảm giá theo **đối tượng**: hạng thành viên, khách mới, danh mục, số lần / khách.
  - **Flash sale** (giá sốc, số suất, đếm ngược), **combo**, **mua X tặng Y**. Chỉ áp dụng OTC / TPCN / dụng cụ / mỹ phẩm, **không khuyến mãi thuốc kê đơn**.
  - Cấu hình **tích điểm và ngưỡng / hệ số hạng thành viên**.
  - **Banner** trang chủ, **trang tĩnh** (chính sách đổi trả, giao hàng, bảo mật...), **gửi thông báo hàng loạt** theo nhóm khách.
- **Cấu hình hệ thống:**
  - Thông tin pháp lý nhà thuốc (hiện ở chân trang).
  - Bật/tắt phương thức thanh toán, danh sách đơn vị vận chuyển.
  - **Khu vực giao hàng & biểu phí theo tỉnh/thành** (34 tỉnh, thành).
  - Mẫu nội dung thông báo gửi khách.
  - **VAT & hóa đơn điện tử**: khách yêu cầu xuất HĐ VAT khi đặt hàng, nhân viên ghi số HĐĐT; hóa đơn in có tách thuế.
  - **Sao lưu dữ liệu** (tải về; tự sao lưu 2h30 hằng ngày, giữ 7 bản).
- **Báo cáo:**
  - Doanh thu theo ngày, tháng, **kênh (online / quầy)**, danh mục, **nhân viên**.
  - Bán chạy, tồn chậm, giá trị tồn, **hàng cận hạn / hết hạn**, lợi nhuận gộp theo giá vốn từng lô.
  - Hiệu suất dược sĩ (số đơn duyệt, thời gian duyệt TB, tỷ lệ từ chối, số cuộc tư vấn).
  - Tỷ lệ hủy / trả, **khách mới / quay lại**.
  - **Xuất Excel** (nhiều sheet), **bản in / PDF**, **file dữ liệu liên thông Dược Quốc gia** (bán ra theo lô kèm thông tin đơn thuốc, nhập vào).

## Trợ lý AI (chat trước khi gặp dược sĩ)

Khách mở trang Tư vấn thì **trợ lý AI trả lời trước**:
- Tra cứu đơn hàng của chính khách, phí giao hàng theo tỉnh, đổi trả, thanh toán, khuyến mãi.
- Thông tin sản phẩm **không kê đơn** (giá, còn hàng, công dụng).
- Hỏi sàng lọc triệu chứng: tuổi, thời gian, thai kỳ, dị ứng, thuốc đang dùng.

**Tự chuyển dược sĩ (kèm tóm tắt để dược sĩ không phải hỏi lại)** khi:
- Khách bấm "Gặp dược sĩ" hoặc nhắn muốn gặp người.
- Có **dấu hiệu nguy hiểm** (khó thở, đau ngực, co giật, ngộ độc, sốt cao...); trợ lý khuyên gọi 115.
- **Đối tượng đặc biệt**: mang thai, cho con bú, trẻ nhỏ, bệnh gan / thận.
- Hỏi **thuốc kê đơn**, kháng sinh, liều dùng, đổi / ngưng thuốc; gửi **ảnh đơn thuốc**.
- Sản phẩm được nhắc tới có **cảnh báo dị ứng / tương tác** với hồ sơ sức khỏe khách (kiểm tra ngay trên máy chủ, hồ sơ sức khỏe không gửi ra ngoài).
- Khách đã trả lời câu hỏi sàng lọc triệu chứng, hoặc trợ lý 2 lần không hiểu câu hỏi.

Hội thoại được giao cho **dược sĩ có chứng chỉ hành nghề** đang online, ít việc nhất. Dược sĩ cũng có thể bấm "Tiếp nhận từ AI" bất cứ lúc nào. Admin bật / tắt trợ lý và đổi tên hiển thị trong Cấu hình.

**Dùng Claude:** đặt biến môi trường trước khi chạy (không bắt buộc):

```bash
# Linux / macOS
export ANTHROPIC_API_KEY=sk-ant-...
# Windows PowerShell
$env:ANTHROPIC_API_KEY="sk-ant-..."
```

Có thể đổi model bằng `ANTHROPIC_MODEL` (mặc định `claude-sonnet-5`). Không có key, trợ lý **trả lời tự động theo kịch bản** (hiểu cả tiếng Việt không dấu) và vẫn áp dụng đủ các quy tắc chuyển dược sĩ ở trên.
Khi dùng Claude, nội dung tin nhắn trong phiên chat với trợ lý và thông tin đơn hàng gần đây của khách được gửi tới Anthropic để tạo câu trả lời.

## Lịch làm việc, chấm công và tính lương

**Admin → nhóm "Nhân sự"**
- **Ca làm** (`/admin/shifts`): khai báo ca (giờ bắt đầu/kết thúc, hỗ trợ ca qua đêm), thời gian nghỉ, phụ cấp ca, màu hiển thị. Mẫu: Ca sáng 07–15, Ca chiều 14–22 (phụ cấp 30.000 đ), Hành chính 08–17.
- **Lịch làm việc** (`/admin/schedule`): lưới tuần nhân viên × ngày, bấm ⊕ để xếp ca; chặn xếp trùng giờ, không xếp lùi quá 31 ngày; bỏ ca chưa chấm công; **sao chép lịch sang tuần sau**. Dược sĩ nhận thông báo khi được xếp ca.
- **Chấm công** (`/admin/attendance`): xem giờ vào/ra theo ngày, trạng thái (Hoàn thành / Muộn X phút / Vắng / Quên kết ca / Đang làm); admin sửa giờ công (bắt buộc ghi lý do, lưu nhật ký).
- **Bảng lương** (`/admin/payroll`): tính lương theo tháng → bảng **Nháp** (nhập thưởng, khấu trừ khác, ghi chú; tính lại giữ nguyên các khoản nhập tay) → **Chốt** (gửi thông báo phiếu lương cho nhân viên) → **Đã trả**. Xuất Excel.
- Lương từng người khai báo ở form **Nhân viên**: lương tháng (lương cơ bản) hoặc lương giờ (đơn giá/giờ) + phụ cấp cố định.

**Cách tính**
- Giờ công mỗi ca = phần thời gian có mặt nằm trong khung ca (trừ giờ nghỉ nếu làm > 4 giờ), tối đa bằng số giờ của ca. Ca vắng / quên kết ca không tính công.
- Lương tháng = lương cơ bản × ngày công ÷ `payroll_standard_days` (mặc định 26).  Lương giờ = giờ công × đơn giá.
- Cộng: phụ cấp ca (theo từng ca đã làm) + phụ cấp cố định (khi có ngày công) + thưởng.
- Trừ: số lần muộn quá `late_grace_minutes` (5 phút) × `late_penalty` (20.000 đ) + bảo hiểm `insurance_permille` (105‰ = 10,5% lương cơ bản, chỉ lương tháng) + khấu trừ khác. Các tham số chỉnh ở **Cấu hình → Chấm công & tính lương**.

**Dược sĩ** (`/staff/my-schedule`, `/staff/my-payslips`): xem lịch tuần, bấm **Vào ca / Kết thúc ca** (mở trước giờ vào ca 60 phút; cũng có trên trang Tổng quan), xem phiếu lương đã chốt.

Dữ liệu mẫu: lịch và chấm công từ đầu tháng trước tới 2 tuần tới cho 4 dược sĩ, bảng lương tháng trước đã trả.

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
- Mã vận đơn nhập tay, chưa kết nối API đơn vị vận chuyển. Liên thông Dược Quốc gia: có file xuất dữ liệu, chưa gọi API (cần tài khoản do Sở Y tế cấp).
- Hóa đơn điện tử: ghi nhận yêu cầu và số HĐĐT, chưa kết nối API nhà cung cấp HĐĐT. Xuất PDF dùng chức năng "In > Lưu thành PDF" của trình duyệt. Trạng thái online của dược sĩ tính theo hoạt động trong 5 phút gần nhất.
