package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.*;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trợ lý AI tiếp nhận chat trước dược sĩ.
 * <ul>
 *   <li>Trả lời câu hỏi thường gặp (đơn hàng, giao hàng, đổi trả, thanh toán, khuyến mãi), thông tin sản phẩm không kê đơn,
 *       hỏi sàng lọc triệu chứng.</li>
 *   <li>Không chẩn đoán, không tư vấn thuốc kê đơn / liều dùng: chuyển dược sĩ kèm tóm tắt.</li>
 *   <li>Chuyển ngay khi: khách yêu cầu, có dấu hiệu nguy hiểm, đối tượng đặc biệt (thai kỳ, trẻ nhỏ), gửi ảnh đơn thuốc,
 *       hỏi thuốc kê đơn, hoặc sản phẩm được nhắc tới có cảnh báo dị ứng / tương tác với hồ sơ khách.</li>
 * </ul>
 * Có ANTHROPIC_API_KEY: dùng Claude; không có: trả lời theo kịch bản (vẫn đủ các quy tắc chuyển dược sĩ).
 */
@Slf4j
@Service
public class AiAssistantService {
    public static final String HANDOFF_TOKEN = "[CHUYEN_DUOC_SI]";
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Dấu hiệu cần xử trí khẩn (so khớp không dấu). */
    private static final List<String> RED_FLAGS = List.of("kho tho", "tho gap", "dau nguc", "tuc nguc", "co giat", "ngat", "bat tinh", "hon me",
            "chay mau nhieu", "non ra mau", "di ngoai ra mau", "phan den", "sung mat", "sung moi", "sung hong", "phat ban toan than",
            "soc phan ve", "tu tu", "muon chet", "ngo doc", "uong nham", "qua lieu", "uong qua nhieu thuoc", "liet", "meo mieng", "yeu nua nguoi",
            "sot cao", "sot 40", "sot 39", "li bi", "lu du", "co cung");
    private static final List<String> SPECIAL_GROUPS = List.of("mang thai", "co thai", "co bau", "dang bau", "ba bau", "cho con bu", "tre so sinh",
            "so sinh", "thang tuoi", "be 1 tuoi", "be 2 tuoi", "tre duoi 2", "nguoi gia yeu", "suy than", "suy gan", "chay than");
    private static final List<String> RX_TOPICS = List.of("khang sinh", "thuoc ke don", "don thuoc", "toa thuoc", "bac si ke", "thuoc huyet ap",
            "thuoc tieu duong", "insulin", "thuoc tim", "corticoid", "thuoc ngu", "an than", "tang lieu", "giam lieu", "doi thuoc", "ngung thuoc");
    private static final List<String> WANT_HUMAN = List.of("gap duoc si", "noi chuyen voi duoc si", "gap nguoi", "nguoi that", "nhan vien tu van",
            "chuyen duoc si", "hoi duoc si", "can duoc si", "tu van vien");
    private static final Map<String, List<String>> SYMPTOMS = new LinkedHashMap<>();

    static {
        SYMPTOMS.put("đau đầu, sốt", List.of("dau dau", "sot", "nhuc dau", "dau nhuc"));
        SYMPTOMS.put("ho, cảm cúm", List.of("ho", "ho khan", "ho co dom", "cam cum", "cam lanh", "so mui", "nghet mui", "dau hong", "viem hong"));
        SYMPTOMS.put("tiêu hóa", List.of("tieu chay", "dau bung", "day hoi", "kho tieu", "tao bon", "buon non", "non", "o chua", "trao nguoc"));
        SYMPTOMS.put("da liễu, dị ứng", List.of("ngua", "mun", "noi me day", "di ung", "phat ban", "chay nang"));
        SYMPTOMS.put("sức khỏe chung", List.of("met moi", "mat ngu", "kho ngu", "suy nhuoc", "tang de khang", "bo sung vitamin", "dau lung", "dau khop"));
    }

