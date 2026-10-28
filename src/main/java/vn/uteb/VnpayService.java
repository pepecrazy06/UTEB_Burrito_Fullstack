package vn.uteb;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import static vn.uteb.Domain.*;
import static vn.uteb.Payments.*;
import static vn.uteb.ShopService.*;

/** VNPay PAY 2.1.0. Browser returns are read-only; only authenticated IPN settles an attempt. */
@Service
@Transactional
class VnpayService {
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;
    final ShopService shop;
    final String base, code, secret;

    VnpayService(ShopService shop, @Value("${uteb.base-url}") String base,
            @Value("${uteb.vnpay.code}") String code, @Value("${uteb.vnpay.secret}") String secret) {
        this.shop = shop; this.base = base.replaceAll("/+$", ""); this.code = code; this.secret = secret;
    }

    boolean configured() {
        try {
            URI uri = URI.create(base);
            return code.matches("[A-Za-z0-9]{8}") && !secret.isBlank()
                && uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null
                && uri.getFragment() == null && (uri.getPath() == null || uri.getPath().isEmpty())
                && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())
                    && Set.of("localhost", "127.0.0.1").contains(uri.getHost()));
        } catch (IllegalArgumentException e) { return false; }
    }

    void requireConfigured() {
        if (!configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "VNPay chưa sẵn sàng. Cần TmnCode, HashSecret và URL website hợp lệ.");
    }

    static String minorAmount(BigDecimal amount) {
        String value = amount.multiply(BigDecimal.valueOf(100)).toBigIntegerExact().toString();
        require(amount.signum() > 0 && value.matches("[0-9]{1,12}"), "Số tiền không phù hợp thanh toán VNPay.");
        return value;
    }

    List<PaymentAttempt> attempts(long orderId) { return shop.where(PaymentAttempt.class, "orderId", orderId); }

    Map<String,Object> start(MealOrder order, String ip) {
        requireConfigured();
        em.lock(order, LockModeType.PESSIMISTIC_WRITE);
        require("NEW".equals(order.status) && List.of("PENDING", "FAILED").contains(order.paymentStatus),
            "Đơn không còn chờ thanh toán VNPay.");
        require(order.paymentExpires != null && order.paymentExpires.isAfter(LocalDateTime.now()), "Đơn đã hết thời gian thanh toán.");
        String amount = minorAmount(order.total);
        PaymentAttempt attempt = attempts(order.id).stream().filter(a -> "PENDING".equals(a.state)).findFirst().orElse(null);
        if (attempt == null) {
            attempt = new PaymentAttempt(); attempt.orderId = order.id;
            attempt.reference = "U" + order.id + "T" + UUID.randomUUID().toString().replace("-", "");
            attempt.amount = order.total; attempt.expiresAt = order.paymentExpires;
            shop.save(attempt);
        }
        order.paymentStatus = "PENDING";
        Map<String,String> fields = new TreeMap<>();
        fields.put("vnp_Version", "2.1.0"); fields.put("vnp_Command", "pay"); fields.put("vnp_TmnCode", code);
        fields.put("vnp_Amount", amount); fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", attempt.reference); fields.put("vnp_OrderInfo", "Thanh toan don hang " + order.id);
        fields.put("vnp_OrderType", "other"); fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", base + "/payments/vnpay/return");
        fields.put("vnp_IpAddr", "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip) ? "127.0.0.1" : ip);
        fields.put("vnp_CreateDate", attempt.createdAt.format(TIME));
        fields.put("vnp_ExpireDate", attempt.expiresAt.format(TIME));
        String raw = vnRaw(fields);
        return Map.of("url", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?" + raw
            + "&vnp_SecureHash=" + sign("HmacSHA512", secret, raw), "simulation", false,
            "reference", attempt.reference, "expiresAt", attempt.expiresAt);
    }

    static Map<String,String> singleValues(MultiValueMap<String,String> values) {
        for (var entry : values.entrySet())
            if (entry.getKey().startsWith("vnp_") && entry.getValue().size() != 1) return null;
        return values.toSingleValueMap();
    }

    boolean authentic(Map<String,String> fields) {
        if (fields == null || code.isBlank() || secret.isBlank() || !code.equals(fields.get("vnp_TmnCode"))) return false;
        String hash = fields.getOrDefault("vnp_SecureHash", "");
        return hash.matches("(?i)[0-9a-f]{128}")
            && equalsSignature(sign("HmacSHA512", secret, vnRaw(fields)), hash.toLowerCase(Locale.ROOT));
    }

    PaymentAttempt findAttempt(String reference) {
        if (reference == null || !reference.matches("[A-Za-z0-9]{1,64}")) return null;
        return em.createQuery("from PaymentAttempt where reference=:r", PaymentAttempt.class)
            .setParameter("r", reference).getResultStream().findFirst().orElse(null);
    }

    boolean amountMatches(Map<String,String> fields, PaymentAttempt attempt, MealOrder order) {
        String value = fields.getOrDefault("vnp_Amount", "");
        return value.matches("[0-9]{1,12}") && new BigDecimal(value).compareTo(attempt.amount.multiply(BigDecimal.valueOf(100))) == 0
            && order.total.compareTo(attempt.amount) == 0 && "VNPAY".equals(order.paymentMethod);
    }

    static Map<String,String> reply(String code, String message) { return Map.of("RspCode", code, "Message", message); }

    Map<String,String> ipn(Map<String,String> fields) {
        if (!authentic(fields)) return reply("97", "Invalid signature or merchant");
        PaymentAttempt attempt = findAttempt(fields.get("vnp_TxnRef"));
        if (attempt == null) return reply("01", "Payment not found");
        MealOrder order = em.find(MealOrder.class, attempt.orderId, LockModeType.PESSIMISTIC_WRITE);
        if (order == null) return reply("01", "Order not found");
        // All start/callback/cancel/expiry operations lock the order first.
        em.refresh(attempt, LockModeType.PESSIMISTIC_WRITE);
        if (!amountMatches(fields, attempt, order)) return reply("04", "Invalid amount");
        String response = fields.getOrDefault("vnp_ResponseCode", ""), state = fields.getOrDefault("vnp_TransactionStatus", "");
        String transaction = fields.getOrDefault("vnp_TransactionNo", "");
        boolean success = "00".equals(response) && "00".equals(state);
        if (!response.matches("[0-9]{2}") || !state.matches("[0-9]{2}")
                || !transaction.matches("[0-9]{1,15}") || success && new BigDecimal(transaction).signum() == 0)
            return reply("99", "Invalid payment result");

        if (!"PENDING".equals(attempt.state)) {
            if ("FAILED".equals(attempt.state) && success) {
                attempt.transactionNo = transaction; attempt.responseCode = response; attempt.transactionStatus = state;
                markReview(order, attempt, transaction);
                return reply("00", "Conflicting payment recorded for review");
            }
            return reply("02", "Payment already confirmed");
        }

        attempt.responseCode = response; attempt.transactionStatus = state; attempt.transactionNo = transaction;
        if ("NEW".equals(order.status) && order.paymentExpires.isBefore(LocalDateTime.now()) && !"PAID".equals(order.paymentStatus))
            shop.expirePaymentOrder(order);
        boolean eligible = "NEW".equals(order.status) && List.of("PENDING", "FAILED").contains(order.paymentStatus);
        if (success && !eligible) {
            markReview(order, attempt, transaction);
        } else {
            attempt.state = success ? "PAID" : "FAILED";
            if (eligible) {
                order.paymentStatus = success ? "PAID" : "FAILED";
                if (success) order.transactionRef = transaction;
                shop.event(order, order.status, success ? "VNPay đã xác nhận thanh toán " + transaction
                    : "Thanh toán VNPay không thành công (" + response + "/" + state + ")", null);
                shop.notifyUser(order.userId, order.id, "Thanh toán VNPay " + order.code + (success ? " thành công." : " chưa thành công. Bạn có thể thử lại trước khi đơn hết hạn."));
            }
        }
        return reply("00", "Confirm success");
    }

    void markReview(MealOrder order, PaymentAttempt attempt, String transaction) {
        attempt.state = "REVIEW_REQUIRED";
        if (!List.of("PAID", "REFUNDED").contains(order.paymentStatus)) order.paymentStatus = "REVIEW_REQUIRED";
        shop.event(order, order.status, "VNPay báo thu tiền cần đối soát, giao dịch " + transaction, null);
        shop.notifyUser(order.userId, order.id, "Thanh toán " + order.code + " cần cửa hàng đối soát. Vui lòng liên hệ cửa hàng.");
    }

    @Transactional(readOnly=true)
    String returnTarget(Map<String,String> fields) {
        if (!authentic(fields)) return "/#payment-result/invalid";
        PaymentAttempt attempt = findAttempt(fields.get("vnp_TxnRef"));
        if (attempt == null) return "/#payment-result/invalid";
        MealOrder order = shop.get(MealOrder.class, attempt.orderId);
        if (!amountMatches(fields, attempt, order)) return "/#payment-result/invalid";
        String response = fields.getOrDefault("vnp_ResponseCode", "");
        String outcome = "00".equals(response) && "00".equals(fields.get("vnp_TransactionStatus"))
            ? "received" : "24".equals(response) ? "cancelled" : "failed";
        return "/#payment-result/" + order.id + "/" + outcome;
    }
}
