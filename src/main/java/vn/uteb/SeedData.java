package vn.uteb;
import org.springframework.stereotype.Component;
import org.springframework.boot.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;import java.time.*;import java.util.*;
import static vn.uteb.Domain.*;
@Component
public class SeedData implements ApplicationRunner {
 static final String MENU_PLACEHOLDER="/assets/menu/photo-pending.png";
 static final Map<String,String> MENU_IMAGES=Map.ofEntries(
  Map.entry("Burrito Gà nướng","/assets/menu/burrito-grilled-chicken-full-plate.jpg"),
  Map.entry("Burrito Bò BBQ","/assets/menu/burrito-bbq-beef-full-plate.jpg"),
  Map.entry("Burrito Heo xé","/assets/menu/burrito-pulled-pork-full-plate.jpg"),
  Map.entry("Burrito Chay","/assets/menu/burrito-vegetarian-full-plate.jpg"),
  Map.entry("Burrito Gà cay","/assets/menu/burrito-spicy-chicken-full-plate.jpg"),
  Map.entry("Burrito Bò đặc biệt","/assets/menu/burrito-premium-beef-full-plate.jpg"),
  Map.entry("Bowl Gà nướng","/assets/menu/bowl-grilled-chicken-wide.jpg"),
  Map.entry("Bowl Bò BBQ","/assets/menu/bowl-bbq-beef-wide.jpg"),
  Map.entry("Bowl Heo xé","/assets/menu/bowl-pulled-pork-wide.jpg"),
  Map.entry("Bowl Chay","/assets/menu/bowl-vegetarian-wide.jpg"),
  Map.entry("Bowl Gà cay","/assets/menu/bowl-spicy-chicken-wide.jpg"),
  Map.entry("Bowl Bò đặc biệt","/assets/menu/bowl-premium-beef-wide.jpg"),
  Map.entry("Tacos Gà nướng","/assets/menu/tacos-grilled-chicken-full-plate.jpg"),
  Map.entry("Tacos Bò BBQ","/assets/menu/tacos-bbq-beef-full-plate.jpg"),
  Map.entry("Tacos Heo xé","/assets/menu/tacos-pulled-pork-full-plate.jpg"),
  Map.entry("Tacos Chay","/assets/menu/tacos-vegetarian-full-plate.jpg"),
  Map.entry("Tacos Gà cay","/assets/menu/tacos-spicy-chicken-full-plate.jpg"),
  Map.entry("Tacos Bò đặc biệt","/assets/menu/tacos-premium-beef-full-plate.jpg"),
  Map.entry("Quesadilla Gà nướng","/assets/menu/quesadilla-grilled-chicken-full-plate.jpg"),
  Map.entry("Quesadilla Bò BBQ","/assets/menu/quesadilla-bbq-beef-full-plate.jpg"),
  Map.entry("Quesadilla Heo xé","/assets/menu/quesadilla-pulled-pork-full-plate.jpg"),
  Map.entry("Quesadilla Chay","/assets/menu/quesadilla-vegetarian-full-plate.jpg"),
  Map.entry("Quesadilla Gà cay","/assets/menu/quesadilla-spicy-chicken-full-plate.jpg"),
  Map.entry("Quesadilla Bò đặc biệt","/assets/menu/quesadilla-premium-beef-full-plate.jpg"),
  Map.entry("Salad Gà nướng","/assets/menu/salad-grilled-chicken-wide.jpg"),
  Map.entry("Salad Bò BBQ","/assets/menu/salad-bbq-beef-wide.jpg"),
  Map.entry("Salad Heo xé","/assets/menu/salad-pulled-pork-wide.jpg"),
  Map.entry("Salad Chay","/assets/menu/salad-vegetarian-wide.jpg"),
  Map.entry("Salad Gà cay","/assets/menu/salad-spicy-chicken-wide.jpg"),
  Map.entry("Salad Bò đặc biệt","/assets/menu/salad-premium-beef-wide.jpg"),
  Map.entry("Kids Gà nướng","/assets/menu/kids-grilled-chicken-full-plate.jpg"),
  Map.entry("Kids Bò BBQ","/assets/menu/kids-bbq-beef-full-plate.jpg"),
  Map.entry("Kids Heo xé","/assets/menu/kids-pulled-pork-full-plate.jpg"),
  Map.entry("Kids Chay","/assets/menu/kids-vegetarian-full-plate.jpg"),
  Map.entry("Kids Gà cay","/assets/menu/kids-spicy-chicken-full-plate.jpg"),
  Map.entry("Kids Bò đặc biệt","/assets/menu/kids-premium-beef-full-plate.jpg"),
  Map.entry("Chips & Salsa","/assets/menu/chips-salsa-full-plate.jpg"),
  Map.entry("Chips & Guacamole","/assets/menu/chips-guacamole-full-plate.jpg"),
  Map.entry("Chips & Queso","/assets/menu/chips-queso-full-plate.jpg"),
  Map.entry("Khoai nướng","/assets/menu/roasted-potatoes-full-plate.jpg"),
  Map.entry("Salsa tươi","/assets/menu/fresh-salsa-wide.jpg"),
  Map.entry("Guacamole","/assets/menu/guacamole-wide.jpg"),
  Map.entry("Trà chanh","/assets/menu/lemon-iced-tea-wide.jpg"),
  Map.entry("Soda chanh","/assets/menu/lime-soda-wide.jpg"),
  Map.entry("Trà đào","/assets/menu/peach-iced-tea-wide.jpg"),
  Map.entry("Nước suối","/assets/menu/mineral-water-wide.jpg"),
  Map.entry("Cola","/assets/menu/cola-wide.jpg"),
  Map.entry("Nước cam","/assets/menu/orange-juice-wide.jpg")
 );
 static final Map<String,String> SIDE_DESCRIPTIONS=Map.of(
  "Chips & Salsa","Bánh tortilla giòn ăn kèm salsa cà chua.",
  "Chips & Guacamole","Bánh tortilla giòn ăn kèm sốt bơ guacamole.",
  "Chips & Queso","Bánh tortilla giòn ăn kèm sốt phô mai queso.",
  "Khoai nướng","Khoai tây nướng dùng như món ăn kèm.",
  "Salsa tươi","Salsa cà chua tươi dùng kèm món chính.",
  "Guacamole","Sốt bơ guacamole dùng kèm món chính."
 );
 static final Map<String,String> DRINK_DESCRIPTIONS=Map.of(
  "Trà chanh","Trà chanh dùng lạnh.",
  "Soda chanh","Soda chanh có ga dùng lạnh.",
  "Trà đào","Trà đào dùng lạnh.",
  "Nước suối","Nước suối đóng chai.",
  "Cola","Nước ngọt cola có ga.",
  "Nước cam","Nước cam dùng lạnh."
 );
 // Suggested menu for the demo: format price plus protein variant, reviewed against local menus.
 static final int[][] MENU_PRICES={
  {89000,99000,94000,84000,92000,109000}, // Burrito
  {89000,99000,94000,84000,92000,109000}, // Bowl
  {79000,89000,84000,74000,82000,99000},  // Tacos
  {99000,109000,104000,94000,102000,119000}, // Quesadilla
  {89000,99000,94000,84000,92000,109000}, // Salad
  {55000,65000,60000,50000,58000,70000}, // Kids
  {39000,49000,49000,35000,25000,39000}, // Sides
  {25000,29000,29000,15000,20000,30000}  // Drinks
 };
 // The customizable values cover only the tortilla/vegetable base; chosen ingredients add calories.
 static final int[] MENU_BASE_CALORIES={320,35,210,430,80,150};
 static final int[] SIDE_CALORIES={300,385,390,220,25,230};
 static final int[] DRINK_CALORIES={80,120,110,0,139,120};
 static int menuCalories(int category,int variant){
  return category<6?MENU_BASE_CALORIES[category]:category==6?SIDE_CALORIES[variant]:DRINK_CALORIES[variant];
 }
 static String menuDescription(String category,String name){
  if("Sides".equals(category))return SIDE_DESCRIPTIONS.get(name);
  if("Drinks".equals(category))return DRINK_DESCRIPTIONS.get(name);
  String filling=name.substring(category.length()+1).toLowerCase(Locale.ROOT);
  return switch(category){
   case "Burrito"->"Burrito cuộn bánh tortilla, gợi ý nhân "+filling+". Chọn thành phần khi đặt.";
   case "Bowl"->"Bowl phục vụ trong tô, gợi ý nhân "+filling+". Chọn thành phần khi đặt.";
   case "Tacos"->"Tacos bánh tortilla, gợi ý nhân "+filling+". Chọn thành phần khi đặt.";
   case "Quesadilla"->"Quesadilla áp chảo, gợi ý nhân "+filling+". Chọn thành phần khi đặt.";
   case "Salad"->"Salad rau, gợi ý nhân "+filling+". Chọn thành phần khi đặt.";
   case "Kids"->"Khẩu phần trẻ em, gợi ý nhân "+filling+". Chọn thành phần khi đặt.";
   default->throw new IllegalArgumentException("Danh mục món không hợp lệ: "+category);
  };
 }
 final ShopService s;final boolean enabled,demo;SeedData(ShopService s,@Value("${uteb.seed:false}")boolean enabled,@Value("${uteb.demo:false}")boolean demo){this.s=s;this.enabled=enabled;this.demo=demo;}
 @Transactional public void run(ApplicationArguments args){if(!demo||!enabled||!s.all(Store.class).isEmpty())return;
  String[] names={"UTEB Võ Văn Ngân","UTEB Quận 1","UTEB Bình Thạnh","UTEB Thủ Đức"};String[] addresses={"1 Võ Văn Ngân, phường Linh Chiểu, TP. Thủ Đức","68 Nguyễn Huệ, phường Bến Nghé, Quận 1","215 Điện Biên Phủ, phường 15, Bình Thạnh","88 Hoàng Diệu 2, phường Linh Trung, TP. Thủ Đức"};List<Store>stores=new ArrayList<>();for(int i=0;i<4;i++){Store st=new Store();st.name=names[i];st.address=addresses[i];st.phone="028 3896 123"+i;st.latitude=10.850+i*.007;st.longitude=106.771-i*.01;s.save(st);stores.add(st);}
  String[] cats={"Burrito","Bowl","Tacos","Quesadilla","Salad","Kids","Sides","Drinks"};for(String name:cats){Category c=new Category();c.name=name;c.slug=name.toLowerCase(Locale.ROOT);s.save(c);}
  String[] variants={"Gà nướng","Bò BBQ","Heo xé","Chay","Gà cay","Bò đặc biệt"};for(int cat=0;cat<8;cat++)for(int v=0;v<6;v++){Product p=new Product();p.category=cats[cat];p.name=cat<6?cats[cat]+" "+variants[v]:cat==6?new String[]{"Chips & Salsa","Chips & Guacamole","Chips & Queso","Khoai nướng","Salsa tươi","Guacamole"}[v]:new String[]{"Trà chanh","Soda chanh","Trà đào","Nước suối","Cola","Nước cam"}[v];p.description=menuDescription(p.category,p.name);p.storeId=stores.get(v%4).id;p.price=BigDecimal.valueOf(MENU_PRICES[cat][v]);p.calories=menuCalories(cat,v);p.customizable=cat<6;p.image=MENU_IMAGES.getOrDefault(p.name,MENU_PLACEHOLDER);p.available=true;s.save(p);}
  ingredient("Gà nướng","PROTEIN",0,180,"");ingredient("Bò BBQ","PROTEIN",15000,220,"");ingredient("Heo xé","PROTEIN",10000,210,"");ingredient("Đậu hũ chay","PROTEIN",0,150,"Đậu nành");ingredient("Cơm trắng","RICE",0,210,"");ingredient("Cơm gạo lứt","RICE",0,190,"");ingredient("Không cơm","RICE",0,0,"");ingredient("Đậu đen","BEANS",0,130,"");ingredient("Đậu pinto","BEANS",0,120,"");ingredient("Không đậu","BEANS",0,0,"");ingredient("Salsa cà chua","SAUCE",0,25,"");ingredient("Sốt cay","SAUCE",0,20,"");ingredient("Không sốt","SAUCE",0,0,"");ingredient("Phô mai","TOPPING",0,110,"Sữa");ingredient("Xà lách","TOPPING",0,10,"");ingredient("Rau củ áp chảo","TOPPING",0,45,"");ingredient("Kem chua","TOPPING",0,90,"Sữa");ingredient("Thêm thịt","EXTRA",25000,180,"");ingredient("Guacamole","EXTRA",15000,100,"");ingredient("Thêm phô mai","EXTRA",10000,110,"Sữa");
  voucher("UTEB20","Ưu đãi 20%","PERCENT",20,100000,50000);voucher("BURRITO50","Giảm 50.000đ","FIXED",50000,200000,50000);voucher("FREEGUAC","Tặng vị bơ","FIXED",15000,89000,15000);
  if(demo){Account customer=account("customer@uteb.vn","Khách hàng UTEB","CUSTOMER",null);customer.points=1500;Account staff=account("staff@uteb.vn","Nhân viên Võ Văn Ngân","STAFF",stores.getFirst().id);account("manager@uteb.vn","Quản lý Võ Văn Ngân","MANAGER",stores.getFirst().id);account("admin@uteb.vn","Quản trị UTEB","ADMIN",null);Account shipper=account("shipper@uteb.vn","Người giao hàng UTEB","SHIPPER",stores.getFirst().id);Account vendor=account("vendor@uteb.vn","Đối tác Võ Văn Ngân","CUSTOMER,VENDOR",stores.getFirst().id);account("vendor2@uteb.vn","Đối tác Quận 1","CUSTOMER,VENDOR",stores.get(1).id);stores.getFirst().ownerId=vendor.id;
   Address address=new Address();address.userId=customer.id;address.label="Nhà";address.recipient=customer.name;address.phone="0901234567";address.detail="25 Võ Văn Ngân, TP. Thủ Đức";address.defaultAddress=true;s.save(address);RewardLog reward=new RewardLog();reward.userId=customer.id;reward.points=1500;reward.description="Điểm mẫu để trải nghiệm Rewards";s.save(reward);
   List<Product>ps=s.all(Product.class).stream().filter(p->p.storeId.equals(stores.getFirst().id)&&p.customizable).toList();String[] statuses={"NEW","CONFIRMED","PREPARING","READY","DELIVERING","COMPLETED","CANCELLED"};for(int i=0;i<statuses.length;i++){Product p=ps.get(i%ps.size());MealOrder o=new MealOrder();o.userId=customer.id;o.storeId=stores.getFirst().id;o.code="UTEB-DEMO-"+(1001+i);o.groupCode=o.code;o.checkoutKey="fixture-order-"+i;o.type=i%2==0?"DELIVERY":"PICKUP";o.status=statuses[i];if(i==4)o.type="DELIVERY";o.paymentMethod="COD";o.paymentStatus=i==5?"PAID":i==6?"CANCELLED":"UNPAID";o.shipperId=i>=4?shipper.id:null;o.contactName=customer.name;o.phone="0901234567";o.address=address.detail;o.subtotal=p.price;o.deliveryFee=o.type.equals("DELIVERY")?new BigDecimal("15000"):BigDecimal.ZERO;o.total=o.subtotal.add(o.deliveryFee);o.commissionRate=stores.getFirst().commission;o.paymentExpires=LocalDateTime.now().plusHours(1);o.createdAt=LocalDateTime.now().minusDays(6-i);o.pointsAwarded=i==5;if(i==5)o.transactionRef="COD-DEMO-1006";s.save(o);OrderLine l=new OrderLine();l.orderId=o.id;l.productId=p.id;l.name=p.name;l.image=p.image;l.unitPrice=p.price;l.quantity=1;l.calories=655;l.customization="Gà nướng · Cơm trắng · Đậu đen · Salsa cà chua";s.save(l);s.event(o,o.status,"Đơn mẫu phục vụ trình diễn",staff.id);}
  }Setting brand=new Setting();brand.name="brand";brand.settingValue="UTEB Burrito";s.save(brand);
 }
 void ingredient(String name,String group,int price,int calories,String allergens){Ingredient i=new Ingredient();i.name=name;i.groupName=group;i.price=BigDecimal.valueOf(price);i.calories=calories;i.allergens=allergens;s.save(i);}
 void voucher(String code,String name,String type,int value,int minimum,int max){Voucher v=new Voucher();v.code=code;v.name=name;v.type=type;v.value=BigDecimal.valueOf(value);v.minimum=BigDecimal.valueOf(minimum);v.maxDiscount=BigDecimal.valueOf(max);s.save(v);}
 Account account(String email,String name,String roles,Long storeId){Account a=new Account();a.email=email;a.name=name;a.roles=roles;a.storeId=storeId;a.phone="0901234567";a.verified=true;a.passwordHash=s.hashPassword("Uteb@12345");return s.save(a);}
}
