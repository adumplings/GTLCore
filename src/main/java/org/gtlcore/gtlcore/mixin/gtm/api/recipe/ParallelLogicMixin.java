package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.function.Predicate;

@Mixin(ParallelLogic.class)
public class ParallelLogicMixin {

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public static int getMaxRecipeMultiplier(@NotNull GTRecipe recipe, @NotNull IRecipeCapabilityHolder holder, int parallelAmount) {
        int minimum = Integer.MAX_VALUE;
        for (RecipeCapability<?> cap : recipe.inputs.keySet()) {
            if (cap.doMatchInRecipe()) {
                minimum = Math.min(minimum, cap.getMaxParallelRatio(holder, recipe, parallelAmount));
            }
        }
        for (RecipeCapability<?> cap : recipe.tickInputs.keySet()) {
            if (cap.doMatchInRecipe()) {
                minimum = Math.min(minimum, cap.getMaxParallelRatio(holder, recipe, parallelAmount));
            }
        }
        if (minimum == Integer.MAX_VALUE) return 0;
        return minimum;
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public static int limitByOutputMerging(@NotNull GTRecipe recipe, @NotNull IRecipeCapabilityHolder holder, int parallelAmount, Predicate<RecipeCapability<?>> canVoid) {
        int max = parallelAmount;
        for (RecipeCapability<?> cap : recipe.outputs.keySet()) {
            if (canVoid.test(cap) || !cap.doMatchInRecipe()) {
                continue;
            }
            if (!recipe.getOutputContents(cap).isEmpty()) {
                int limit = cap.limitParallel(recipe, holder, parallelAmount);
                if (limit == 0) {
                    return 0;
                }
                max = Math.min(max, limit);
            }
        }
        for (RecipeCapability<?> cap : recipe.tickOutputs.keySet()) {
            if (canVoid.test(cap) || !cap.doMatchInRecipe()) {
                continue;
            }
            if (!recipe.getTickOutputContents(cap).isEmpty()) {
                int limit = cap.limitParallel(recipe, holder, parallelAmount);
                if (limit == 0) {
                    return 0;
                }
                max = Math.min(max, limit);
            }
        }
        return max;
    }
}
