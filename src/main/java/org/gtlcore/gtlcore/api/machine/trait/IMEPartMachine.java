package org.gtlcore.gtlcore.api.machine.trait;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import com.lowdragmc.lowdraglib.side.fluid.FluidStack;

import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import javax.annotation.Nullable;

public interface IMEPartMachine {

    default @Nullable Object2LongOpenCustomHashMap<ItemStack> getItemMap() {
        return null;
    }

    default @Nullable Object2LongOpenHashMap<FluidStack> getFluidMap() {
        return null;
    }

    default void setChanged(boolean value) {}

    default boolean isMEOutPutBus() {
        return false;
    }

    default void setMEOutPutBus(boolean value) {}

    default boolean isMEOutPutHatch() {
        return false;
    }

    default void setMEOutPutHatch(boolean value) {}

    default boolean isMEOutPutDual() {
        return false;
    }

    default void setMEOutPutDual(boolean value) {}

    default boolean isRecipeOutput(GTRecipe recipe) {
        return false;
    }
}
