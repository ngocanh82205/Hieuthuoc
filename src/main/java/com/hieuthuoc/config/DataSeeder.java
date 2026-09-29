package com.hieuthuoc.config;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.*;
import com.hieuthuoc.service.FileStorageService;
import com.hieuthuoc.service.StockService;
import com.hieuthuoc.service.Texts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Tạo dữ liệu mẫu khi database trống (tắt bằng app.seed-demo-data=false). */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {
    private final UserRepository userRepo;
    private final StaffRoleRepository staffRoleRepo;
    private final WarehouseRepository warehouseRepo;
    private final StockAdjustmentRepository adjustmentRepo;
    private final SupplierPaymentRepository supplierPaymentRepo;
    private final DrugInteractionRepository interactionRepo;
    private final PromotionRepository promotionRepo;
    private final StaticPageRepository pageRepo;
    private final ShippingZoneRepository zoneRepo;
    private final com.hieuthuoc.service.CatalogService catalogService;
    private final AddressRepository addressRepo;
    private final CategoryRepository categoryRepo;
    private final SupplierRepository supplierRepo;
    private final ProductRepository productRepo;
    private final ReceiptRepository receiptRepo;
    private final BatchRepository batchRepo;
    private final VoucherRepository voucherRepo;
    private final PostRepository postRepo;
    private final OrderRepository orderRepo;
    private final ReviewRepository reviewRepo;
    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;
    private final NotificationRepository notificationRepo;
    private final AuditLogRepository auditRepo;
    private final StockService stockService;
    private final FileStorageService files;
    private final PasswordEncoder encoder;
    private final ProductQuestionRepository questionRepo;
    private final CallbackRequestRepository callbackRepo;

    private static final String[][] CATEGORIES = {
            {"Giảm đau - Hạ sốt", "bi-thermometer-half"},
            {"Kháng sinh - Kháng khuẩn", "bi-capsule"},
            {"Tim mạch - Huyết áp", "bi-heart-pulse"},
            {"Tiêu hóa", "bi-droplet-half"},
            {"Hô hấp - Cảm cúm", "bi-lungs"},
            {"Vitamin & Khoáng chất", "bi-sun"},
            {"Thực phẩm chức năng", "bi-flower1"},
            {"Dụng cụ y tế", "bi-bandaid"},
            {"Chăm sóc da", "bi-stars"},
            {"Thần kinh", "bi-activity"},
    };

    /** cat, tên, hoạt chất, hàm lượng, dạng bào chế, quy cách, SĐK, NSX, nước, loại, ĐVT, giá, giá cũ, tối đa/đơn, mô tả, cách dùng, chống chỉ định, tác dụng phụ */
    /** Thuốc bán lẻ theo vỉ: slug -> {đơn vị gốc, số vỉ trong 1 hộp, giá 1 vỉ}. Giá hộp giữ như bảng PRODUCTS. */
    private static final Map<String, Object[]> SPLIT = Map.of(
            "panadol-extra", new Object[]{"Vỉ", 15, 12_500L},
            "efferalgan-500mg-vien-sui", new Object[]{"Vỉ", 4, 12_500L},
            "ibuprofen-400mg", new Object[]{"Vỉ", 10, 7_000L},
            "augmentin-625mg", new Object[]{"Vỉ", 2, 110_000L},
            "amoxicillin-500mg-domesco", new Object[]{"Vỉ", 10, 9_000L},
            "amlodipin-5mg-stada", new Object[]{"Vỉ", 3, 11_000L},
            "concor-5mg", new Object[]{"Vỉ", 3, 45_000L},
            "decolgen-nd", new Object[]{"Vỉ", 25, 5_000L});

    private static final Object[][] PRODUCTS = {
            {0, "Panadol Extra", "Paracetamol, Caffeine", "500mg/65mg", "Viên nén bao phim", "Hộp 15 vỉ x 12 viên", "VD-21189-14", "GSK", "Việt Nam", "OTC", "Hộp", 185000, 199000, 5,
                    "Giảm các cơn đau nhẹ đến vừa: đau đầu, đau nửa đầu, đau cơ, đau bụng kinh, đau họng, đau răng; hạ sốt.",
                    "Người lớn và trẻ em trên 12 tuổi: 1-2 viên mỗi 4-6 giờ khi cần. Không quá 8 viên/24 giờ.",
                    "Quá mẫn với paracetamol hoặc caffeine. Suy gan nặng.", "Hiếm gặp: phát ban, buồn nôn. Dùng quá liều có thể gây tổn thương gan."},
            {0, "Efferalgan 500mg viên sủi", "Paracetamol", "500mg", "Viên sủi", "Hộp 4 vỉ x 4 viên", "VN-19954-16", "UPSA", "Pháp", "OTC", "Hộp", 48000, null, 5,
                    "Điều trị triệu chứng các chứng đau và/hoặc sốt như đau đầu, tình trạng như cúm, đau răng, nhức mỏi cơ.",
                    "Hòa tan hoàn toàn viên thuốc vào cốc nước. Người lớn: 1-2 viên/lần, cách nhau ít nhất 4 giờ.",
                    "Suy gan. Quá mẫn với paracetamol. Chế độ ăn kiêng muối.", "Phản ứng dị ứng, giảm tiểu cầu (rất hiếm)."},
            {0, "Hapacol 250 bột sủi trẻ em", "Paracetamol", "250mg", "Bột sủi bọt", "Hộp 24 gói x 1,5g", "VD-20560-14", "DHG Pharma", "Việt Nam", "OTC", "Hộp", 42000, null, 5,
                    "Hạ sốt, giảm đau cho trẻ em trong các trường hợp cảm cúm, nhiễm khuẩn, mọc răng, sau tiêm chủng.",
                    "Trẻ 3-6 tuổi: 1 gói/lần; trẻ 7-12 tuổi: 2 gói/lần. Cách nhau 4-6 giờ, không quá 5 lần/ngày.",
                    "Quá mẫn với paracetamol. Trẻ bị suy gan, thiếu G6PD.", "Ít gặp: ban da, buồn nôn."},
            {0, "Ibuprofen 400mg", "Ibuprofen", "400mg", "Viên nén bao phim", "Hộp 10 vỉ x 10 viên", "VD-24520-16", "Stada Việt Nam", "Việt Nam", "OTC", "Hộp", 65000, null, 3,
                    "Giảm đau, kháng viêm trong đau đầu, đau răng, đau bụng kinh, đau cơ xương khớp; hạ sốt.",
                    "Người lớn: 1 viên x 2-3 lần/ngày, uống sau ăn.",
                    "Loét dạ dày tá tràng tiến triển, suy gan thận nặng, phụ nữ có thai 3 tháng cuối, hen do aspirin.", "Đau thượng vị, buồn nôn, chóng mặt."},
            {1, "Augmentin 625mg", "Amoxicillin, Acid clavulanic", "500mg/125mg", "Viên nén bao phim", "Hộp 2 vỉ x 7 viên", "VN-20493-17", "GlaxoSmithKline", "Anh", "ETC", "Hộp", 215000, null, 3,
                    "Điều trị nhiễm khuẩn đường hô hấp, tai mũi họng, đường tiết niệu, da và mô mềm do vi khuẩn nhạy cảm.",
                    "Theo chỉ định của bác sĩ. Thông thường: 1 viên x 2 lần/ngày, uống đầu bữa ăn.",
                    "Dị ứng nhóm beta-lactam (penicillin, cephalosporin).", "Tiêu chảy, buồn nôn, phát ban, nhiễm nấm Candida."},
            {1, "Amoxicillin 500mg Domesco", "Amoxicillin", "500mg", "Viên nang cứng", "Hộp 10 vỉ x 10 viên", "VD-25013-16", "Domesco", "Việt Nam", "ETC", "Hộp", 85000, null, 3,
                    "Điều trị nhiễm khuẩn do vi khuẩn nhạy cảm với amoxicillin.", "Theo chỉ định của bác sĩ.", "Dị ứng penicillin.", "Buồn nôn, tiêu chảy, ban da."},
            {1, "Zinnat 500mg", "Cefuroxim", "500mg", "Viên nén bao phim", "Hộp 1 vỉ x 10 viên", "VN-18763-15", "GlaxoSmithKline", "Anh", "ETC", "Hộp", 245000, null, 2,
                    "Kháng sinh cephalosporin thế hệ 2 điều trị nhiễm khuẩn đường hô hấp, tai mũi họng, tiết niệu.",
                    "Theo chỉ định của bác sĩ. Uống sau ăn.", "Dị ứng cephalosporin.", "Tiêu chảy, đau đầu, tăng men gan thoáng qua."},
            {2, "Amlodipin 5mg Stada", "Amlodipin", "5mg", "Viên nén", "Hộp 3 vỉ x 10 viên", "VD-23417-15", "Stada Việt Nam", "Việt Nam", "ETC", "Hộp", 32000, null, 5,
                    "Điều trị tăng huyết áp, đau thắt ngực ổn định.", "Theo chỉ định của bác sĩ. Thường 1 viên/ngày.",
                    "Quá mẫn với dihydropyridin. Hạ huyết áp nặng, sốc tim.", "Phù cổ chân, đỏ bừng mặt, đau đầu."},
            {2, "Concor 5mg", "Bisoprolol fumarat", "5mg", "Viên nén bao phim", "Hộp 3 vỉ x 10 viên", "VN-17135-13", "Merck", "Đức", "ETC", "Hộp", 128000, null, 5,
                    "Điều trị tăng huyết áp, đau thắt ngực, suy tim mạn ổn định.", "Theo chỉ định của bác sĩ. Uống buổi sáng.",
                    "Suy tim cấp, block nhĩ thất độ II-III, nhịp chậm, hen phế quản nặng.", "Mệt mỏi, chóng mặt, lạnh đầu chi."},
            {2, "Losartan 50mg", "Losartan kali", "50mg", "Viên nén bao phim", "Hộp 3 vỉ x 10 viên", "VD-26814-17", "Pymepharco", "Việt Nam", "ETC", "Hộp", 45000, null, 5,
                    "Điều trị tăng huyết áp, bảo vệ thận ở bệnh nhân đái tháo đường type 2.", "Theo chỉ định của bác sĩ.",
                    "Phụ nữ có thai. Quá mẫn với losartan.", "Chóng mặt, tăng kali máu."},
            {3, "Smecta hương cam", "Diosmectit", "3g", "Bột pha hỗn dịch uống", "Hộp 30 gói", "VN-20138-16", "Ipsen", "Pháp", "OTC", "Hộp", 115000, 125000, 5,
                    "Điều trị tiêu chảy cấp và mạn ở trẻ em và người lớn; giảm đau do viêm thực quản, dạ dày.",
                    "Người lớn: 3 gói/ngày, pha trong nửa cốc nước.", "Quá mẫn với thành phần thuốc.", "Táo bón (hiếm)."},
            {3, "Berberin 100mg", "Berberin clorid", "100mg", "Viên nén bao đường", "Lọ 100 viên", "VD-22765-15", "Mekophar", "Việt Nam", "OTC", "Lọ", 16000, null, 10,
                    "Hỗ trợ điều trị tiêu chảy, lỵ trực khuẩn, viêm ruột.", "Người lớn: 4-6 viên/lần x 2 lần/ngày.", "Phụ nữ có thai.", "Táo bón nhẹ."},
            {3, "Omeprazol 20mg", "Omeprazol", "20mg", "Viên nang tan trong ruột", "Hộp 2 vỉ x 7 viên", "VD-28765-18", "DHG Pharma", "Việt Nam", "ETC", "Hộp", 28000, null, 5,
                    "Điều trị loét dạ dày tá tràng, trào ngược dạ dày thực quản.", "Theo chỉ định của bác sĩ. Uống trước ăn sáng 30 phút.",
                    "Quá mẫn với omeprazol.", "Đau đầu, buồn nôn, tiêu chảy."},
            {3, "Enterogermina 2 tỷ/5ml", "Bacillus clausii", "2 tỷ bào tử", "Hỗn dịch uống", "Hộp 20 ống x 5ml", "VN-20720-17", "Sanofi", "Ý", "OTC", "Hộp", 165000, 180000, 5,
                    "Phòng và điều trị rối loạn hệ vi khuẩn đường ruột, tiêu chảy do dùng kháng sinh.",
                    "Người lớn: 2-3 ống/ngày; trẻ em: 1-2 ống/ngày.", "Quá mẫn với thành phần thuốc.", "Chưa ghi nhận."},
            {4, "Decolgen ND", "Paracetamol, Phenylephrin", "500mg/10mg", "Viên nén", "Hộp 25 vỉ x 4 viên", "VD-26017-16", "United Pharma", "Việt Nam", "OTC", "Hộp", 125000, null, 3,
                    "Giảm các triệu chứng cảm cúm: sốt, nhức đầu, sổ mũi, nghẹt mũi.", "Người lớn: 1 viên mỗi 6 giờ.",
                    "Tăng huyết áp nặng, bệnh mạch vành, cường giáp.", "Hồi hộp, mất ngủ nhẹ."},
            {4, "Siro ho Prospan 100ml", "Cao lá thường xuân", "0,7g/100ml", "Siro", "Chai 100ml", "VN-19875-16", "Engelhard", "Đức", "OTC", "Chai", 89000, 95000, 5,
                    "Điều trị viêm đường hô hấp cấp có kèm ho, ho do viêm phế quản mạn tính.",
                    "Người lớn: 5-7,5ml x 3 lần/ngày.", "Không dung nạp fructose.", "Rối loạn tiêu hóa nhẹ."},
            {5, "Viên sủi Vitamin C 1000mg", "Acid ascorbic", "1000mg", "Viên sủi", "Tuýp 10 viên", "VD-29012-18", "Bidiphar", "Việt Nam", "SUPPLEMENT", "Tuýp", 35000, 42000, 10,
                    "Bổ sung vitamin C, tăng cường sức đề kháng, chống oxy hóa.", "Người lớn: 1 viên/ngày, hòa tan trong nước.",
                    "Sỏi thận oxalat, thiếu G6PD.", "Dùng liều cao có thể gây tiêu chảy."},
            {5, "Canxi D3 Corbiere", "Calci, Vitamin D3", "500mg/200IU", "Dung dịch uống", "Hộp 30 ống x 5ml", "VD-24098-16", "Sanofi", "Việt Nam", "SUPPLEMENT", "Hộp", 110000, null, 5,
                    "Bổ sung canxi và vitamin D3 cho trẻ em đang lớn, phụ nữ có thai, người cao tuổi.", "Uống 1-2 ống/ngày.",
                    "Tăng canxi máu, sỏi thận.", "Táo bón nhẹ."},
            {6, "Omega-3 Fish Oil 1000mg", "Dầu cá (EPA, DHA)", "1000mg", "Viên nang mềm", "Lọ 100 viên", "TPCN 4512/2020", "Blackmores", "Úc", "SUPPLEMENT", "Lọ", 395000, 450000, 3,
                    "Hỗ trợ tim mạch, não bộ và thị lực. Sản phẩm này không phải là thuốc, không có tác dụng thay thế thuốc chữa bệnh.",
                    "Uống 1 viên x 1-3 lần/ngày, sau bữa ăn.", "Người đang dùng thuốc chống đông cần hỏi ý kiến bác sĩ.", "Ợ hơi mùi cá."},
            {6, "Ginkgo Biloba 120mg", "Cao bạch quả", "120mg", "Viên nén", "Hộp 3 vỉ x 10 viên", "TPCN 3321/2021", "Traphaco", "Việt Nam", "SUPPLEMENT", "Hộp", 99000, null, 5,
                    "Hỗ trợ tăng cường tuần hoàn máu não. Sản phẩm này không phải là thuốc, không có tác dụng thay thế thuốc chữa bệnh.",
                    "Uống 1 viên x 2 lần/ngày.", "Người đang dùng thuốc chống đông, phụ nữ có thai.", "Đau đầu nhẹ (hiếm)."},
            {7, "Máy đo huyết áp bắp tay Omron HEM-7121", null, null, null, "Hộp 1 máy", "220001234/PCBB-HN", "Omron", "Nhật Bản", "DEVICE", "Cái", 890000, 990000, 2,
                    "Máy đo huyết áp tự động bắp tay, công nghệ IntelliSense, phát hiện nhịp tim bất thường.",
                    "Quấn vòng bít ngang tim, ngồi yên 5 phút trước khi đo.", null, null},
            {7, "Nhiệt kế điện tử Microlife MT200", null, null, null, "Hộp 1 cái", "220005678/PCBA-HN", "Microlife", "Thụy Sĩ", "DEVICE", "Cái", 75000, null, 5,
                    "Nhiệt kế điện tử đo ở miệng, nách, hậu môn; cho kết quả sau 60 giây.", "Đặt đầu đo đúng vị trí đến khi có tiếng bíp.", null, null},
            {7, "Khẩu trang y tế 4 lớp", null, null, null, "Hộp 50 cái", "220009999/PCBA-HCM", "Nam Anh", "Việt Nam", "DEVICE", "Hộp", 35000, 45000, 20,
                    "Khẩu trang y tế 4 lớp kháng khuẩn, lọc bụi.", "Dùng 1 lần.", null, null},
            {8, "Kem chống nắng La Roche-Posay Anthelios SPF50+", null, null, "Kem", "Tuýp 50ml", "123456/21/CBMP-QLD", "La Roche-Posay", "Pháp", "COSMETIC", "Tuýp", 485000, 530000, 3,
                    "Kem chống nắng phổ rộng, kiểm soát dầu, dành cho da nhạy cảm.", "Thoa trước khi ra nắng 20 phút, thoa lại sau mỗi 2 giờ.", null, null},
            {8, "Sữa rửa mặt Cetaphil Gentle Skin Cleanser", null, null, "Sữa rửa mặt", "Chai 500ml", "98765/20/CBMP-QLD", "Galderma", "Canada", "COSMETIC", "Chai", 345000, null, 3,
                    "Làm sạch dịu nhẹ, không gây kích ứng, phù hợp da nhạy cảm.", "Dùng 2 lần/ngày.", null, null},
            {9, "Seduxen 5mg", "Diazepam", "5mg", "Viên nén", "Hộp 10 vỉ x 10 viên", "VN-16582-13", "Gedeon Richter", "Hungary", "SPECIAL", "Hộp", 60000, null, null,
                    "Thuốc hướng thần - chỉ bán tại nhà thuốc theo đơn thuốc \"H\" của bác sĩ. Không bán online.",
                    "Theo chỉ định của bác sĩ.", "Suy hô hấp, nhược cơ, ngưng thở khi ngủ.", "Buồn ngủ, lệ thuộc thuốc."},
    };

    private static final String[][] POSTS = {
            {"Cách dùng thuốc hạ sốt đúng cho trẻ em",
                    "Hướng dẫn cha mẹ dùng paracetamol an toàn, đúng liều theo cân nặng của trẻ.",
                    "Paracetamol là thuốc hạ sốt được khuyến cáo phổ biến cho trẻ em. Liều thường dùng là 10-15mg/kg cân nặng mỗi lần, cách nhau 4-6 giờ, không quá 4-5 lần/ngày.\n\n"
                            + "Chỉ nên dùng thuốc hạ sốt khi trẻ sốt từ 38,5°C trở lên. Với mức sốt thấp hơn, hãy cho trẻ mặc thoáng, uống nhiều nước và lau người bằng nước ấm.\n\n"
                            + "Không phối hợp nhiều sản phẩm cùng chứa paracetamol (ví dụ thuốc cảm và thuốc hạ sốt) vì dễ gây quá liều, ảnh hưởng đến gan.\n\n"
                            + "Đưa trẻ đi khám ngay nếu trẻ dưới 3 tháng tuổi bị sốt, sốt cao liên tục trên 2 ngày, co giật, li bì hoặc nôn nhiều."},
            {"Vì sao không nên tự ý dùng kháng sinh?",
                    "Lạm dụng kháng sinh là nguyên nhân chính dẫn đến tình trạng kháng thuốc.",
                    "Kháng sinh chỉ có tác dụng với vi khuẩn, không có tác dụng với virus gây cảm cúm thông thường. Việc tự ý dùng kháng sinh khi không cần thiết vừa không hiệu quả, vừa làm tăng nguy cơ kháng thuốc.\n\n"
                            + "Theo quy định, kháng sinh là thuốc kê đơn - nhà thuốc chỉ được bán khi có đơn của bác sĩ. Đó cũng là lý do website yêu cầu bạn tải lên đơn thuốc khi mua các sản phẩm này.\n\n"
                            + "Khi được kê kháng sinh, hãy uống đủ liều, đủ thời gian, kể cả khi đã thấy đỡ. Ngưng thuốc sớm tạo điều kiện cho vi khuẩn sống sót và trở nên kháng thuốc."},
            {"Theo dõi huyết áp tại nhà: những điều cần biết",
                    "Đo huyết áp đúng cách giúp kiểm soát bệnh tăng huyết áp hiệu quả hơn.",
                    "Nên đo huyết áp vào cùng thời điểm mỗi ngày, tốt nhất là buổi sáng trước khi uống thuốc và buổi tối trước khi đi ngủ.\n\n"
                            + "Trước khi đo, ngồi nghỉ 5 phút, không hút thuốc, không uống cà phê trong vòng 30 phút. Đặt tay ngang mức tim, quấn vòng bít vừa khít cánh tay.\n\n"
                            + "Ghi lại kết quả vào sổ theo dõi và mang theo khi đi tái khám để bác sĩ điều chỉnh thuốc phù hợp."},
    };

    private StaffRole role(String name) {
        return staffRoleRepo.findByNameIgnoreCase(name).orElseThrow();
    }

    /** Dữ liệu danh mục hệ thống (tạo nếu chưa có, kể cả khi database đã có dữ liệu cũ). */
    private void seedReferenceData() {
        if (staffRoleRepo.count() == 0) {
            staffRoleRepo.save(new StaffRole("Dược sĩ quản lý", "Dược sĩ phụ trách chuyên môn / quản lý nhà thuốc - toàn quyền nghiệp vụ",
                    StaffPermission.values()));
            staffRoleRepo.save(new StaffRole("Dược sĩ", "Duyệt đơn thuốc, tư vấn, xử lý đơn và bán tại quầy",
                    StaffPermission.RX_REVIEW, StaffPermission.ORDER, StaffPermission.CONSULT, StaffPermission.POS));
            staffRoleRepo.save(new StaffRole("Dược sĩ phụ trách kho", "Dược sĩ kiêm quản lý kho: nhập hàng, soạn hàng, kiểm kê, chuyển kho",
                    StaffPermission.RX_REVIEW, StaffPermission.ORDER, StaffPermission.CONSULT, StaffPermission.POS, StaffPermission.INVENTORY));
            staffRoleRepo.save(new StaffRole("Dược sĩ tư vấn", "Dược sĩ chuyên tư vấn / chăm sóc khách hàng: chat, hỏi đáp, gọi lại, theo dõi đơn",
                    StaffPermission.RX_REVIEW, StaffPermission.ORDER, StaffPermission.CONSULT));
            staffRoleRepo.save(new StaffRole("Biên tập viên", "Bài viết sức khỏe, thông tin sản phẩm, kiểm duyệt đánh giá",
                    StaffPermission.CONTENT));
        }
        if (warehouseRepo.count() == 0) {
            warehouseRepo.save(new Warehouse("Kho chính - Nhà thuốc Thanh Xuân", "123 Nguyễn Trãi, Thanh Xuân, Hà Nội", true, true));
            warehouseRepo.save(new Warehouse("Kho dự trữ Long Biên", "KCN Sài Đồng, Long Biên, Hà Nội", false, false));
        }
        if (zoneRepo.count() == 0) {
            zoneRepo.save(new ShippingZone("Nội thành Hà Nội", List.of("Hà Nội"), 15000, 300000L, "2 - 4 giờ", 1));
            zoneRepo.save(new ShippingZone("Miền Bắc", List.of("Hải Phòng", "Tuyên Quang", "Cao Bằng", "Lai Châu", "Lào Cai", "Thái Nguyên", "Điện Biên",
                    "Lạng Sơn", "Sơn La", "Phú Thọ", "Bắc Ninh", "Quảng Ninh", "Hưng Yên", "Ninh Bình"), 25000, 500000L, "1 - 2 ngày", 2));
            zoneRepo.save(new ShippingZone("Miền Trung - Tây Nguyên", List.of("Thanh Hóa", "Nghệ An", "Hà Tĩnh", "Quảng Trị", "Huế", "Đà Nẵng", "Quảng Ngãi",
                    "Gia Lai", "Khánh Hòa", "Lâm Đồng", "Đắk Lắk"), 30000, 500000L, "2 - 3 ngày", 3));
            zoneRepo.save(new ShippingZone("Miền Nam", List.of("TP. Hồ Chí Minh", "Đồng Nai", "Tây Ninh", "Vĩnh Long", "Đồng Tháp", "Cà Mau", "An Giang", "Cần Thơ"),
                    30000, 500000L, "2 - 4 ngày", 4));
        }
        if (pageRepo.count() == 0) {
            pageRepo.save(new StaticPage("chinh-sach-doi-tra", "Chính sách đổi trả & hoàn tiền", """
                    ## Điều kiện đổi trả
                    Khách hàng được đổi/trả trong 7 ngày kể từ khi nhận hàng khi sản phẩm bị lỗi từ nhà sản xuất, giao sai sản phẩm, hư hỏng do vận chuyển hoặc còn nguyên tem niêm phong.

                    ## Không áp dụng đổi trả
                    - Thuốc kê đơn (trừ trường hợp lỗi từ nhà thuốc)
                    - Sản phẩm đã mở niêm phong, đã sử dụng
                    - Sản phẩm cần bảo quản lạnh

                    ## Hoàn tiền
                    Sau khi nhà thuốc duyệt yêu cầu, tiền được hoàn về tài khoản / ví của khách trong 3 - 7 ngày làm việc. Điểm tích lũy đã dùng được hoàn lại.""", 1));
            pageRepo.save(new StaticPage("chinh-sach-giao-hang", "Chính sách giao hàng", """
                    ## Phạm vi & phí giao hàng
                    Nhà thuốc giao hàng toàn quốc. Phí ship tính theo khu vực, miễn phí cho đơn đạt ngưỡng (xem chi tiết khi thanh toán).

                    ## Thời gian
                    - Nội thành Hà Nội: 2 - 4 giờ
                    - Các tỉnh miền Bắc: 1 - 2 ngày
                    - Miền Trung, miền Nam: 2 - 4 ngày

                    Đơn có thuốc kê đơn chỉ được giao sau khi dược sĩ duyệt đơn thuốc.""", 2));
            pageRepo.save(new StaticPage("chinh-sach-bao-mat", "Chính sách bảo mật thông tin", """
                    ## Thông tin thu thập
                    Họ tên, số điện thoại, địa chỉ giao hàng, hồ sơ sức khỏe (dị ứng, bệnh nền) và ảnh đơn thuốc khách hàng cung cấp.

                    ## Mục đích sử dụng
                    Xử lý đơn hàng, tư vấn dùng thuốc an toàn, chăm sóc khách hàng. Ảnh đơn thuốc và hồ sơ sức khỏe chỉ dược sĩ được xem.

                    ## Cam kết
                    Không chia sẻ thông tin cho bên thứ ba trừ khi pháp luật yêu cầu. Khách hàng có thể yêu cầu xem, sửa hoặc xóa dữ liệu cá nhân.""", 3));
            pageRepo.save(new StaticPage("dieu-khoan-su-dung", "Điều khoản sử dụng", """
                    Khi sử dụng website, khách hàng đồng ý cung cấp thông tin chính xác và sử dụng thuốc theo hướng dẫn của bác sĩ, dược sĩ.

                    Thông tin trên website chỉ mang tính tham khảo, không thay thế chẩn đoán và điều trị của bác sĩ.

                    Thuốc kiểm soát đặc biệt không được bán online theo quy định của pháp luật.""", 4));
        }
        if (interactionRepo.count() == 0) {
            for (String[] r : com.hieuthuoc.service.SafetyService.DEFAULT_INTERACTIONS) interactionRepo.save(new DrugInteraction(r[0], r[1], r[2], r[3]));
        }
        if (productRepo.count() > 0) catalogService.syncMasters();
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedReferenceData();
        if (userRepo.count() > 0) return;
        log.info("Database trống - đang tạo dữ liệu mẫu...");
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        User admin = user(Role.ADMIN, "Quản trị viên", "admin@hieuthuoc.vn", "0901000001", "admin123", null);
        User ds1 = user(Role.PHARMACIST, "DS. Nguyễn Thị Lan", "duocsi@hieuthuoc.vn", "0901000002", "duocsi123", "012345/HNO-CCHND");
        User ds2 = user(Role.PHARMACIST, "DS. Phạm Quốc Huy", "duocsi2@hieuthuoc.vn", "0901000003", "duocsi123", "023456/HNO-CCHND");
        // DS. Lan là dược sĩ quản lý: đủ quyền; DS. Huy: dược sĩ bán hàng, cấp thêm quyền nội dung
        ds1.setStaffRole(role("Dược sĩ quản lý"));
        ds1.setDegree("Dược sĩ đại học - ĐH Dược Hà Nội");
        ds1.setShift("Ca sáng 7h - 15h");
        ds2.setStaffRole(role("Dược sĩ"));
        ds2.setPermissions("CONTENT");
        ds2.setDegree("Dược sĩ cao đẳng");
        ds2.setShift("Ca chiều 14h - 22h");
        User kho = user(Role.PHARMACIST, "DS. Vũ Văn Kiên", "duocsi3@hieuthuoc.vn", "0901000004", "duocsi123", "034567/HNO-CCHND");
        kho.setStaffRole(role("Dược sĩ phụ trách kho"));
        kho.setDegree("Dược sĩ đại học - ĐH Y Dược Thái Bình");
        kho.setShift("Hành chính 8h - 17h");
        User cskh = user(Role.PHARMACIST, "DS. Đỗ Thu Hà", "duocsi4@hieuthuoc.vn", "0901000005", "duocsi123", "045678/HNO-CCHND");
        cskh.setStaffRole(role("Dược sĩ tư vấn"));
        cskh.setDegree("Dược sĩ cao đẳng");
        cskh.setShift("Ca chiều 14h - 22h");
        User kh1 = user(Role.CUSTOMER, "Trần Văn An", "khachhang@gmail.com", "0912345678", "123456", null);
        kh1.setAllergies("Dị ứng Aspirin");
        kh1.setChronicConditions("Viêm dạ dày");
        kh1.setGender("Nam");
        kh1.setBirthday(LocalDate.of(1990, 5, 12));
        User kh2 = user(Role.CUSTOMER, "Lê Thị Bình", "binh@gmail.com", "0987654321", "123456", null);
        kh2.setGender("Nữ");
        User kh3 = user(Role.CUSTOMER, "Hoàng Minh Châu", "chau@gmail.com", "0934567890", "123456", null);
        kh3.setChronicConditions("Tăng huyết áp");
        List<User> customers = List.of(kh1, kh2, kh3);
        address(kh1, "45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội");
        address(kh2, "12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội");
        address(kh3, "88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM");

        List<Category> cats = new ArrayList<>();
        for (int i = 0; i < CATEGORIES.length; i++) {
            Category c = new Category();
            c.setName(CATEGORIES[i][0]);
            c.setSlug(Texts.slugify(CATEGORIES[i][0]));
            c.setIcon(CATEGORIES[i][1]);
            c.setSortOrder(i + 1);
            cats.add(categoryRepo.save(c));
        }
        // Danh mục đa cấp: Thuốc > Tim mạch - Huyết áp > Thuốc huyết áp
        Category drugRoot = new Category();
        drugRoot.setName("Thuốc");
        drugRoot.setSlug("thuoc");
        drugRoot.setIcon("bi-capsule-pill");
        drugRoot.setSortOrder(0);
        categoryRepo.save(drugRoot);
        for (int i : new int[]{0, 1, 2, 3, 4, 9}) cats.get(i).setParent(drugRoot);
        Category bp = new Category();
        bp.setName("Thuốc huyết áp");
        bp.setSlug("thuoc-huyet-ap");
        bp.setIcon("bi-heart-pulse");
        bp.setParent(cats.get(2));
        categoryRepo.save(bp);

        Supplier sup1 = supplier("Công ty CP Dược phẩm Trung ương 1", "02438252000", "sales@pharbaco.vn", "160 Tôn Đức Thắng, Hà Nội");
        Supplier sup2 = supplier("Công ty TNHH Phân phối Zuellig Pharma", "02838123456", "order@zuellig.vn", "KCN Tân Tạo, TP.HCM");

        List<Product> products = new ArrayList<>();
        for (int i = 0; i < PRODUCTS.length; i++) {
            Object[] d = PRODUCTS[i];
            Product p = new Product();
            p.setCategory(cats.get((Integer) d[0]));
            p.setName((String) d[1]);
            p.setSlug(Texts.slugify((String) d[1]));
            p.setActiveIngredient((String) d[2]);
            p.setStrength((String) d[3]);
            p.setDosageForm((String) d[4]);
            p.setPackaging((String) d[5]);
            p.setRegistrationNo((String) d[6]);
            p.setManufacturer((String) d[7]);
            p.setCountry((String) d[8]);
            p.setDrugType(DrugType.valueOf((String) d[9]));
            p.setUnit((String) d[10]);
            p.setPrice(((Integer) d[11]).longValue());
            p.setOldPrice(d[12] == null ? null : ((Integer) d[12]).longValue());
            p.setMaxPerOrder((Integer) d[13]);
            Object[] split = SPLIT.get(Texts.slugify((String) d[1]));
            int factor = 1;
            if (split != null) {
                // Đơn vị gốc = Vỉ, thêm đơn vị Hộp quy đổi
                factor = (Integer) split[1];
                p.getUnits().add(new ProductUnit(p, p.getUnit(), factor, p.getPrice()));
                p.setUnit((String) split[0]);
                p.setPrice((Long) split[2]);
                if (p.getOldPrice() != null) p.setOldPrice(Math.round(p.getOldPrice() / (double) factor));
                if (p.getMaxPerOrder() != null) p.setMaxPerOrder(p.getMaxPerOrder() * factor);
            }
            p.setDescription((String) d[14]);
            p.setUsageInstruction((String) d[15]);
            p.setContraindications((String) d[16]);
            p.setSideEffects((String) d[17]);
            p.setMinStock(10);
            if (p.getCategory() == cats.get(2) && p.getActiveIngredient() != null
                    && p.getActiveIngredient().toLowerCase().matches(".*(amlodipin|losartan|bisoprolol).*")) p.setCategory(bp);
            p.setCreatedAt(now.minusDays(60).plusMinutes(i));
            products.add(productRepo.save(p));

            // Phiếu nhập đầu kỳ đã duyệt + lô hàng
            Supplier sup = i % 2 == 0 ? sup1 : sup2;
            Receipt r = new Receipt();
            r.setCode(String.format("PN-INIT-%03d", i + 1));
            r.setSupplier(sup);
            r.setCreatedBy(ds1);
            r.setStatus(ApprovalStatus.APPROVED);
            r.setApprovedBy(admin);
            r.setApprovedAt(now.minusDays(60));
            r.setCreatedAt(now.minusDays(60));
            r.setNote("Nhập hàng đầu kỳ");
            List<Object[]> lots = new ArrayList<>(List.of(
                    new Object[]{String.format("L24%03dA", i + 1), today.minusDays(300), today.plusDays(400 + i * 10L), 40 + (i % 5) * 20},
                    new Object[]{String.format("L25%03dB", i + 1), today.minusDays(100), today.plusDays(700 + i * 5L), 30 + (i % 3) * 25}));
            if (i == 1) lots.set(0, new Object[]{"L24002X", today.minusDays(700), today.plusDays(45), 25});          // lô cận hạn
            if (i == 11) lots.add(new Object[]{"L23012Z", today.minusDays(800), today.minusDays(10), 12});          // lô đã hết hạn
            if (i == 18) lots = new ArrayList<>(List.<Object[]>of(new Object[]{"L25019A", today.minusDays(50), today.plusDays(500), 6})); // sắp hết hàng
            long importPrice = Math.round(p.getPrice() * 0.72);
            for (Object[] lot : lots) {
                ReceiptItem it = new ReceiptItem();
                it.setReceipt(r);
                it.setProduct(p);
                it.setBatchNo((String) lot[0]);
                it.setMfgDate((LocalDate) lot[1]);
                it.setExpDate((LocalDate) lot[2]);
                it.setQuantity((Integer) lot[3] * factor);
                it.setImportPrice(importPrice);
                r.getItems().add(it);
            }
            receiptRepo.save(r);
            for (ReceiptItem it : r.getItems()) {
                Batch b = new Batch();
                b.setProduct(p);
                b.setBatchNo(it.getBatchNo());
                b.setMfgDate(it.getMfgDate());
                b.setExpDate(it.getExpDate());
                b.setQuantity(it.getQuantity());
                b.setImportPrice(importPrice);
                b.setSupplier(sup);
                b.setReceipt(r);
                batchRepo.save(b);
            }
        }
        Map<String, Product> bySlug = new HashMap<>();
        products.forEach(p -> bySlug.put(p.getSlug(), p));

        // Phiếu nhập đang chờ admin duyệt
        Receipt pending = new Receipt();
        pending.setCode("PN-DEMO-CHO-DUYET");
        pending.setSupplier(sup2);
        pending.setCreatedBy(ds1);
        pending.setNote("Bổ sung hàng Omega-3 sắp hết");
        ReceiptItem pi = new ReceiptItem();
        pi.setReceipt(pending);
        pi.setProduct(products.get(18));
        pi.setBatchNo("L26019C");
        pi.setMfgDate(today.minusDays(20));
        pi.setExpDate(today.plusDays(720));
        pi.setQuantity(50);
        pi.setImportPrice(285000);
        pending.getItems().add(pi);
        receiptRepo.save(pending);

        voucher("WELCOME10", "Giảm 10% cho đơn từ 100.000đ (tối đa 50.000đ)", VoucherType.PERCENT, 10, 100000, 50000L, 1000, today.minusDays(30), today.plusDays(180));
        voucher("GIAM30K", "Giảm 30.000đ cho đơn từ 300.000đ", VoucherType.FIXED, 30000, 300000, null, 200, today.minusDays(10), today.plusDays(60));
        voucher("FREESHIP20", "Giảm 20.000đ cho đơn từ 150.000đ", VoucherType.FIXED, 20000, 150000, null, 500, today.minusDays(5), today.plusDays(90));
        voucher("VITAMIN15", "Giảm 15% cho đơn từ 200.000đ (tối đa 40.000đ)", VoucherType.PERCENT, 15, 200000, 40000L, 300, today.minusDays(5), today.plusDays(45));
        voucherRepo.findAll().forEach(v -> v.setShowInWallet(!v.getCode().equals("WELCOME10")));
        // Voucher theo đối tượng
        voucherRepo.findByCodeIgnoreCase("VITAMIN15").ifPresent(v -> v.setCategory(cats.get(5)));
        voucher("VIPVANG", "Thành viên Vàng trở lên: giảm 50.000đ cho đơn từ 400.000đ", VoucherType.FIXED, 50000, 400000, null, null, today.minusDays(1), today.plusDays(90));
        voucherRepo.findByCodeIgnoreCase("VIPVANG").ifPresent(v -> { v.setMinTier(MemberTier.VANG); v.setPerUserLimit(2); v.setShowInWallet(true); });
        voucher("MOIDEN25K", "Khách mua lần đầu: giảm 25.000đ cho đơn từ 150.000đ", VoucherType.FIXED, 25000, 150000, null, null, today.minusDays(1), today.plusDays(120));
        voucherRepo.findByCodeIgnoreCase("MOIDEN25K").ifPresent(v -> { v.setNewCustomerOnly(true); v.setPerUserLimit(1); v.setShowInWallet(true); });

        // Khuyến mãi: flash sale, combo, mua X tặng Y (chỉ OTC / TPCN)
        java.util.function.Function<String, Product> byName = n -> products.stream().filter(x -> x.getName().equals(n)).findFirst().orElseThrow();
        Product vitC = byName.apply("Viên sủi Vitamin C 1000mg");
        Promotion flash = new Promotion();
        flash.setName("Flash sale Vitamin C tăng đề kháng");
        flash.setType(Promotion.FLASH_SALE);
        flash.setProduct(vitC);
        flash.setSalePrice(Math.round(vitC.getPrice() * 0.7 / 100) * 100);
        flash.setQuantityLimit(50);
        flash.setSoldCount(12);
        flash.setStartAt(now.minusHours(2));
        flash.setEndAt(now.plusDays(2).withHour(23).withMinute(59));
        promotionRepo.save(flash);
        Promotion combo = new Promotion();
        combo.setName("Combo tăng đề kháng: Vitamin C + Canxi D3");
        combo.setType(Promotion.COMBO);
        combo.setComboDiscount(20000L);
        combo.getItems().add(new PromotionItem(combo, vitC, 1));
        combo.getItems().add(new PromotionItem(combo, byName.apply("Canxi D3 Corbiere"), 1));
        combo.setStartAt(now.minusDays(3));
        combo.setEndAt(now.plusDays(30));
        promotionRepo.save(combo);
        Promotion gift = new Promotion();
        gift.setName("Mua 2 Omega-3 tặng khẩu trang");
        gift.setType(Promotion.GIFT);
        gift.setProduct(byName.apply("Omega-3 Fish Oil 1000mg"));
        gift.setBuyQuantity(2);
        gift.setGiftProduct(byName.apply("Khẩu trang y tế 4 lớp"));
        gift.setGiftQuantity(1);
        gift.setStartAt(now.minusDays(1));
        gift.setEndAt(now.plusDays(20));
        promotionRepo.save(gift);

        for (int i = 0; i < POSTS.length; i++) {
            Post p = new Post();
            p.setTitle(POSTS[i][0]);
            p.setSlug(Texts.slugify(POSTS[i][0]));
            p.setSummary(POSTS[i][1]);
            p.setContent(POSTS[i][2]);
            p.setAuthor(i % 2 == 0 ? ds1 : ds2);
            p.setCreatedAt(now.minusDays((i + 1) * 3L));
            postRepo.save(p);
        }

        // Đơn hàng đã hoàn thành trong 28 ngày qua (để có số liệu báo cáo)
        List<Product> sellable = products.stream()
                .filter(p -> p.getDrugType() != DrugType.ETC && p.getDrugType() != DrugType.SPECIAL).toList();
        for (int d = 28; d >= 1; d--) {
            int n = 1 + d % 3;
            for (int k = 0; k < n; k++) {
                User u = customers.get((d + k) % 3);
                LinkedHashSet<Product> picks = new LinkedHashSet<>(List.of(
                        sellable.get((d * 3 + k) % sellable.size()), sellable.get((d * 7 + k * 5) % sellable.size())));
                LocalDateTime created = now.minusDays(d).withHour(9 + k * 3).withMinute(15);
                Order o = newOrder(u, created, k % 2 == 1 ? PaymentMethod.ONLINE : PaymentMethod.COD);
                int idx = 0;
                for (Product p : picks) addItem(o, p, 1 + (d + idx++) % 2);
                o.setShippingFee(o.getSubtotal() >= 300000 ? 0 : 20000);
                o.setTotal(o.getSubtotal() + o.getShippingFee());
                o.setStatus(OrderStatus.PREPARING);
                stockService.allocateFefo(o);
                o.setStatus(OrderStatus.COMPLETED);
                o.setPaymentStatus(PaymentStatus.PAID);
                o.setHandledBy(k % 2 == 0 ? ds1 : ds2);
                o.setCompletedAt(created.plusDays(1));
                history(o, OrderStatus.PENDING, "Khách đặt hàng", u, created);
                history(o, OrderStatus.COMPLETED, "Giao hàng thành công", o.getHandledBy(), created.plusDays(1));
                orderRepo.save(o);
                u.setPoints(u.getPoints() + (int) (o.getTotal() / 10000));
            }
        }

        // Đơn có thuốc kê đơn đang chờ dược sĩ duyệt
        files.writeSample(FileStorageService.Kind.PRESCRIPTIONS, "sample-rx-1.svg", sampleRxSvg(today).getBytes(StandardCharsets.UTF_8));
        Order rxOrder = newOrder(kh1, now.minusHours(2), PaymentMethod.COD);
        rxOrder.setCode("DHDEMORX1");
        rxOrder.setNote("Giao giờ hành chính");
        addItem(rxOrder, bySlug.get("augmentin-625mg"), 1);
        addItem(rxOrder, bySlug.get("panadol-extra"), 1);
        rxOrder.setShippingFee(0);
        rxOrder.setTotal(rxOrder.getSubtotal());
        rxOrder.setNeedsPrescription(true);
        rxOrder.setStatus(OrderStatus.PENDING_RX);
        Prescription rx = new Prescription();
        rx.setOrder(rxOrder);
        rx.setUser(kh1);
        rx.setImage("sample-rx-1.svg");
        rx.setCustomerNote("Đơn bác sĩ kê hôm nay");
        rx.setCreatedAt(now.minusHours(2));
        rxOrder.getPrescriptions().add(rx);
        history(rxOrder, OrderStatus.PENDING_RX, "Khách đặt hàng kèm đơn thuốc", kh1, now.minusHours(2));
        orderRepo.save(rxOrder);

        // Đơn thường chờ xác nhận
        Order o2 = newOrder(kh2, now.minusHours(1), PaymentMethod.COD);
        o2.setCode("DHDEMO002");
        addItem(o2, bySlug.get("smecta-huong-cam"), 2);
        o2.setShippingFee(20000);
        o2.setTotal(o2.getSubtotal() + 20000);
        o2.setStatus(OrderStatus.PENDING);
        history(o2, OrderStatus.PENDING, "Khách đặt hàng", kh2, now.minusHours(1));
        orderRepo.save(o2);

        review(bySlug.get("panadol-extra"), kh2, 5, "Thuốc giảm đau nhanh, giao hàng nhanh.");
        ProductQuestion q1 = new ProductQuestion();
        q1.setProduct(bySlug.get("panadol-extra"));
        q1.setUser(kh3);
        q1.setQuestion("Người bị tăng huyết áp có dùng Panadol Extra được không ạ?");
        q1.setAnswer("Panadol Extra có chứa caffeine có thể làm tăng nhịp tim, huyết áp. Người tăng huyết áp nên ưu tiên paracetamol đơn thuần và hỏi ý kiến bác sĩ.");
        q1.setAnsweredBy(ds1);
        q1.setAnsweredAt(now.minusDays(1));
        questionRepo.save(q1);
        ProductQuestion q2 = new ProductQuestion();
        q2.setProduct(bySlug.get("smecta-huong-cam"));
        q2.setUser(kh1);
        q2.setQuestion("Smecta uống trước hay sau bữa ăn ạ? Có uống cùng thuốc khác được không?");
        questionRepo.save(q2);
        CallbackRequest cb = new CallbackRequest();
        cb.setUser(kh2);
        cb.setName(kh2.getFullName());
        cb.setPhone(kh2.getPhone());
        cb.setPreferredTime("Chiều (14h - 17h)");
        cb.setNote("Cần tư vấn thuốc cho bé 3 tuổi bị sốt");
        callbackRepo.save(cb);
        review(bySlug.get("smecta-huong-cam"), kh3, 4, "Dùng ổn, đóng gói cẩn thận.");

        Conversation conv = new Conversation();
        conv.setCustomer(kh3);
        conv.setPharmacist(ds1);
        conv.setCreatedAt(now.minusMinutes(30));
        conv.setUpdatedAt(now.minusMinutes(20));
        conversationRepo.save(conv);
        message(conv, kh3, "Chào dược sĩ, tôi bị tăng huyết áp, có dùng được Decolgen khi bị cảm không ạ?", now.minusMinutes(30));
        message(conv, ds1, "Chào anh, Decolgen có chứa phenylephrin có thể làm tăng huyết áp, anh không nên dùng. "
                + "Anh có thể dùng paracetamol đơn thuần để hạ sốt, giảm đau và rửa mũi bằng nước muối sinh lý nhé.", now.minusMinutes(20));

        // Hội thoại trợ lý AI đã chuyển dược sĩ (chờ dược sĩ nhận)
        Conversation conv2 = new Conversation();
        conv2.setCustomer(kh2);
        conv2.setMode("HUMAN");
        conv2.setHandoffReason("Đối tượng đặc biệt: \"mang thai\"");
        conv2.setAiSummary("Khách nữ đang mang thai 5 tháng, bị cảm 2 ngày: sổ mũi, đau họng nhẹ, không sốt. Chưa dùng thuốc gì. "
                + "Hỏi thuốc cảm dùng được khi mang thai. Cần hỏi thêm: dị ứng thuốc, bệnh nền.");
        conv2.setHandedOffAt(now.minusMinutes(5));
        conv2.setCreatedAt(now.minusMinutes(6));
        conv2.setUpdatedAt(now.minusMinutes(5));
        conversationRepo.save(conv2);
        message(conv2, kh2, "Mình đang mang thai 5 tháng, bị cảm 2 ngày nay sổ mũi, đau họng, uống thuốc gì được ạ?", now.minusMinutes(6));
        botMessage(conv2, "AI", "Với phụ nữ mang thai / cho con bú, trẻ nhỏ hoặc người có bệnh gan, thận, việc dùng thuốc cần dược sĩ tư vấn trực tiếp. "
                + "Mình chuyển bạn cho dược sĩ nhé.", now.minusMinutes(5));
        botMessage(conv2, "SYSTEM", "Đã chuyển cuộc trò chuyện cho dược sĩ. Hiện chưa có dược sĩ online, dược sĩ sẽ trả lời sớm nhất "
                + "(hoặc bạn có thể để lại số để được gọi lại).", now.minusMinutes(5));

        Notification n = new Notification();
        n.setUser(ds1);
        n.setMessage("Đơn DHDEMORX1 có thuốc kê đơn cần duyệt");
        n.setLink("/staff/prescriptions");
        notificationRepo.save(n);
        AuditLog a = new AuditLog();
        a.setUser(admin);
        a.setAction("system.seed");
        a.setDetail("Khởi tạo dữ liệu mẫu");
        auditRepo.save(a);
        // Kho dự trữ, phiếu hủy chờ duyệt, công nợ nhà cung cấp
        Warehouse reserve = warehouseRepo.findAllByOrderByMainDescNameAsc().stream().filter(w -> !w.isMain()).findFirst().orElse(null);
        for (int i = 0; i < 3 && reserve != null; i++) {
            Batch b = new Batch();
            b.setProduct(products.get(i));
            b.setBatchNo(String.format("DT25%03d", i + 1));
            b.setMfgDate(today.minusDays(30));
            b.setExpDate(today.plusDays(900));
            b.setQuantity(60);
            b.setImportPrice(Math.round(products.get(i).getPrice() * 0.7));
            b.setSupplier(sup1);
            b.setWarehouse(reserve);
            batchRepo.save(b);
        }
        batchRepo.findByProductOrderByExpDateAsc(products.get(1)).stream().findFirst().ifPresent(b -> {
            StockAdjustment wo = new StockAdjustment();
            wo.setBatch(b);
            wo.setQuantity(-3);
            wo.setType("WRITE_OFF");
            wo.setReason("Hộp bị móp, ẩm - đề nghị hủy");
            wo.setUser(kho);
            wo.setStatus(ApprovalStatus.PENDING);
            adjustmentRepo.save(wo);
        });
        sup1.setPaymentTermDays(30);
        sup1.setTaxCode("0100108536");
        sup2.setPaymentTermDays(45);
        SupplierPayment pay = new SupplierPayment();
        pay.setSupplier(sup1);
        pay.setAmount(5_000_000);
        pay.setPaidDate(today.minusDays(20));
        pay.setMethod("Chuyển khoản");
        pay.setNote("Thanh toán đợt 1");
        pay.setCreatedBy(admin);
        supplierPaymentRepo.save(pay);

        catalogService.syncMasters();
        log.info("Đã tạo dữ liệu mẫu. Tài khoản: admin@hieuthuoc.vn/admin123, duocsi@hieuthuoc.vn/duocsi123, khachhang@gmail.com/123456");
    }

    private User user(Role role, String name, String email, String phone, String password, String license) {
        User u = new User();
        u.setRole(role);
        u.setFullName(name);
        u.setEmail(email);
        u.setPhone(phone);
        u.setPasswordHash(encoder.encode(password));
        u.setLicenseNo(license);
        u.setCreatedAt(LocalDateTime.now().minusDays(90));
        return userRepo.save(u);
    }

    private void address(User u, String line) {
        Address a = new Address();
        a.setUser(u);
        a.setRecipient(u.getFullName());
        a.setPhone(u.getPhone());
        a.setAddressLine(line);
        a.setDefaultAddress(true);
        addressRepo.save(a);
    }

    private Supplier supplier(String name, String phone, String email, String address) {
        Supplier s = new Supplier();
        s.setName(name);
        s.setPhone(phone);
        s.setEmail(email);
        s.setAddress(address);
        return supplierRepo.save(s);
    }

    private void voucher(String code, String desc, VoucherType type, long value, long min, Long max, Integer limit, LocalDate start, LocalDate end) {
        Voucher v = new Voucher();
        v.setCode(code);
        v.setDescription(desc);
        v.setType(type);
        v.setValue(value);
        v.setMinOrder(min);
        v.setMaxDiscount(max);
        v.setUsageLimit(limit);
        v.setStartDate(start);
        v.setEndDate(end);
        voucherRepo.save(v);
    }

    private Order newOrder(User u, LocalDateTime created, PaymentMethod pm) {
        Order o = new Order();
        o.setCode(Texts.code("DH"));
        o.setUser(u);
        o.setRecipient(u.getFullName());
        o.setPhone(u.getPhone());
        o.setAddress(addressRepo.findByUserOrderByDefaultAddressDescIdAsc(u).get(0).getAddressLine());
        o.setPaymentMethod(pm);
        o.setCreatedAt(created);
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
        o.getItems().add(it);
        o.setSubtotal(o.getSubtotal() + p.getPrice() * qty);
    }

    private void history(Order o, OrderStatus status, String note, User user, LocalDateTime at) {
        OrderHistory h = new OrderHistory();
        h.setOrder(o);
        h.setStatus(status);
        h.setNote(note);
        h.setUser(user);
        h.setCreatedAt(at);
        o.getHistory().add(h);
    }

    private void review(Product p, User u, int rating, String comment) {
        Review r = new Review();
        r.setProduct(p);
        r.setUser(u);
        r.setRating(rating);
        r.setComment(comment);
        reviewRepo.save(r);
    }

    private void botMessage(Conversation c, String kind, String body, LocalDateTime at) {
        Message m = new Message();
        m.setConversation(c);
        m.setKind(kind);
        m.setBody(body);
        m.setCreatedAt(at);
        messageRepo.save(m);
    }

    private void message(Conversation c, User sender, String body, LocalDateTime at) {
        Message m = new Message();
        m.setConversation(c);
        m.setSender(sender);
        m.setBody(body);
        m.setCreatedAt(at);
        messageRepo.save(m);
    }

    private static String sampleRxSvg(LocalDate today) {
        String date = today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        return """
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
    }
}