    private static final Pattern ORDER_CODE = Pattern.compile("\\bdh[0-9a-z]{6,}\\b");

    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;
    private final OrderRepository orderRepo;
    private final ProductRepository productRepo;
    private final VoucherRepository voucherRepo;
    private final ShippingZoneRepository zoneRepo;
    private final ChatService chatService;
    private final ClaudeClient claude;
    private final SettingService settings;
    private final StockService stockService;
    private final PromotionService promotionService;
    private final SafetyService safety;
    private final TransactionTemplate tx;
    private final ExecutorService executor = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "ai-assistant");
        t.setDaemon(true);
        return t;
    });

    public AiAssistantService(ConversationRepository conversationRepo, MessageRepository messageRepo, OrderRepository orderRepo,
                              ProductRepository productRepo, VoucherRepository voucherRepo, ShippingZoneRepository zoneRepo,
                              ChatService chatService, ClaudeClient claude, SettingService settings, StockService stockService,
                              PromotionService promotionService, SafetyService safety, PlatformTransactionManager txManager) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.orderRepo = orderRepo;
        this.productRepo = productRepo;
        this.voucherRepo = voucherRepo;
        this.zoneRepo = zoneRepo;
        this.chatService = chatService;
        this.claude = claude;
        this.settings = settings;
        this.stockService = stockService;
        this.promotionService = promotionService;
        this.safety = safety;
        this.tx = new TransactionTemplate(txManager);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public boolean usesClaude() {
        return claude.isConfigured();
    }

    public String engineLabel() {
        return claude.isConfigured() ? "Claude (" + claude.model() + ")" : "Trả lời tự động theo kịch bản (chưa cấu hình ANTHROPIC_API_KEY)";
    }

    /** Gọi sau khi tin nhắn của khách đã được lưu: trợ lý trả lời ở luồng nền (giao diện nhận qua polling). */
    public void onCustomerMessage(Long conversationId) {
        executor.submit(() -> {
            try {
                tx.executeWithoutResult(s -> respond(conversationId));
            } catch (Exception e) {
                log.warn("Trợ lý AI lỗi ở hội thoại #{}: {}", conversationId, e.getMessage());
            }
        });
    }

    /** Khách bấm "Gặp dược sĩ". */
    @Transactional
    public void requestHandoff(User customer) {
        Conversation c = chatService.latestFor(customer);
        if (c == null || !c.isAiMode()) return;
        chatService.handoff(c, "Khách yêu cầu gặp dược sĩ", summarize(c, "Khách yêu cầu gặp dược sĩ"));
    }

    /* ============================ Xử lý 1 lượt ============================ */

    record Reply(String text, boolean handoff, String reason) {
    }

    @Transactional
    public void respond(Long conversationId) {
        Conversation c = conversationRepo.findById(conversationId).orElse(null);
        if (c == null || !c.isAiMode()) return;
        List<Message> msgs = messageRepo.findByConversationAndIdGreaterThanOrderByIdAsc(c, 0L);
        if (msgs.isEmpty()) return;
        Message last = msgs.get(msgs.size() - 1);
        if (last.getSender() == null || last.getSender().isStaff()) return; // đã trả lời rồi
        User customer = c.getCustomer();
        String raw = Objects.requireNonNullElse(last.getBody(), "");
        String n = " " + normalize(raw) + " ";

        Reply r = hardRules(last, raw, n);
        if (r == null) {
            if (claude.isConfigured()) {
                try {
                    r = askClaude(c, customer, msgs, raw);
                } catch (Exception e) {
                    log.warn("Claude API lỗi, dùng trả lời tự động: {}", e.getMessage());
                    r = fallback(c, customer, raw, n);
                }
            } else {
                r = fallback(c, customer, raw, n);
            }
        }
        // Kiểm tra an toàn: sản phẩm được nhắc tới có cảnh báo với hồ sơ sức khỏe / thuốc khách đã mua?
        String text = r.text();
        boolean handoff = r.handoff();
        String reason = r.reason();
        List<Product> mentioned = mentionedProducts(text + " " + raw);
        if (!mentioned.isEmpty()) {
            List<SafetyService.Warning> ws = safety.check(customer, mentioned, safety.recentProducts(customer)).stream()
                    .filter(w -> !"info".equals(w.level())).toList();
            if (!ws.isEmpty()) {
                text += "\n\n⚠ Lưu ý an toàn: " + ws.get(0).message() + " Mình chuyển dược sĩ kiểm tra kỹ cho bạn trước khi dùng.";
                handoff = true;
                reason = "Cảnh báo an toàn: " + ws.get(0).message();
            }
        }
        chatService.postBot(c, "AI", text);
        if (handoff) chatService.handoff(c, reason, summarize(c, reason));
    }

    /** Quy tắc chuyển dược sĩ luôn áp dụng (không phụ thuộc AI). */
    Reply hardRules(Message last, String raw, String n) {
        if (last.getImage() != null) {
            return new Reply("Mình đã nhận được ảnh của bạn. Ảnh đơn thuốc / hộp thuốc cần dược sĩ đọc và xác nhận, mình chuyển ngay cho dược sĩ nhé.",
                    true, "Khách gửi ảnh (đơn thuốc / sản phẩm)");
        }
        for (String k : RED_FLAGS) {
            if (word(n, k)) {
                return new Reply("Triệu chứng bạn mô tả có thể cần được xử trí y tế ngay. Nếu tình trạng nặng hoặc diễn biến nhanh, hãy gọi cấp cứu 115 "
                        + "hoặc đến cơ sở y tế gần nhất. Mình đang chuyển ngay cho dược sĩ để hỗ trợ bạn.", true, "Dấu hiệu cần xử trí khẩn: \"" + k + "\"");
            }
        }
        for (String k : WANT_HUMAN) {
            if (word(n, k)) return new Reply("Dạ, mình chuyển bạn sang dược sĩ ngay ạ.", true, "Khách yêu cầu gặp dược sĩ");
        }
        for (String k : SPECIAL_GROUPS) {
            if (word(n, k)) {
                return new Reply("Với phụ nữ mang thai / cho con bú, trẻ nhỏ hoặc người có bệnh gan, thận, việc dùng thuốc cần dược sĩ tư vấn trực tiếp. "
                        + "Mình chuyển bạn cho dược sĩ nhé.", true, "Đối tượng đặc biệt: \"" + k + "\"");
            }
        }
        for (String k : RX_TOPICS) {
            if (word(n, k)) {
                return new Reply("Câu hỏi về thuốc kê đơn / đơn thuốc cần dược sĩ có chứng chỉ hành nghề trả lời. Mình chuyển bạn cho dược sĩ ngay nhé. "
                        + "Nếu có đơn thuốc, bạn có thể chụp ảnh gửi vào đây.", true, "Câu hỏi về thuốc kê đơn: \"" + k + "\"");
            }
        }
        for (Product p : productRepo.findByActiveTrueOrderByNameAsc()) {
            if (!p.getDrugType().isPrescription() && p.getDrugType().isSellableOnline()) continue;
            if (mentions(n, p)) {
                return new Reply(p.getName() + " là " + p.getDrugType().getLabel().toLowerCase() + " - cần dược sĩ tư vấn"
                        + (p.getDrugType().isPrescription() ? " và có đơn của bác sĩ." : ", không bán online.") + " Mình chuyển bạn cho dược sĩ nhé.",
                        true, "Hỏi về " + p.getDrugType().getLabel().toLowerCase() + ": " + p.getName());
            }
        }
        return null;
    }

    /* ============================ Claude ============================ */

    private Reply askClaude(Conversation c, User customer, List<Message> msgs, String raw) {
        List<ClaudeClient.Turn> turns = new ArrayList<>();
        int from = Math.max(0, msgs.size() - 20);
        for (Message m : msgs.subList(from, msgs.size())) {
            if (m.isSystem()) continue;
            String body = Objects.requireNonNullElse(m.getBody(), m.getImage() != null ? "[ảnh]" : "");
            boolean fromCustomer = m.getSender() != null && !m.getSender().isStaff();
            turns.add(new ClaudeClient.Turn(fromCustomer ? "user" : "assistant", fromCustomer ? body : body));
        }
        String out = claude.complete(systemPrompt(customer, raw), turns, 700);
        boolean handoff = out.contains(HANDOFF_TOKEN);
        out = out.replace(HANDOFF_TOKEN, "").replaceAll("[*#`]", "").trim();
        if (out.isEmpty()) out = "Mình chuyển bạn cho dược sĩ để được tư vấn chính xác nhé.";
        return new Reply(out, handoff, "Trợ lý AI đề nghị dược sĩ tư vấn");
    }

    String systemPrompt(User customer, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là ").append(settings.get("ai_name")).append(" - trợ lý chat của nhà thuốc ").append(settings.get("store_name"))
                .append(" (nhà thuốc đạt chuẩn GPP). Bạn tiếp nhận khách trước khi chuyển dược sĩ.\n\n")
                .append("NHIỆM VỤ: trả lời câu hỏi về đơn hàng, giao hàng, đổi trả, thanh toán, khuyến mãi, thông tin chung về sản phẩm KHÔNG kê đơn có trong danh sách; ")
                .append("với triệu chứng nhẹ thì hỏi thêm: tuổi, triệu chứng kéo dài bao lâu, có đang mang thai/cho con bú, dị ứng thuốc, bệnh nền, thuốc đang dùng.\n\n")
                .append("QUY TẮC BẮT BUỘC:\n")
                .append("- Không chẩn đoán bệnh. Không kê hay tư vấn thuốc kê đơn, kháng sinh, không đưa liều dùng cụ thể theo cân nặng/tuổi, không khuyên đổi/ngưng thuốc bác sĩ kê.\n")
                .append("- Chỉ nhắc tới sản phẩm có trong danh sách bên dưới, không bịa giá, không hứa hẹn công dụng chữa khỏi bệnh.\n")
                .append("- Khi cần chuyên môn (chọn thuốc cụ thể, liều dùng, tương tác, đối tượng đặc biệt, triệu chứng kéo dài/nặng), hoặc khách muốn gặp người thật, ")
                .append("hãy nói ngắn gọn rằng bạn sẽ chuyển cho dược sĩ và thêm đúng chuỗi ").append(HANDOFF_TOKEN).append(" vào cuối câu trả lời.\n")
                .append("- Dấu hiệu cấp cứu: khuyên gọi 115 / đến cơ sở y tế ngay và thêm ").append(HANDOFF_TOKEN).append(".\n")
                .append("- Trả lời bằng tiếng Việt, thân thiện, xưng \"mình\" gọi \"bạn\", tối đa khoảng 120 từ, văn bản thuần (không markdown, không dùng dấu *).\n")
                .append("- Thông tin chỉ mang tính tham khảo, không thay thế tư vấn của dược sĩ / bác sĩ.\n\n");
        sb.append("THÔNG TIN NHÀ THUỐC: ").append(settings.get("store_address")).append(" · Hotline ").append(settings.get("store_phone"))
                .append(" · Dược sĩ phụ trách: ").append(settings.get("pharmacist_in_charge")).append("\n");
        sb.append("GIAO HÀNG: ").append(shippingText()).append("\n");
        sb.append("THANH TOÁN: ").append(String.join(", ", settings.enabledPaymentMethods().stream().map(PaymentMethod::getLabel).toList())).append("\n");
        sb.append("ĐỔI TRẢ: trong ").append(settings.get("return_days")).append(" ngày. ").append(settings.get("return_policy")).append("\n");
        sb.append("KHUYẾN MÃI: ").append(promoText()).append("\n\n");
        sb.append("ĐƠN HÀNG GẦN ĐÂY CỦA KHÁCH: ").append(ordersText(customer, 5)).append("\n\n");
        sb.append("SẢN PHẨM KHÔNG KÊ ĐƠN (tên | loại | giá | tồn | công dụng):\n");
        for (Product p : catalogFor(question, 40)) {
            sb.append("- ").append(p.getName()).append(" | ").append(p.getDrugType().getShortLabel()).append(" | ")
                    .append(money(p.isFlashSale() ? p.getFlashPrice() : p.getPrice())).append("/").append(p.getUnit())
                    .append(p.isFlashSale() ? " (flash sale)" : "").append(" | ").append(p.getAvailable() > 0 ? "còn hàng" : "hết hàng")
                    .append(" | ").append(Texts.trim(Objects.requireNonNullElse(p.getDescription(), ""), 140)).append("\n");
        }
        return sb.toString();
    }

    /** Tóm tắt cuộc trò chuyện cho dược sĩ. */
    String summarize(Conversation c, String reason) {
        List<Message> msgs = messageRepo.findByConversationAndIdGreaterThanOrderByIdAsc(c, 0L);
        List<String> fromCustomer = msgs.stream().filter(m -> m.getSender() != null && !m.getSender().isStaff() && m.getBody() != null)
                .map(Message::getBody).toList();
        List<String> tail = fromCustomer.subList(Math.max(0, fromCustomer.size() - 4), fromCustomer.size());
        String basic = "Khách nhắn: " + String.join(" | ", tail);
        if (claude.isConfigured() && !tail.isEmpty()) {
            try {
                List<ClaudeClient.Turn> turns = new ArrayList<>();
                for (Message m : msgs.subList(Math.max(0, msgs.size() - 20), msgs.size())) {
                    if (m.getBody() == null || m.isSystem()) continue;
                    boolean fc = m.getSender() != null && !m.getSender().isStaff();
                    turns.add(new ClaudeClient.Turn("user", (fc ? "Khách: " : "Trợ lý: ") + m.getBody()));
                }
                String s = claude.complete("Tóm tắt cuộc trò chuyện giữa khách và trợ lý nhà thuốc cho dược sĩ, tiếng Việt, tối đa 80 từ, văn bản thuần: "
                        + "nhu cầu chính, triệu chứng (thời gian, mức độ), đối tượng, dị ứng / bệnh nền / thuốc đang dùng nếu có, điều còn thiếu cần hỏi. "
                        + "Lý do chuyển: " + reason, turns, 300);
                if (!s.isBlank()) return s.replaceAll("[*#`]", "");
            } catch (Exception e) {
                log.warn("Không tóm tắt được bằng Claude: {}", e.getMessage());
            }
        }
        return basic;
    }

    /* ============================ Trả lời tự động (không có API key) ============================ */

    Reply fallback(Conversation c, User customer, String raw, String n) {
        Reply r = understood(c, customer, raw, n);
        if (r != null) {
            c.setAiMisses(0);
            return r;
        }
        int misses = (c.getAiMisses() == null ? 0 : c.getAiMisses()) + 1;
        c.setAiMisses(misses);
        if (misses >= 2) {
            return new Reply("Mình chưa hiểu rõ câu hỏi của bạn, mình chuyển bạn cho dược sĩ để được hỗ trợ tốt hơn nhé.", true, "Trợ lý chưa hiểu câu hỏi");
        }
        return new Reply("Mình chưa hiểu rõ ý bạn. Mình có thể giúp: tra cứu đơn hàng, phí giao hàng, đổi trả, thanh toán, khuyến mãi, "
                + "thông tin sản phẩm không kê đơn, hoặc ghi nhận triệu chứng để chuyển dược sĩ. Bạn cũng có thể bấm \"Gặp dược sĩ\" bất cứ lúc nào.", false, null);
    }

    private Reply understood(Conversation c, User customer, String raw, String n) {
        // Khách trả lời câu hỏi sàng lọc -> chuyển dược sĩ kèm thông tin
        if (Boolean.TRUE.equals(c.getTriageAsked())) {
            return new Reply("Cảm ơn bạn đã cung cấp thông tin. Mình đã ghi nhận và chuyển cho dược sĩ để tư vấn thuốc, liều dùng phù hợp cho bạn nhé.",
                    true, "Tư vấn triệu chứng - cần dược sĩ chọn thuốc");
        }
        if (n.trim().matches("(xin chao|chao|chao ban|hello|hi|alo|chao shop|shop oi|ad oi)( ban| shop| ad| a| ah| nhe)?\\s*[!.?]*")) {
            return new Reply("Chào " + firstName(customer) + ", mình là " + settings.get("ai_name") + ". Mình có thể giúp bạn tra cứu đơn hàng, phí giao hàng, "
                    + "đổi trả, khuyến mãi, thông tin sản phẩm, hoặc ghi nhận triệu chứng để dược sĩ tư vấn. Bạn cần hỗ trợ gì ạ?", false, null);
        }
        if (has(n, "cam on", "thank", "thanks", "tks", "cam on ban")) {
            return new Reply("Rất vui được hỗ trợ bạn! Nếu cần tư vấn thêm về thuốc, bạn cứ nhắn hoặc bấm \"Gặp dược sĩ\" nhé.", false, null);
        }
        Matcher m = ORDER_CODE.matcher(n);
        if (m.find() || has(n, "don hang", "don cua toi", "kiem tra don", "tra cuu don", "tinh trang don", "bao gio giao", "giao chua", "den dau roi", "huy don")) {
            String text = m.find(0) ? orderDetail(customer, m.group().toUpperCase()) : null;
            if (text == null) text = "Các đơn gần đây của bạn:\n" + ordersText(customer, 3);
            if (has(n, "huy don")) text += "\nBạn có thể tự hủy đơn khi đơn chưa giao, tại mục Tài khoản > Đơn hàng của tôi.";
            return new Reply(text + "\nChi tiết từng đơn xem tại Tài khoản > Đơn hàng của tôi.", false, null);
        }
        if (has(n, "phi ship", "ship", "giao hang", "van chuyen", "phi giao", "freeship", "mien phi giao")) {
            String province = ShippingZone.PROVINCES.stream().filter(p -> word(n, normalize(p))).findFirst()
                    .orElse(n.contains(" hcm ") || n.contains(" sai gon ") ? "TP. Hồ Chí Minh" : null);
            if (province != null) {
                ShippingZone z = zoneRepo.findByActiveTrueOrderBySortOrderAscIdAsc().stream().filter(x -> x.covers(province)).findFirst().orElse(null);
                if (z != null) {
                    return new Reply("Giao về " + province + " (khu vực " + z.getName() + "): phí " + money(z.getFee())
                            + (z.getFreeThreshold() != null ? ", miễn phí cho đơn từ " + money(z.getFreeThreshold()) : "")
                            + (z.getEta() != null ? ", thời gian khoảng " + z.getEta() : "") + ".", false, null);
                }
                if (!settings.deliversTo(province)) {
                    return new Reply("Hiện nhà thuốc chưa giao hàng tới " + province + ". Bạn có thể nhờ người nhận tại nhà thuốc: "
                            + settings.get("store_address") + ".", false, null);
                }
            }
            return new Reply("Phí giao hàng theo khu vực: " + shippingText() + "\nĐơn có thuốc kê đơn chỉ giao sau khi dược sĩ duyệt đơn thuốc. "
                    + "Bạn cũng có thể chọn nhận tại nhà thuốc: " + settings.get("store_address") + ".", false, null);
        }
        if (has(n, "doi tra", "tra hang", "hoan tien", "doi hang", "tra lai")) {
            return new Reply("Chính sách đổi trả: trong " + settings.get("return_days") + " ngày kể từ khi nhận hàng. " + settings.get("return_policy")
                    + "\nBạn gửi yêu cầu đổi trả tại trang chi tiết đơn hàng.", false, null);
        }
        if (has(n, "thanh toan", "chuyen khoan", "cod", "vi dien tu", "momo", "vnpay", "tra tien")) {
            return new Reply("Nhà thuốc nhận: " + String.join(", ", settings.enabledPaymentMethods().stream().map(PaymentMethod::getLabel).toList())
                    + ". Với chuyển khoản, sau khi đặt hàng bạn sẽ thấy mã QR và nội dung chuyển khoản.", false, null);
        }
        if (has(n, "dia chi", "o dau", "hotline", "so dien thoai", "gio mo cua", "mo cua", "lien he")) {
            return new Reply(settings.get("store_name") + ": " + settings.get("store_address") + ". Hotline " + settings.get("store_phone")
                    + ". Dược sĩ phụ trách chuyên môn: " + settings.get("pharmacist_in_charge") + ".", false, null);
        }
        if (has(n, "khuyen mai", "giam gia", "voucher", "ma giam", "flash sale", "uu dai", "combo", "qua tang")) {
            return new Reply("Ưu đãi hiện có: " + promoText() + "\nLưu ý: khuyến mãi và mã giảm giá không áp dụng cho thuốc kê đơn.", false, null);
        }
        if (has(n, "tich diem", "diem cua toi", "hang thanh vien", "bao nhieu diem")) {
            return new Reply("Bạn đang có " + customer.getPoints() + " điểm, hạng " + MemberTier.of(orderRepo.totalSpent(customer)).getLabel()
                    + ". Xem chi tiết và kho voucher tại trang Tài khoản.", false, null);
        }
        // Hỏi về sản phẩm cụ thể (không kê đơn)
        List<Product> named = catalog().stream().filter(p -> mentions(n, p)).limit(3).toList();
        if (!named.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Product p : named) {
                sb.append(p.getName()).append(" (").append(p.getDrugType().getShortLabel()).append("): ")
                        .append(money(p.isFlashSale() ? p.getFlashPrice() : p.getPrice())).append("/").append(p.getUnit())
                        .append(p.isFlashSale() ? " - đang flash sale" : "").append(", ").append(p.getAvailable() > 0 ? "còn hàng" : "tạm hết hàng").append(". ")
                        .append(Texts.trim(Objects.requireNonNullElse(p.getDescription(), ""), 160)).append("\n");
            }
            sb.append("Liều dùng cụ thể theo tuổi và tình trạng sức khỏe, bạn nên hỏi dược sĩ trước khi dùng nhé.");
            return new Reply(sb.toString().trim(), false, null);
        }
        // Triệu chứng -> hỏi sàng lọc
        for (Map.Entry<String, List<String>> e : SYMPTOMS.entrySet()) {
            if (e.getValue().stream().anyMatch(k -> word(n, k))) {
                c.setTriageAsked(true);
                return new Reply("Mình ghi nhận bạn đang gặp vấn đề " + e.getKey() + ". Để dược sĩ tư vấn chính xác, bạn cho mình biết thêm:\n"
                        + "1. Tuổi của người dùng thuốc\n2. Triệu chứng kéo dài bao lâu, mức độ ra sao\n"
                        + "3. Có đang mang thai / cho con bú, dị ứng thuốc hay bệnh nền gì không\n4. Đang dùng thuốc nào khác không", false, null);
            }
        }
        return null;
    }

    /* ============================ Dữ liệu ngữ cảnh ============================ */

    private List<Product> catalog() {
        List<Product> list = new ArrayList<>(productRepo.findByActiveTrueOrderByNameAsc().stream()
                .filter(p -> !p.getDrugType().isPrescription() && p.getDrugType().isSellableOnline()).toList());
        stockService.fill(list);
        promotionService.decorate(list);
        return list;
    }

    /** Sản phẩm liên quan câu hỏi trước, sau đó các sản phẩm còn lại (giới hạn để prompt gọn). */
    private List<Product> catalogFor(String question, int limit) {
        String n = " " + normalize(question) + " ";
        List<Product> all = catalog();
        List<Product> out = new ArrayList<>(all.stream().filter(p -> mentions(n, p) || relatedToQuestion(n, p)).toList());
        for (Product p : all) if (out.size() < limit && !out.contains(p)) out.add(p);
        return out.subList(0, Math.min(limit, out.size()));
    }

    private static boolean relatedToQuestion(String n, Product p) {
        String text = normalize(Objects.requireNonNullElse(p.getDescription(), "") + " " + Objects.requireNonNullElse(p.getUsageInstruction(), ""));
        for (String w : n.trim().split("\\s+")) if (w.length() >= 4 && text.contains(w)) return true;
        return false;
    }

    private List<Product> mentionedProducts(String text) {
        String n = " " + normalize(text) + " ";
        return productRepo.findByActiveTrueOrderByNameAsc().stream().filter(p -> mentions(n, p)).toList();
    }

    private static final Set<String> GENERIC = Set.of("vien", "sui", "thuoc", "hop", "tre", "em", "bot", "siro", "kem", "may", "chai", "tuyp",
            "ong", "goi", "huong", "cam", "extra", "plus", "forte", "gentle", "skin", "cleanser", "stada", "domesco", "y", "te", "lop", "dien", "tu",
            "bap", "tay", "do", "huyet", "ap", "nhiet", "ke", "khau", "trang", "sua", "rua", "mat", "chong", "nang", "fish", "oil", "biloba", "canxi");

    /** Khách/AI nhắc tới sản phẩm: chứa tên đầy đủ, hoặc một từ đặc trưng trong tên (VD: panadol, smecta, omega). */
    private static boolean mentions(String n, Product p) {
        String name = normalize(p.getName());
        if (n.contains(name)) return true;
        for (String w : name.split("\\s+")) {
            if (w.length() >= 5 && !GENERIC.contains(w) && !w.matches(".*\\d.*") && n.contains(" " + w)) return true;
        }
        return false;
    }

    private String shippingText() {
        List<ShippingZone> zones = zoneRepo.findByActiveTrueOrderBySortOrderAscIdAsc();
        if (zones.isEmpty()) {
            return money(settings.getLong("shipping_fee")) + ", miễn phí cho đơn từ " + money(settings.getLong("free_ship_threshold")) + ".";
        }
        List<String> parts = new ArrayList<>();
        for (ShippingZone z : zones) {
            parts.add(z.getName() + " " + money(z.getFee()) + (z.getFreeThreshold() != null ? " (miễn phí từ " + money(z.getFreeThreshold()) + ")" : "")
                    + (z.getEta() != null ? ", " + z.getEta() : ""));
        }
        return String.join("; ", parts) + ".";
    }

    private String promoText() {
        List<String> parts = new ArrayList<>();
        for (Promotion p : promotionService.running()) parts.add(p.getName());
        LocalDate today = LocalDate.now();
        for (Voucher v : voucherRepo.findAllByOrderByIdDesc()) {
            if (!v.isActive() || !v.isInWallet() || (v.getEndDate() != null && v.getEndDate().isBefore(today))) continue;
            parts.add("mã " + v.getCode() + (v.getDescription() != null ? " (" + v.getDescription() + ")" : ""));
        }
        return parts.isEmpty() ? "hiện chưa có chương trình." : String.join("; ", parts) + ".";
    }

    private String ordersText(User customer, int limit) {
        List<Order> orders = orderRepo.findByUserOrderByCreatedAtDescIdDesc(customer);
        if (orders.isEmpty()) return "(chưa có đơn hàng)";
        StringBuilder sb = new StringBuilder();
        for (Order o : orders.subList(0, Math.min(limit, orders.size()))) {
            sb.append("• ").append(o.getCode()).append(" (").append(o.getCreatedAt().format(D)).append("): ").append(o.getStatus().getLabel())
                    .append(", ").append(money(o.getTotal()))
                    .append(o.getTrackingCode() != null ? ", mã vận đơn " + o.getCarrier() + " " + o.getTrackingCode() : "").append("\n");
        }
        return sb.toString().trim();
    }

    private String orderDetail(User customer, String code) {
        return orderRepo.findByUserOrderByCreatedAtDescIdDesc(customer).stream().filter(o -> o.getCode().equalsIgnoreCase(code)).findFirst()
                .map(o -> "Đơn " + o.getCode() + " đặt ngày " + o.getCreatedAt().format(D) + ": " + o.getStatus().getLabel()
                        + ", thanh toán " + o.getPaymentStatus().getLabel().toLowerCase() + ", tổng " + money(o.getTotal())
                        + (o.getTrackingCode() != null ? ". Đơn vị vận chuyển " + o.getCarrier() + ", mã vận đơn " + o.getTrackingCode() : "") + ".")
                .orElse("Mình không tìm thấy đơn " + code + " trong tài khoản của bạn. Các đơn gần đây:\n" + ordersText(customer, 3));
    }

    private static String firstName(User u) {
        String[] parts = u.getFullName().trim().split("\\s+");
        return parts[parts.length - 1];
    }

    private static String money(long v) {
        return String.format("%,d", v).replace(',', '.') + " đ";
    }

    private static boolean has(String n, String... keys) {
        for (String k : keys) if (word(n, k)) return true;
        return false;
    }

    /** n đã chuẩn hóa và có khoảng trắng hai đầu: so khớp nguyên từ / cụm từ. */
    private static boolean word(String n, String k) {
        return n.contains(" " + k.trim() + " ");
    }

    /** Chữ thường, bỏ dấu tiếng Việt, gộp khoảng trắng - để so khớp cả khi khách gõ không dấu. */
    public static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }
}
