package com.hieuthuoc.api;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.CartService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Đối tượng truyền dữ liệu (DTO) của REST API - không trả thực thể JPA trực tiếp. */
public final class Dto {
    private Dto() {
    }

    public record Code(String code, String label) {
        static Code of(OrderStatus s) {
            return s == null ? null : new Code(s.name(), s.getLabel());
        }

        static Code of(PaymentStatus s) {
            return s == null ? null : new Code(s.name(), s.getLabel());
        }

        static Code of(PaymentMethod s) {
            return s == null ? null : new Code(s.name(), s.getLabel());
        }

        static Code of(ApprovalStatus s) {
            return s == null ? null : new Code(s.name(), s.getLabel());
        }

        static Code of(DrugType s) {
            return s == null ? null : new Code(s.name(), s.getLabel());
        }
    }

    /* ---------------- Người dùng ---------------- */

    public record UserDto(Long id, String fullName, String email, String phone, String role, String position, String gender, LocalDate birthday,
                          int points, String allergies, String chronicConditions, boolean pregnancy, List<String> permissions) {
        public static UserDto of(User u) {
            return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole().name(), u.positionLabel(), u.getGender(), u.getBirthday(),
                    u.getPoints(), u.getAllergies(), u.getChronicConditions(), u.isPregnancy(), u.permissionSet().stream().map(Enum::name).toList());
        }
    }

    public record TokenDto(String accessToken, String tokenType, long expiresIn, UserDto user) {
    }

    public record AddressDto(Long id, String recipient, String phone, String addressLine, String province, Integer ghnProvinceId, Integer ghnDistrictId,
                             String ghnWardCode, boolean isDefault, String fullText) {
        public static AddressDto of(Address a) {
            return new AddressDto(a.getId(), a.getRecipient(), a.getPhone(), a.getAddressLine(), a.getProvince(), a.getGhnProvinceId(), a.getGhnDistrictId(),
                    a.getGhnWardCode(), a.isDefault(), a.fullText());
        }
    }

    /* ---------------- Danh mục, sản phẩm ---------------- */

    public record CategoryDto(Long id, String name, String slug, String icon, Long parentId, int depth) {
        public static CategoryDto of(Category c) {
            return new CategoryDto(c.getId(), c.getName(), c.getSlug(), c.getIcon(), c.getParentId(), c.getDepth());
        }
    }

    public record UnitDto(Long id, String name, int factor, long price) {
        public static UnitDto of(UnitOption u) {
            return new UnitDto(u.id(), u.name(), u.factor(), u.price());
        }
    }

    public record ProductDto(Long id, String name, String slug, String image, Code drugType, boolean prescription, String category, String activeIngredient,
                             String strength, String unit, long price, Long oldPrice, Long flashPrice, long available, Double rating, long reviewCount) {
        public static ProductDto of(Product p) {
            return new ProductDto(p.getId(), p.getName(), p.getSlug(), p.imageUrl(), Code.of(p.getDrugType()), p.getDrugType().isPrescription(),
                    p.getCategory() != null ? p.getCategory().getName() : null, p.getActiveIngredient(), p.getStrength(), p.getUnit(), p.getPrice(),
                    p.getOldPrice(), p.getFlashPrice(), p.getAvailable(), p.getAvgRating(), p.getReviewCount());
        }
    }

    public record ProductDetailDto(ProductDto product, List<UnitDto> units, String dosageForm, String packaging, String registrationNo, String manufacturer,
                                   String country, String description, String usageInstruction, String contraindications, String sideEffects,
                                   Integer maxPerOrder, boolean sellableOnline, List<String> promotions, List<ProductDto> related) {
    }

    public record ReviewDto(Long id, String user, int rating, String comment, boolean verifiedPurchase, LocalDateTime createdAt) {
        public static ReviewDto of(Review r) {
            return new ReviewDto(r.getId(), r.getUser().getFullName(), r.getRating(), r.getComment(), r.getOrder() != null, r.getCreatedAt());
        }
    }

    public record PostDto(Long id, String title, String slug, String summary, String image, String author, LocalDateTime publishedAt, String content) {
        public static PostDto of(Post p, boolean withContent) {
            return new PostDto(p.getId(), p.getTitle(), p.getSlug(), p.getSummary(), p.imageUrl(), p.getAuthor() != null ? p.getAuthor().getFullName() : null,
                    p.getCreatedAt(), withContent ? p.getContent() : null);
        }
    }

    public record FaqDto(Long id, String group, String question, String answer) {
        public static FaqDto of(Faq f) {
            return new FaqDto(f.getId(), f.getGroup(), f.getQuestion(), f.getAnswer());
        }
    }

    /* ---------------- Giỏ hàng (báo giá) ---------------- */

    public record CartLineDto(String key, Long productId, String name, String unit, Long unitId, int quantity, long unitPrice, long lineTotal,
                              boolean prescription, String error) {
        public static CartLineDto of(CartService.Line l) {
            return new CartLineDto(l.getKey(), l.getProduct().getId(), l.getProduct().getName(), l.getUnit().name(), l.getUnit().id(), l.getQuantity(),
                    l.getUnitPrice(), l.getLineTotal(), l.getProduct().getDrugType().isPrescription(), l.getError());
        }
    }

    public record QuoteDto(List<CartLineDto> lines, List<String> errors, long subtotal, long promoDiscount, List<String> promoNotes, String voucher,
                           String voucherError, long discount, int pointsAvailable, int pointsUsed, long pointsDiscount, long shippingFee, long total,
                           boolean prescriptionRequired) {
        public static QuoteDto of(CartService.View v) {
            return new QuoteDto(v.getLines().stream().map(CartLineDto::of).toList(), v.getErrors(), v.getSubtotal(), v.getPromoDiscount(), v.getPromoNotes(),
                    v.getVoucher() != null ? v.getVoucher().getCode() : null, v.getVoucherError(), v.getDiscount(), v.getPointsAvailable(), v.getPointsUsed(),
                    v.getPointsDiscount(), v.getShippingFee(), v.getTotal(), v.isRxRequired());
        }
    }

    /* ---------------- Đơn hàng ---------------- */

    public record OrderItemDto(Long id, Long productId, String productName, String unit, int quantity, long price, long lineTotal, boolean prescription, boolean gift) {
        public static OrderItemDto of(OrderItem i) {
            return new OrderItemDto(i.getId(), i.getProductId(), i.getProductName(), i.getUnit(), i.getQuantity(), i.getPrice(), i.lineTotal(), i.isPrescription(), i.isGift());
        }
    }

    public record HistoryDto(Code status, String note, String by, LocalDateTime at) {
        public static HistoryDto of(OrderHistory h) {
            return new HistoryDto(Code.of(h.getStatus()), h.getNote(), h.getUser() != null ? h.getUser().getFullName() : null, h.getCreatedAt());
        }
    }

    public record OrderDto(Long id, String code, Code status, Code paymentMethod, Code paymentStatus, String channel, String shippingMethod, long subtotal,
                           long discount, long promoDiscount, long pointsDiscount, long shippingFee, long total, int itemCount, boolean needsPrescription,
                           String recipient, String phone, String address, String province, String carrier, String trackingCode, String note,
                           String returnStatus, LocalDateTime createdAt, List<OrderItemDto> items, List<HistoryDto> history) {
        public static OrderDto of(Order o, boolean detail) {
            return new OrderDto(o.getId(), o.getCode(), Code.of(o.getStatus()), Code.of(o.getPaymentMethod()), Code.of(o.getPaymentStatus()), o.getChannel(),
                    o.getShippingMethod().name(), o.getSubtotal(), o.getDiscount(), o.getPromoDiscount(), o.getPointsDiscount(), o.getShippingFee(), o.getTotal(),
                    o.itemCount(), o.isNeedsPrescription(), o.getRecipient(), o.getPhone(), o.getAddress(), o.getProvince(), o.getCarrier(), o.getTrackingCode(),
                    o.getNote(), o.getReturnStatus() != null ? o.getReturnStatus().name() : null, o.getCreatedAt(),
                    detail ? o.getItems().stream().map(OrderItemDto::of).toList() : null,
                    detail ? o.getHistory().stream().map(HistoryDto::of).toList() : null);
        }
    }

    /* ---------------- Đơn thuốc ---------------- */

    public record PrescriptionDto(Long id, String orderCode, Long orderId, Code status, String image, String customerNote, String customer, String customerPhone,
                                  String patientName, String doctorName, String clinic, LocalDate rxDate, String pharmacist, String pharmacistNote,
                                  String rejectReason, LocalDateTime createdAt, LocalDateTime reviewedAt) {
        public static PrescriptionDto of(Prescription p) {
            return new PrescriptionDto(p.getId(), p.getOrder() != null ? p.getOrder().getCode() : null, p.getOrder() != null ? p.getOrder().getId() : null,
                    Code.of(p.getStatus()), "/files/prescriptions/" + p.getImage(), p.getCustomerNote(), p.getUser().getFullName(), p.getUser().getPhone(),
                    p.getPatientName(), p.getDoctorName(), p.getClinic(), p.getRxDate(), p.getPharmacist() != null ? p.getPharmacist().getFullName() : null,
                    p.getPharmacistNote(), p.getRejectReason(), p.getCreatedAt(), p.getReviewedAt());
        }
    }

    /* ---------------- Thông báo, tư vấn ---------------- */

    public record NotificationDto(Long id, String message, String link, boolean read, LocalDateTime createdAt) {
        public static NotificationDto of(UserNotification n) {
            return new NotificationDto(n.getId(), n.getMessage(), n.getLink(), n.isSeen(), n.getCreatedAt());
        }
    }

    /* ---------------- Kho ---------------- */

    public record StockDto(Long productId, String name, String unit, long onHand, long available, int minStock, boolean lowStock) {
        public static StockDto of(Product p) {
            return new StockDto(p.getId(), p.getName(), p.getUnit(), p.getOnHand(), p.getAvailable(), p.getMinStock(), p.isLowStock());
        }
    }

    public record BatchDto(Long id, Long productId, String product, String batchNo, LocalDate expDate, int quantity, boolean locked, long daysLeft) {
        public static BatchDto of(Batch b) {
            return new BatchDto(b.getId(), b.getProductId(), b.getProduct().getName(), b.getBatchNo(), b.getExpDate(), b.getQuantity(), b.isLocked(), b.daysLeft());
        }
    }
}
