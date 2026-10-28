package vn.uteb;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static vn.uteb.Domain.*;
import static vn.uteb.ShopService.*;

/** Manual cancellation/refund of prepaid, unprepared orders. Never calls a gateway. */
@Service @Transactional
public class OrderCancellationService {
    @PersistenceContext EntityManager em;
    private final ShopService shop;
    public OrderCancellationService(ShopService shop){this.shop=shop;}

    private MealOrder lockedOrder(long id){
        MealOrder order=em.find(MealOrder.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(order==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn hàng.");
        // Refresh also covers callers whose persistence context already contains the order.
        em.refresh(order,LockModeType.PESSIMISTIC_WRITE);return order;
    }
    private void conflict(boolean ok,String message){if(!ok)throw new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private void administrator(Account actor,Long storeId){forbidden(shop.has(actor,"ADMIN")||(shop.has(actor,"MANAGER","VENDOR")&&Objects.equals(actor.storeId,storeId)));}
    private void beforeCooking(MealOrder order){require(List.of("NEW","CONFIRMED").contains(order.status),"Chỉ yêu cầu hoặc duyệt hủy trước khi chế biến.");}
    private void paid(MealOrder order){
        // Historical MoMo orders may still need manual refunds; new checkout only accepts COD/VNPay.
        require("PAID".equals(order.paymentStatus)&&List.of("VNPAY","MOMO").contains(order.paymentMethod)
                &&order.total!=null&&order.total.signum()>0&&order.transactionRef!=null&&!order.transactionRef.isBlank(),"Đơn chưa có thanh toán trực tuyến thành công để hoàn tiền.");
        if("VNPAY".equals(order.paymentMethod)){
            boolean confirmed=shop.where(PaymentAttempt.class,"orderId",order.id).stream().anyMatch(attempt->
                    "PAID".equals(attempt.state)&&"00".equals(attempt.responseCode)&&"00".equals(attempt.transactionStatus)
                    &&order.transactionRef.equals(attempt.transactionNo)&&order.total.compareTo(attempt.amount)==0);
            require(confirmed,"Chưa có giao dịch VNPay đã xác minh khớp với đơn hàng.");
        }
    }
    private void samePayment(OrderCancellation request,MealOrder order){
        paid(order);require(request.amount.compareTo(order.total)==0&&Objects.equals(request.paymentMethod,order.paymentMethod)
                &&Objects.equals(request.paymentReference,order.transactionRef),"Dữ liệu thanh toán đã thay đổi; cần đối soát trước khi xử lý.");
    }
    private OrderCancellation forOrder(long id){return shop.where(OrderCancellation.class,"orderId",id).stream().findFirst().orElse(null);}
    private void notifyOperators(MealOrder order,String message){
        for(Account actor:shop.all(Account.class))if(actor.active&&actor.verified&&(shop.has(actor,"ADMIN")
                ||shop.has(actor,"MANAGER","VENDOR")&&Objects.equals(actor.storeId,order.storeId)))shop.notifyUser(actor.id,order.id,message);
    }

    public OrderCancellation request(long orderId,Map<String,Object> body){
        Account actor=shop.me();MealOrder order=lockedOrder(orderId);forbidden(Objects.equals(actor.id,order.userId));
        conflict(forOrder(orderId)==null,"Đơn đã có yêu cầu hủy; vui lòng xem kết quả xử lý.");
        beforeCooking(order);paid(order);
        String reason=str(body,"reason");require(reason.length()>=20&&reason.length()<=2000,"Lý do cần 20–2000 ký tự.");
        OrderCancellation request=new OrderCancellation();request.orderId=order.id;request.userId=order.userId;request.storeId=order.storeId;
        request.reason=reason;request.amount=order.total;request.paymentMethod=order.paymentMethod;request.paymentReference=order.transactionRef;
        shop.save(request);shop.event(order,order.status,"Khách yêu cầu hủy đơn đã thanh toán; yêu cầu #"+request.id,actor.id);
        shop.notifyUser(order.userId,order.id,"Đã nhận yêu cầu hủy "+order.code+". Đơn tạm chờ xét duyệt; chưa hoàn tiền.");
        notifyOperators(order,"Có yêu cầu hủy đơn đã thanh toán "+order.code);shop.audit("CANCELLATION_REQUESTED",order.code);
        return request;
    }

    /** Other order operators see state only, not private refund evidence. */
    public Map<String,Object> read(long orderId){
        MealOrder order=shop.accessibleOrder(orderId);Account actor=shop.me();OrderCancellation request=forOrder(orderId);
        if(request==null)return Map.of();
        if(Objects.equals(actor.id,order.userId)||shop.has(actor,"ADMIN")||(shop.has(actor,"MANAGER","VENDOR")&&Objects.equals(actor.storeId,order.storeId)))
            return Map.of("request",request);
        return Map.of("status",request.status);
    }

    public List<Map<String,Object>> list(String status){
        Account actor=shop.me();forbidden(shop.has(actor,"ADMIN","MANAGER","VENDOR"));
        require(status==null||status.isBlank()||List.of("REQUESTED","APPROVED","REJECTED","REFUNDED").contains(status),"Trạng thái yêu cầu hủy không hợp lệ.");
        return shop.all(OrderCancellation.class).stream().filter(r->shop.has(actor,"ADMIN")||Objects.equals(actor.storeId,r.storeId))
                .filter(r->status==null||status.isBlank()||status.equals(r.status))
                .map(r->Map.<String,Object>of("request",r,"order",shop.get(MealOrder.class,r.orderId))).toList();
    }

    // All competing operations take the order lock first, then the request lock.
    private OrderCancellation lockedRequest(long id){
        OrderCancellation snapshot=shop.get(OrderCancellation.class,id);lockedOrder(snapshot.orderId);
        em.refresh(snapshot,LockModeType.PESSIMISTIC_WRITE);return snapshot;
    }
    public OrderCancellation decide(long id,Map<String,Object> body){
        OrderCancellation request=lockedRequest(id);Account actor=shop.me();administrator(actor,request.storeId);
        conflict("REQUESTED".equals(request.status),"Yêu cầu đã được xử lý.");
        String decision=str(body,"status"),note=str(body,"note");
        require(List.of("APPROVED","REJECTED").contains(decision),"Chỉ chọn duyệt hủy hoặc từ chối.");
        require(note.length()<=1000&&(!"REJECTED".equals(decision)||note.length()>=5),"Từ chối cần lý do từ 5 đến 1000 ký tự.");
        MealOrder order=shop.get(MealOrder.class,request.orderId);
        if("APPROVED".equals(decision)){
            beforeCooking(order);samePayment(request,order);
            // Return reserved inventory once, at cancellation approval, never at refund confirmation.
            Map<Long,Integer> quantities=new TreeMap<>();
            shop.where(OrderLine.class,"orderId",order.id).forEach(line->quantities.merge(line.productId,line.quantity,Integer::sum));
            quantities.forEach((productId,quantity)->{Product product=em.find(Product.class,productId,LockModeType.PESSIMISTIC_WRITE);require(product!=null,"Món không còn tồn tại; cần đối soát tồn kho.");em.refresh(product,LockModeType.PESSIMISTIC_WRITE);product.stock+=quantity;});
            order.status="CANCELLED"; // paymentStatus stays PAID until manual confirmation.
        }
        request.status=decision;request.decisionNote=note;request.decidedBy=actor.id;request.decidedAt=LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        shop.event(order,order.status,"APPROVED".equals(decision)?"Đã duyệt hủy; chờ hoàn tiền thủ công.":"Từ chối yêu cầu hủy; xem lý do tại yêu cầu #"+request.id,actor.id);
        shop.notifyUser(order.userId,order.id,"Yêu cầu hủy "+order.code+("APPROVED".equals(decision)?" đã được duyệt, đang chờ hoàn tiền.":" bị từ chối. Xem lý do trong chi tiết đơn."));
        shop.audit("CANCELLATION_"+decision,order.code+" / "+request.id);return request;
    }

    public OrderCancellation confirmRefund(long id,Map<String,Object> body){
        OrderCancellation request=lockedRequest(id);Account actor=shop.me();administrator(actor,request.storeId);
        conflict("APPROVED".equals(request.status),"Chỉ xác nhận một lần cho yêu cầu đã duyệt và đang chờ hoàn tiền.");
        MealOrder order=shop.get(MealOrder.class,request.orderId);require("CANCELLED".equals(order.status),"Đơn chưa được duyệt hủy.");samePayment(request,order);
        require(Boolean.TRUE.equals(body.get("confirmed")),"Cần xác nhận đã hoàn tiền thủ công ngoài hệ thống.");
        String reference=str(body,"reference"),evidence=str(body,"evidence"),note=str(body,"note");
        require(reference.length()>=3&&reference.length()<=120,"Mã giao dịch hoàn tiền cần 3–120 ký tự.");
        require(evidence.length()>=8&&evidence.length()<=255,"Bằng chứng hoàn tiền cần 8–255 ký tự.");require(note.length()<=1000,"Ghi chú tối đa 1000 ký tự.");
        BigDecimal amount;LocalDateTime refundedAt;
        try{amount=new BigDecimal(str(body,"amount"));refundedAt=LocalDateTime.parse(str(body,"refundedAt"));}
        catch(RuntimeException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Số tiền hoặc thời gian hoàn tiền không hợp lệ.");}
        require(amount.signum()>0&&amount.scale()<=2&&amount.compareTo(request.amount)==0,"Chỉ xác nhận hoàn đủ số tiền đã thanh toán.");
        require(request.decidedAt!=null&&!refundedAt.isBefore(request.decidedAt)&&!refundedAt.isAfter(LocalDateTime.now()),"Thời gian hoàn tiền phải sau khi duyệt và không ở tương lai.");
        conflict(shop.where(OrderCancellation.class,"refundReference",reference).isEmpty(),"Mã giao dịch hoàn tiền đã được sử dụng.");
        request.refundReference=reference;request.evidence=evidence;request.refundNote=note;request.confirmedAmount=amount;
        request.refundedAt=refundedAt;request.refundedBy=actor.id;request.status="REFUNDED";order.paymentStatus="REFUNDED";
        shop.event(order,order.status,"Đã xác nhận hoàn tiền thủ công "+amount.toPlainString()+" VND; mã yêu cầu "+request.id,actor.id);
        shop.notifyUser(order.userId,order.id,"Đã xác nhận hoàn tiền thủ công cho "+order.code+". Xem bằng chứng trong chi tiết đơn.");
        shop.audit("CANCELLATION_REFUNDED",order.code+" / "+request.id);return request;
    }
}
