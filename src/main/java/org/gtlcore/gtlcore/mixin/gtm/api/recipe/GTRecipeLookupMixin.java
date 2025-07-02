package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.machine.trait.IDistinctMachine;
import org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.WorkableTieredMachine;
import com.gregtechceu.gtceu.api.machine.steam.SteamWorkableMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.lookup.*;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.ResearchStationMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.primitive.PrimitiveWorkableMachine;

import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Predicate;

@Mixin(GTRecipeLookup.class)
public abstract class GTRecipeLookupMixin {

    @Unique
    private IRecipeCapabilityHolder gtlcore$machine;

    @Shadow(remap = false)
    private static final WeakHashMap<AbstractMapIngredient, WeakReference<AbstractMapIngredient>> ingredientRoot = new WeakHashMap();

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public @Nullable GTRecipe findRecipe(IRecipeCapabilityHolder holder) {
        return this.find(holder, (recipe) -> RecipeRunnerHelper.matchRecipe(holder, recipe));
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    protected @Nullable List<List<AbstractMapIngredient>> prepareRecipeFind(@NotNull IRecipeCapabilityHolder holder) {
        this.gtlcore$machine = holder;
        if (holder instanceof PrimitiveWorkableMachine || holder instanceof SteamWorkableMachine ||
                holder instanceof WorkableTieredMachine || holder instanceof ResearchStationMachine) {
            int totalSize = 0;
            for (Map.Entry<RecipeCapability<?>, List<IRecipeHandler<?>>> entries : holder.getCapabilitiesProxy().row(IO.IN).entrySet()) {
                int size = 0;
                if ((entries.getKey()).isRecipeSearchFilter()) {
                    for (IRecipeHandler<?> entry : entries.getValue())
                        if (entry.getSize() != -1) size += entry.getSize();
                    if (size == Integer.MAX_VALUE) return null;
                    totalSize += size;
                }
            }
            if (totalSize == 0) return null;
            List<List<AbstractMapIngredient>> list = new ObjectArrayList<>(totalSize);
            list.addAll(fromHolder(holder));
            if (list.isEmpty()) return null;
            return list;
        } else if (holder instanceof IDistinctMachine iDistinctMachine) {
            if (iDistinctMachine.getRecipeHandleParts().isEmpty()) return null;
            List<List<AbstractMapIngredient>> list = new ObjectArrayList<>(iDistinctMachine.getRecipeHandleParts().size());
            list.addAll(this.gtlcore$fromHolder(iDistinctMachine));
            if (list.isEmpty()) {
                return null;
            }
            return list;
        }
        return List.of();
    }

    @Unique
    protected @NotNull List<List<AbstractMapIngredient>> gtlcore$fromHolder(@NotNull IDistinctMachine r) {
        List<List<AbstractMapIngredient>> list;
        List<RecipeHandlePart> recipeHandleParts = r.getCapabilities().getOrDefault(IO.IN, new ObjectArrayList<>());
        if (recipeHandleParts.isEmpty()) return Collections.emptyList();
        list = new ObjectArrayList<>(recipeHandleParts.size());
        if (r.isDistinct()) {
            for (var part : recipeHandleParts) {
                List<AbstractMapIngredient> ingredients = new ObjectArrayList<>();
                for (var it = part.getHandlerMap().reference2ObjectEntrySet().fastIterator(); it.hasNext();) {
                    var next = it.next();
                    var cap = next.getKey();
                    for (var handler : next.getValue()) {
                        for (Object content : cap.compressIngredients(handler.getContents())) {
                            ingredients.addAll(cap.convertToMapIngredient(content).stream()
                                    .sorted(Comparator.comparing(i -> !i.isSpecialIngredient())).toList());
                        }
                    }
                }
                list.add(ingredients);
            }
        } else {
            var fluidHandlers = r.getCapabilitiesFlat(IO.IN, FluidRecipeCapability.CAP);
            List<AbstractMapIngredient> fluidIngredients = new ObjectArrayList<>();
            for (var fluidPart : fluidHandlers) {
                for (Object content : FluidRecipeCapability.CAP.compressIngredients(fluidPart.getContents())) {
                    fluidIngredients.addAll(FluidRecipeCapability.CAP.convertToMapIngredient(content));
                }
            }
            for (var itemPart : recipeHandleParts) {
                List<AbstractMapIngredient> itemIngredients = new ObjectArrayList<>();
                for (var itemHandler : itemPart.getCapability(ItemRecipeCapability.CAP)) {
                    for (Object content : ItemRecipeCapability.CAP.compressIngredients(itemHandler.getContents())) {
                        itemIngredients.addAll(ItemRecipeCapability.CAP.convertToMapIngredient(content));
                    }
                }
                itemIngredients.addAll(fluidIngredients);
                list.add(itemIngredients);
            }
        }
        return list;
    }

    /**
     * @author Adonis
     * @reason .
     */
    @Overwrite(remap = false)
    public @Nullable GTRecipe recurseIngredientTreeFindRecipe(@NotNull List<List<AbstractMapIngredient>> ingredients,
                                                              @NotNull Branch branchMap, @NotNull Predicate<GTRecipe> canHandle, int index, int count, long skip) {
        if (count == ingredients.size()) {
            return null;
        } else if (this.gtlcore$machine instanceof PrimitiveWorkableMachine || this.gtlcore$machine instanceof SteamWorkableMachine ||
                this.gtlcore$machine instanceof WorkableTieredMachine || this.gtlcore$machine instanceof ResearchStationMachine) {
                    for (AbstractMapIngredient obj : ingredients.get(index)) {
                        Map<AbstractMapIngredient, Either<GTRecipe, Branch>> targetMap = determineRootNodes(obj, branchMap);
                        Either<GTRecipe, Branch> result = targetMap.get(obj);
                        if (result != null) {
                            GTRecipe r = result.map(potentialRecipe -> canHandle.test(potentialRecipe) ? potentialRecipe : null,
                                    potentialBranch -> diveIngredientTreeFindRecipe(ingredients, potentialBranch, canHandle, index, count, skip));
                            if (r != null) {
                                return r;
                            }
                        }
                    }
                } else
            if (this.gtlcore$machine instanceof IDistinctMachine) {
                List<AbstractMapIngredient> ingredient = new ObjectArrayList<>(ingredients.get(index));
                return this.gtlcore$diveIngredientTreeFindRecipe(ingredient, branchMap, canHandle);
            }
        return null;
    }

    @Unique
    private @Nullable GTRecipe gtlcore$diveIngredientTreeFindRecipe(@NotNull List<AbstractMapIngredient> ingredients, @NotNull Branch branchMap,
                                                                    @NotNull Predicate<GTRecipe> canHandle) {
        if (ingredients.isEmpty()) return null;
        for (var o : ingredients) {
            Map<AbstractMapIngredient, Either<GTRecipe, Branch>> targetMap = determineRootNodes(o, branchMap);
            Either<GTRecipe, Branch> result = targetMap.get(o);
            if (result != null) {
                GTRecipe r = result.map((potentialRecipe) -> canHandle.test(potentialRecipe) ? potentialRecipe : null,
                        (potentialBranch) -> this.gtlcore$diveIngredientTreeFindRecipe(ingredients, potentialBranch, canHandle));
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    private @Nullable GTRecipe diveIngredientTreeFindRecipe(@NotNull List<List<AbstractMapIngredient>> ingredients, @NotNull Branch map, @NotNull Predicate<GTRecipe> canHandle, int currentIndex, int count, long skip) {
        int i = (currentIndex + 1) % ingredients.size();
        while (i != currentIndex) {
            if (((skip & (1L << i)) == 0)) {
                GTRecipe found = recurseIngredientTreeFindRecipe(ingredients, map, canHandle, i, count + 1, skip | (1L << i));
                if (found != null) {
                    return found;
                }
            }
            i = (i + 1) % ingredients.size();
        }
        return null;
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    protected @NotNull List<List<AbstractMapIngredient>> fromRecipe(@NotNull GTRecipe r) {
        List<List<AbstractMapIngredient>> list = new ObjectArrayList<>(r.inputs.size());
        r.inputs.forEach((cap, contents) -> {
            if (cap.isRecipeSearchFilter() && !contents.isEmpty()) {
                List<Object> ingredients = new ArrayList<>();
                for (Content content : contents) ingredients.add(content.getContent());
                for (Object ingredient : cap.compressIngredients(ingredients))
                    retrieveCachedIngredient(list, cap.convertToMapIngredient(ingredient), ingredientRoot);
            }
        });
        return list;
    }

    @Shadow(remap = false)
    protected abstract @NotNull List<List<AbstractMapIngredient>> fromHolder(@NotNull IRecipeCapabilityHolder r);

    @Shadow(remap = false)
    public abstract @Nullable GTRecipe find(@NotNull IRecipeCapabilityHolder holder, @NotNull Predicate<GTRecipe> canHandle);

    @Shadow(remap = false)
    protected static void retrieveCachedIngredient(@NotNull List<List<AbstractMapIngredient>> list, @NotNull List<AbstractMapIngredient> ingredients, @NotNull WeakHashMap<AbstractMapIngredient, WeakReference<AbstractMapIngredient>> cache) {}

    @Shadow(remap = false)
    protected static @NotNull Map<AbstractMapIngredient, Either<GTRecipe, Branch>> determineRootNodes(@NotNull AbstractMapIngredient ingredient, @NotNull Branch branchMap) {
        throw new RuntimeException();
    }
}
