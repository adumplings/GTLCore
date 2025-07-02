package org.gtlcore.gtlcore.api.machine.trait;

import org.gtlcore.gtlcore.api.recipe.RecipeResult;

public interface IRecipeStatus {

    default void setRecipeStatus(RecipeResult result) {}

    default RecipeResult getRecipeStatus() {
        return null;
    }
}
