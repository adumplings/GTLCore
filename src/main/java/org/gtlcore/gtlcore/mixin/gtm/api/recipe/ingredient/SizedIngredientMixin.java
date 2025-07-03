package org.gtlcore.gtlcore.mixin.gtm.api.recipe.ingredient;

import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;

import net.minecraft.world.item.crafting.Ingredient;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient.create;

@Mixin(SizedIngredient.class)
public class SizedIngredientMixin {

    /**
     * @author .
     * @reason .
     */
    @Inject(method = "copy", at = @At("HEAD"), remap = false, cancellable = true)
    private static void copy(Ingredient ingredient, CallbackInfoReturnable<Ingredient> cir) {
        if (ingredient instanceof SizedIngredient sizedIngredient) {
            Ingredient var5 = sizedIngredient.getInner();
            if (var5 instanceof IntProviderIngredient intProviderIngredient) {
                cir.setReturnValue(new IntProviderIngredient(intProviderIngredient.getInner(), intProviderIngredient.getCountProvider()));
            } else {
                cir.setReturnValue(create(sizedIngredient.getInner(), sizedIngredient.getAmount()));
            }
        } else if (ingredient instanceof IntCircuitIngredient circuit) {
            cir.setReturnValue(circuit.copy());
        } else if (ingredient instanceof IntProviderIngredient intProviderIngredient) {
            cir.setReturnValue(new IntProviderIngredient(intProviderIngredient.getInner(), intProviderIngredient.getCountProvider()));
        } else {
            cir.setReturnValue(create(ingredient));
        }
        cir.cancel();
    }
}
