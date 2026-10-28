package vn.uteb;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import static vn.uteb.Domain.*;
import static vn.uteb.ShopService.*;

@Service
class AiMenuService {
    private final ShopService shop;
    private final MenuAiClient ai;
    AiMenuService(ShopService shop,MenuAiClient ai) { this.shop=shop; this.ai=ai; }
    boolean configured() { return ai.configured(); }
    boolean ready() { return ai.ready(); }

    // No open database transaction is held while waiting for the AI network call.
    Map<String,Object> recommend(long storeId,String message,List<String> previous) {
        Store store=shop.get(Store.class,storeId);
        require(store.active,"Chi nhánh đang tạm ngừng phục vụ.");
        List<Ingredient> ingredients=shop.all(Ingredient.class);
        MenuPreferences preferences=ai.understand(message,previous,ingredients.stream().map(i->i.name).toList());
        if(preferences.allergy()) return reply("Thực đơn chưa có thông tin đầy đủ về chất gây dị ứng trong phần nền. Bạn hãy xác nhận trực tiếp với cửa hàng trước khi chọn món.",List.of(),List.of());
        if(!"FOOD".equals(preferences.intent())) return reply("CLARIFY".equals(preferences.intent())&&!preferences.clarification().isBlank()
            ? preferences.clarification():"Mình hỗ trợ chọn món UTEB. Bạn thích nhân gì, khoảng bao nhiêu kcal hoặc ngân sách bao nhiêu?",List.of(),List.of());
        List<String> criteria=criteria(preferences);
        List<Recipe> recipes=choose(shop.all(Product.class),shop.all(Ingredient.class),storeId,preferences);
        List<Map<String,Object>> items=new ArrayList<>();
        for(Recipe recipe:recipes) {
            // Re-read and validate against the exact same quote function used by checkout.
            Product product=shop.get(Product.class,recipe.product.id);
            if(!product.available||product.stock<1||!Objects.equals(product.storeId,storeId)) continue;
            try {
                Quote quote=shop.quote(product,recipe.ids());
                if(!withinLimits(quote.price(),quote.calories(),preferences)) continue;
                Map<String,Object> item=new LinkedHashMap<>();
                item.put("productId",product.id); item.put("storeId",storeId); item.put("name",product.name);
                item.put("image",product.image); item.put("category",product.category);
                item.put("price",quote.price()); item.put("calories",quote.calories());
                item.put("ingredientIds",recipe.ingredients.stream().map(i->i.id).toList());
                item.put("customization",quote.description().isBlank()?"Khẩu phần cố định":quote.description());
                item.put("reason",preferences.lowCalories()?"Ưu tiên ít calo trong các cấu hình phù hợp.":"Phù hợp với các tiêu chí và nguyên liệu bạn đã chọn.");
                items.add(item);
            } catch(ResponseStatusException unavailableIngredient) { /* Catalog can change during a request. */ }
        }
        return reply(items.isEmpty()?"Chưa có món đang bán đáp ứng đủ các tiêu chí này. Bạn có thể tăng giới hạn calo/ngân sách hoặc đổi loại nhân."
            :"Mình gợi ý "+items.size()+" món tại "+store.name+". Calo và giá bên dưới tính cho một phần theo đúng cấu hình gợi ý.",criteria,items);
    }

    static Map<String,Object> reply(String message,List<String> criteria,List<?> items) {
        return Map.of("message",message,"criteria",criteria,"items",items);
    }
    record Recipe(Product product,List<Ingredient> ingredients,BigDecimal price,int calories,int penalty) {
        String ids() { return String.join(",",ingredients.stream().map(i->i.id.toString()).toList()); }
    }
    static String plain(String value) { return Normalizer.normalize(Objects.toString(value,""),Normalizer.Form.NFD).replaceAll("\\p{M}+","").replace('đ','d').replace('Đ','D').toLowerCase(Locale.ROOT); }
    static String protein(String name) {
        String n=plain(name);
        if(n.contains("dau hu")||n.endsWith(" chay")) return "TOFU";
        if(n.contains("ga ")||n.startsWith("ga")||n.contains(" ga")) return "CHICKEN";
        if(n.contains("bo ")||n.startsWith("bo")||n.contains(" bo")) return "BEEF";
        if(n.contains("heo")) return "PORK";
        return "";
    }
    static boolean withinLimits(BigDecimal price,int calories,MenuPreferences p) {
        return (p.minCalories()==null||calories>=p.minCalories())&&(p.maxCalories()==null||calories<=p.maxCalories())
            &&(p.maxPrice()==null||price.compareTo(BigDecimal.valueOf(p.maxPrice()))<=0);
    }

