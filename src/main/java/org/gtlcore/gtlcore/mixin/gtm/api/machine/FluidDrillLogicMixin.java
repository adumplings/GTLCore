package org.gtlcore.gtlcore.mixin.gtm.api.machine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidVeinSavedData;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.machine.trait.FluidDrillLogic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(FluidDrillLogic.class)
public abstract class FluidDrillLogicMixin extends RecipeLogic {

    @Shadow(remap = false)
    private @Nullable Fluid veinFluid;

    @Shadow(remap = false)
    protected abstract int getChunkX();

    @Shadow(remap = false)
    protected abstract int getChunkZ();

    @Shadow(remap = false)
    protected abstract @Nullable GTRecipe getFluidDrillRecipe();

    @Shadow(remap = false)
    protected abstract void depleteVein();

    public FluidDrillLogicMixin(IRecipeLogicMachine machine) {
        super(machine);
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public void findAndHandleRecipe() {
        Level var2 = this.getMachine().getLevel();
        if (var2 instanceof ServerLevel serverLevel) {
            this.lastRecipe = null;
            BedrockFluidVeinSavedData data = BedrockFluidVeinSavedData.getOrCreate(serverLevel);
            if (this.veinFluid == null) {
                this.veinFluid = data.getFluidInChunk(this.getChunkX(), this.getChunkZ());
                if (this.veinFluid == null) {
                    if (this.subscription != null) {
                        this.subscription.unsubscribe();
                        this.subscription = null;
                    }
                    return;
                }
            }
            GTRecipe match = this.getFluidDrillRecipe();
            if (match != null) {
                this.setupRecipe(match);
            }
        }
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public void onRecipeFinish() {
        this.machine.afterWorking();
        if (this.lastRecipe != null) {
            this.lastRecipe.postWorking(this.machine);
            this.lastRecipe.handleRecipeIO(IO.OUT, this.machine, this.chanceCaches);
        }
        this.depleteVein();
        GTRecipe match = this.getFluidDrillRecipe();
        if (match != null) {
            this.setupRecipe(match);
            return;
        }
        this.setStatus(Status.IDLE);
        this.progress = 0;
        this.duration = 0;
    }
}
