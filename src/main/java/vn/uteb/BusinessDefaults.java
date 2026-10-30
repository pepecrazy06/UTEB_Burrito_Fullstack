package vn.uteb;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import static vn.uteb.Domain.*;

/** SQL Server uses V12; isolated demo/test databases receive equivalent reference data. */
@Component @Order(-100)
class BusinessDefaults implements ApplicationRunner {
 @PersistenceContext EntityManager em;final boolean demo;
 BusinessDefaults(@Value("${uteb.demo:false}")boolean demo){this.demo=demo;}
 @Transactional public void run(ApplicationArguments args){if(!demo)return;
  for(var d:BusinessSettings.DEFINITIONS)if(em.createQuery("select count(s) from Setting s where s.name=:n",Long.class).setParameter("n",d.name()).getSingleResult()==0){Setting s=new Setting();s.name=d.name();s.settingValue=d.initial();em.persist(s);}
  if(em.createQuery("select count(r) from RewardOffer r",Long.class).getSingleResult()==0){reward("DRINK","Thức uống",500,"25000");reward("CHIPS","Chips & salsa",800,"35000");reward("BURRITO","Một chiếc burrito",1200,"89000");}
  if(em.createQuery("select count(c) from CateringPackage c",Long.class).getSingleResult()==0){pack("BOX","Burrito Box","Burrito cuộn sẵn, dễ chia phần.","99000");pack("PARTY","Build Your Own Party","Cùng phối nhân, cơm và topping tại bàn tiệc.","99000");pack("CHIPS","Chips & Salsa","Món ăn kèm để chia sẻ cùng cả nhóm.","45000");}
 }
 void reward(String code,String name,int points,String amount){RewardOffer r=new RewardOffer();r.code=code;r.name=name;r.points=points;r.amount=new BigDecimal(amount);em.persist(r);}
 void pack(String code,String name,String description,String price){CateringPackage p=new CateringPackage();p.code=code;p.name=name;p.description=description;p.pricePerPerson=new BigDecimal(price);em.persist(p);}
 // Import legacy names once; operational pricing never depends on display names.
 static void recipe(Product p,List<Ingredient> ingredients){
  p.requiredGroups=switch(p.category){case "Tacos","Quesadilla"->"PROTEIN,SAUCE";case "Salad"->"PROTEIN,BEANS,SAUCE";case "Kids"->"PROTEIN,RICE,SAUCE";default->"PROTEIN,RICE,BEANS,SAUCE";};
  p.optionalGroups=switch(p.category){case "Tacos","Quesadilla"->"RICE,BEANS,TOPPING,EXTRA";case "Salad"->"RICE,TOPPING,EXTRA";case "Kids"->"BEANS,TOPPING,EXTRA";default->"TOPPING,EXTRA";};
  p.ingredientCalorieFactor="Kids".equals(p.category)?new BigDecimal("0.5"):BigDecimal.ONE;p.vegetarian=p.name.endsWith(" Chay");
  String protein=p.name.endsWith(" Bò BBQ")||p.name.endsWith(" Bò đặc biệt")?"Bò BBQ":p.name.endsWith(" Heo xé")?"Heo xé":p.vegetarian?"Đậu hũ chay":"Gà nướng";
  List<String> suggestions=new ArrayList<>();for(String group:p.requiredGroups.split(","))ingredients.stream().sorted(Comparator.comparing(i->i.id)).filter(i->i.groupName.equals(group)&&(group.equals("PROTEIN")?i.name.equals(protein):group.equals("SAUCE")&&p.name.endsWith(" Gà cay")?i.name.equals("Sốt cay"):true)).findFirst().ifPresent(i->{suggestions.add(i.id.toString());if(group.equals("PROTEIN")&&(protein.equals("Bò BBQ")||protein.equals("Heo xé")))p.includedProteinId=i.id;});p.suggestedIngredientIds=String.join(",",suggestions);
 }
}
