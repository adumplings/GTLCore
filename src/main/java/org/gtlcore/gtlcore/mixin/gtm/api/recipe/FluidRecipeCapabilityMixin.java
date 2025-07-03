package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.machine.trait.IDistinctMachine;
import org.gtlcore.gtlcore.api.machine.trait.IMEPartMachine;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;

import com.lowdragmc.lowdraglib.side.fluid.FluidStack;

import com.google.common.primitives.Ints;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability.CAP;

@Mixin(FluidRecipeCapability.class)
public class FluidRecipeCapabilityMixin {

    /**
     * @author Adonis
     * @reason 流体输入输出上限改为long
     */
    @Overwrite(remap = false)
    public FluidIngredient copyWithModifier(FluidIngredient content, ContentModifier modifier) {
        if (content.isEmpty()) {
            return content.copy();
        } else {
            FluidIngredient copy = content.copy();
            copy.setAmount(modifier.apply(copy.getAmount()).longValue());
            return copy;
        }
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public int limitParallel(GTRecipe recipe, IRecipeCapabilityHolder holder, int multiplier) {
        if (holder instanceof IMEPartMachine iMEPartMachine &&
                (iMEPartMachine.isMEOutPutDual() || iMEPartMachine.isMEOutPutHatch())) {
            return multiplier;
        }
        var outputContents = recipe.outputs.get(CAP);
        if (outputContents == null || outputContents.isEmpty()) return multiplier;
        List<IRecipeHandler<?>> handlers = holder.getCapabilitiesProxy().get(IO.OUT, CAP);
        if (handlers == null || handlers.isEmpty()) return 0;
        int minMultiplier = 0;
        int maxMultiplier = multiplier;
        long maxAmount = 0;
        List<FluidIngredient> ingredients = new ArrayList<>(outputContents.size());
        for (var content : outputContents) {
            var ing = CAP.of(content.content);
            maxAmount = Math.max(maxAmount, ing.getAmount());
            ingredients.add(ing);
        }
        if (maxAmount == 0) return multiplier;
        if (multiplier > Long.MAX_VALUE / maxAmount) {
            maxMultiplier = multiplier = Ints.saturatedCast(Long.MAX_VALUE / maxAmount);
        }
        while (minMultiplier != maxMultiplier) {
            List<FluidIngredient> copied = new ArrayList<>();
            for (final var ing : ingredients) {
                copied.add(this.copyWithModifier(ing, ContentModifier.multiplier(multiplier)));
            }
            for (var handler : handlers) {
                copied = (List<FluidIngredient>) handler.handleRecipe(IO.OUT, recipe, copied, null, true);
                if (copied == null) break;
            }
            int[] bin = ParallelLogic.adjustMultiplier(copied == null, minMultiplier, multiplier, maxMultiplier);
            minMultiplier = bin[0];
            multiplier = bin[1];
            maxMultiplier = bin[2];
        }
        return multiplier;
    }

    /**
     * @author Adonis
     * @reason 支持流体隔离
     */
    @Overwrite(remap = false)
    public int getMaxParallelRatio(IRecipeCapabilityHolder holder, GTRecipe recipe, int parallelAmount) {
        if (parallelAmount <= 1 || recipe.inputs.get(CAP) == null) return parallelAmount;
        if (holder instanceof IDistinctMachine iDistinctMachine) {
            if (iDistinctMachine.getRecipeHandleParts().isEmpty()) return 0;
            Object2LongOpenHashMap<FluidStack> ingredientStacks = new Object2LongOpenHashMap<>();
            if (iDistinctMachine.isDistinct() && iDistinctMachine.getDistinctHatch() != null) {
                List<IRecipeHandler<?>> distinctIMultiPart = iDistinctMachine.getDistinctHatch().getHandlerMap().getOrDefault(CAP, Collections.emptyList());
                for (IRecipeHandler<?> handler : distinctIMultiPart) {
                    for (Object o : handler.getContents()) {
                        if (o instanceof FluidStack fluidStack) {
                            ingredientStacks.computeLong(fluidStack, (k, v) -> v == null ? fluidStack.getAmount() : v + fluidStack.getAmount());
                        }
                    }
                }
            } else {
                for (var container : iDistinctMachine.getCapabilitiesFlat(IO.IN, CAP)) {
                    for (Object object : container.getContents()) {
                        if (object instanceof FluidStack fluidStack) {
                            ingredientStacks.computeLong(fluidStack, (k, v) -> v == null ? fluidStack.getAmount() : v + fluidStack.getAmount());
                        }
                    }
                }
            }
            Object2LongOpenHashMap<FluidIngredient> fluidCountMap = new Object2LongOpenHashMap<>();
            for (Content content : recipe.getInputContents(CAP)) {
                FluidIngredient fluidInput = CAP.of(content.content);
                if (content.chance > 0) {
                    fluidCountMap.addTo(fluidInput, fluidInput.getAmount());
                }
            }
            long needed;
            long available;
            for (var it = fluidCountMap.object2LongEntrySet().fastIterator(); it.hasNext(); parallelAmount = Ints.saturatedCast(Math.min(parallelAmount, available / needed))) {
                var entry = it.next();
                needed = entry.getLongValue();
                available = 0;
                for (var iter = Object2LongMaps.fastIterator(ingredientStacks); iter.hasNext();) {
                    var inputFluid = iter.next();
                    if (entry.getKey().test(inputFluid.getKey())) {
                        available += inputFluid.getLongValue();
                        break;
                    }
                }
                if (available < needed) {
                    parallelAmount = 0;
                    break;
                }
            }
            return parallelAmount;
        }
        return 1;
    }
}
