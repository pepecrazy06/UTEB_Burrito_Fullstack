package vn.uteb;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static vn.uteb.Domain.*;
import static vn.uteb.ShopService.*;

/** Dashboard aggregates at order grain, using the existing Vietnam-local creation time. */
@Service @Transactional(readOnly=true)
public class StatisticsService {
    static final String ZONE="Asia/Ho_Chi_Minh";
    private static final String EARNED=" and o.status in ('COMPLETED','RETURN_REQUESTED','RETURNING','RETURNED') and o.paymentStatus='PAID' and o.total>=0"
            +" and not exists (select r.id from RefundRequest r where r.orderId=o.id and r.status='REFUNDED')";
    private static final String BASE="(case when coalesce(o.subtotal,0)>coalesce(o.discount,0) then coalesce(o.subtotal,0)-coalesce(o.discount,0) else 0 end)";
    private static final String RATE="(case when o.commissionRate>=0 and o.commissionRate<=1 then o.commissionRate else 0 end)";
    @PersistenceContext EntityManager em;
    private boolean has(Account actor,String...roles){return Arrays.stream(roles).anyMatch(role->Arrays.asList(actor.roles.split(",")).contains(role));}
    private ResponseStatusException invalid(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private LocalDate date(String value,String label){
        if(value==null||value.isBlank())return null;
        try{if(!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new DateTimeParseException("format",value,0);LocalDate result=LocalDate.parse(value);if(result.getYear()<1)throw new DateTimeParseException("year",value,0);return result;}
        catch(DateTimeParseException e){throw invalid(label+" không hợp lệ. Dùng định dạng YYYY-MM-DD.");}
    }
    private Long storeId(String value){
        if(value==null||value.isBlank())return null;
        try{if(!value.matches("[1-9][0-9]*"))throw new NumberFormatException();return Long.valueOf(value);}
        catch(NumberFormatException e){throw invalid("Mã chi nhánh phải là số nguyên dương hợp lệ.");}
    }
    private <T> TypedQuery<T> query(String jpql,Class<T> type,Map<String,Object> parameters){
        TypedQuery<T> query=em.createQuery(jpql,type);parameters.forEach(query::setParameter);return query;
    }

    private List<Store> allowedStores(Account actor){
        if(has(actor,"ADMIN"))return em.createQuery("from Store order by id",Store.class).getResultList();
        Set<Long> ids=new TreeSet<>();if(actor.storeId!=null&&has(actor,"MANAGER","VENDOR","STAFF"))ids.add(actor.storeId);
        // Shippers retain access to assigned orders, including cross-branch assignments.
        if(has(actor,"SHIPPER"))ids.addAll(em.createQuery("select distinct o.storeId from MealOrder o where o.shipperId=:actor and o.storeId is not null",Long.class).setParameter("actor",actor.id).getResultList());
        return ids.isEmpty()?List.of():em.createQuery("from Store s where s.id in :ids order by s.id",Store.class).setParameter("ids",ids).getResultList();
    }
    public Map<String,Object> dashboard(Account actor,String startValue,String endValue,String storeValue){
        forbidden(has(actor,"ADMIN","MANAGER","VENDOR","STAFF","SHIPPER"));
        LocalDate start=date(startValue,"Từ ngày"),end=date(endValue,"Đến ngày");Long selected=storeId(storeValue);
        require(start==null||end==null||!start.isAfter(end),"Từ ngày không được lớn hơn đến ngày.");
        List<Store> allowed=allowedStores(actor);
        if(selected!=null){
            if(has(actor,"ADMIN")){if(em.find(Store.class,selected)==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Chi nhánh không tồn tại.");}
            else forbidden(allowed.stream().anyMatch(s->s.id.equals(selected)));
        }
        Map<String,Object> parameters=new LinkedHashMap<>();StringBuilder where=new StringBuilder("o.createdAt is not null");
        if(!has(actor,"ADMIN")){
            List<String> scopes=new ArrayList<>();
            if(actor.storeId!=null&&has(actor,"MANAGER","VENDOR","STAFF")){
                parameters.put("actorStore",actor.storeId);
                scopes.add("o.storeId=:actorStore");
            }
            if(has(actor,"SHIPPER")){scopes.add("o.shipperId=:actor");parameters.put("actor",actor.id);}
            where.append(" and (").append(scopes.isEmpty()?"1=0":String.join(" or ",scopes)).append(")");
        }
        if(selected!=null){where.append(" and o.storeId=:store");parameters.put("store",selected);}
        if(start!=null){where.append(" and o.createdAt>=:start");parameters.put("start",start.atStartOfDay());}
        if(end!=null){
            if(end.equals(LocalDate.of(9999,12,31))){where.append(" and o.createdAt<=:end");parameters.put("end",end.atTime(23,59,59,999999000));}
            else{where.append(" and o.createdAt<:end");parameters.put("end",end.plusDays(1).atStartOfDay());}
        }
        String scope=where.toString(),earned=scope+EARNED;
        Object[] totals=query("select count(o),count(distinct o.userId),count(distinct o.storeId) from MealOrder o where "+scope,Object[].class,parameters).getSingleResult();
        Object[] amounts=query("select coalesce(sum(o.total),0),coalesce(sum(round("+BASE+"*"+RATE+",0)),0),count(o) from MealOrder o where "+earned,Object[].class,parameters).getSingleResult();
        BigDecimal revenue=(BigDecimal)amounts[0],commission=(BigDecimal)amounts[1];
        Map<String,BigDecimal> daily=new LinkedHashMap<>();
        for(Object[] row:query("select cast(o.createdAt as LocalDate),sum(o.total) from MealOrder o where "+earned+" and o.createdAt is not null group by cast(o.createdAt as LocalDate) order by cast(o.createdAt as LocalDate)",Object[].class,parameters).getResultList())daily.put(row[0].toString(),(BigDecimal)row[1]);
        Map<String,Long> top=new LinkedHashMap<>();
        for(Object[] row:query("select l.name,sum(l.quantity) from OrderLine l where l.name is not null and l.quantity>0 and exists (select o.id from MealOrder o where o.id=l.orderId and "+earned+") group by l.name order by sum(l.quantity) desc,l.name",Object[].class,parameters).getResultList())top.put((String)row[0],((Number)row[1]).longValue());
        List<MealOrder> recent=query("from MealOrder o where "+scope+" order by o.createdAt desc,o.id desc",MealOrder.class,parameters).setMaxResults(5).getResultList();
        Map<String,Object> filters=new LinkedHashMap<>();filters.put("startDate",start==null?null:start.toString());filters.put("endDate",end==null?null:end.toString());filters.put("storeId",selected);filters.put("timeZone",ZONE);filters.put("dateField","createdAt");
        Map<String,Object> result=new LinkedHashMap<>();result.put("orders",recent);result.put("totalOrders",totals[0]);result.put("customers",totals[1]);result.put("stores",totals[2]);
        result.put("revenue",revenue);result.put("commission",commission);result.put("net",revenue.subtract(commission));result.put("completedOrders",amounts[2]);result.put("daily",daily);result.put("topProducts",top);result.put("filters",filters);
        result.put("availableStores",allowed.stream().map(s->Map.<String,Object>of("id",s.id,"name",s.name,"active",s.active)).toList());
        Map<String,Long> byStatus=new LinkedHashMap<>();
        for(Object[] row:query("select o.status,count(o) from MealOrder o where "+scope+" group by o.status order by o.status",Object[].class,parameters).getResultList())byStatus.put((String)row[0],((Number)row[1]).longValue());
        result.put("byStatus",byStatus);result.put("assignedOnly",has(actor,"SHIPPER")&&!has(actor,"ADMIN","MANAGER","VENDOR","STAFF"));
        return result;
    }
}
