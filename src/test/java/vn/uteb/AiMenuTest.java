package vn.uteb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static vn.uteb.Domain.*;

class AiMenuTest {
    ObjectMapper json=new ObjectMapper();
    List<Ingredient> ingredients;
    @BeforeEach void catalog() {
        ingredients=new ArrayList<>();
        add("Gà nướng","PROTEIN",0,180); add("Bò BBQ","PROTEIN",15000,220);
        add("Heo xé","PROTEIN",10000,210); add("Đậu hũ chay","PROTEIN",0,150);
        add("Cơm trắng","RICE",0,210); add("Cơm gạo lứt","RICE",0,190); add("Không cơm","RICE",0,0);
        add("Đậu đen","BEANS",0,130); add("Đậu pinto","BEANS",0,120); add("Không đậu","BEANS",0,0);
        add("Salsa cà chua","SAUCE",0,25); add("Sốt cay","SAUCE",0,20); add("Không sốt","SAUCE",0,0);
    }
    void add(String name,String group,int price,int calories) {
        Ingredient i=new Ingredient();i.id=(long)ingredients.size()+1;i.name=name;i.groupName=group;i.price=BigDecimal.valueOf(price);i.calories=calories;i.vegetarian=!group.equals("PROTEIN")||name.equals("Đậu hũ chay");ingredients.add(i);
    }
    static Product product(long id,String name,String category,int price,int calories) {
        Product p=new Product();p.id=id;p.storeId=1L;p.name=name;p.category=category;p.price=BigDecimal.valueOf(price);p.calories=calories;
        p.requiredGroups=switch(category){case "Tacos"->"PROTEIN,SAUCE";case "Salad"->"PROTEIN,BEANS,SAUCE";default->"PROTEIN,RICE,BEANS,SAUCE";};p.vegetarian=name.endsWith(" Chay");if(name.endsWith(" Bò BBQ"))p.includedProteinId=2L;return p;
    }
    MenuPreferences preferences(String protein,Integer maximum,boolean light) {
        return new MenuPreferences("FOOD",protein.isEmpty()?List.of():List.of(protein),List.of(),false,light,null,maximum,null,List.of(),false,false,"ANY",List.of(),false,"");
    }
    @Test void caloriesIncludeTheWholeSelectedMealAndReduceRiceToRespectLimit() {
        Product bowl=product(1,"Bowl Gà nướng","Bowl",89000,35);
        var normal=AiMenuService.choose(List.of(bowl),ingredients,1,preferences("CHICKEN",null,false)).getFirst();
        assertThat(normal.calories()).isEqualTo(580);
        var light=AiMenuService.choose(List.of(bowl),ingredients,1,preferences("CHICKEN",399,false)).getFirst();
        assertThat(light.calories()).isLessThanOrEqualTo(399).isGreaterThan(35);
        assertThat(light.ingredients()).extracting(i->i.name).contains("Không cơm");
    }
    @Test void namedBeefPriceIncludesProteinExactlyOnce() {
        Product beef=product(1,"Burrito Bò BBQ","Burrito",99000,320);
        var result=AiMenuService.choose(List.of(beef),ingredients,1,preferences("BEEF",null,false));
        assertThat(result).hasSize(1);assertThat(result.getFirst().price()).isEqualByComparingTo("99000");
        assertThat(result.getFirst().ingredients()).extracting(i->i.name).contains("Bò BBQ");
    }
    @Test void tacoRecipeDoesNotRequireRiceAndBeans() {
        var result=AiMenuService.choose(List.of(product(1,"Tacos Gà nướng","Tacos",79000,210)),ingredients,1,preferences("CHICKEN",500,false)).getFirst();
        assertThat(result.ingredients()).extracting(i->i.groupName).containsExactly("PROTEIN","SAUCE");
        assertThat(result.calories()).isEqualTo(415);
    }
    @Test void vegetarianAndExclusionsDoNotFallBackToMeat() {
        var prefs=new MenuPreferences("FOOD",List.of(),List.of("CHICKEN","BEEF","PORK"),true,true,null,400,null,List.of(),false,false,"ANY",List.of(),false,"");
        var result=AiMenuService.choose(List.of(product(1,"Salad Chay","Salad",84000,80),product(2,"Salad Gà nướng","Salad",89000,80)),ingredients,1,prefs);
        assertThat(result).hasSize(1);assertThat(result.getFirst().product().name).isEqualTo("Salad Chay");
        assertThat(result.getFirst().ingredients()).filteredOn(i->i.groupName.equals("PROTEIN")).extracting(i->i.name).containsExactly("Đậu hũ chay");
    }
    @Test void noMatchNeverRelaxesBudgetOrCalories() {
        assertThat(AiMenuService.choose(List.of(product(1,"Bowl Gà nướng","Bowl",89000,35)),ingredients,1,preferences("CHICKEN",100,false))).isEmpty();
        var budget=new MenuPreferences("FOOD",List.of(),List.of(),false,false,null,null,10000,List.of(),false,false,"ANY",List.of(),false,"");
        assertThat(AiMenuService.choose(List.of(product(1,"Bowl Gà nướng","Bowl",89000,35)),ingredients,1,budget)).isEmpty();
    }
    @Test void onlyCurrentStoreAvailableAndInStockItemsAreRecommended() {
        Product other=product(1,"Bowl Gà nướng","Bowl",89000,35);other.storeId=2L;
        Product sold=product(2,"Bowl Gà nướng","Bowl",89000,35);sold.stock=0;
        Product hidden=product(3,"Bowl Gà nướng","Bowl",89000,35);hidden.available=false;
        assertThat(AiMenuService.choose(List.of(other,sold,hidden),ingredients,1,preferences("CHICKEN",null,false))).isEmpty();
    }
    @Test void unavailableRequiredProteinDoesNotCreateInvalidRecipes() {
        ingredients.stream().filter(i->i.name.equals("Gà nướng")).forEach(i->i.available=false);
        assertThat(AiMenuService.choose(List.of(product(1,"Bowl Gà nướng","Bowl",89000,35)),ingredients,1,preferences("CHICKEN",null,false))).isEmpty();
    }
    @Test void noRiceNoBeansArePreservedAndNonSpicyExcludesSpicyNamedDishes() {
        var prefs=new MenuPreferences("FOOD",List.of("CHICKEN"),List.of(),false,false,null,null,null,List.of(),true,true,"NO",List.of(),false,"");
        var result=AiMenuService.choose(List.of(product(1,"Bowl Gà nướng","Bowl",89000,35),product(2,"Burrito Gà cay","Burrito",92000,320)),ingredients,1,prefs);
        assertThat(result).hasSize(1);assertThat(result.getFirst().ingredients()).extracting(i->i.name).contains("Không cơm","Không đậu").doesNotContain("Sốt cay");
    }
    @Test void fixedMealDoesNotPretendItCanRemoveRice() {
        Product fixed=product(1,"Burrito Gà guacamole","Burrito",109000,850);fixed.customizable=false;
        var prefs=new MenuPreferences("FOOD",List.of("CHICKEN"),List.of(),false,false,null,null,null,List.of(),true,false,"ANY",List.of(),false,"");
        assertThat(AiMenuService.choose(List.of(fixed),ingredients,1,prefs)).isEmpty();
    }
    @Test void localClientUsesSchemaAndNoAuthorizationOrExternalProvider() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicReference<JsonNode> sent=new AtomicReference<>();
        server.createContext("/api/chat",exchange->{
            try {
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isNull();
                sent.set(json.readTree(exchange.getRequestBody()));
                byte[] reply=json.writeValueAsBytes(Map.of("done",true,"done_reason","stop","message",Map.of("content",json.writeValueAsString(preferences("BEEF",600,false)))));
                exchange.sendResponseHeaders(200,reply.length);exchange.getResponseBody().write(reply);
            }finally{exchange.close();}
        });
        server.start();
        try {
            var client=new MenuAiClient(json,true,"qwen3:4b-instruct",HttpClient.newHttpClient(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/chat"));
            var result=client.understand("Thèm bò, dưới 601 kcal",List.of("Không cay"),ingredients.stream().map(i->i.name).toList());
            assertThat(result.proteins()).containsExactly("BEEF");assertThat(result.maxCalories()).isEqualTo(600);
            assertThat(sent.get().path("stream").asBoolean()).isFalse();assertThat(sent.get().path("think").asBoolean()).isFalse();
            assertThat(sent.get().path("format").path("additionalProperties").asBoolean(true)).isFalse();
            assertThat(sent.get().path("messages").get(1).path("content").asText()).isEqualTo("Không cay");
        } finally {server.stop(0);}
    }
    @Test void incompleteGenerationNeverBecomesRecommendations() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/chat",exchange->{byte[] bytes="{\"done\":true,\"done_reason\":\"length\",\"message\":{\"content\":\"{}\"}}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        try {
            var client=new MenuAiClient(json,true,"qwen3:4b-instruct",HttpClient.newHttpClient(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/chat"));
            assertThatThrownBy(()->client.understand("Gà",List.of(),List.of())).isInstanceOf(ResponseStatusException.class);
        } finally {server.stop(0);}
    }
    @Test void endpointIsRestrictedToLocalhostAndCloudModelsAreDisabled() {
        assertThatThrownBy(()->MenuAiClient.localEndpoint("https://api.openai.com")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->MenuAiClient.localEndpoint("http://example.com:11434")).isInstanceOf(IllegalArgumentException.class);
        assertThat(MenuAiClient.localEndpoint("http://127.0.0.1:11434").getPath()).isEqualTo("/api/chat");
        assertThat(new MenuAiClient(json,true,"model:cloud",HttpClient.newHttpClient(),URI.create("http://127.0.0.1/api/chat")).configured()).isFalse();
    }
    MenuPreferences advisor(String goal,List<String> likes,List<String> keywords,int servings,Integer budget,List<String> proteins,List<String> categories){
        return new MenuPreferences("FOOD",proteins,List.of(),false,false,null,null,null,categories,false,false,"ANY",List.of(),false,"","RECOMMEND",goal,likes,keywords,servings,budget);
    }
    @Test void hungryRequestKeepsRiceBeansAndRanksAFullMealInsteadOfTheLowestCalories(){
        var result=AiMenuService.choose(List.of(product(1,"Burrito Gà nướng","Burrito",89000,320),product(2,"Tacos Gà nướng","Tacos",79000,210)),ingredients,1,advisor("FILLING",List.of(),List.of(),1,null,List.of("CHICKEN"),List.of()));
        assertThat(result.getFirst().product().category).isEqualTo("Burrito");
        assertThat(result.getFirst().ingredients()).extracting(i->i.name).contains("Cơm trắng","Đậu đen").doesNotContain("Không cơm","Không đậu");
    }
    @Test void requestedToppingsAreActuallyIncludedAndTheirCostCountsTowardBudget(){
        add("Phô mai","TOPPING",10000,110);add("Guacamole","EXTRA",15000,100);
        Product bowl=product(1,"Bowl Gà nướng","Bowl",89000,35);
        var prefs=advisor("BALANCED",List.of("Phô mai","Guacamole"),List.of(),1,114000,List.of("CHICKEN"),List.of());
        var result=AiMenuService.choose(List.of(bowl),ingredients,1,prefs).getFirst();
        assertThat(result.ingredients()).extracting(i->i.name).contains("Phô mai","Guacamole");assertThat(result.price()).isEqualByComparingTo("114000");
        assertThat(AiMenuService.choose(List.of(bowl),ingredients,1,advisor("VALUE",List.of("Phô mai","Guacamole"),List.of(),1,100000,List.of("CHICKEN"),List.of()))).isEmpty();
    }
    @Test void groupBudgetAndStockApplyToAllRequestedPortions(){
        Product bowl=product(1,"Bowl Gà nướng","Bowl",89000,35);
        assertThat(AiMenuService.choose(List.of(bowl),ingredients,1,advisor("BALANCED",List.of(),List.of(),2,180000,List.of(),List.of()))).hasSize(1);
        assertThat(AiMenuService.choose(List.of(bowl),ingredients,1,advisor("BALANCED",List.of(),List.of(),3,200000,List.of(),List.of()))).isEmpty();
        bowl.stock=1;assertThat(AiMenuService.choose(List.of(bowl),ingredients,1,advisor("BALANCED",List.of(),List.of(),2,200000,List.of(),List.of()))).isEmpty();
    }
    @Test void comparisonIncludesBothRequestedCategoriesAndFillings(){
        var result=AiMenuService.choose(List.of(product(1,"Burrito Gà nướng","Burrito",89000,320),product(2,"Tacos Gà nướng","Tacos",79000,210),product(3,"Bowl Bò BBQ","Bowl",99000,35)),ingredients,1,advisor("BALANCED",List.of(),List.of(),1,null,List.of("CHICKEN","BEEF"),List.of("Burrito","Bowl")));
        assertThat(result).extracting(r->r.product().category).contains("Burrito","Bowl");
        assertThat(result.stream().flatMap(r->r.ingredients().stream()).filter(i->"PROTEIN".equals(i.groupName)).map(i->AiMenuService.protein(i.name))).contains("CHICKEN","BEEF");
    }
    @Test void fixedEggCheeseRecipeCanBeFoundWithoutPretendingItHasAChickenProtein(){
        Product egg=product(1,"Burrito Trứng phô mai","Burrito",89000,650);egg.customizable=false;
        var result=AiMenuService.choose(List.of(egg,product(2,"Bowl Gà nướng","Bowl",89000,35)),ingredients,1,advisor("BALANCED",List.of(),List.of("trứng phô mai"),1,null,List.of(),List.of()));
        assertThat(result).hasSize(1);assertThat(result.getFirst().product().id).isEqualTo(egg.id);
    }
    @Test void askingForEggAndCheeseDoesNotReturnAnyChickenDishJustBecauseItCanHaveCheese(){
        add("Phô mai","TOPPING",10000,110);Product egg=product(1,"Burrito Trứng phô mai","Burrito",89000,650);egg.customizable=false;
        var result=AiMenuService.choose(List.of(egg,product(2,"Burrito Gà nướng","Burrito",89000,320)),ingredients,1,advisor("BALANCED",List.of("Phô mai"),List.of("trứng","phô mai"),1,null,List.of(),List.of("Burrito")));
        assertThat(result).hasSize(1);assertThat(result.getFirst().product().id).isEqualTo(egg.id);
    }
    @Test void naturalResponseUsesOnlyPublicCatalogFactsAndPreservesBothSidesOfConversation()throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicReference<JsonNode> sent=new AtomicReference<>();
        server.createContext("/api/chat",exchange->{sent.set(json.readTree(exchange.getRequestBody()));byte[] bytes=json.writeValueAsBytes(Map.of("done",true,"done_reason","stop","message",Map.of("content","{\"answer\":\"Bạn thích vị cay, nên có thể thử cấu hình gà với sốt cay bên dưới.\"}")));exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        try{var client=new MenuAiClient(json,true,"qwen3:4b-instruct",HttpClient.newHttpClient(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/chat"));
            String answer=client.respond("Món thứ hai có cay không?",List.of(new AiConversationTurn("Tôi thích gà","Hai món cho bạn",List.of("Burrito Gà nướng","Tacos Gà cay"))),Map.of("exactSuggestions",List.of(Map.of("name","Tacos Gà cay","price",79000))));
            assertThat(answer).contains("vị cay");assertThat(sent.get().path("messages").get(2).path("role").asText()).isEqualTo("assistant");assertThat(sent.get().path("messages").get(2).path("content").asText()).contains("Tacos Gà cay");assertThat(sent.get().path("messages").get(3).path("content").asText()).contains("79000");assertThat(sent.get().path("format").path("additionalProperties").asBoolean(true)).isFalse();
        }finally{server.stop(0);}
    }
}
