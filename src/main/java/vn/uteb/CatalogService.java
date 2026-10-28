package vn.uteb;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static vn.uteb.Domain.*;
import static vn.uteb.ShopService.require;

/** Public rankings use completed, paid, non-refunded purchases, never seed counters. */
@Service @Transactional(readOnly=true)
public class CatalogService {
    @PersistenceContext EntityManager em;
    static String searchKey(String value) {
        return Normalizer.normalize(Objects.requireNonNullElse(value,""),Normalizer.Form.NFD)
                .replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replace('đ','d').trim();
    }
    public Map<Long,Map<String,Object>> stats() {
        Map<Long,Map<String,Object>> result=new LinkedHashMap<>();
        for(Object[] row:em.createQuery("select l.productId,sum(l.quantity) from OrderLine l, MealOrder o where l.orderId=o.id and l.quantity>0 and o.status in ('COMPLETED','RETURN_REQUESTED','RETURNING','RETURNED') and o.paymentStatus='PAID' and not exists (select r.id from RefundRequest r where r.orderId=o.id and r.status='REFUNDED') group by l.productId",Object[].class).getResultList())
            metric(result,(Long)row[0]).put("sold",((Number)row[1]).longValue());
        for(Object[] row:em.createQuery("select r.productId,avg(cast(r.stars as double)),count(r) from Review r group by r.productId",Object[].class).getResultList()) {
            metric(result,(Long)row[0]).put("rating",((Number)row[1]).doubleValue());
            metric(result,(Long)row[0]).put("reviews",((Number)row[2]).longValue());
        }
        for(Object[] row:em.createQuery("select m.productId,count(m) from UserMark m where m.kind='FAVORITE' group by m.productId",Object[].class).getResultList())
            metric(result,(Long)row[0]).put("favorites",((Number)row[1]).longValue());
        return result;
    }
    private Map<String,Object> metric(Map<Long,Map<String,Object>> result,Long id) {
        return result.computeIfAbsent(id,key->new LinkedHashMap<>(Map.of("sold",0L,"rating",0d,"reviews",0L,"favorites",0L)));
    }
    private double score(Map<Long,Map<String,Object>> metrics,Product p,String key) {
        return ((Number)metrics.getOrDefault(p.id,Map.of()).getOrDefault(key,0)).doubleValue();
    }
    Store activeStore(long id) {
        Store store=em.find(Store.class,id);
        if(store==null||!store.active)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Cửa hàng không còn phục vụ.");
        return store;
    }
    public Map<String,Object> list(Long storeId,String category,String query,String sort,int page,int size,long minimumSold) {
        require(page>=1&&size>=1&&size<=20&&minimumSold>=0,"Phân trang hoặc số lượng bán không hợp lệ.");
        require(List.of("default","new","top","rating","favorite","price-asc","price-desc").contains(sort),"Cách sắp xếp không hợp lệ.");
        if(storeId!=null)activeStore(storeId);
        require(query.length()<=100&&category.length()<=100,"Từ khóa hoặc danh mục tối đa 100 ký tự.");String text=searchKey(query);
        List<Product> products=new ArrayList<>(em.createQuery("select p from Product p, Store s where p.storeId=s.id and p.available=true and s.active=true",Product.class).getResultList());
        Map<Long,Map<String,Object>> metrics=stats();
        products.removeIf(p->(storeId!=null&&!storeId.equals(p.storeId))||(!category.isBlank()&&!category.equals("ALL")&&!category.equals(p.category))||!searchKey(p.name).contains(text)||score(metrics,p,"sold")<minimumSold);
        Comparator<Product> order=switch(sort) {
            case "new"->Comparator.comparing((Product p)->p.createdAt,Comparator.nullsLast(Comparator.reverseOrder()));
            case "top"->Comparator.comparingDouble((Product p)->score(metrics,p,"sold")).reversed();
            case "rating"->Comparator.comparingDouble((Product p)->score(metrics,p,"rating")).reversed().thenComparing(Comparator.comparingDouble((Product p)->score(metrics,p,"reviews")).reversed());
            case "favorite"->Comparator.comparingDouble((Product p)->score(metrics,p,"favorites")).reversed();
            case "price-asc"->Comparator.comparing(p->p.price);
            case "price-desc"->Comparator.comparing((Product p)->p.price).reversed();
            default->Comparator.comparing(p->p.id);
        };
        products.sort(order.thenComparing(Comparator.comparing((Product p)->p.id).reversed()));
        if(List.of("new","top","rating","favorite").contains(sort)&&products.size()>20)products=new ArrayList<>(products.subList(0,20));
        int total=products.size(),pages=(total+size-1)/size;
        int from=(int)Math.min((long)(page-1)*size,total),to=Math.min(from+size,total);
        List<Map<String,Object>> items=products.subList(from,to).stream().map(p->Map.<String,Object>of("product",p,"stats",metric(metrics,p.id))).toList();
        return Map.of("items",items,"total",total,"page",page,"pages",pages,"size",size);
    }
    public Map<String,Object> product(long id,int reviewPage) {
        require(reviewPage>=1,"Trang đánh giá không hợp lệ.");
        Product product=em.find(Product.class,id);
        if(product==null||!product.available)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Món không còn bán.");
        Store store=activeStore(product.storeId);
        long count=em.createQuery("select count(r) from Review r where r.productId=:id",Long.class).setParameter("id",id).getSingleResult();
        long offset=(long)(reviewPage-1)*8;require(offset<=Integer.MAX_VALUE,"Trang đánh giá không hợp lệ.");
        List<Review> reviews=em.createQuery("from Review r where r.productId=:id order by r.createdAt desc,r.id desc",Review.class).setParameter("id",id).setFirstResult((int)offset).setMaxResults(8).getResultList();
        return Map.of("product",product,"store",store,"stats",metric(stats(),id),"reviews",reviews,"reviewPage",reviewPage,"reviewPages",(count+7)/8);
    }
    public Map<String,Object> shop(long id) {
        Store store=activeStore(id);LocalDateTime now=LocalDateTime.now();
        List<Voucher> promotions=em.createQuery("from Voucher v where v.active=true and v.userId is null and (v.storeId is null or v.storeId=:id) and v.startsAt<:now and v.endsAt>:now and v.used<v.usageLimit order by v.endsAt",Voucher.class).setParameter("id",id).setParameter("now",now).getResultList();
        return Map.of("store",store,"promotions",promotions);
    }
}
