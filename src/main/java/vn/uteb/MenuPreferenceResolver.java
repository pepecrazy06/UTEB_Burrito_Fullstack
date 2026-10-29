package vn.uteb;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Check model extraction against the user's words: currency is never a calorie constraint. */
final class MenuPreferenceResolver {
    private MenuPreferenceResolver(){}
    private static final Pattern MONEY=Pattern.compile("(?<![a-z0-9])([0-9]+(?:[.,][0-9]+)*)\\s*(nghin|ngan|k|dong|vnd|d)(?![a-z])");
    private static final Pattern CALORIES=Pattern.compile("([0-9]+)\\s*(?:kcal|calo|calories)\\b");
    private static final Pattern PEOPLE=Pattern.compile("\\b([0-9]{1,2}|mot|hai|ba|bon|nam|sau|bay|tam|chin|muoi)\\s*(nguoi|suat|phan)\\b");
    private static String plain(String value){return AiMenuService.plain(value);}
    static MenuPreferences resolve(MenuPreferences p,String message,List<String> previous,List<AiConversationTurn> turns,List<String> known){
        List<String> requests=new ArrayList<>(turns.isEmpty()?previous:turns.stream().map(AiConversationTurn::user).toList());requests.add(message);
        String source=plain(String.join("\n",requests));Integer min=null,max=null,price=null,total=null;int servings=1;
        List<String> explicitCategories=new ArrayList<>(),explicitProteins=new ArrayList<>();Set<String> excluded=new LinkedHashSet<>();boolean noRice=false,noBeans=false,vegetarian=false;String spicy="ANY";
        for(String request:requests){
            String text=plain(request);Matcher people=PEOPLE.matcher(text);
            List<String> mentionedCategories=List.of("Burrito","Bowl","Tacos","Quesadilla","Salad","Kids","Sides","Drinks").stream().filter(c->Pattern.compile("\\b"+plain(c)+"\\b").matcher(text).find()).toList();
            if(!mentionedCategories.isEmpty())explicitCategories=new ArrayList<>(mentionedCategories);
            List<String> wantedProteins=new ArrayList<>();
            for(String filling:List.of("CHICKEN","BEEF","PORK","TOFU")){
                String words=switch(filling){case "CHICKEN"->"gà|ga|chicken";case "BEEF"->"bò|bo|beef";case "PORK"->"heo|lợn|lon|pork";default->"đậu hũ|dau hu|tofu";};
                if(!Pattern.compile("(?iuU)\\b(?:"+words+")\\b").matcher(request).find())continue;
                String normalized=switch(filling){case "CHICKEN"->"ga|chicken";case "BEEF"->"bo|beef";case "PORK"->"heo|lon|pork";default->"dau hu|tofu";};
                boolean negative=Pattern.compile("\\b(?:khong|tranh|bo)\\s+(?:(?:an|dung|thich|nhan|thit)\\s+){0,3}(?:"+normalized+")\\b").matcher(text).find();
                if(negative)excluded.add(filling);else{wantedProteins.add(filling);excluded.remove(filling);}
            }
            if(!wantedProteins.isEmpty())explicitProteins=wantedProteins;
            if(text.contains("an chay")||text.contains("mon chay")||text.contains("vegan")||text.contains("vegetarian")||text.matches("(?s).*\\bkhong (an|dung) thit\\s*(?:[.,!?]|nua|nhe|$).*"))vegetarian=!text.contains("khong an chay");
            if(text.matches("(?s).*(khong (an )?cay|it cay).*"))spicy="NO";
            else if(text.matches("(?s).*(thich cay|an cay|muon cay|sot cay|ga cay).*"))spicy="YES";
            if(text.matches("(?s).*(khong (an )?com|bo com).*"))noRice=true;else if(text.matches("(?s).*(them com|giu com|co com).*"))noRice=false;
            if(text.matches("(?s).*(khong (an )?dau(?! hu)|bo dau(?! hu)).*"))noBeans=true;else if(text.matches("(?s).*(them dau|giu dau|co dau).*"))noBeans=false;
            while(people.find()){
                String before=text.substring(Math.max(0,people.start()-16),people.start());
                if(!people.group(2).equals("nguoi")&&Set.of("mot","1").contains(people.group(1))&&(before.matches(".*(moi|mot|tren)\\s*$")||text.contains("mot phan")))continue;
                String value=people.group(1);servings=switch(value){case "mot"->1;case "hai"->2;case "ba"->3;case "bon"->4;case "nam"->5;case "sau"->6;case "bay"->7;case "tam"->8;case "chin"->9;case "muoi"->10;default->Integer.parseInt(value);};
            }
            if(text.matches("(?s).*(bo gioi han calo|khong gioi han calo|khong can an kieng|khong can kieng).*")){min=null;max=null;}
            Matcher calorie=CALORIES.matcher(text);
            while(calorie.find()){
                int value=Integer.parseInt(calorie.group(1));String before=text.substring(Math.max(0,calorie.start()-25),calorie.start());
                if(before.matches("(?s).*(toi thieu|it nhat|tu|tren)\\s*$"))min=value+(before.matches("(?s).*tren\\s*$")?1:0);
                else max=value-(before.matches("(?s).*duoi\\s*$")?1:0);
            }
            if(text.matches("(?s).*(bo gioi han gia|khong gioi han ngan sach|khong can tiet kiem).*")){price=null;total=null;}
            Matcher money=MONEY.matcher(text);
            while(money.find()){
                String number=money.group(1),unit=money.group(2);boolean thousands=Set.of("k","nghin","ngan").contains(unit);
                if(thousands)number=number.replace(',','.');else number=number.replace(".","").replace(",","");
                int amount=new BigDecimal(number).multiply(thousands?BigDecimal.valueOf(1000):BigDecimal.ONE).intValueExact();
                boolean each=text.matches("(?s).*(moi nguoi|mot nguoi|moi phan|mot phan|/nguoi|/phan).*" );
                if(servings>1&&!each){total=amount;price=null;}else{price=amount;total=null;}
            }
        }
        Map<String,String> ingredients=new LinkedHashMap<>();known.forEach(name->ingredients.put(plain(name),name));
        String original=String.join("\n",requests).toLowerCase(Locale.ROOT);
        List<String> keywords=new ArrayList<>(p.productKeywords().stream().map(MenuPreferenceResolver::ingredientKey).filter(k->mentioned(k,source,original)&&!Set.of("ga","bo","heo","dau hu","burrito","bowl","tacos","quesadilla","salad","kids","sides","drinks").contains(k)).toList());
        if(source.matches("(?s).*(burrito trung|nhan trung|mon trung|trung pho mai).*")&&keywords.stream().noneMatch(k->plain(k).contains("trung")))keywords.add("trứng");
        List<String> likes=new ArrayList<>(),avoid=new ArrayList<>(),unknown=new ArrayList<>();
        for(String name:p.preferredIngredients()){
            String key=ingredientKey(name);if(Set.of("ga","bo","heo","dau hu","ga nuong","bo bbq","heo xe","dau hu chay").contains(key))continue;
            String exact=ingredients.get(key);
            boolean recipeTerm=keywords.stream().anyMatch(k->plain(k).contains(key)||key.contains(plain(k)));
            if(exact!=null&&mentioned(key,source,original))likes.add(exact);else if(exact==null&&source.contains(key)&&!recipeTerm)unknown.add(name);
        }
        for(String name:p.avoidIngredients()){
            String key=ingredientKey(name);String exact=ingredients.get(key);
            if(exact!=null&&mentioned(key,source,original))avoid.add(exact);else if(exact==null&&source.contains(key))unknown.add(name);
        }
        boolean reference=plain(message).matches("(?s).*(mon (thu|do|nay)|lua chon (thu|do)|cai (thu|do)).*");
        List<String> categories=reference&&!p.categories().isEmpty()?p.categories():explicitCategories;
        if(reference&&explicitProteins.isEmpty())explicitProteins=new ArrayList<>(p.proteins());
        explicitProteins.removeIf(excluded::contains);
        String intent=p.intent(),clarification=p.clarification(),topic=p.topic();
        if("STORE".equals(topic)&&!keywords.isEmpty())topic="INGREDIENTS";
        if(!"RECOMMEND".equals(topic)&&"OTHER".equals(intent))intent="FOOD";
        if(!unknown.isEmpty()){intent="CLARIFY";clarification="Thực đơn chưa xác nhận được nguyên liệu "+String.join(", ",unknown)+". Bạn muốn chọn nguyên liệu khác hay hỏi trực tiếp cửa hàng?";}
        boolean light=source.contains("it calo")||source.contains("giam calo");
        if(plain(message).matches("(?s).*(khong can an kieng|khong can kieng|khong can it calo).*"))light=false;
        return new MenuPreferences(intent,explicitProteins,List.copyOf(excluded),vegetarian,light,min,max,price,categories,
            noRice,noBeans,spicy,avoid.stream().distinct().toList(),AiPublicKnowledge.hasAllergy(requests),clarification,topic,p.goal(),likes.stream().distinct().toList(),keywords.stream().distinct().toList(),servings,total);
    }
    private static boolean mentioned(String key,String source,String original){
        return switch(key){case "guacamole"->source.contains("guacamole")||source.contains("avocado")||original.contains("bơ");case "pho mai"->source.contains("pho mai")||source.contains("cheese");case "kem chua"->source.contains("kem chua")||source.contains("sour cream");default->source.contains(key);};
    }
    private static String ingredientKey(String name){
        String key=plain(name);
        if(name.toLowerCase(Locale.ROOT).contains("bơ")||Set.of("sot bo","avocado","bo guacamole").contains(key))return "guacamole";
        if(key.equals("cheese"))return "pho mai";
        if(key.equals("sour cream"))return "kem chua";
        return key;
    }
}
