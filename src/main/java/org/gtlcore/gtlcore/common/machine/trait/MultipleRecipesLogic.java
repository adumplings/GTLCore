package org.gtlcore.gtlcore.common.machine.trait;

import org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine;
import org.gtlcore.gtlcore.api.machine.trait.ILockRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import net.minecraft.nbt.CompoundTag;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

@Getter
public class MultipleRecipesLogic extends RecipeLogic implements ILockRecipe {

    private final ParallelMachine parallel;

    private final BiPredicate<CompoundTag, IRecipeLogicMachine> dataCheck;

    public MultipleRecipesLogic(ParallelMachine machine) {
        this(machine, null);
    }

    public MultipleRecipesLogic(ParallelMachine machine, BiPredicate<CompoundTag, IRecipeLogicMachine> dataCheck) {
        super((IRecipeLogicMachine) machine);
        this.parallel = machine;
        this.dataCheck = dataCheck;
    }

    @Override
    public WorkableElectricMultiblockMachine getMachine() {
        return (WorkableElectricMultiblockMachine) super.getMachine();
    }

    @Override
    public void findAndHandleRecipe() {
        lastRecipe = null;
        var match = getRecipe();
        if (match != null) {
            if (RecipeRunnerHelper.matchRecipeOutput(machine, match)) {
                setupRecipe(match);
            }
        }
    }

    @Nullable
    private GTRecipe getRecipe() {
        if (!machine.hasProxies()) return null;
        GTRecipe match = lookupRecipe();
        if (match == null) return null;
        GTRecipe recipe = buildEmptyRecipe();
        recipe.outputs.put(ItemRecipeCapability.CAP, new ArrayList<>());
        recipe.outputs.put(FluidRecipeCapability.CAP, new ArrayList<>());
        long maxEUt = getMachine().getOverclockVoltage();
        long totalEu = 0;
        int parallel = this.parallel.getMaxParallel();
        for (int i = 0; i < 64; i++) {
            match = parallelRecipe(match, parallel);
            GTRecipe input = buildEmptyRecipe();
            long inputEUt = RecipeHelper.getInputEUt(match);
            if (inputEUt > maxEUt) {
                continue;
            }
            // 需要在此check data
            if (dataCheck != null && !dataCheck.test(match.data, machine)) {
                continue;
            }
            input.inputs.putAll(match.inputs);
            input.setId(match.id);
            if (RecipeRunnerHelper.handleRecipeInput(machine, input)) {
                totalEu += match.duration * inputEUt;
                List<Content> item = match.outputs.get(ItemRecipeCapability.CAP);
                if (item != null) {
                    recipe.outputs.get(ItemRecipeCapability.CAP).addAll(item);
                }
                List<Content> fluid = match.outputs.get(FluidRecipeCapability.CAP);
                if (fluid != null) {
                    recipe.outputs.get(FluidRecipeCapability.CAP).addAll(fluid);
                }
            }
            match = lookupRecipe();
            if (match == null) break;
        }
        if (recipe.outputs.get(ItemRecipeCapability.CAP).isEmpty() && recipe.outputs.get(FluidRecipeCapability.CAP).isEmpty())
            return null;
        double d = (double) totalEu / maxEUt;
        long eut = d > 20 ? maxEUt : (long) (maxEUt * d / 20);
        recipe.tickInputs.put(EURecipeCapability.CAP, List.of(new Content(eut, ChanceLogic.getMaxChancedValue(), ChanceLogic.getMaxChancedValue(), 0, null, null)));
        recipe.duration = (int) Math.max(d, 20);
        return recipe;
    }

    private GTRecipe lookupRecipe() {
        if (this.isLock()) {
            if (this.getLockRecipe() == null) this.setLockRecipe(machine.getRecipeType().getLookup().findRecipe(machine));
            else if (!RecipeRunnerHelper.matchRecipe(machine, this.getLockRecipe())) return null;
            return this.getLockRecipe();
        } else return machine.getRecipeType().getLookup().findRecipe(machine);
    }

    private GTRecipe buildEmptyRecipe() {
        return GTRecipeBuilder.ofRaw().buildRawRecipe();
    }

    private GTRecipe parallelRecipe(GTRecipe recipe, int max) {
        int maxMultipliers = Integer.MAX_VALUE;
        for (RecipeCapability<?> cap : recipe.inputs.keySet()) {
            if (cap.doMatchInRecipe()) {
                int currentMultiplier = cap.getMaxParallelRatio(machine, recipe, max);
                if (currentMultiplier < maxMultipliers) {
                    maxMultipliers = currentMultiplier;
                }
            }
        }
        if (maxMultipliers > 0) {
            recipe = recipe.copy(ContentModifier.multiplier(maxMultipliers), false);
        }
        return recipe;
    }

    @Override
    public void onRecipeFinish() {
        machine.afterWorking();
        if (lastRecipe != null) {
            lastRecipe.postWorking(this.machine);
            RecipeRunnerHelper.handleRecipeOutput(this.machine, lastRecipe);
        }
        var match = getRecipe();
        if (match != null) {
            if (RecipeRunnerHelper.matchRecipeOutput(machine, match)) {
                setupRecipe(match);
                return;
            }
        }
        setStatus(Status.IDLE);
        progress = 0;
        duration = 0;
    }
}
