package org.gtlcore.gtlcore.api.recipe;

import org.gtlcore.gtlcore.api.machine.trait.IDistinctMachine;
import org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.chance.boost.ChanceBoostFunction;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;

import it.unimi.dsi.fastutil.objects.*;

import java.util.*;

/**
 * 部分代码参考自gto
 * &#064;line <a href="https://github.com/GregTech-Odyssey/GTOCore">...</a>
 */

public class RecipeRunner {

    private final GTRecipe recipe;
    private final RecipeHandlePart recipeHandlePart;
    private final IO io;
    private final boolean isTick;
    private final IRecipeCapabilityHolder holder;
    private final Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches;
    private final boolean simulated;
    private Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> recipeContent;

    public RecipeRunner(GTRecipe recipe, IO io, boolean isTick, IRecipeCapabilityHolder holder,
                        Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches, boolean simulated) {
        RecipeHandlePart recipeHandlePart = null;
        if (io == IO.IN && holder instanceof IDistinctMachine iDistinctMachine && iDistinctMachine.isDistinct()) {
            if (recipe.id.equals(iDistinctMachine.getRecipeId())) {
                recipeHandlePart = iDistinctMachine.getDistinctHatch();
            } else {
                iDistinctMachine.setRecipeId(recipe.id);
                iDistinctMachine.setDistinctHatch(null);
            }
        }
        this.recipeHandlePart = recipeHandlePart;
        this.recipe = recipe;
        this.io = io;
        this.isTick = isTick;
        this.holder = holder;
        this.chanceCaches = chanceCaches;
        this.recipeContent = new Reference2ObjectOpenHashMap<>();
        this.simulated = simulated;
    }

    public RecipeResult handle(Map<RecipeCapability<?>, List<Content>> entry) {
        this.fillContent(entry);
        if (this.recipeContent.isEmpty()) return RecipeResult.SUCCESS;
        return this.handleContents();
    }

    private void fillContent(Map<RecipeCapability<?>, List<Content>> entries) {
        for (var entry : entries.entrySet()) {
            RecipeCapability<?> cap = entry.getKey();
            if (!cap.doMatchInRecipe()) continue;
            if (entry.getValue().isEmpty()) continue;
            List<Content> chancedContents = new ArrayList<>();
            var contentList = this.recipeContent.computeIfAbsent(cap, c -> new ObjectArrayList<>());
            for (Content cont : entry.getValue()) {
                if (simulated) {
                    contentList.add(cont.content);
                } else {
                    if (cont.chance >= cont.maxChance) {
                        contentList.add(cont.content);
                    } else {
                        chancedContents.add(cont.copy(cap, ContentModifier.multiplier(1.0 / recipe.parallels)));
                    }
                }
            }
            if (!chancedContents.isEmpty()) {
                ChanceBoostFunction function = recipe.getType().getChanceFunction();
                ChanceLogic logic = recipe.getChanceLogicForCapability(cap, this.io, this.isTick);
                int recipeTier = RecipeHelper.getPreOCRecipeEuTier(recipe);
                int holderTier = holder.getChanceTier();
                var cache = this.chanceCaches.get(cap);
                chancedContents = logic.roll(chancedContents, function, recipeTier, holderTier, cache, recipe.parallels, cap);
                if (chancedContents != null) {
                    for (Content cont : chancedContents) {
                        contentList.add(cont.content);
                    }
                }
            }
            if (contentList.isEmpty()) recipeContent.remove(cap);
        }
    }

    private RecipeResult handleContents() {
        return handleContentsInternal(io) ? RecipeResult.SUCCESS : RecipeResult.fail(null);
    }

    private boolean handleContentsInternal(IO capIO) {
        if (holder instanceof IDistinctMachine iDistinctMachine) {
            if (iDistinctMachine.isDistinct() && simulated && capIO == IO.IN) return this.simulatedHandle();
            if (recipeHandlePart != null) {
                return recipeHandlePart.handleRecipe(capIO, recipe, recipeContent, simulated).isEmpty();
            } else {
                if (capIO == IO.IN) {
                    var itemContent = recipeContent.getOrDefault(ItemRecipeCapability.CAP, new ObjectArrayList<>());
                    if (!itemContent.isEmpty()) {
                        Map<RecipeCapability<?>, List<Object>> contents = new HashMap<>();
                        contents.put(ItemRecipeCapability.CAP, itemContent);
                        for (var item : iDistinctMachine.getCapabilities().get(capIO)) {
                            var result = item.handleRecipe(capIO, recipe, contents, simulated);
                            if (result.isEmpty()) {
                                itemContent.clear();
                                break;
                            } else contents = result;
                        }
                    }
                    List<?> fluidContent = recipeContent.getOrDefault(FluidRecipeCapability.CAP, new ObjectArrayList<>());
                    if (!fluidContent.isEmpty()) {
                        for (var fluid : iDistinctMachine.getCapabilitiesFlat(capIO, FluidRecipeCapability.CAP)) {
                            var result = fluid.handleRecipe(capIO, recipe, fluidContent, null, simulated);
                            if (result == null) {
                                fluidContent.clear();
                                break;
                            } else fluidContent = result;
                        }
                    }
                    return itemContent.isEmpty() && fluidContent.isEmpty();
                } else {
                    var handlers = iDistinctMachine.getCapabilities().getOrDefault(IO.OUT, new ObjectArrayList<>());
                    handlers.sort(RecipeHandlePart.COMPARATOR.reversed());
                    for (var handler : handlers) {
                        recipeContent = handler.handleRecipe(capIO, recipe, recipeContent, simulated);
                        if (recipeContent.isEmpty()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean simulatedHandle() {
        if (holder instanceof IDistinctMachine iDistinctMachine) {
            if (iDistinctMachine.getRecipeHandleParts().isEmpty()) return false;
            List<Object> itemContent = recipeContent.computeIfAbsent(ItemRecipeCapability.CAP, k -> new ObjectArrayList<>());
            List<Object> fluidContent = recipeContent.computeIfAbsent(FluidRecipeCapability.CAP, k -> new ObjectArrayList<>());
            if (itemContent.isEmpty() && fluidContent.isEmpty()) return false;
            if (recipeHandlePart != null) {
                return recipeHandlePart.testRecipeHandle(iDistinctMachine, recipe, itemContent, fluidContent);
            }
            for (RecipeHandlePart recipeHandlePart : iDistinctMachine.getCapabilities().get(IO.IN)) {
                if (recipeHandlePart.testRecipeHandle(iDistinctMachine, recipe, itemContent, fluidContent)) {
                    return true;
                }
            }
        }
        return false;
    }
}
