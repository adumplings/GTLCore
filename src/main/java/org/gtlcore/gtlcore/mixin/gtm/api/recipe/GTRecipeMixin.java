package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.machine.trait.IRecipeStatus;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.resources.ResourceLocation;

import org.spongepowered.asm.mixin.*;

import java.util.List;
import java.util.Map;

@Mixin(GTRecipe.class)
public abstract class GTRecipeMixin {

    @Shadow(remap = false)
    public ResourceLocation id;
    @Mutable
    @Final
    @Shadow(remap = false)
    public final Map<RecipeCapability<?>, List<Content>> inputs;
    @Mutable
    @Final
    @Shadow(remap = false)
    public final Map<RecipeCapability<?>, List<Content>> outputs;
    @Mutable
    @Final
    @Shadow(remap = false)
    public final Map<RecipeCapability<?>, List<Content>> tickInputs;
    @Mutable
    @Final
    @Shadow(remap = false)
    public final Map<RecipeCapability<?>, List<Content>> tickOutputs;

    protected GTRecipeMixin(Map<RecipeCapability<?>, List<Content>> inputs, Map<RecipeCapability<?>, List<Content>> outputs, Map<RecipeCapability<?>, List<Content>> tickInputs, Map<RecipeCapability<?>, List<Content>> tickOutputs) {
        this.inputs = inputs;
        this.outputs = outputs;
        this.tickInputs = tickInputs;
        this.tickOutputs = tickOutputs;
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public GTRecipe.ActionResult matchTickRecipe(IRecipeCapabilityHolder holder) {
        if (!this.hasTick()) return GTRecipe.ActionResult.SUCCESS;
        if (holder instanceof WorkableElectricMultiblockMachine machine) {
            GTRecipe lastRecipe = machine.getRecipeLogic().getLastOriginRecipe();
            if (lastRecipe == null || !this.id.equals(lastRecipe.id)) {
                long eu = this.getTickInputContents(EURecipeCapability.CAP).stream()
                        .map(Content::getContent).mapToLong(EURecipeCapability.CAP::of).sum();
                if (GTUtil.getTierByVoltage(eu) > GTUtil.getFloorTierByVoltage(machine.getOverclockVoltage()) && holder instanceof IRecipeStatus recipeStatus) {
                    recipeStatus.setRecipeStatus(RecipeResult.FailVOLTAGETIER);
                    return GTRecipe.ActionResult.fail(null);
                }
            }
        }
        return this.matchRecipe(holder, true);
    }

    @Shadow(remap = false)
    public abstract List<Content> getTickInputContents(RecipeCapability<?> capability);

    @Shadow(remap = false)
    protected abstract GTRecipe.ActionResult matchRecipe(IRecipeCapabilityHolder holder, boolean tick);

    @Shadow(remap = false)
    public abstract boolean hasTick();
}
