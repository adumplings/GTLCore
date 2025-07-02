package org.gtlcore.gtlcore.mixin.gtm.api.machine;

import org.gtlcore.gtlcore.api.machine.trait.ILockRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;

import java.util.*;

@Mixin(RecipeLogic.class)
public abstract class RecipeLogicMixin implements ILockRecipe {

    @Persisted
    @Getter
    private boolean isLock = false;
    @Persisted
    @Getter
    @Setter
    private GTRecipe lockRecipe;

    @Mutable
    @Final
    @Shadow(remap = false)
    public final IRecipeLogicMachine machine;
    @Mutable
    @Final
    @Shadow(remap = false)
    protected final Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches;
    @Shadow(remap = false)
    public List<GTRecipe> lastFailedMatches;
    @Shadow(remap = false)
    protected @Nullable GTRecipe lastRecipe;
    @Shadow(remap = false)
    protected @Nullable GTRecipe lastOriginRecipe;
    @Shadow(remap = false)
    protected OCParams ocParams;
    @Shadow(remap = false)
    protected OCResult ocResult;
    @Shadow(remap = false)
    protected boolean recipeDirty;
    @Shadow(remap = false)
    protected int progress;
    @Shadow(remap = false)
    protected int duration;
    @Shadow(remap = false)
    private boolean isActive;

    @Shadow(remap = false)
    protected abstract void handleSearchingRecipes(Iterator<GTRecipe> matches);

    @Shadow(remap = false)
    protected abstract Iterator<GTRecipe> searchRecipe();

    @Shadow(remap = false)
    public abstract void markLastRecipeDirty();

    @Shadow(remap = false)
    public abstract void setStatus(RecipeLogic.Status status);

    @Shadow(remap = false)
    public abstract RecipeLogic.Status getStatus();

    @Shadow(remap = false)
    public abstract void setupRecipe(GTRecipe recipe);

    @Shadow(remap = false)
    public abstract void updateTickSubscription();

    public RecipeLogicMixin(IRecipeLogicMachine machine, Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches) {
        this.machine = machine;
        this.chanceCaches = chanceCaches;
    }

    public void setLock(boolean look) {
        isLock = look;
        lockRecipe = null;
        updateTickSubscription();
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    protected boolean handleRecipeIO(GTRecipe recipe, IO io) {
        if (!(this.machine.hasProxies() && io != IO.BOTH)) return false;
        return RecipeRunnerHelper.handleRecipeInput(this.machine, recipe);
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public void findAndHandleRecipe() {
        this.lastFailedMatches = null;
        if (!this.recipeDirty && this.lastRecipe != null && gtlcore$checkLastRecipe(this.lastRecipe)) {
            GTRecipe recipe = this.lastRecipe;
            this.lastRecipe = null;
            this.lastOriginRecipe = null;
            this.setupRecipe(recipe);
        } else {
            this.lastRecipe = null;
            if (this.isLock && lockRecipe != null) {
                this.lastOriginRecipe = lockRecipe;
                GTRecipe modified = machine.fullModifyRecipe(lastOriginRecipe.copy(), this.ocParams, this.ocResult);
                if (modified != null && this.gtlcore$checkLastRecipe(modified)) {
                    setupRecipe(modified);
                }
            } else {
                this.lastOriginRecipe = null;
                this.handleSearchingRecipes(this.searchRecipe());
            }
        }
        this.recipeDirty = false;
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public boolean checkMatchedRecipeAvailable(GTRecipe match) {
        GTRecipe modified = this.machine.fullModifyRecipe(match.copy(), this.ocParams, this.ocResult);
        if (modified != null) {
            if (gtlcore$checkLastRecipe(modified)) {
                this.setupRecipe(modified);
            }
            if (this.lastRecipe != null && this.getStatus() == RecipeLogic.Status.WORKING) {
                this.lastOriginRecipe = match;
                this.lastFailedMatches = null;
                if (this.isLock) this.lockRecipe = match;
                return true;
            }
        }
        return false;
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
            RecipeRunnerHelper.handleRecipeOutput(this.machine, this.lastRecipe);
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
            if (!this.recipeDirty && gtlcore$checkLastRecipe(this.lastRecipe)) {
                this.setupRecipe(this.lastRecipe);
            } else {
                this.setStatus(RecipeLogic.Status.IDLE);
                this.progress = 0;
                this.duration = 0;
                this.isActive = false;
            }
        }
    }

    @Unique
    private boolean gtlcore$checkLastRecipe(GTRecipe lastRecipe) {
        return RecipeRunnerHelper.matchRecipe(this.machine, lastRecipe) &&
                lastRecipe.matchTickRecipe(this.machine).isSuccess() &&
                lastRecipe.checkConditions(this.machine.getRecipeLogic()).isSuccess();
    }
}
