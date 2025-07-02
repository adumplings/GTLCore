package org.gtlcore.gtlcore.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RecipeHandlePart {

    public static final RecipeHandlePart NO_DATA = new RecipeHandlePart(IO.NONE);

    public static final Comparator<RecipeHandlePart> COMPARATOR = (h1, h2) -> {
        int cmp = Long.compare(h1.getPriority(), h2.getPriority());
        if (cmp != 0) return cmp;
        boolean b1 = h1.getTotalContentAmount() > 0;
        boolean b2 = h2.getTotalContentAmount() > 0;
        return Boolean.compare(b1, b2);
    };

    @Getter
    private final IO handlerIO;
    @Getter
    private final Reference2ObjectOpenHashMap<RecipeCapability<?>, List<IRecipeHandler<?>>> handlerMap = new Reference2ObjectOpenHashMap<>();
    private final List<IRecipeHandler<?>> allHandlers = new ArrayList<>();

    public RecipeHandlePart(IO io) {
        this.handlerIO = io;
    }

    public static RecipeHandlePart of(IO io, Iterable<IRecipeHandler<?>> handlers) {
        RecipeHandlePart rhl = new RecipeHandlePart(io);
        rhl.addHandlers(handlers);
        return rhl;
    }

    public void addHandlers(Iterable<IRecipeHandler<?>> handlers) {
        for (var handler : handlers) {
            getHandlerMap().computeIfAbsent(handler.getCapability(), c -> new ArrayList<>()).add(handler);
            allHandlers.add(handler);
        }
        if (handlerIO == IO.OUT) sort();
    }

    private void sort() {
        for (var list : getHandlerMap().values()) {
            list.sort(IRecipeHandler.ENTRY_COMPARATOR);
        }
    }

    public boolean hasCapability(RecipeCapability<?> cap) {
        return getHandlerMap().containsKey(cap);
    }

    public @NotNull List<IRecipeHandler<?>> getCapability(RecipeCapability<?> cap) {
        return getHandlerMap().getOrDefault(cap, Collections.emptyList());
    }

    public long getPriority() {
        long priority = 0;
        for (var handler : allHandlers) priority += handler.getPriority();
        return priority;
    }

    public double getTotalContentAmount() {
        double sum = 0;
        for (var handler : allHandlers) sum += handler.getTotalContentAmount();
        return sum;
    }

    public Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> handleRecipe(IO io, GTRecipe recipe,
                                                                                       Map<RecipeCapability<?>, List<Object>> contents,
                                                                                       boolean simulate) {
        var copy = new Reference2ObjectOpenHashMap<>(contents);
        if (!getHandlerMap().isEmpty()) {
            for (var it = copy.reference2ObjectEntrySet().fastIterator(); it.hasNext();) {
                var entry = it.next();
                var handlerList = getCapability(entry.getKey());
                for (var handler : handlerList) {
                    var left = handler.handleRecipe(io, recipe, entry.getValue(), null, simulate);
                    if (left == null) {
                        it.remove();
                        break;
                    } else {
                        entry.setValue(new ArrayList<>(left));
                    }
                }
            }
        }
        return copy;
    }

    public boolean testRecipeHandle(IDistinctMachine iDistinctMachine, GTRecipe recipe, List<Object> itemContent, List<Object> fluidContent) {
        if (itemContent.isEmpty()) {
            List<?> copyFluid = new ObjectArrayList<>(fluidContent);
            for (var handle : this.getCapability(FluidRecipeCapability.CAP)) {
                copyFluid = handle.handleRecipe(IO.IN, recipe, copyFluid, null, true);
                if (copyFluid == null) {
                    iDistinctMachine.setDistinctHatch(this);
                    return true;
                }
            }
        } else if (fluidContent.isEmpty()) {
            List<?> copyItem = new ObjectArrayList<>(itemContent);
            for (var handle : this.getCapability(ItemRecipeCapability.CAP)) {
                copyItem = handle.handleRecipe(IO.IN, recipe, copyItem, null, true);
                if (copyItem == null) {
                    iDistinctMachine.setDistinctHatch(this);
                    return true;
                }
            }
        } else {
            List<?> copyItem = new ObjectArrayList<>(itemContent);
            for (var handle : this.getCapability(ItemRecipeCapability.CAP)) {
                copyItem = handle.handleRecipe(IO.IN, recipe, copyItem, null, true);
                if (copyItem == null) {
                    List<?> copyFluid = new ObjectArrayList<>(fluidContent);
                    for (var h : this.getCapability(FluidRecipeCapability.CAP)) {
                        copyFluid = h.handleRecipe(IO.IN, recipe, copyFluid, null, true);
                        if (copyFluid == null) {
                            iDistinctMachine.setDistinctHatch(this);
                            return true;
                        }
                    }
                    copyItem = new ObjectArrayList<>(itemContent);
                }
            }
        }
        return false;
    }
}
