package vn.uteb;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MenuPreferenceResolverTest {
 final List<String> ingredients=List.of("Phô mai","Gà nướng","Sốt cay","Guacamole");
 MenuPreferences model(String topic,List<String> likes,Integer calories,Integer price,int servings,Integer total){return new MenuPreferences("OTHER",List.of("CHICKEN"),List.of(),false,false,null,calories,price,List.of(),false,false,"NO",List.of(),false,"",topic,"BALANCED",likes,List.of("gà"),servings,total);}
 @Test void vndPriceNeverBecomesCaloriesAndProteinAliasDoesNotBecomeAnUnknownTopping(){
  var p=MenuPreferenceResolver.resolve(model("RECOMMEND",List.of("Phô mai","Gà"),120000,120000,1,null),"Thèm gà thêm phô mai, không cay, tối đa 120.000đ một phần",List.of(),List.of(),ingredients);
  assertThat(p.maxCalories()).isNull();assertThat(p.maxPrice()).isEqualTo(120000);assertThat(p.preferredIngredients()).containsExactly("Phô mai");assertThat(p.productKeywords()).isEmpty();
 }
 @Test void twoPersonBudgetIsTotalRatherThanPerServing(){
  var p=MenuPreferenceResolver.resolve(model("RECOMMEND",List.of("Gà"),null,200000,2,200000),"Hai người ăn trong tổng ngân sách 200.000đ",List.of(),List.of(),ingredients);
  assertThat(p.servings()).isEqualTo(2);assertThat(p.totalBudget()).isEqualTo(200000);assertThat(p.maxPrice()).isNull();
 }
 @Test void comparisonAndStoreTopicsAreNotRejectedAsUnrelated(){for(String topic:List.of("COMPARE","STORE","ORDERING","GREETING"))assertThat(MenuPreferenceResolver.resolve(model(topic,List.of(),null,null,1,null),"Cho mình hỏi về UTEB",List.of(),List.of(),ingredients).intent()).isEqualTo("FOOD");}
 @Test void explicitCalorieLimitAndPreviousBudgetSurviveAChangeOfFilling(){
  var p=MenuPreferenceResolver.resolve(model("RECOMMEND",List.of(),999999,null,1,null),"Đổi sang bò, vẫn không cay",List.of("Gà dưới 600 kcal, tối đa 100k"),List.of(),ingredients);
  assertThat(p.maxCalories()).isEqualTo(599);assertThat(p.maxPrice()).isEqualTo(100000);
 }
 @Test void groupSizeAndBudgetPersistThroughFollowupWithoutDuplicatingAssistantConstraints(){
  var history=List.of(new AiConversationTurn("Hai người ăn trong 200k","Gợi ý ba món dưới 500 kcal",List.of()));
  var p=MenuPreferenceResolver.resolve(model("RECOMMEND",List.of(),500,null,1,null),"Đổi sang bò",List.of(),history,ingredients);
  assertThat(p.servings()).isEqualTo(2);assertThat(p.totalBudget()).isEqualTo(200000);assertThat(p.maxCalories()).isNull();
 }
 @Test void unsupportedExplicitIngredientAsksForClarificationInsteadOfDroppingTheRequest(){
  var p=MenuPreferenceResolver.resolve(model("RECOMMEND",List.of("Hành tím"),null,null,1,null),"Thêm hành tím nhé",List.of(),List.of(),ingredients);
  assertThat(p.intent()).isEqualTo("CLARIFY");assertThat(p.clarification()).contains("Hành tím");
 }
 @Test void avocadoButterAndCheeseAliasesMapToActualIngredients(){
  var p=MenuPreferenceResolver.resolve(model("RECOMMEND",List.of("Bơ","cheese"),null,null,1,null),"Thêm bơ và cheese",List.of(),List.of(),ingredients);
  assertThat(p.preferredIngredients()).containsExactly("Guacamole","Phô mai");
 }
 @Test void eggInTheNameOfAFixedRecipeDoesNotBecomeAnUnknownOptionalTopping(){
  var raw=new MenuPreferences("FOOD",List.of(),List.of(),false,false,null,null,null,List.of("Burrito"),false,false,"ANY",List.of(),false,"","INGREDIENTS","BALANCED",List.of("Trứng","Phô mai"),List.of("trứng phô mai"),1,null);
  var p=MenuPreferenceResolver.resolve(raw,"Có burrito trứng phô mai không?",List.of(),List.of(),ingredients);
  assertThat(p.intent()).isEqualTo("FOOD");assertThat(p.preferredIngredients()).containsExactly("Phô mai");assertThat(p.productKeywords()).containsExactly("trung pho mai");
 }
 @Test void explicitComparisonCategoriesWinEvenIfTheModelOmitsThem(){
  var p=MenuPreferenceResolver.resolve(model("COMPARE",List.of(),null,null,1,null),"Burrito và Bowl khác nhau thế nào?",List.of(),List.of(),ingredients);
  assertThat(p.categories()).containsExactly("Burrito","Bowl");assertThat(p.proteins()).isEmpty();assertThat(p.spicy()).isEqualTo("ANY");
 }
 @Test void eggQuestionDoesNotInheritInventedChickenOrSpicyPreferences(){
  var p=MenuPreferenceResolver.resolve(model("INGREDIENTS",List.of("Trứng","Phô mai"),null,null,1,null),"Có burrito trứng phô mai không?",List.of(),List.of(),ingredients);
  assertThat(p.intent()).isEqualTo("FOOD");assertThat(p.proteins()).isEmpty();assertThat(p.spicy()).isEqualTo("ANY");assertThat(p.productKeywords()).contains("trứng");
 }
 @Test void followupChangesFillingButKeepsNonSpicyPreferenceAndAvocadoIsNotBeef(){
  var changed=MenuPreferenceResolver.resolve(model("RECOMMEND",List.of(),null,null,1,null),"Đổi sang bò",List.of("Thèm gà, không cay"),List.of(),ingredients);
  assertThat(changed.proteins()).containsExactly("BEEF");assertThat(changed.spicy()).isEqualTo("NO");
  var avocado=MenuPreferenceResolver.resolve(model("RECOMMEND",List.of("Bơ"),null,null,1,null),"Thêm bơ",List.of(),List.of(),ingredients);
  assertThat(avocado.proteins()).isEmpty();
 }
 @Test void modelCannotAddACheesePreferenceThatTheUserNeverMentioned(){
  var p=MenuPreferenceResolver.resolve(model("RECOMMEND",List.of("Phô mai"),null,null,2,200000),"Hai người ăn trong 200k, thích gà",List.of(),List.of(),ingredients);
  assertThat(p.preferredIngredients()).isEmpty();
 }
 @Test void askingWhetherANamedRecipeExistsIsAMenuQuestionRatherThanAStoreOnlyQuestion(){
  var p=MenuPreferenceResolver.resolve(model("STORE",List.of("Trứng","Phô mai"),null,null,1,null),"Có burrito trứng phô mai không?",List.of(),List.of(),ingredients);
  assertThat(p.topic()).isEqualTo("INGREDIENTS");assertThat(p.productKeywords()).contains("trứng");
 }
}