    static List<Recipe> choose(List<Product> products,List<Ingredient> catalog,long storeId,MenuPreferences prefs) {
        Comparator<Recipe> ordering=prefs.lowCalories()
            ? Comparator.comparingInt(Recipe::calories).thenComparingInt(Recipe::penalty).thenComparing(Recipe::price)
            : Comparator.comparingInt(Recipe::penalty).thenComparing(Recipe::price).thenComparingInt(Recipe::calories);
        ordering=ordering.thenComparing(r->r.product.id);
        List<Recipe> best=new ArrayList<>();
        for(Product p:products) {
            if(!Objects.equals(p.storeId,storeId)||!p.available||p.stock<1) continue;
            if(prefs.categories().isEmpty()?!Set.of("Burrito","Bowl","Tacos","Quesadilla","Salad").contains(p.category):!prefs.categories().contains(p.category)) continue;
            if(prefs.vegetarian()&&!p.name.endsWith(" Chay")) continue;
            if("NO".equals(prefs.spicy())&&plain(p.name).contains("cay")) continue;
            if(!p.customizable) {
                String filling=protein(p.name);
                if(prefs.noRice()||prefs.noBeans()||!prefs.avoidIngredients().isEmpty()
                    ||prefs.excludedProteins().contains(filling)||(!prefs.proteins().isEmpty()&&!prefs.proteins().contains(filling))
                    ||"YES".equals(prefs.spicy())||("NO".equals(prefs.spicy())&&plain(p.name).contains("cay"))) continue;
                if(withinLimits(p.price,p.calories,prefs)) best.add(new Recipe(p,List.of(),p.price,p.calories,0));
                continue;
            }
            List<List<Ingredient>> groups=new ArrayList<>();
            for(String group:menuRequiredGroups(p)) {
                List<Ingredient> options=catalog.stream().filter(i->i.available&&group.equals(i.groupName))
                    .filter(i->!prefs.avoidIngredients().contains(i.name))
                    .filter(i->!"PROTEIN".equals(group)||(!prefs.excludedProteins().contains(protein(i.name))
                        &&(prefs.proteins().isEmpty()?protein(i.name).equals(protein(p.name)):prefs.proteins().contains(protein(i.name)))
                        &&(!p.name.endsWith(" Chay")||"TOFU".equals(protein(i.name)))))
                    .filter(i->!"RICE".equals(group)||!prefs.noRice()||"khong com".equals(plain(i.name)))
                    .filter(i->!"BEANS".equals(group)||!prefs.noBeans()||"khong dau".equals(plain(i.name)))
                    .filter(i->!"SAUCE".equals(group)||"ANY".equals(prefs.spicy())||("YES".equals(prefs.spicy())==plain(i.name).contains("cay")))
                    .toList();
                groups.add(options);
            }
            List<List<Ingredient>> combinations=new ArrayList<>();
            combinations.add(new ArrayList<>());
            for(List<Ingredient> group:groups) {
                List<List<Ingredient>> next=new ArrayList<>();
                expand: for(List<Ingredient> prior:combinations) for(Ingredient i:group) {
                    List<Ingredient> combined=new ArrayList<>(prior); combined.add(i); next.add(combined);
                    if(next.size()>=4096) break expand;
                }
                combinations=next;
            }
            Recipe winner=null;
            for(List<Ingredient> selection:combinations) {
                BigDecimal price=p.price; int kcal=p.calories,penalty=0;
                for(Ingredient i:selection) {
                    price=price.add(i.price); kcal+=p.category.equals("Kids")?(i.calories+1)/2:i.calories;
                    String name=plain(i.name);
                    if("PROTEIN".equals(i.groupName)&&!protein(i.name).equals(protein(p.name))) penalty+=20;
                    if("RICE".equals(i.groupName)&&!prefs.noRice()) penalty+=name.equals("com trang")?0:name.equals("com gao lut")?1:8;
                    if("BEANS".equals(i.groupName)&&!prefs.noBeans()) penalty+=name.equals("dau den")?0:name.equals("dau pinto")?1:8;
                    if("SAUCE".equals(i.groupName)&&"ANY".equals(prefs.spicy())) penalty+=name.equals(plain(p.name).contains("cay")?"sot cay":"salsa ca chua")?0:2;
                }
                String included=includedMenuProtein(p);
                if(!included.isBlank()) price=price.subtract(catalog.stream().filter(i->included.equals(i.name)&&"PROTEIN".equals(i.groupName)).findFirst().map(i->i.price).orElse(BigDecimal.ZERO));
                if(price.signum()<=0||!withinLimits(price,kcal,prefs)) continue;
                Recipe candidate=new Recipe(p,List.copyOf(selection),price,kcal,penalty);
                if(winner==null||ordering.compare(candidate,winner)<0) winner=candidate;
            }
            if(winner!=null) best.add(winner);
        }
        return best.stream().sorted(ordering).limit(3).toList();
    }

    static List<String> criteria(MenuPreferences p) {
        List<String> tags=new ArrayList<>();
        Map<String,String> names=Map.of("CHICKEN","Gà","BEEF","Bò","PORK","Heo","TOFU","Đậu hũ");
        if(p.vegetarian()) tags.add("Ăn chay");
        p.proteins().forEach(x->tags.add("Nhân "+names.get(x)));
        p.excludedProteins().forEach(x->tags.add("Không ăn "+names.get(x)));
        if(p.lowCalories()) tags.add("Ưu tiên ít calo");
        if(p.minCalories()!=null) tags.add("Từ "+p.minCalories()+" kcal/phần");
        if(p.maxCalories()!=null) tags.add("Tối đa "+p.maxCalories()+" kcal/phần");
        if(p.maxPrice()!=null) tags.add("Tối đa "+p.maxPrice()+"đ/phần");
        if(p.noRice()) tags.add("Không cơm"); if(p.noBeans()) tags.add("Không đậu");
        if(!"ANY".equals(p.spicy())) tags.add("YES".equals(p.spicy())?"Ăn cay":"Không cay");
        tags.addAll(p.categories()); p.avoidIngredients().forEach(x->tags.add("Không "+x));
        return tags;
    }
}
