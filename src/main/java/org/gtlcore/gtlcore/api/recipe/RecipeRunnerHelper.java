package org.gtlcore.gtlcore.api.recipe;

import org.gtlcore.gtlcore.api.machine.trait.IMEPartMachine;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeStatus;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.WorkableTieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.steam.SteamWorkableMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.ResearchStationMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.primitive.PrimitiveWorkableMachine;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.*;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class RecipeRunnerHelper {

    public static boolean matchRecipe(IRecipeCapabilityHolder holder, GTRecipe recipe) {
        return matchRecipeInput(holder, recipe) && matchRecipeOutput(holder, recipe);
    }

    public static boolean matchRecipeInput(IRecipeCapabilityHolder holder, GTRecipe recipe) {
        if (recipe.inputs.isEmpty()) return true;
        return handleRecipe(IO.IN, holder, recipe.inputs, Collections.emptyMap(), false, recipe, true).isSuccess();
    }

    public static boolean matchRecipeOutput(IRecipeCapabilityHolder holder, GTRecipe recipe) {
        if (recipe.outputs.isEmpty()) return true;
        if (holder instanceof IMEPartMachine machine && machine.isRecipeOutput(recipe)) return true;
        return handleRecipe(IO.OUT, holder, recipe.outputs, Collections.emptyMap(), false, recipe, true).isSuccess();
    }

    public static boolean handleRecipeIO(IRecipeLogicMachine holder, GTRecipe recipe) {
        return handleRecipeInput(holder, recipe) && handleRecipeOutput(holder, recipe);
    }

    public static boolean handleRecipeInput(IRecipeLogicMachine holder, GTRecipe recipe) {
        return handleRecipe(IO.IN, holder, recipe.inputs, holder.getRecipeLogic().getChanceCaches(), true, recipe, false).isSuccess();
    }

    public static boolean handleRecipeOutput(IRecipeLogicMachine holder, GTRecipe recipe) {
        return handleRecipe(IO.OUT, holder, recipe.outputs, holder.getRecipeLogic().getChanceCaches(), true, recipe, false).isSuccess();
    }

    public static RecipeResult handleRecipe(IO io, IRecipeCapabilityHolder holder, Map<RecipeCapability<?>, List<Content>> contents,
                                            Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches, boolean isTick, GTRecipe recipe, boolean isSimulate) {
        if (holder instanceof PrimitiveWorkableMachine || holder instanceof SteamWorkableMachine ||
                holder instanceof WorkableTieredMachine || holder instanceof ResearchStationMachine) {
            if (isSimulate) {
                GTRecipe.ActionResult result = recipe.matchRecipe(holder);
                if (result.isSuccess()) return RecipeResult.SUCCESS;
            } else {
                if (recipe.handleRecipe(io, holder, isTick, contents, chanceCaches)) return RecipeResult.SUCCESS;
            }
        } else {
            RecipeRunner runner = new RecipeRunner(recipe, io, isTick, holder, chanceCaches, isSimulate);
            RecipeResult result = runner.handle(contents);
            if (holder instanceof IRecipeStatus status) {
                status.setRecipeStatus(result.isSuccess() ? result : (io == IO.IN ? RecipeResult.FailFind : RecipeResult.FailOutput));
            }
            if (result.isSuccess()) return result;
        }
        return RecipeResult.fail(null);
    }
}
