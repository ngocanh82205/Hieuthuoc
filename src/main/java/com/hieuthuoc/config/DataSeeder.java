package com.hieuthuoc.config;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.FileStorageService;
import com.hieuthuoc.service.StockService;
import com.hieuthuoc.service.Texts;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dữ liệu khởi tạo, tương ứng database/seeders của bản Laravel:
 * - ReferenceData: trang tĩnh, tương tác thuốc, FAQ (chỉ tạo khi chưa có, chạy lại an toàn).
 * - Admin: tạo từ ADMIN_EMAIL / ADMIN_PASSWORD (không có mật khẩu cố định trong mã nguồn).
 * - DemoData: sản phẩm, lô, ~55 đơn trong 28 ngày, đơn thuốc chờ duyệt, hội thoại... khi app.seed-demo-data=true và DB chưa có khách hàng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    private final PasswordEncoder encoder;
    private final StockService stock;
    private final FileStorageService files;
    private final PlatformTransactionManager txManager;

    @PersistenceContext
    private EntityManager em;

    @Value("${app.seed-demo-data:true}")
    private boolean seedDemo;
    @Value("${app.env:local}")
    private String env;
    @Value("${ADMIN_EMAIL:}")
    private String adminEmail;
    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;
    @Value("${ADMIN_NAME:Quản trị viên}")
    private String adminName;

    private final Map<Long, String[]> addresses = new HashMap<>();

    @Override
    public void run(String... args) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        try {
            tx.executeWithoutResult(s -> referenceData());
            tx.executeWithoutResult(s -> admin());
            if (seedDemo && count("select count(u) from User u where u.role = com.hieuthuoc.entity.Role.CUSTOMER") == 0) {
                tx.executeWithoutResult(s -> demoData());
            }
        } catch (RuntimeException e) {
            log.warn("Không tạo được dữ liệu khởi tạo: {}", e.getMessage(), e);
        }
    }

    private long count(String jpql) {
        return em.createQuery(jpql, Long.class).getSingleResult();
    }

    private boolean local() {
        return "local".equals(env) || "testing".equals(env);
    }

    /* ============================ ReferenceDataSeeder ============================ */

    private static final String[][] INTERACTIONS = {
            {"ibuprofen", "aspirin", "danger", "Phối hợp hai NSAID làm tăng nguy cơ loét, xuất huyết tiêu hóa."},
            {"ibuprofen", "losartan", "warning", "NSAID làm giảm tác dụng hạ áp của losartan, tăng nguy cơ suy thận."},
            {"ibuprofen", "amlodipin", "warning", "NSAID có thể làm giảm tác dụng hạ huyết áp."},
            {"ibuprofen", "bisoprolol", "warning", "NSAID có thể làm giảm tác dụng hạ huyết áp của bisoprolol."},
            {"phenylephrin", "bisoprolol", "warning", "Phenylephrin gây co mạch, tăng huyết áp - đối kháng thuốc hạ áp."},
            {"phenylephrin", "amlodipin", "warning", "Phenylephrin gây tăng huyết áp - đối kháng thuốc hạ áp."},
            {"phenylephrin", "losartan", "warning", "Phenylephrin gây tăng huyết áp - đối kháng thuốc hạ áp."},
            {"caffeine", "bisoprolol", "warning", "Caffeine có thể làm tăng nhịp tim, huyết áp."},
            {"omeprazol", "clopidogrel", "danger", "Omeprazol làm giảm tác dụng chống kết tập tiểu cầu của clopidogrel."},
            {"dầu cá", "aspirin", "warning", "Dầu cá liều cao phối hợp thuốc chống kết tập tiểu cầu làm tăng nguy cơ chảy máu."},
            {"bạch quả", "aspirin", "warning", "Cao bạch quả phối hợp aspirin/thuốc chống đông làm tăng nguy cơ chảy máu."},
            {"bạch quả", "ibuprofen", "warning", "Cao bạch quả phối hợp NSAID làm tăng nguy cơ chảy máu."},
            {"bacillus", "amoxicillin", "info", "Uống men vi sinh cách kháng sinh ít nhất 2 giờ."},
            {"bacillus", "cefuroxim", "info", "Uống men vi sinh cách kháng sinh ít nhất 2 giờ."},
            {"calci", "cefuroxim", "info", "Nên uống calci cách kháng sinh 2 giờ để tránh giảm hấp thu."},
            {"diazepam", "phenylephrin", "warning", "Cần thận trọng khi phối hợp thuốc an thần với thuốc cảm."},
    };

    private void referenceData() {
        if (count("select count(p) from StaticPage p") == 0) {
            String[][] pages = {
                    {"chinh-sach-doi-tra", "Chính sách đổi trả & hoàn tiền", "## Điều kiện đổi trả\nKhách hàng được đổi/trả trong 7 ngày kể từ khi nhận hàng khi sản phẩm bị lỗi từ nhà sản xuất, giao sai sản phẩm, hư hỏng do vận chuyển hoặc còn nguyên tem niêm phong.\n\n## Không áp dụng đổi trả\n- Thuốc kê đơn (trừ trường hợp lỗi từ nhà thuốc)\n- Sản phẩm đã mở niêm phong, đã sử dụng\n- Sản phẩm cần bảo quản lạnh\n\n## Hoàn tiền\nSau khi nhà thuốc duyệt yêu cầu, tiền được hoàn về tài khoản / ví của khách trong 3 - 7 ngày làm việc. Điểm tích lũy đã dùng được hoàn lại."},
                    {"chinh-sach-giao-hang", "Chính sách giao hàng", "## Phạm vi & phí giao hàng\nNhà thuốc giao hàng toàn quốc qua Giao Hàng Nhanh (GHN) và nhân viên nhà thuốc. Phí ship tính theo địa chỉ nhận, miễn phí cho đơn đạt ngưỡng (xem chi tiết khi thanh toán).\n\n## Thời gian\n- Nội thành Hà Nội: 2 - 4 giờ\n- Các tỉnh miền Bắc: 1 - 2 ngày\n- Miền Trung, miền Nam: 2 - 4 ngày\n\n## Theo dõi đơn\nKhách xem trạng thái đơn và mã vận đơn tại mục Đơn hàng của tôi.\n\nĐơn có thuốc kê đơn chỉ được giao sau khi dược sĩ duyệt đơn thuốc."},
                    {"chinh-sach-bao-mat", "Chính sách bảo mật thông tin", "## Thông tin thu thập\nHọ tên, số điện thoại, địa chỉ giao hàng, hồ sơ sức khỏe (dị ứng, bệnh nền) và ảnh đơn thuốc khách hàng cung cấp.\n\n## Mục đích sử dụng\nXử lý đơn hàng, tư vấn dùng thuốc an toàn, chăm sóc khách hàng. Ảnh đơn thuốc và hồ sơ sức khỏe chỉ dược sĩ được xem.\n\n## Cam kết\nKhông chia sẻ thông tin cho bên thứ ba trừ khi pháp luật yêu cầu. Khách hàng có thể yêu cầu xem, sửa hoặc xóa dữ liệu cá nhân."},
                    {"dieu-khoan-su-dung", "Điều khoản sử dụng", "Khi sử dụng website, khách hàng đồng ý cung cấp thông tin chính xác và sử dụng thuốc theo hướng dẫn của bác sĩ, dược sĩ.\n\nThông tin trên website chỉ mang tính tham khảo, không thay thế chẩn đoán và điều trị của bác sĩ.\n\nThuốc kiểm soát đặc biệt không được bán online theo quy định của pháp luật."},
                    {"lien-he", "Liên hệ", "## Nhà thuốc VinaPharma\n- Địa chỉ: 123 Nguyễn Trãi, Thanh Xuân, Hà Nội\n- Hotline: 1900 1234 (7h - 22h mỗi ngày)\n- Email: hotro@vinapharma.vn\n\n## Hỗ trợ trực tuyến\n- Chat với dược sĩ tại mục Tư vấn"},
            };
            for (int i = 0; i < pages.length; i++) {
                StaticPage p = new StaticPage();
                p.setSlug(pages[i][0]);
                p.setTitle(pages[i][1]);
                p.setContent(pages[i][2]);
                p.setSortOrder(i + 1);
                em.persist(p);
            }
        }
        if (count("select count(d) from DrugInteraction d") == 0) {
            for (String[] r : INTERACTIONS) {
                DrugInteraction d = new DrugInteraction();
                d.setIngredientA(r[0]);
                d.setIngredientB(r[1]);
                d.setLevel(r[2]);
                d.setMessage(r[3]);
                em.persist(d);
            }
        }
        if (count("select count(f) from Faq f") == 0) {
            String[][] faqs = {
                    {"Đặt hàng", "Làm sao để mua thuốc kê đơn trên website?", "Khi thêm thuốc kê đơn vào giỏ, bạn cần tải ảnh đơn thuốc của bác sĩ ở bước thanh toán. Dược sĩ sẽ kiểm tra và duyệt đơn trước khi giao hàng. Bạn cũng có thể gửi đơn thuốc mà không cần chọn sản phẩm tại mục \"Gửi đơn thuốc\", dược sĩ sẽ lên đơn và gửi báo giá."},
                    {"Đặt hàng", "Tôi có thể hủy đơn hàng không?", "Bạn được hủy đơn tại mục Đơn hàng của tôi cho đến trước khi đơn chuyển sang trạng thái Đang vận chuyển. Nếu đơn đã thanh toán, nhà thuốc sẽ hoàn tiền trong 3 - 7 ngày làm việc."},
                    {"Đặt hàng", "Vì sao một số thuốc không bán online?", "Theo quy định, thuốc gây nghiện, thuốc hướng thần và tiền chất (thuốc kiểm soát đặc biệt) chỉ được bán trực tiếp tại nhà thuốc khi có đơn thuốc hợp lệ."},
                    {"Thanh toán", "Website hỗ trợ những hình thức thanh toán nào?", "Thanh toán khi nhận hàng (COD), chuyển khoản ngân hàng qua mã VietQR, cổng thanh toán PayOS (VietQR ngân hàng) và VNPay (thẻ ATM nội địa, thẻ Visa/Master/JCB, QR ngân hàng)."},
                    {"Thanh toán", "Tôi đã thanh toán nhưng đơn vẫn báo chưa thanh toán?", "Với PayOS/VNPay, hệ thống cập nhật ngay khi cổng thanh toán xác nhận. Với chuyển khoản, nhân viên sẽ đối soát và xác nhận trong giờ làm việc. Nếu quá 24 giờ vẫn chưa cập nhật, hãy gửi yêu cầu hỗ trợ kèm ảnh chụp giao dịch."},
                    {"Giao hàng", "Phí giao hàng được tính như thế nào?", "Phí giao hàng tính theo địa chỉ nhận (qua Giao Hàng Nhanh hoặc biểu phí theo khu vực). Đơn đạt ngưỡng miễn phí vận chuyển sẽ không mất phí ship. Bạn cũng có thể chọn nhận tại nhà thuốc."},
                    {"Giao hàng", "Làm sao theo dõi đơn hàng?", "Vào Tài khoản > Đơn hàng của tôi để xem trạng thái (Đang xử lý, Đã đóng gói, Đang vận chuyển, Đã giao) và mã vận đơn GHN."},
                    {"Đổi trả", "Chính sách đổi trả như thế nào?", "Bạn được đổi/trả trong 7 ngày khi sản phẩm lỗi, giao sai hoặc hư hỏng do vận chuyển. Không đổi trả thuốc kê đơn và sản phẩm đã mở niêm phong. Gửi yêu cầu tại trang chi tiết đơn hàng."},
                    {"Tài khoản", "Tôi quên mật khẩu thì làm sao?", "Bấm \"Quên mật khẩu\" ở trang đăng nhập và để lại số điện thoại. Nhà thuốc sẽ gọi điện xác minh và cấp mật khẩu tạm. Nếu tài khoản liên kết Google, bạn có thể đăng nhập bằng Google."},
                    {"Tài khoản", "Điểm tích lũy và hạng thành viên hoạt động ra sao?", "Mỗi 10.000đ trong đơn hoàn thành được 1 điểm (nhân hệ số theo hạng Đồng / Bạc / Vàng / Kim cương). 1 điểm trừ 1.000đ khi thanh toán phần hàng không kê đơn."},
                    {"Tư vấn", "Tôi có thể hỏi dược sĩ bằng cách nào?", "Chat tại mục Tư vấn (trợ lý AI trả lời trước, chuyển dược sĩ khi cần), hoặc gọi hotline."},
            };
            for (int i = 0; i < faqs.length; i++) {
                Faq f = new Faq();
                f.setGroup(faqs[i][0]);
                f.setQuestion(faqs[i][1]);
                f.setAnswer(faqs[i][2]);
                f.setSortOrder(i + 1);
                f.setActive(true);
                em.persist(f);
            }
        }
    }

    /* ============================ AdminSeeder ============================ */

    private void admin() {
        String email = adminEmail == null ? "" : adminEmail.trim().toLowerCase();
        if (email.isEmpty() || adminPassword == null || adminPassword.isEmpty()) return;
        boolean exists = !em.createQuery("select u from User u where lower(u.email) = :e", User.class).setParameter("e", email).getResultList().isEmpty();
        if (exists) return;
        if (adminPassword.length() < 8) {
            log.error("ADMIN_PASSWORD phải có ít nhất 8 ký tự.");
            return;
        }
        user(Role.ADMIN, adminName, email, null, adminPassword, null, Map.of());
        log.info("Đã tạo admin {}.", email);
    }

    /* ============================ DemoDataSeeder ============================ */

    private static final Object[][] CATEGORIES = {
            {"Giảm đau - Hạ sốt", "bi-thermometer-half"}, {"Kháng sinh - Kháng khuẩn", "bi-capsule"}, {"Tim mạch - Huyết áp", "bi-heart-pulse"},
            {"Tiêu hóa", "bi-droplet-half"}, {"Hô hấp - Cảm cúm", "bi-lungs"}, {"Vitamin & Khoáng chất", "bi-sun"},
            {"Thực phẩm chức năng", "bi-flower1"}, {"Dụng cụ y tế", "bi-bandaid"}, {"Chăm sóc da", "bi-stars"}, {"Thần kinh", "bi-activity"},
    };

    /** Thuốc bán lẻ theo vỉ: slug => [đơn vị gốc, số vỉ trong 1 hộp, giá 1 vỉ]. */
    private static final Map<String, Object[]> SPLIT = Map.of(
            "panadol-extra", new Object[]{"Vỉ", 15, 12500L}, "efferalgan-500mg-vien-sui", new Object[]{"Vỉ", 4, 12500L},
            "ibuprofen-400mg", new Object[]{"Vỉ", 10, 7000L}, "augmentin-625mg", new Object[]{"Vỉ", 2, 110000L},
            "amoxicillin-500mg-domesco", new Object[]{"Vỉ", 10, 9000L}, "amlodipin-5mg-stada", new Object[]{"Vỉ", 3, 11000L},
            "concor-5mg", new Object[]{"Vỉ", 3, 45000L}, "decolgen-nd", new Object[]{"Vỉ", 25, 5000L});

    /** cat, tên, hoạt chất, hàm lượng, dạng bào chế, quy cách, SĐK, NSX, nước, loại, ĐVT, giá, giá cũ, tối đa/đơn, mô tả, cách dùng, chống chỉ định, tác dụng phụ */
    private static final Object[][] PRODUCTS = {
            {0, "Panadol Extra", "Paracetamol, Caffeine", "500mg/65mg", "Viên nén bao phim", "Hộp 15 vỉ x 12 viên", "VD-21189-14", "GSK", "Việt Nam", "OTC", "Hộp", 185000L, 199000L, 5,
                    "Giảm các cơn đau nhẹ đến vừa: đau đầu, đau nửa đầu, đau cơ, đau bụng kinh, đau họng, đau răng; hạ sốt.",
                    "Người lớn và trẻ em trên 12 tuổi: 1-2 viên mỗi 4-6 giờ khi cần. Không quá 8 viên/24 giờ.",
                    "Quá mẫn với paracetamol hoặc caffeine. Suy gan nặng.", "Hiếm gặp: phát ban, buồn nôn. Dùng quá liều có thể gây tổn thương gan."},
            {0, "Efferalgan 500mg viên sủi", "Paracetamol", "500mg", "Viên sủi", "Hộp 4 vỉ x 4 viên", "VN-19954-16", "UPSA", "Pháp", "OTC", "Hộp", 48000L, null, 5,
                    "Điều trị triệu chứng các chứng đau và/hoặc sốt như đau đầu, tình trạng như cúm, đau răng, nhức mỏi cơ.",
                    "Hòa tan hoàn toàn viên thuốc vào cốc nước. Người lớn: 1-2 viên/lần, cách nhau ít nhất 4 giờ.",
                    "Suy gan. Quá mẫn với paracetamol. Chế độ ăn kiêng muối.", "Phản ứng dị ứng, giảm tiểu cầu (rất hiếm)."},
            {0, "Hapacol 250 bột sủi trẻ em", "Paracetamol", "250mg", "Bột sủi bọt", "Hộp 24 gói x 1,5g", "VD-20560-14", "DHG Pharma", "Việt Nam", "OTC", "Hộp", 42000L, null, 5,
                    "Hạ sốt, giảm đau cho trẻ em trong các trường hợp cảm cúm, nhiễm khuẩn, mọc răng, sau tiêm chủng.",
                    "Trẻ 3-6 tuổi: 1 gói/lần; trẻ 7-12 tuổi: 2 gói/lần. Cách nhau 4-6 giờ, không quá 5 lần/ngày.",
                    "Quá mẫn với paracetamol. Trẻ bị suy gan, thiếu G6PD.", "Ít gặp: ban da, buồn nôn."},
            {0, "Ibuprofen 400mg", "Ibuprofen", "400mg", "Viên nén bao phim", "Hộp 10 vỉ x 10 viên", "VD-24520-16", "Stada Việt Nam", "Việt Nam", "OTC", "Hộp", 65000L, null, 3,
                    "Giảm đau, kháng viêm trong đau đầu, đau răng, đau bụng kinh, đau cơ xương khớp; hạ sốt.",
                    "Người lớn: 1 viên x 2-3 lần/ngày, uống sau ăn.",
                    "Loét dạ dày tá tràng tiến triển, suy gan thận nặng, phụ nữ có thai 3 tháng cuối, hen do aspirin.", "Đau thượng vị, buồn nôn, chóng mặt."},
            {1, "Augmentin 625mg", "Amoxicillin, Acid clavulanic", "500mg/125mg", "Viên nén bao phim", "Hộp 2 vỉ x 7 viên", "VN-20493-17", "GlaxoSmithKline", "Anh", "ETC", "Hộp", 215000L, null, 3,
                    "Điều trị nhiễm khuẩn đường hô hấp, tai mũi họng, đường tiết niệu, da và mô mềm do vi khuẩn nhạy cảm.",
                    "Theo chỉ định của bác sĩ. Thông thường: 1 viên x 2 lần/ngày, uống đầu bữa ăn.",
                    "Dị ứng nhóm beta-lactam (penicillin, cephalosporin).", "Tiêu chảy, buồn nôn, phát ban, nhiễm nấm Candida."},
            {1, "Amoxicillin 500mg Domesco", "Amoxicillin", "500mg", "Viên nang cứng", "Hộp 10 vỉ x 10 viên", "VD-25013-16", "Domesco", "Việt Nam", "ETC", "Hộp", 85000L, null, 3,
                    "Điều trị nhiễm khuẩn do vi khuẩn nhạy cảm với amoxicillin.", "Theo chỉ định của bác sĩ.", "Dị ứng penicillin.", "Buồn nôn, tiêu chảy, ban da."},
            {1, "Zinnat 500mg", "Cefuroxim", "500mg", "Viên nén bao phim", "Hộp 1 vỉ x 10 viên", "VN-18763-15", "GlaxoSmithKline", "Anh", "ETC", "Hộp", 245000L, null, 2,
                    "Kháng sinh cephalosporin thế hệ 2 điều trị nhiễm khuẩn đường hô hấp, tai mũi họng, tiết niệu.",
                    "Theo chỉ định của bác sĩ. Uống sau ăn.", "Dị ứng cephalosporin.", "Tiêu chảy, đau đầu, tăng men gan thoáng qua."},
            {2, "Amlodipin 5mg Stada", "Amlodipin", "5mg", "Viên nén", "Hộp 3 vỉ x 10 viên", "VD-23417-15", "Stada Việt Nam", "Việt Nam", "ETC", "Hộp", 32000L, null, 5,
                    "Điều trị tăng huyết áp, đau thắt ngực ổn định.", "Theo chỉ định của bác sĩ. Thường 1 viên/ngày.",
                    "Quá mẫn với dihydropyridin. Hạ huyết áp nặng, sốc tim.", "Phù cổ chân, đỏ bừng mặt, đau đầu."},
            {2, "Concor 5mg", "Bisoprolol fumarat", "5mg", "Viên nén bao phim", "Hộp 3 vỉ x 10 viên", "VN-17135-13", "Merck", "Đức", "ETC", "Hộp", 128000L, null, 5,
                    "Điều trị tăng huyết áp, đau thắt ngực, suy tim mạn ổn định.", "Theo chỉ định của bác sĩ. Uống buổi sáng.",
                    "Suy tim cấp, block nhĩ thất độ II-III, nhịp chậm, hen phế quản nặng.", "Mệt mỏi, chóng mặt, lạnh đầu chi."},
            {2, "Losartan 50mg", "Losartan kali", "50mg", "Viên nén bao phim", "Hộp 3 vỉ x 10 viên", "VD-26814-17", "Pymepharco", "Việt Nam", "ETC", "Hộp", 45000L, null, 5,
                    "Điều trị tăng huyết áp, bảo vệ thận ở bệnh nhân đái tháo đường type 2.", "Theo chỉ định của bác sĩ.",
                    "Phụ nữ có thai. Quá mẫn với losartan.", "Chóng mặt, tăng kali máu."},
            {3, "Smecta hương cam", "Diosmectit", "3g", "Bột pha hỗn dịch uống", "Hộp 30 gói", "VN-20138-16", "Ipsen", "Pháp", "OTC", "Hộp", 115000L, 125000L, 5,
                    "Điều trị tiêu chảy cấp và mạn ở trẻ em và người lớn; giảm đau do viêm thực quản, dạ dày.",
                    "Người lớn: 3 gói/ngày, pha trong nửa cốc nước.", "Quá mẫn với thành phần thuốc.", "Táo bón (hiếm)."},
            {3, "Berberin 100mg", "Berberin clorid", "100mg", "Viên nén bao đường", "Lọ 100 viên", "VD-22765-15", "Mekophar", "Việt Nam", "OTC", "Lọ", 16000L, null, 10,
                    "Hỗ trợ điều trị tiêu chảy, lỵ trực khuẩn, viêm ruột.", "Người lớn: 4-6 viên/lần x 2 lần/ngày.", "Phụ nữ có thai.", "Táo bón nhẹ."},
            {3, "Omeprazol 20mg", "Omeprazol", "20mg", "Viên nang tan trong ruột", "Hộp 2 vỉ x 7 viên", "VD-28765-18", "DHG Pharma", "Việt Nam", "ETC", "Hộp", 28000L, null, 5,
                    "Điều trị loét dạ dày tá tràng, trào ngược dạ dày thực quản.", "Theo chỉ định của bác sĩ. Uống trước ăn sáng 30 phút.",
                    "Quá mẫn với omeprazol.", "Đau đầu, buồn nôn, tiêu chảy."},
            {3, "Enterogermina 2 tỷ/5ml", "Bacillus clausii", "2 tỷ bào tử", "Hỗn dịch uống", "Hộp 20 ống x 5ml", "VN-20720-17", "Sanofi", "Ý", "OTC", "Hộp", 165000L, 180000L, 5,
                    "Phòng và điều trị rối loạn hệ vi khuẩn đường ruột, tiêu chảy do dùng kháng sinh.",
                    "Người lớn: 2-3 ống/ngày; trẻ em: 1-2 ống/ngày.", "Quá mẫn với thành phần thuốc.", "Chưa ghi nhận."},
            {4, "Decolgen ND", "Paracetamol, Phenylephrin", "500mg/10mg", "Viên nén", "Hộp 25 vỉ x 4 viên", "VD-26017-16", "United Pharma", "Việt Nam", "OTC", "Hộp", 125000L, null, 3,
                    "Giảm các triệu chứng cảm cúm: sốt, nhức đầu, sổ mũi, nghẹt mũi.", "Người lớn: 1 viên mỗi 6 giờ.",
                    "Tăng huyết áp nặng, bệnh mạch vành, cường giáp.", "Hồi hộp, mất ngủ nhẹ."},
            {4, "Siro ho Prospan 100ml", "Cao lá thường xuân", "0,7g/100ml", "Siro", "Chai 100ml", "VN-19875-16", "Engelhard", "Đức", "OTC", "Chai", 89000L, 95000L, 5,
                    "Điều trị viêm đường hô hấp cấp có kèm ho, ho do viêm phế quản mạn tính.",
                    "Người lớn: 5-7,5ml x 3 lần/ngày.", "Không dung nạp fructose.", "Rối loạn tiêu hóa nhẹ."},
            {5, "Viên sủi Vitamin C 1000mg", "Acid ascorbic", "1000mg", "Viên sủi", "Tuýp 10 viên", "VD-29012-18", "Bidiphar", "Việt Nam", "SUPPLEMENT", "Tuýp", 35000L, 42000L, 10,
                    "Bổ sung vitamin C, tăng cường sức đề kháng, chống oxy hóa.", "Người lớn: 1 viên/ngày, hòa tan trong nước.",
                    "Sỏi thận oxalat, thiếu G6PD.", "Dùng liều cao có thể gây tiêu chảy."},
            {5, "Canxi D3 Corbiere", "Calci, Vitamin D3", "500mg/200IU", "Dung dịch uống", "Hộp 30 ống x 5ml", "VD-24098-16", "Sanofi", "Việt Nam", "SUPPLEMENT", "Hộp", 110000L, null, 5,
                    "Bổ sung canxi và vitamin D3 cho trẻ em đang lớn, phụ nữ có thai, người cao tuổi.", "Uống 1-2 ống/ngày.",
                    "Tăng canxi máu, sỏi thận.", "Táo bón nhẹ."},
            {6, "Omega-3 Fish Oil 1000mg", "Dầu cá (EPA, DHA)", "1000mg", "Viên nang mềm", "Lọ 100 viên", "TPCN 4512/2020", "Blackmores", "Úc", "SUPPLEMENT", "Lọ", 395000L, 450000L, 3,
                    "Hỗ trợ tim mạch, não bộ và thị lực. Sản phẩm này không phải là thuốc, không có tác dụng thay thế thuốc chữa bệnh.",
                    "Uống 1 viên x 1-3 lần/ngày, sau bữa ăn.", "Người đang dùng thuốc chống đông cần hỏi ý kiến bác sĩ.", "Ợ hơi mùi cá."},
            {6, "Ginkgo Biloba 120mg", "Cao bạch quả", "120mg", "Viên nén", "Hộp 3 vỉ x 10 viên", "TPCN 3321/2021", "Traphaco", "Việt Nam", "SUPPLEMENT", "Hộp", 99000L, null, 5,
                    "Hỗ trợ tăng cường tuần hoàn máu não. Sản phẩm này không phải là thuốc, không có tác dụng thay thế thuốc chữa bệnh.",
                    "Uống 1 viên x 2 lần/ngày.", "Người đang dùng thuốc chống đông, phụ nữ có thai.", "Đau đầu nhẹ (hiếm)."},
            {7, "Máy đo huyết áp bắp tay Omron HEM-7121", null, null, null, "Hộp 1 máy", "220001234/PCBB-HN", "Omron", "Nhật Bản", "DEVICE", "Cái", 890000L, 990000L, 2,
                    "Máy đo huyết áp tự động bắp tay, công nghệ IntelliSense, phát hiện nhịp tim bất thường.",
                    "Quấn vòng bít ngang tim, ngồi yên 5 phút trước khi đo.", null, null},
            {7, "Nhiệt kế điện tử Microlife MT200", null, null, null, "Hộp 1 cái", "220005678/PCBA-HN", "Microlife", "Thụy Sĩ", "DEVICE", "Cái", 75000L, null, 5,
                    "Nhiệt kế điện tử đo ở miệng, nách, hậu môn; cho kết quả sau 60 giây.", "Đặt đầu đo đúng vị trí đến khi có tiếng bíp.", null, null},
            {7, "Khẩu trang y tế 4 lớp", null, null, null, "Hộp 50 cái", "220009999/PCBA-HCM", "Nam Anh", "Việt Nam", "DEVICE", "Hộp", 35000L, 45000L, 20,
                    "Khẩu trang y tế 4 lớp kháng khuẩn, lọc bụi.", "Dùng 1 lần.", null, null},
            {8, "Kem chống nắng La Roche-Posay Anthelios SPF50+", null, null, "Kem", "Tuýp 50ml", "123456/21/CBMP-QLD", "La Roche-Posay", "Pháp", "COSMETIC", "Tuýp", 485000L, 530000L, 3,
                    "Kem chống nắng phổ rộng, kiểm soát dầu, dành cho da nhạy cảm.", "Thoa trước khi ra nắng 20 phút, thoa lại sau mỗi 2 giờ.", null, null},
            {8, "Sữa rửa mặt Cetaphil Gentle Skin Cleanser", null, null, "Sữa rửa mặt", "Chai 500ml", "98765/20/CBMP-QLD", "Galderma", "Canada", "COSMETIC", "Chai", 345000L, null, 3,
                    "Làm sạch dịu nhẹ, không gây kích ứng, phù hợp da nhạy cảm.", "Dùng 2 lần/ngày.", null, null},
            {9, "Seduxen 5mg", "Diazepam", "5mg", "Viên nén", "Hộp 10 vỉ x 10 viên", "VN-16582-13", "Gedeon Richter", "Hungary", "SPECIAL", "Hộp", 60000L, null, null,
                    "Thuốc hướng thần - chỉ bán tại nhà thuốc theo đơn thuốc \"H\" của bác sĩ. Không bán online.",
                    "Theo chỉ định của bác sĩ.", "Suy hô hấp, nhược cơ, ngưng thở khi ngủ.", "Buồn ngủ, lệ thuộc thuốc."},
    };

    private static final String[][] POSTS = {
            {"Cách dùng thuốc hạ sốt đúng cho trẻ em", "Hướng dẫn cha mẹ dùng paracetamol an toàn, đúng liều theo cân nặng của trẻ.",
                    "Paracetamol là thuốc hạ sốt được khuyến cáo phổ biến cho trẻ em. Liều thường dùng là 10-15mg/kg cân nặng mỗi lần, cách nhau 4-6 giờ, không quá 4-5 lần/ngày.\n\nChỉ nên dùng thuốc hạ sốt khi trẻ sốt từ 38,5°C trở lên. Với mức sốt thấp hơn, hãy cho trẻ mặc thoáng, uống nhiều nước và lau người bằng nước ấm.\n\nKhông phối hợp nhiều sản phẩm cùng chứa paracetamol (ví dụ thuốc cảm và thuốc hạ sốt) vì dễ gây quá liều, ảnh hưởng đến gan.\n\nĐưa trẻ đi khám ngay nếu trẻ dưới 3 tháng tuổi bị sốt, sốt cao liên tục trên 2 ngày, co giật, li bì hoặc nôn nhiều."},
            {"Vì sao không nên tự ý dùng kháng sinh?", "Lạm dụng kháng sinh là nguyên nhân chính dẫn đến tình trạng kháng thuốc.",
                    "Kháng sinh chỉ có tác dụng với vi khuẩn, không có tác dụng với virus gây cảm cúm thông thường. Việc tự ý dùng kháng sinh khi không cần thiết vừa không hiệu quả, vừa làm tăng nguy cơ kháng thuốc.\n\nTheo quy định, kháng sinh là thuốc kê đơn - nhà thuốc chỉ được bán khi có đơn của bác sĩ. Đó cũng là lý do website yêu cầu bạn tải lên đơn thuốc khi mua các sản phẩm này.\n\nKhi được kê kháng sinh, hãy uống đủ liều, đủ thời gian, kể cả khi đã thấy đỡ. Ngưng thuốc sớm tạo điều kiện cho vi khuẩn sống sót và trở nên kháng thuốc."},
            {"Theo dõi huyết áp tại nhà: những điều cần biết", "Đo huyết áp đúng cách giúp kiểm soát bệnh tăng huyết áp hiệu quả hơn.",
                    "Nên đo huyết áp vào cùng thời điểm mỗi ngày, tốt nhất là buổi sáng trước khi uống thuốc và buổi tối trước khi đi ngủ.\n\nTrước khi đo, ngồi nghỉ 5 phút, không hút thuốc, không uống cà phê trong vòng 30 phút. Đặt tay ngang mức tim, quấn vòng bít vừa khít cánh tay.\n\nGhi lại kết quả vào sổ theo dõi và mang theo khi đi tái khám để bác sĩ điều chỉnh thuốc phù hợp."},
    };

    private void demoData() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now().withNano(0);

        // Chạy thử trên máy (local/testing): tạo admin demo. Triển khai thật: dùng admin tạo từ ADMIN_EMAIL / ADMIN_PASSWORD.
        User admin;
        if (local()) {
            admin = em.createQuery("select u from User u where u.role = com.hieuthuoc.entity.Role.ADMIN and u.email = 'admin@hieuthuoc.vn'", User.class)
                    .getResultStream().findFirst().orElse(null);
            if (admin == null) admin = user(Role.ADMIN, "Quản trị viên", "admin@hieuthuoc.vn", "0901000001", "admin123", null, Map.of());
        } else {
            admin = em.createQuery("select u from User u where u.role = com.hieuthuoc.entity.Role.ADMIN order by u.id", User.class)
                    .setMaxResults(1).getResultStream().findFirst().orElse(null);
        }
        if (admin == null) {
            log.warn("Bỏ qua dữ liệu mẫu: cần tạo admin trước (đặt ADMIN_EMAIL / ADMIN_PASSWORD).");
            return;
        }
        User ds1 = user(Role.PHARMACIST, "DS. Nguyễn Thị Lan", "duocsi@hieuthuoc.vn", "0901000002", "duocsi123", "012345/HNO-CCHND",
                Map.of("degree", "Dược sĩ đại học - ĐH Dược Hà Nội"));
        User ds2 = user(Role.PHARMACIST, "DS. Phạm Quốc Huy", "duocsi2@hieuthuoc.vn", "0901000003", "duocsi123", "023456/HNO-CCHND",
                Map.of("degree", "Dược sĩ cao đẳng"));
        User ds3 = user(Role.PHARMACIST, "DS. Vũ Văn Kiên", "duocsi3@hieuthuoc.vn", "0901000004", "duocsi123", "034567/HNO-CCHND",
                Map.of("degree", "Dược sĩ đại học - ĐH Y Dược Thái Bình"));
        user(Role.PHARMACIST, "DS. Đỗ Thu Hà", "duocsi4@hieuthuoc.vn", "0901000005", "duocsi123", "045678/HNO-CCHND", Map.of("degree", "Dược sĩ cao đẳng"));
        User kh1 = user(Role.CUSTOMER, "Trần Văn An", "khachhang@gmail.com", "0912345678", "123456", null,
                Map.of("allergies", "Dị ứng Aspirin", "chronic_conditions", "Viêm dạ dày", "gender", "Nam", "birthday", "1990-05-12"));
        User kh2 = user(Role.CUSTOMER, "Lê Thị Bình", "binh@gmail.com", "0987654321", "123456", null, Map.of("gender", "Nữ"));
        User kh3 = user(Role.CUSTOMER, "Hoàng Minh Châu", "chau@gmail.com", "0934567890", "123456", null, Map.of("chronic_conditions", "Tăng huyết áp"));
        User[] customers = {kh1, kh2, kh3};
        address(kh1, "45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội", "Hà Nội");
        address(kh2, "12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội", "Hà Nội");
        address(kh3, "88 Nguyễn Huệ, Bến Nghé, Quận 1, TP. Hồ Chí Minh", "TP. Hồ Chí Minh");

        // Danh mục đa cấp: Thuốc > Tim mạch - Huyết áp > Thuốc huyết áp
        Category drugRoot = category("Thuốc", "thuoc", "bi-capsule-pill", 0, null);
        List<Category> cats = new ArrayList<>();
        for (int i = 0; i < CATEGORIES.length; i++) {
            String name = (String) CATEGORIES[i][0];
            cats.add(category(name, Texts.slugify(name), (String) CATEGORIES[i][1], i + 1, Set.of(0, 1, 2, 3, 4, 9).contains(i) ? drugRoot : null));
        }
        Category bp = category("Thuốc huyết áp", "thuoc-huyet-ap", "bi-heart-pulse", 0, cats.get(2));

        Supplier sup1 = supplier("Công ty CP Dược phẩm Trung ương 1", "02438252000", "sales@pharbaco.vn", "160 Tôn Đức Thắng, Hà Nội", "0100108536");
        Supplier sup2 = supplier("Công ty TNHH Phân phối Zuellig Pharma", "02838123456", "order@zuellig.vn", "KCN Tân Tạo, TP.HCM", null);

        List<Product> products = new ArrayList<>();
        Map<String, Product> bySlug = new HashMap<>();
        Map<String, Product> byName = new HashMap<>();
        for (int i = 0; i < PRODUCTS.length; i++) {
            Object[] d = PRODUCTS[i];
            int ci = (int) d[0];
            String name = (String) d[1];
            String ing = (String) d[2];
            String unit = (String) d[10];
            long price = (long) d[11];
            Long old = (Long) d[12];
            Integer max = (Integer) d[13];
            String type = (String) d[9];
            String slug = Texts.slugify(name);
            Object[] split = SPLIT.get(slug);
            int factor = 1;
            Object[] boxUnit = null;
            if (split != null) {
                // Đơn vị gốc = Vỉ, thêm đơn vị Hộp quy đổi
                factor = (int) split[1];
                boxUnit = new Object[]{unit, factor, price};
                unit = (String) split[0];
                price = (long) split[2];
                old = old != null ? Math.round((double) old / factor) : null;
                max = max != null ? max * factor : null;
            }
            Category cat = cats.get(ci);
            if (ci == 2 && ing != null && ing.toLowerCase().matches(".*(amlodipin|losartan|bisoprolol).*")) cat = bp;
            Product p = new Product();
            p.setCategory(cat);
            p.setName(name);
            p.setSlug(slug);
            p.setActiveIngredient(ing);
            p.setStrength((String) d[3]);
            p.setDosageForm((String) d[4]);
            p.setPackaging((String) d[5]);
            p.setRegistrationNo((String) d[6]);
            p.setManufacturer((String) d[7]);
            p.setCountry((String) d[8]);
            p.setDrugType(DrugType.valueOf(type));
            p.setUnit(unit);
            p.setPrice(price);
            p.setOldPrice(old);
            p.setMaxPerOrder(max);
            p.setDescription((String) d[14]);
            p.setUsageInstruction((String) d[15]);
            p.setContraindications((String) d[16]);
            p.setSideEffects((String) d[17]);
            p.setMinStock(10);
            p.setActive(true);
            p.setWeightGram("DEVICE".equals(type) ? 500 : 150);
            p.setMetaTitle(name + " - chính hãng, giá tốt");
            String desc = (String) d[14];
            p.setMetaDescription(desc.length() > 160 ? desc.substring(0, 160) : desc);
            p.setCreatedAt(now.minusDays(60).plusMinutes(i));
            em.persist(p);
            if (boxUnit != null) {
                ProductUnit u = new ProductUnit();
                u.setProduct(p);
                u.setName((String) boxUnit[0]);
                u.setFactor((int) boxUnit[1]);
                u.setPrice((long) boxUnit[2]);
                em.persist(u);
                p.getUnits().add(u);
            }
            products.add(p);
            bySlug.put(slug, p);
            byName.put(name, p);

            // Phiếu nhập đầu kỳ đã duyệt + lô hàng
            Supplier sup = i % 2 == 0 ? sup1 : sup2;
            Receipt r = new Receipt();
            r.setCode(String.format("PN-INIT-%03d", i + 1));
            r.setSupplier(sup);
            r.setCreator(ds1);
            r.setStatus(ApprovalStatus.APPROVED);
            r.setApprover(admin);
            r.setApprovedAt(now.minusDays(60));
            r.setCreatedAt(now.minusDays(60));
            r.setNote("Nhập hàng đầu kỳ");
            em.persist(r);
            List<Object[]> lots = new ArrayList<>();
            lots.add(new Object[]{String.format("L24%03dA", i + 1), today.minusDays(300), today.plusDays(400 + i * 10L), 40 + (i % 5) * 20});
            lots.add(new Object[]{String.format("L25%03dB", i + 1), today.minusDays(100), today.plusDays(700 + i * 5L), 30 + (i % 3) * 25});
            if (i == 1) lots.set(0, new Object[]{"L24002X", today.minusDays(700), today.plusDays(45), 25});      // lô cận hạn
            if (i == 11) lots.add(new Object[]{"L23012Z", today.minusDays(800), today.minusDays(10), 12});        // lô đã hết hạn
            if (i == 18) lots = new ArrayList<>(Collections.singletonList(new Object[]{"L25019A", today.minusDays(50), today.plusDays(500), 6}));      // sắp hết hàng
            long importPrice = Math.round(p.getPrice() * 0.72);
            for (Object[] lot : lots) {
                int qty = (int) lot[3] * factor;
                ReceiptItem ri = new ReceiptItem();
                ri.setReceipt(r);
                ri.setProduct(p);
                ri.setBatchNo((String) lot[0]);
                ri.setMfgDate((LocalDate) lot[1]);
                ri.setExpDate((LocalDate) lot[2]);
                ri.setQuantity(qty);
                ri.setImportPrice(importPrice);
                em.persist(ri);
                Batch b = new Batch();
                b.setProduct(p);
                b.setBatchNo((String) lot[0]);
                b.setMfgDate((LocalDate) lot[1]);
                b.setExpDate((LocalDate) lot[2]);
                b.setQuantity(qty);
                b.setImportPrice(importPrice);
                b.setSupplier(sup);
                b.setReceipt(r);
                em.persist(b);
            }
        }

        // Thuốc tương đương cùng hoạt chất (được phép thay khi duyệt đơn)
        bySlug.get("augmentin-625mg").getEquivalents().add(bySlug.get("amoxicillin-500mg-domesco"));

        // Phiếu nhập đang chờ admin duyệt
        Receipt pending = new Receipt();
        pending.setCode("PN-DEMO-CHO-DUYET");
        pending.setSupplier(sup2);
        pending.setCreator(ds1);
        pending.setNote("Bổ sung hàng Omega-3 sắp hết");
        em.persist(pending);
        ReceiptItem pi = new ReceiptItem();
        pi.setReceipt(pending);
        pi.setProduct(products.get(18));
        pi.setBatchNo("L26019C");
        pi.setMfgDate(today.minusDays(20));
        pi.setExpDate(today.plusDays(720));
        pi.setQuantity(50);
        pi.setImportPrice(285000);
        em.persist(pi);

        // Mã giảm giá
        voucher("WELCOME10", "Giảm 10% cho đơn từ 100.000đ (tối đa 50.000đ)", "PERCENT", 10, 100000, 50000L, 1000, -30, 180, v -> v.setShowInWallet(false));
        voucher("GIAM30K", "Giảm 30.000đ cho đơn từ 300.000đ", "FIXED", 30000, 300000, null, 200, -10, 60, v -> { });
        voucher("FREESHIP20", "Giảm 20.000đ cho đơn từ 150.000đ", "FIXED", 20000, 150000, null, 500, -5, 90, v -> { });
        voucher("VITAMIN15", "Giảm 15% sản phẩm Vitamin & Khoáng chất từ 200.000đ (tối đa 40.000đ)", "PERCENT", 15, 200000, 40000L, 300, -5, 45,
                v -> v.setCategory(cats.get(5)));
        voucher("VIPVANG", "Thành viên Vàng trở lên: giảm 50.000đ cho đơn từ 400.000đ", "FIXED", 50000, 400000, null, null, -1, 90, v -> {
            v.setMinTier(MemberTier.VANG);
            v.setPerUserLimit(2);
        });
        voucher("MOIDEN25K", "Khách mua lần đầu: giảm 25.000đ cho đơn từ 150.000đ", "FIXED", 25000, 150000, null, null, -1, 120, v -> {
            v.setNewCustomerOnly(true);
            v.setPerUserLimit(1);
        });

        // Khuyến mãi: flash sale, combo, mua X tặng Y (chỉ OTC / TPCN)
        Product vitC = byName.get("Viên sủi Vitamin C 1000mg");
        Promotion flash = promotion("Flash sale Vitamin C tăng đề kháng", Promotion.FLASH_SALE, now.minusHours(2), now.plusDays(2).withHour(23).withMinute(59));
        flash.setProduct(vitC);
        flash.setSalePrice(Math.round(vitC.getPrice() * 0.7 / 100) * 100);
        flash.setQuantityLimit(50);
        flash.setSoldCount(12);
        Promotion combo = promotion("Combo tăng đề kháng: Vitamin C + Canxi D3", Promotion.COMBO, now.minusDays(3), now.plusDays(30));
        combo.setComboDiscount(20000L);
        promotionItem(combo, vitC);
        promotionItem(combo, byName.get("Canxi D3 Corbiere"));
        Promotion gift = promotion("Mua 2 Omega-3 tặng khẩu trang", Promotion.GIFT, now.minusDays(1), now.plusDays(20));
        gift.setProduct(byName.get("Omega-3 Fish Oil 1000mg"));
        gift.setBuyQuantity(2);
        gift.setGiftProduct(byName.get("Khẩu trang y tế 4 lớp"));
        gift.setGiftQuantity(1);

        for (int i = 0; i < POSTS.length; i++) {
            Post post = new Post();
            post.setTitle(POSTS[i][0]);
            post.setSlug(Texts.slugify(POSTS[i][0]));
            post.setSummary(POSTS[i][1]);
            post.setContent(POSTS[i][2]);
            post.setAuthor(i % 2 == 0 ? ds1 : ds2);
            post.setPublished(true);
            post.setCreatedAt(now.minusDays((i + 1) * 3L));
            em.persist(post);
        }
        em.flush();

        // Đơn hàng đã hoàn thành trong 28 ngày qua (để có số liệu báo cáo)
        List<Product> sellable = products.stream().filter(p -> p.getDrugType() != DrugType.ETC && p.getDrugType() != DrugType.SPECIAL).toList();
        PaymentMethod[] methods = {PaymentMethod.COD, PaymentMethod.PAYOS, PaymentMethod.VNPAY, PaymentMethod.BANK_TRANSFER};
        for (int d = 28; d >= 1; d--) {
            int n = 1 + d % 3;
            for (int k = 0; k < n; k++) {
                User u = customers[(d + k) % 3];
                LinkedHashMap<Long, Product> picks = new LinkedHashMap<>();
                Product a = sellable.get((d * 3 + k) % sellable.size());
                Product b = sellable.get((d * 7 + k * 5) % sellable.size());
                picks.put(a.getId(), a);
                picks.put(b.getId(), b);
                LocalDateTime created = now.minusDays(d).withHour(9 + k * 3).withMinute(15).withSecond(0);
                PaymentMethod pm = methods[(d + k) % 4];
                Order o = newOrder(u, created, pm, OrderStatus.PREPARING, null);
                int idx = 0;
                for (Product p : picks.values()) addItem(o, p, 1 + (d + idx++) % 2);
                finishTotals(o, o.getSubtotal() >= 300000 ? 0 : 20000);
                em.flush();
                stock.allocate(o);
                User handler = k % 2 == 0 ? ds1 : ds2;
                o.setStatus(OrderStatus.COMPLETED);
                o.setPaymentStatus(PaymentStatus.PAID);
                o.setHandler(handler);
                o.setCompletedAt(created.plusDays(1));
                o.setCarrier("Giao Hàng Nhanh (GHN)");
                o.setTrackingCode("GHN" + md5(o.getCode()).substring(0, 8).toUpperCase());
                o.setPointsEarned((int) (o.getTotal() / 10000));
                o.setCostAmount(stock.costOf(o));
                history(o, OrderStatus.PENDING, "Khách đặt hàng", u, created);
                history(o, OrderStatus.CONFIRMED, "Nhà thuốc xác nhận đơn", handler, created.plusMinutes(20));
                history(o, OrderStatus.PACKED, "Đã soạn hàng và đóng gói", handler, created.plusHours(2));
                history(o, OrderStatus.SHIPPING, "Bàn giao cho đơn vị vận chuyển", handler, created.plusHours(3));
                history(o, OrderStatus.COMPLETED, "Giao hàng thành công", handler, created.plusDays(1));
                PaymentTransaction t = new PaymentTransaction();
                t.setOrder(o);
                t.setGateway(pm.name());
                t.setAmount(BigDecimal.valueOf(o.getTotal()));
                t.setStatus(PaymentTransaction.SUCCESS);
                t.setTransactionId(pm.isGateway() ? String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999)) : null);
                t.setGatewayOrderId(o.getCode());
                t.setMessage(pm == PaymentMethod.COD ? "Thu tiền khi giao hàng" : "Thanh toán thành công");
                t.setPaidAt(pm == PaymentMethod.COD ? created.plusDays(1) : created.plusMinutes(3));
                t.setCreatedAt(created);
                t.setUpdatedAt(created);
                em.persist(t);
                u.setPoints(u.getPoints() + (int) (o.getTotal() / 10000));
            }
        }

        // Đơn có thuốc kê đơn đang chờ dược sĩ duyệt
        writeSampleRx(today);
        Order rxOrder = newOrder(kh1, now.minusHours(2), PaymentMethod.COD, OrderStatus.PENDING_RX, "DHDEMORX1");
        rxOrder.setNote("Giao giờ hành chính");
        addItem(rxOrder, bySlug.get("augmentin-625mg"), 1);
        addItem(rxOrder, bySlug.get("panadol-extra"), 1);
        rxOrder.setNeedsPrescription(true);
        finishTotals(rxOrder, 0);
        Prescription rx = new Prescription();
        rx.setOrder(rxOrder);
        rx.setUser(kh1);
        rx.setImage("sample-rx-1.svg");
        rx.setCustomerNote("Đơn bác sĩ kê hôm nay");
        rx.setStatus(ApprovalStatus.PENDING);
        rx.setCreatedAt(now.minusHours(2));
        em.persist(rx);
        history(rxOrder, OrderStatus.PENDING_RX, "Khách đặt hàng kèm đơn thuốc", kh1, now.minusHours(2));

        // Đơn thường chờ xác nhận + đơn đang giao
        Order o2 = newOrder(kh2, now.minusHours(1), PaymentMethod.COD, OrderStatus.PENDING, "DHDEMO002");
        addItem(o2, bySlug.get("smecta-huong-cam"), 2);
        finishTotals(o2, 15000);
        history(o2, OrderStatus.PENDING, "Khách đặt hàng", kh2, now.minusHours(1));
        Order o3 = newOrder(kh3, now.minusHours(20), PaymentMethod.PAYOS, OrderStatus.PREPARING, "DHDEMO003");
        addItem(o3, byName.get("Omega-3 Fish Oil 1000mg"), 1);
        finishTotals(o3, 0);
        em.flush();
        stock.allocate(o3);
        o3.setStatus(OrderStatus.SHIPPING);
        o3.setPaymentStatus(PaymentStatus.PAID);
        o3.setHandler(ds2);
        o3.setCarrier("Giao Hàng Nhanh (GHN)");
        o3.setTrackingCode("GHNDEMO003");
        o3.setShippingStatus("delivering");
        history(o3, OrderStatus.PENDING, "Khách đặt hàng", kh3, now.minusHours(20));
        history(o3, OrderStatus.SHIPPING, "Bàn giao cho GHN, mã vận đơn GHNDEMO003", ds2, now.minusHours(4));
        PaymentTransaction t3 = new PaymentTransaction();
        t3.setOrder(o3);
        t3.setGateway("PAYOS");
        t3.setAmount(BigDecimal.valueOf(o3.getTotal()));
        t3.setStatus(PaymentTransaction.SUCCESS);
        t3.setTransactionId("3123456789");
        t3.setGatewayOrderId(String.valueOf(o3.getId() * 1000 + 1));
        t3.setMessage("Thành công.");
        t3.setPaidAt(now.minusHours(20));
        em.persist(t3);

        review(bySlug.get("panadol-extra"), kh2, 5, "Thuốc giảm đau nhanh, giao hàng nhanh.");
        review(bySlug.get("smecta-huong-cam"), kh3, 4, "Dùng ổn, đóng gói cẩn thận.");
        review(vitC, kh1, 5, "Vị cam dễ uống, giá flash sale rất tốt.");

        Conversation conv = new Conversation();
        conv.setCustomer(kh3);
        conv.setPharmacist(ds1);
        conv.setMode("HUMAN");
        conv.setCreatedAt(now.minusMinutes(30));
        conv.setUpdatedAt(now.minusMinutes(20));
        em.persist(conv);
        message(conv, kh3, null, "Chào dược sĩ, tôi bị tăng huyết áp, có dùng được Decolgen khi bị cảm không ạ?", now.minusMinutes(30));
        message(conv, ds1, null, "Chào anh, Decolgen có chứa phenylephrin có thể làm tăng huyết áp, anh không nên dùng. Anh có thể dùng paracetamol đơn thuần để hạ sốt, giảm đau và rửa mũi bằng nước muối sinh lý nhé.", now.minusMinutes(20));
        Conversation conv2 = new Conversation();
        conv2.setCustomer(kh2);
        conv2.setMode("HUMAN");
        conv2.setHandoffReason("Đối tượng đặc biệt: \"mang thai\"");
        conv2.setAiSummary("Khách nữ đang mang thai 5 tháng, bị cảm 2 ngày: sổ mũi, đau họng nhẹ, không sốt. Chưa dùng thuốc gì. Hỏi thuốc cảm dùng được khi mang thai. Cần hỏi thêm: dị ứng thuốc, bệnh nền.");
        conv2.setHandedOffAt(now.minusMinutes(5));
        conv2.setCreatedAt(now.minusMinutes(6));
        conv2.setUpdatedAt(now.minusMinutes(5));
        em.persist(conv2);
        message(conv2, kh2, null, "Mình đang mang thai 5 tháng, bị cảm 2 ngày nay sổ mũi, đau họng, uống thuốc gì được ạ?", now.minusMinutes(6));
        message(conv2, null, "AI", "Với phụ nữ mang thai / cho con bú, trẻ nhỏ hoặc người có bệnh gan, thận, việc dùng thuốc cần dược sĩ tư vấn trực tiếp. Mình chuyển bạn cho dược sĩ nhé.", now.minusMinutes(5));
        message(conv2, null, "SYSTEM", "Đã chuyển cuộc trò chuyện cho dược sĩ. Dược sĩ sẽ trả lời sớm nhất.", now.minusMinutes(5));

        // Lịch sử xem sản phẩm (đã xem gần đây, gợi ý)
        for (int i = 1; i <= 120; i++) {
            ProductView v = new ProductView();
            v.setUserId(i % 4 == 0 ? null : customers[i % 3].getId());
            v.setSessionId("seed" + (i % 17));
            v.setProduct(products.get((i * 7) % products.size()));
            v.setViewedAt(now.minusDays(i % 28).minusMinutes(i * 13L));
            em.persist(v);
        }
        UserNotification noti = new UserNotification();
        noti.setUser(ds1);
        noti.setMessage("Đơn DHDEMORX1 có thuốc kê đơn cần duyệt");
        noti.setLink("/staff/prescriptions");
        em.persist(noti);
        AuditLog al = new AuditLog();
        al.setUser(admin);
        al.setAction("system.seed");
        al.setDetail("Khởi tạo dữ liệu mẫu");
        al.setCreatedAt(now);
        em.persist(al);

        // Phiếu hủy chờ duyệt
        Batch b = em.createQuery("select b from Batch b where b.product.id = :p order by b.expDate", Batch.class)
                .setParameter("p", products.get(1).getId()).setMaxResults(1).getSingleResult();
        StockAdjustment adj = new StockAdjustment();
        adj.setBatch(b);
        adj.setQuantity(-3);
        adj.setType("WRITE_OFF");
        adj.setReason("Hộp bị móp, ẩm - đề nghị hủy");
        adj.setUser(ds3);
        adj.setStatus(ApprovalStatus.PENDING);
        em.persist(adj);

        log.info("Đã tạo dữ liệu mẫu. Tài khoản: {}{}, duocsi@hieuthuoc.vn/duocsi123, khachhang@gmail.com/123456",
                admin.getEmail(), local() ? "/admin123" : " (mật khẩu ADMIN_PASSWORD)");
    }

    private User user(Role role, String name, String email, String phone, String password, String license, Map<String, String> extra) {
        User u = new User();
        u.setRole(role);
        u.setFullName(name);
        u.setEmail(email);
        u.setPhone(phone);
        u.setPassword(encoder.encode(password));
        u.setLicenseNo(license);
        u.setCreatedAt(LocalDateTime.now().withNano(0).minusDays(90));
        u.setDegree(extra.get("degree"));
        u.setAllergies(extra.get("allergies"));
        u.setChronicConditions(extra.get("chronic_conditions"));
        u.setGender(extra.get("gender"));
        if (extra.get("birthday") != null) u.setBirthday(LocalDate.parse(extra.get("birthday")));
        em.persist(u);
        return u;
    }

    private void address(User u, String line, String province) {
        addresses.put(u.getId(), new String[]{line, province});
        Address a = new Address();
        a.setUser(u);
        a.setRecipient(u.getFullName());
        a.setPhone(u.getPhone());
        a.setAddressLine(line);
        a.setProvince(province);
        a.setDefault(true);
        em.persist(a);
    }

    private Category category(String name, String slug, String icon, int sort, Category parent) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        c.setIcon(icon);
        c.setSortOrder(sort);
        c.setParent(parent);
        em.persist(c);
        return c;
    }

    private Supplier supplier(String name, String phone, String email, String address, String taxCode) {
        Supplier s = new Supplier();
        s.setName(name);
        s.setPhone(phone);
        s.setEmail(email);
        s.setAddress(address);
        s.setTaxCode(taxCode);
        em.persist(s);
        return s;
    }

    private void voucher(String code, String desc, String type, long value, long min, Long max, Integer limit, int startDays, int endDays,
                         java.util.function.Consumer<Voucher> extra) {
        Voucher v = new Voucher();
        v.setCode(code);
        v.setDescription(desc);
        v.setType(type);
        v.setDiscountValue(value);
        v.setMinOrder(min);
        v.setMaxDiscount(max);
        v.setUsageLimit(limit);
        v.setStartDate(LocalDate.now().plusDays(startDays));
        v.setEndDate(LocalDate.now().plusDays(endDays));
        extra.accept(v);
        em.persist(v);
    }

    private Promotion promotion(String name, String type, LocalDateTime start, LocalDateTime end) {
        Promotion p = new Promotion();
        p.setName(name);
        p.setType(type);
        p.setStartAt(start);
        p.setEndAt(end);
        p.setActive(true);
        em.persist(p);
        return p;
    }

    private void promotionItem(Promotion promo, Product p) {
        PromotionItem it = new PromotionItem();
        it.setPromotion(promo);
        it.setProduct(p);
        it.setQuantity(1);
        em.persist(it);
        promo.getItems().add(it);
    }

    private Order newOrder(User u, LocalDateTime created, PaymentMethod pm, OrderStatus status, String code) {
        String[] addr = addresses.get(u.getId());
        Order o = new Order();
        o.setCode(code != null ? code : Texts.code("DH"));
        o.setUser(u);
        o.setRecipient(u.getFullName());
        o.setPhone(u.getPhone());
        o.setAddress(addr[0]);
        o.setProvince(addr[1]);
        o.setPaymentMethod(pm);
        o.setStatus(status);
        o.setCreatedAt(created);
        o.setUpdatedAt(created);
        em.persist(o);
        return o;
    }

    private void addItem(Order o, Product p, int qty) {
        OrderItem it = new OrderItem();
        it.setOrder(o);
        it.setProduct(p);
        it.setProductName(p.getName());
        it.setUnit(p.getUnit());
        it.setDrugType(p.getDrugType());
        it.setPrice(p.getPrice());
        it.setQuantity(qty);
        it.setUnitFactor(1);
        em.persist(it);
        o.getItems().add(it);
        o.setSubtotal(o.getSubtotal() + p.getPrice() * qty);
    }

    private void finishTotals(Order o, long shipping) {
        o.setShippingFee(shipping);
        o.setTotal(o.getSubtotal() + shipping);
    }

    private void history(Order o, OrderStatus status, String note, User user, LocalDateTime at) {
        OrderHistory h = new OrderHistory();
        h.setOrder(o);
        h.setStatus(status);
        h.setNote(note);
        h.setUser(user);
        h.setCreatedAt(at);
        em.persist(h);
    }

    private void review(Product p, User u, int rating, String comment) {
        Review r = new Review();
        r.setProduct(p);
        r.setUser(u);
        r.setRating(rating);
        r.setComment(comment);
        em.persist(r);
    }

    private void message(Conversation c, User sender, String kind, String body, LocalDateTime at) {
        Message m = new Message();
        m.setConversation(c);
        m.setSender(sender);
        m.setKind(kind);
        m.setBody(body);
        m.setCreatedAt(at);
        m.setUpdatedAt(at);
        em.persist(m);
    }

    private static String md5(String s) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return "00000000";
        }
    }

    private void writeSampleRx(LocalDate today) {
        String date = today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="600" height="800" viewBox="0 0 600 800">
                <rect width="600" height="800" fill="#fffef6"/><rect x="20" y="20" width="560" height="760" fill="none" stroke="#999"/>
                <text x="300" y="70" font-family="Arial" font-size="22" text-anchor="middle" font-weight="bold">BỆNH VIỆN ĐA KHOA DEMO</text>
                <text x="300" y="120" font-family="Arial" font-size="28" text-anchor="middle" font-weight="bold">ĐƠN THUỐC</text>
                <text x="50" y="180" font-family="Arial" font-size="16">Họ tên: Trần Văn An      Tuổi: 35      Giới tính: Nam</text>
                <text x="50" y="210" font-family="Arial" font-size="16">Chẩn đoán: Viêm họng cấp</text>
                <text x="50" y="270" font-family="Arial" font-size="16">1. Augmentin 625mg  x 14 viên</text>
                <text x="70" y="295" font-family="Arial" font-size="14" fill="#555">Ngày uống 2 lần, mỗi lần 1 viên, sau ăn</text>
                <text x="50" y="335" font-family="Arial" font-size="16">2. Paracetamol 500mg x 10 viên</text>
                <text x="70" y="360" font-family="Arial" font-size="14" fill="#555">Uống khi sốt trên 38.5 độ</text>
                <text x="380" y="620" font-family="Arial" font-size="16">Ngày %s</text>
                <text x="380" y="650" font-family="Arial" font-size="16">Bác sĩ điều trị</text>
                <text x="370" y="720" font-family="Arial" font-size="18" font-style="italic">BS. Lê Minh Tuấn</text>
                <text x="300" y="770" font-family="Arial" font-size="12" text-anchor="middle" fill="#c00">ẢNH MẪU DÙNG CHO DEMO</text>
                </svg>
                """.formatted(date);
        try {
            Path dir = files.publicRoot().getParent().resolve("private").resolve("prescriptions");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("sample-rx-1.svg"), svg, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Không ghi được ảnh đơn thuốc mẫu: {}", e.getMessage());
        }
    }
}
