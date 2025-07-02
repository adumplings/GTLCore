package org.gtlcore.gtlcore.api.recipe;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public record RecipeResult(boolean isSuccess, @Nullable Component reason) {

    public static final RecipeResult SUCCESS = new RecipeResult(true, null);
    public static final RecipeResult FailFind = new RecipeResult(false, Component.translatable("gtceu.recipe.fail.find"));
    public static final RecipeResult FailOutput = new RecipeResult(false, Component.translatable("gtceu.recipe.fail.Output"));
    public static final RecipeResult FailVOLTAGETIER = new RecipeResult(false, Component.translatable("gtceu.recipe.fail.voltagetier"));

    public static RecipeResult fail(@Nullable Supplier<Component> reason) {
        if (reason != null) {
            return new RecipeResult(false, reason.get());
        }
        return new RecipeResult(false, null);
    }
}
