package vn.uteb;

import java.util.List;

/** The model extracts preferences; product IDs, prices and calories come from our catalog. */
record MenuPreferences(String intent, List<String> proteins, List<String> excludedProteins,
                       boolean vegetarian, boolean lowCalories, Integer minCalories,
                       Integer maxCalories, Integer maxPrice, List<String> categories,
                       boolean noRice, boolean noBeans, String spicy,
                       List<String> avoidIngredients, boolean allergy, String clarification,
                       String topic, String goal, List<String> preferredIngredients,
                       List<String> productKeywords, int servings, Integer totalBudget) {
    MenuPreferences {
        proteins = proteins == null ? List.of() : List.copyOf(proteins);
        excludedProteins = excludedProteins == null ? List.of() : List.copyOf(excludedProteins);
        categories = categories == null ? List.of() : List.copyOf(categories);
        avoidIngredients = avoidIngredients == null ? List.of() : List.copyOf(avoidIngredients);
        preferredIngredients = preferredIngredients == null ? List.of() : List.copyOf(preferredIngredients);
        productKeywords = productKeywords == null ? List.of() : List.copyOf(productKeywords);
        topic=topic==null?"RECOMMEND":topic;
        goal=goal==null?"BALANCED":goal;
    }
    // Existing callers and tests retain the same meaning: one meal, no assumed diet.
    MenuPreferences(String intent,List<String> proteins,List<String> excludedProteins,boolean vegetarian,
                    boolean lowCalories,Integer minCalories,Integer maxCalories,Integer maxPrice,
                    List<String> categories,boolean noRice,boolean noBeans,String spicy,
                    List<String> avoidIngredients,boolean allergy,String clarification){
        this(intent,proteins,excludedProteins,vegetarian,lowCalories,minCalories,maxCalories,maxPrice,categories,
            noRice,noBeans,spicy,avoidIngredients,allergy,clarification,"RECOMMEND","BALANCED",List.of(),List.of(),1,null);
    }
}
