package vn.uteb;

import java.util.List;

/** The model extracts preferences; product IDs, prices and calories come from our catalog. */
record MenuPreferences(String intent, List<String> proteins, List<String> excludedProteins,
                       boolean vegetarian, boolean lowCalories, Integer minCalories,
                       Integer maxCalories, Integer maxPrice, List<String> categories,
                       boolean noRice, boolean noBeans, String spicy,
                       List<String> avoidIngredients, boolean allergy, String clarification) {
    MenuPreferences {
        proteins = proteins == null ? List.of() : List.copyOf(proteins);
        excludedProteins = excludedProteins == null ? List.of() : List.copyOf(excludedProteins);
        categories = categories == null ? List.of() : List.copyOf(categories);
        avoidIngredients = avoidIngredients == null ? List.of() : List.copyOf(avoidIngredients);
    }
}
