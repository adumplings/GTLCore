package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.machine.trait.IDistinctMachine;
import org.gtlcore.gtlcore.api.machine.trait.IMEPartMachine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import com.google.common.primitives.Ints;
import it.unimi.dsi.fastutil.objects.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.*;

import static com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP;

@Mixin(ItemRecipeCapability.class)
public class ItemRecipeCapabilityMixin {

    @Shadow(remap = false)
    public Ingredient copyWithModifier(Ingredient content, ContentModifier modifier) {
        return null;
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public int limitParallel(GTRecipe recipe, IRecipeCapabilityHolder holder, int multiplier) {
        if (holder instanceof IMEPartMachine iMEPartMachine && (iMEPartMachine.isMEOutPutBus() ||
                iMEPartMachine.isMEOutPutDual() || recipe.getOutputContents(CAP).isEmpty())) {
            return multiplier;
        }
        var outputContents = recipe.outputs.get(CAP);
        if (outputContents == null || outputContents.isEmpty()) return multiplier;
        List<IRecipeHandler<?>> handlers = holder.getCapabilitiesProxy().get(IO.OUT, CAP);
        if (handlers == null || handlers.isEmpty()) return 0;
        int minMultiplier = 0;
        int maxMultiplier = multiplier;
        int maxCount = 0;
        List<Ingredient> ingredients = new ArrayList<>(outputContents.size());
        for (var content : outputContents) {
            var ing = CAP.of(content.content);
            int count;
            if (ing instanceof SizedIngredient sized) count = sized.getAmount();
            else if (ing instanceof IntProviderIngredient provider) count = provider.getCountProvider().getMaxValue();
            else count = 1;

            maxCount = Math.max(maxCount, count);
            ingredients.add(ing);
        }
        if (maxCount == 0) return multiplier;
        if (multiplier > Integer.MAX_VALUE / maxCount) {
            maxMultiplier = multiplier = Integer.MAX_VALUE / maxCount;
        }
        while (minMultiplier != maxMultiplier) {
            List<Ingredient> copied = new ArrayList<>();
            for (final var ing : ingredients) {
                copied.add(this.copyWithModifier(ing, ContentModifier.multiplier(multiplier)));
            }
            for (var handler : handlers) {
                copied = (List<Ingredient>) handler.handleRecipe(IO.OUT, recipe, copied, null, true);
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
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public int getMaxParallelRatio(IRecipeCapabilityHolder holder, GTRecipe recipe, int parallelAmount) {
        if (parallelAmount <= 1) return parallelAmount;
        if (holder instanceof IDistinctMachine iDistinctMachine) {
            if (iDistinctMachine.getRecipeHandleParts().isEmpty()) return 0;
            Object2LongOpenCustomHashMap<ItemStack> ingredientStacks = new Object2LongOpenCustomHashMap<>(ItemStackHashStrategy.comparingAllButCount());
            if (iDistinctMachine.isDistinct() && iDistinctMachine.getDistinctHatch() != null) {
                List<IRecipeHandler<?>> distinctIMultiPart = iDistinctMachine.getDistinctHatch().getHandlerMap().getOrDefault(CAP, Collections.emptyList());
                for (IRecipeHandler<?> handler : distinctIMultiPart) {
                    for (Object o : handler.getContents()) {
                        if (o instanceof ItemStack itemStack) {
                            ingredientStacks.computeLong(itemStack, (k, v) -> v == null ? itemStack.getCount() : v + itemStack.getCount());
                        }
                    }
                }
            } else {
                Object2LongOpenCustomHashMap<ItemStack> map = new Object2LongOpenCustomHashMap<>(ItemStackHashStrategy.comparingAllButCount());
                List<IRecipeHandler<?>> recipeHandlerList = iDistinctMachine.getCapabilitiesFlat(IO.IN, CAP);
                for (IRecipeHandler<?> container : recipeHandlerList) {
                    Object2LongOpenCustomHashMap<ItemStack> itemMap = new Object2LongOpenCustomHashMap<>(ItemStackHashStrategy.comparingAllButCount());
                    for (Object o : container.getContents()) {
                        if (o instanceof ItemStack itemStack) {
                            itemMap.computeLong(itemStack, (k, v) -> v == null ? itemStack.getCount() : v + itemStack.getCount());
                        }
                    }
                    if (container.isDistinct()) {
                        for (var obj : itemMap.object2LongEntrySet()) {
                            ingredientStacks.computeLong(obj.getKey(), (k, v) -> v == null ? obj.getLongValue() : v + obj.getLongValue());
                        }
                    } else {
                        for (var obj : itemMap.object2LongEntrySet()) {
                            map.computeLong(obj.getKey(), (k, v) -> v == null ? obj.getLongValue() : v + obj.getLongValue());
                        }
                    }
                }
                for (var obj : map.object2LongEntrySet()) {
                    ingredientStacks.computeLong(obj.getKey(), (k, v) -> v == null ? obj.getLongValue() : v + obj.getLongValue());
                }
            }
            long minMultiplier = Integer.MAX_VALUE;
            Object2IntOpenHashMap<Ingredient> countableMap = new Object2IntOpenHashMap<>();
            Ingredient repeatIngredient = Ingredient.EMPTY;
            ItemStack repeatStack = ItemStack.EMPTY;
            boolean hasRepeat = false;
            for (Content content : recipe.getInputContents(CAP)) {
                Ingredient recipeIngredient = CAP.of(content.content);
                if (!hasRepeat) repeatStack = recipeIngredient.getItems()[0];
                hasRepeat = recipeIngredient.test(repeatStack);
                if (repeatIngredient.isEmpty() && hasRepeat) repeatIngredient = recipeIngredient;
                int ingredientCount;
                if (recipeIngredient instanceof SizedIngredient sizedIngredient) {
                    ingredientCount = sizedIngredient.getAmount();
                } else if (recipeIngredient instanceof IntProviderIngredient intProviderIngredient) {
                    ingredientCount = intProviderIngredient.getSampledCount(GTValues.RNG);
                } else {
                    ingredientCount = 1;
                }
                if (content.chance > 0) {
                    countableMap.addTo(hasRepeat ? repeatIngredient : recipeIngredient, ingredientCount);
                }
            }
            if (countableMap.isEmpty()) {
                return parallelAmount;
            }
            for (var recipeInputEntry : countableMap.object2IntEntrySet()) {
                long needed = recipeInputEntry.getIntValue();
                long available = 0;
                for (var inventoryEntry : ingredientStacks.object2LongEntrySet()) {
                    if (recipeInputEntry.getKey().test(inventoryEntry.getKey())) {
                        available += inventoryEntry.getLongValue();
                        break;
                    }
                }
                if (available >= needed) {
                    long ratio = Math.min(parallelAmount, available / needed);
                    if (ratio < minMultiplier) {
                        minMultiplier = ratio;
                    }
                } else {
                    return 0;
                }
            }
            return Ints.saturatedCast(minMultiplier);
        }
        return 1;
    }
}
