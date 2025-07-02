package org.gtlcore.gtlcore.mixin.gtm.api.machine;

import com.gregtechceu.gtceu.api.capability.IObjectHolder;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.ResearchStationMachine;

import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "com.gregtechceu.gtceu.common.machine.multiblock.electric.research.ResearchStationMachine.ResearchStationRecipeLogic")
public abstract class ResearchStationRecipeLogicMixin extends RecipeLogic {

    public ResearchStationRecipeLogicMixin(IRecipeLogicMachine machine) {
        super(machine);
    }

    @Shadow(remap = false)
    public abstract @NotNull ResearchStationMachine getMachine();

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public void onRecipeFinish() {
        this.machine.afterWorking();
        if (this.lastRecipe != null) {
            this.lastRecipe.postWorking(this.machine);
            this.handleRecipeIO(this.lastRecipe, IO.OUT);
            if (this.machine.alwaysTryModifyRecipe()) {
                if (this.lastOriginRecipe != null) {
                    GTRecipe modified = this.machine.fullModifyRecipe(this.lastOriginRecipe.copy(), this.ocParams, this.ocResult);
                    if (modified == null) {
                        this.markLastRecipeDirty();
                    } else {
                        this.lastRecipe = modified;
                    }
                } else {
                    this.markLastRecipeDirty();
                }
            }
            if (!this.recipeDirty && this.lastRecipe.matchRecipe(this.machine).isSuccess() && this.lastRecipe.matchTickRecipe(this.machine).isSuccess() && this.lastRecipe.checkConditions(this).isSuccess()) {
                this.setupRecipe(this.lastRecipe);
            } else {
                this.setStatus(RecipeLogic.Status.IDLE);
                this.progress = 0;
                this.duration = 0;
            }
        }
        IObjectHolder holder = this.getMachine().getObjectHolder();
        holder.setHeldItem(ItemStack.EMPTY);
        ItemStack outputItem = ItemStack.EMPTY;
        if (!this.lastRecipe.getOutputContents(ItemRecipeCapability.CAP).isEmpty()) {
            outputItem = ItemRecipeCapability.CAP.of(this.getLastRecipe().getOutputContents(ItemRecipeCapability.CAP).get(0).content).getItems()[0];
        }
        holder.setDataItem(outputItem);
        holder.setLocked(false);
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    protected boolean handleRecipeIO(GTRecipe recipe, IO io) {
        return io == IO.OUT || recipe.handleRecipeIO(io, this.machine, this.chanceCaches);
    }
}
