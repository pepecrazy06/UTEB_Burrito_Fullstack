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
        return recommend(storeId,message,previous,List.of());
    }
    Map<String,Object> recommend(long storeId,String message,List<String> previous,List<AiConversationTurn> conversation) {
        Store store=shop.get(Store.class,storeId);
        require(store.active,"Chi nhánh đang tạm ngừng phục vụ.");
        List<Ingredient> ingredients=shop.all(Ingredient.class);
        String publicAnswer=AiPublicKnowledge.answer(shop,store,ingredients,message,previous,conversation);
        if(publicAnswer!=null)return reply(publicAnswer,List.of(),List.of());
        List<String> ingredientNames=ingredients.stream().map(i->i.name).toList();
        MenuPreferences preferences=conversation.isEmpty()?ai.understand(message,previous,ingredientNames):ai.understand(message,previous,ingredientNames,conversation);
        if(preferences.allergy()&&!Set.of("STORE","ORDERING","GREETING").contains(preferences.topic())) return reply(AiPublicKnowledge.allergy(shop,store,ingredients,AiPublicKnowledge.requests(previous,conversation,message)),List.of(),List.of());
        if(!"FOOD".equals(preferences.intent())) return reply("CLARIFY".equals(preferences.intent())&&!preferences.clarification().isBlank()
            ? preferences.clarification():"Mình là trợ lý món ăn UTEB: có thể gợi ý theo món bạn thèm, khẩu vị, ngân sách, so sánh món hoặc giải thích cách đặt hàng. Bạn đang muốn ăn gì?",List.of(),List.of());
        List<String> criteria=criteria(preferences);
        List<Product> menu=shop.all(Product.class).stream().filter(p->Objects.equals(p.storeId,storeId)&&p.available&&p.stock>0).toList();
        boolean suggest=Set.of("RECOMMEND","COMPARE","INGREDIENTS").contains(preferences.topic());
        List<Recipe> recipes=suggest?choose(menu,ingredients,storeId,preferences):List.of();
        List<Map<String,Object>> items=new ArrayList<>();
        for(Recipe recipe:recipes) {
            // Re-read and validate against the exact same quote function used by checkout.
            Product product=shop.get(Product.class,recipe.product.id);
            if(!product.available||product.stock<preferences.servings()||!Objects.equals(product.storeId,storeId)) continue;
            try {
                Quote quote=shop.quote(product,recipe.ids());
                if(!withinLimits(quote.price(),quote.calories(),preferences)) continue;
                Map<String,Object> item=new LinkedHashMap<>();
                item.put("productId",product.id); item.put("storeId",storeId); item.put("name",product.name);
                item.put("image",product.image); item.put("category",product.category);
                item.put("price",quote.price()); item.put("calories",quote.calories());
                item.put("quantity",preferences.servings());item.put("totalPrice",quote.price().multiply(BigDecimal.valueOf(preferences.servings())));
                item.put("showCalories",preferences.lowCalories()||"LIGHT".equals(preferences.goal())||preferences.minCalories()!=null||preferences.maxCalories()!=null);
                item.put("ingredientIds",recipe.ingredients.stream().map(i->i.id).toList());
                item.put("customization",quote.description().isBlank()?"Khẩu phần cố định":quote.description());
                item.put("reason",reason(preferences));
                items.add(item);
            } catch(ResponseStatusException unavailableIngredient) { /* Catalog can change during a request. */ }
        }
        String fallback=suggest?(items.isEmpty()?"Chưa có món đang bán đáp ứng đủ yêu cầu này tại "+store.name+". Bạn muốn đổi loại món, nguyên liệu hoặc khoảng giá nào?"
            :"Mình chọn "+items.size()+" phương án hợp với yêu cầu của bạn tại "+store.name+". "+reason(preferences)+(preferences.servings()>1?" Mỗi phương án gồm "+preferences.servings()+" phần cùng món; giá chưa gồm phí giao và ưu đãi.":" Bạn có thể xem hoặc chỉnh cấu hình trước khi thêm vào giỏ."))
            :"GREETING".equals(preferences.topic())?"Chào bạn! Hôm nay bạn thèm món gì: gà, bò, ăn chay hay một món thật no? Mình cũng có thể so sánh món và tư vấn theo ngân sách."
            :"ORDERING".equals(preferences.topic())?"Bạn chọn món, tùy chỉnh nguyên liệu rồi thêm vào giỏ. Đăng nhập để chọn nhận tại cửa hàng hoặc giao tận nơi, áp mã và thanh toán COD hoặc VNPay."
            :store.name+" ở "+store.address+", giờ hoạt động "+store.hours+". Bạn muốn biết về giao hàng hay cách đặt món?";
        Map<String,Object> facts=new LinkedHashMap<>();
        Map<String,Object> storeFacts=new LinkedHashMap<>();storeFacts.put("name",store.name);storeFacts.put("address",store.address);storeFacts.put("hours",store.hours);storeFacts.put("phone",store.phone);storeFacts.put("pickup",store.pickup);storeFacts.put("delivery",store.delivery);storeFacts.put("internalDeliveryFee",store.deliveryFee);
        facts.put("store",storeFacts);facts.put("preferences",preferences);facts.put("exactSuggestions",items);
        facts.put("activeStores",shop.all(Store.class).stream().filter(s->s.active).map(s->Map.of("name",s.name,"address",Objects.toString(s.address,""),"hours",Objects.toString(s.hours,""),"phone",Objects.toString(s.phone,""),"pickup",s.pickup,"delivery",s.delivery)).toList());
        boolean explainMenu=Set.of("COMPARE","INGREDIENTS").contains(preferences.topic())||suggest&&items.isEmpty();
        Set<Object> suggestedIds=new HashSet<>(items.stream().map(i->i.get("productId")).toList());
        facts.put("availableMenu",explainMenu?menu.stream()
            .filter(p->!suggestedIds.isEmpty()?suggestedIds.contains(p.id):(preferences.categories().isEmpty()||preferences.categories().contains(p.category))&&(preferences.productKeywords().isEmpty()||preferences.productKeywords().stream().anyMatch(k->plain(p.name+" "+p.description).contains(plain(k)))))
            .limit(12).map(p->Map.of("name",p.name,"category",p.category,"description",Objects.toString(p.description,""),"basePrice",p.price,"baseCalories",p.calories,"customizable",p.customizable)).toList():List.of());
        facts.put("availableIngredients",explainMenu?ingredients.stream().filter(i->i.available).map(i->Map.of("name",i.name,"group",i.groupName,"extraPrice",i.price,"extraCalories",i.calories)).toList():List.of());
        facts.put("policies",List.of("Ngân sách món chưa gồm phí giao và ưu đãi; phí chính xác xem ở bước thanh toán.","Miễn phí giao cho đơn từ 150000 VND tại mỗi chi nhánh.","Thanh toán COD hoặc VNPay; VNPay cần được cấu hình sẵn sàng.","Trợ lý không tự thêm giỏ hoặc đặt đơn.","Không có dữ liệu gram protein, độ no bảo đảm hoặc chất gây dị ứng đầy đủ."));
        // Use known serving formats for comparisons; a small language model must not invent size or nutrition differences.
        Map<String,String> formats=Map.of("Burrito","Burrito là bánh tortilla cuộn nhân.","Bowl","Bowl phục vụ phần nhân trong tô, không cuộn bánh tortilla.","Tacos","Tacos dùng bánh tortilla nhỏ với nhân và sốt.","Quesadilla","Quesadilla là món bánh tortilla áp chảo.","Salad","Salad có phần nền rau cùng nhân và sốt.","Kids","Kids là nhóm phần ăn cho trẻ em.","Sides","Sides là các món ăn kèm.","Drinks","Drinks là nhóm đồ uống.");
        facts.put("servingFormats",formats);
        if("COMPARE".equals(preferences.topic())&&preferences.categories().size()>1){
            String explanation=String.join(" ",preferences.categories().stream().map(formats::get).filter(Objects::nonNull).toList());
            return reply(explanation+" Bạn có thể chọn nhân và cấu hình phù hợp cho từng món. "+(items.isEmpty()?fallback:"Các thẻ bên dưới là món đang bán, kèm giá và nguyên liệu thực tế để bạn đối chiếu."),criteria,items);
        }
        String answer=null;
        if(!suggest||!items.isEmpty())try{answer=ai.respond(message,conversation,facts);}catch(ResponseStatusException unavailableAnswer){ /* Validated recommendations remain usable if the prose generation times out. */ }
        if(answer!=null&&!items.isEmpty()&&plain(answer).matches("(?s).*(vuot.{0,30}ngan sach|ngoai ngan sach|nguyen lieu khong mong muon|khong dap ung yeu cau).*"))answer=null;
        if(answer!=null&&answer.startsWith(message))answer=answer.substring(message.length()).strip();
        return reply(answer==null||answer.isBlank()?fallback:answer,criteria,items);
    }
    static String reason(MenuPreferences p){
        if(p.lowCalories()||"LIGHT".equals(p.goal()))return "Ưu tiên phần ăn nhẹ theo các yêu cầu bạn đưa ra.";
        if("FILLING".equals(p.goal()))return "Ưu tiên cấu hình đầy đủ nhân và các thành phần chính cho nhu cầu ăn no.";
        if("VALUE".equals(p.goal()))return "Ưu tiên mức giá thấp trong những món đáp ứng khẩu vị của bạn.";
        if(!p.preferredIngredients().isEmpty())return "Có nguyên liệu bạn thích: "+String.join(", ",p.preferredIngredients())+".";
        return "Giữ đúng loại nhân, khẩu vị và những nguyên liệu bạn muốn tránh.";
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
            &&(p.maxPrice()==null||price.compareTo(BigDecimal.valueOf(p.maxPrice()))<=0)
            &&(p.totalBudget()==null||price.multiply(BigDecimal.valueOf(p.servings())).compareTo(BigDecimal.valueOf(p.totalBudget()))<=0);
    }

    static List<Recipe> choose(List<Product> products,List<Ingredient> catalog,long storeId,MenuPreferences prefs) {
        Comparator<Recipe> ordering=prefs.lowCalories()||"LIGHT".equals(prefs.goal())
            ? Comparator.comparingInt(Recipe::calories).thenComparingInt(Recipe::penalty).thenComparing(Recipe::price)
            : "FILLING".equals(prefs.goal())?Comparator.comparingInt(Recipe::penalty).thenComparing(Comparator.comparingInt(Recipe::calories).reversed()).thenComparing(Recipe::price)
            : "VALUE".equals(prefs.goal())?Comparator.comparing(Recipe::price).thenComparingInt(Recipe::penalty)
            : Comparator.comparingInt(Recipe::penalty).thenComparing(Recipe::price).thenComparingInt(Recipe::calories);
        ordering=ordering.thenComparing(r->r.product.id);
        List<Recipe> best=new ArrayList<>();
        for(Product p:products) {
            if(!Objects.equals(p.storeId,storeId)||!p.available||p.stock<prefs.servings()) continue;
            if(!prefs.productKeywords().isEmpty()&&!prefs.productKeywords().stream().allMatch(k->plain(p.name).contains(plain(k))||plain(p.description).contains(plain(k))||(p.customizable&&prefs.preferredIngredients().stream().anyMatch(i->plain(i).contains(plain(k))))))continue;
            if(prefs.categories().isEmpty()?!Set.of("Burrito","Bowl","Tacos","Quesadilla","Salad").contains(p.category):!prefs.categories().contains(p.category)) continue;
            if(prefs.vegetarian()&&!p.vegetarian) continue;
            if("NO".equals(prefs.spicy())&&plain(p.name).contains("cay")) continue;
            if(!p.customizable) {
                String filling=protein(p.name);
                if(prefs.noRice()||prefs.noBeans()||!prefs.avoidIngredients().isEmpty()
                    ||prefs.preferredIngredients().stream().anyMatch(name->!plain(p.name+" "+p.description).contains(plain(name)))
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
                        &&(!p.vegetarian||i.vegetarian)))
                    .filter(i->!"RICE".equals(group)||!prefs.noRice()||"khong com".equals(plain(i.name)))
                    .filter(i->!"BEANS".equals(group)||!prefs.noBeans()||"khong dau".equals(plain(i.name)))
                    .filter(i->!"SAUCE".equals(group)||"ANY".equals(prefs.spicy())||("YES".equals(prefs.spicy())==plain(i.name).contains("cay")))
                    .toList();
                groups.add(options);
            }
            List<Ingredient> additions=new ArrayList<>();boolean missingPreference=false;
            for(String wanted:prefs.preferredIngredients()){
                Ingredient match=catalog.stream().filter(i->i.available&&i.name.equals(wanted)&&!prefs.avoidIngredients().contains(i.name)).findFirst().orElse(null);
                if(match==null){missingPreference=true;break;}
                if(List.of("TOPPING","EXTRA").contains(match.groupName)){
                    if(p.vegetarian&&!match.vegetarian){missingPreference=true;break;}
                    additions.add(match);
                }else if(!menuRequiredGroups(p).contains(match.groupName)){
                    if(!List.of("RICE","BEANS").contains(match.groupName)){missingPreference=true;break;}
                    if((prefs.noRice()&&"RICE".equals(match.groupName)&&!plain(match.name).equals("khong com"))||(prefs.noBeans()&&"BEANS".equals(match.groupName)&&!plain(match.name).equals("khong dau"))){missingPreference=true;break;}
                    additions.add(match);
                }
            }
            if(missingPreference)continue;
            List<List<Ingredient>> combinations=new ArrayList<>();
            combinations.add(new ArrayList<>(additions));
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
                if(!selection.stream().map(i->i.name).toList().containsAll(prefs.preferredIngredients()))continue;
                BigDecimal price=p.price; int kcal=p.calories,penalty=0;
                for(Ingredient i:selection) {
                    price=price.add(i.price); kcal+=ingredientCalories(p,i);
                    String name=plain(i.name);
                    if("PROTEIN".equals(i.groupName)&&!protein(i.name).equals(protein(p.name))) penalty+=20;
                    if("RICE".equals(i.groupName)&&!prefs.noRice()) penalty+=name.equals("com trang")?0:name.equals("com gao lut")?1:8;
                    if("BEANS".equals(i.groupName)&&!prefs.noBeans()) penalty+=name.equals("dau den")?0:name.equals("dau pinto")?1:8;
                    if("SAUCE".equals(i.groupName)) penalty+=name.equals("YES".equals(prefs.spicy())||"ANY".equals(prefs.spicy())&&plain(p.name).contains("cay")?"sot cay":"salsa ca chua")?0:2;
                }
                Long included=includedMenuProtein(p);
                if(included!=null) price=price.subtract(catalog.stream().filter(i->included.equals(i.id)&&"PROTEIN".equals(i.groupName)).findFirst().map(i->i.price).orElse(BigDecimal.ZERO));
                if(price.signum()<=0||!withinLimits(price,kcal,prefs)) continue;
                Recipe candidate=new Recipe(p,List.copyOf(selection),price,kcal,penalty);
                if(winner==null||ordering.compare(candidate,winner)<0) winner=candidate;
            }
            if(winner!=null) best.add(winner);
        }
        List<Recipe> ranked=best.stream().sorted(ordering).toList();List<Recipe> diverse=new ArrayList<>();
        if(prefs.proteins().size()>1)for(String requested:prefs.proteins())ranked.stream().filter(r->r.product.customizable?r.ingredients.stream().anyMatch(i->"PROTEIN".equals(i.groupName)&&requested.equals(protein(i.name))):requested.equals(protein(r.product.name))).findFirst().ifPresent(r->{if(!diverse.contains(r)&&diverse.size()<3)diverse.add(r);});
        if(prefs.categories().size()>1)for(String requested:prefs.categories())ranked.stream().filter(r->requested.equals(r.product.category)).findFirst().ifPresent(r->{if(!diverse.contains(r)&&diverse.size()<3)diverse.add(r);});
        for(Recipe candidate:ranked){if(diverse.size()>=3)break;if(!diverse.contains(candidate))diverse.add(candidate);}
        return List.copyOf(diverse);
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
        if(p.totalBudget()!=null)tags.add("Ngân sách nhóm "+p.totalBudget()+"đ");
        if(p.servings()>1)tags.add(p.servings()+" phần cùng món");
        if("FILLING".equals(p.goal()))tags.add("Ưu tiên ăn no");if("VALUE".equals(p.goal()))tags.add("Ưu tiên tiết kiệm");if("LIGHT".equals(p.goal())&&!p.lowCalories())tags.add("Ăn nhẹ");
        p.preferredIngredients().forEach(x->tags.add("Có "+x));
        if(p.noRice()) tags.add("Không cơm"); if(p.noBeans()) tags.add("Không đậu");
        if(!"ANY".equals(p.spicy())) tags.add("YES".equals(p.spicy())?"Ăn cay":"Không cay");
        tags.addAll(p.categories()); p.avoidIngredients().forEach(x->tags.add("Không "+x));
        return tags;
    }
}
