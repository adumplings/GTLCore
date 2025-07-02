package org.gtlcore.gtlcore.mixin.gtm.api.machine;

import org.gtlcore.gtlcore.api.machine.trait.IDistinctMachine;
import org.gtlcore.gtlcore.api.machine.trait.IMEPartMachine;
import org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDistinctPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputHatchPartMachine;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.resources.ResourceLocation;

import com.hepdd.gtmthings.common.block.machine.multiblock.part.appeng.MEOutputPartMachine;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import lombok.Setter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Mixin(WorkableMultiblockMachine.class)
public abstract class WorkableMultiblockMachineMixin extends MultiblockControllerMachine implements IDistinctMachine, IMEPartMachine {

    @Persisted
    @Getter
    @Setter
    private boolean MEOutPutBus = false;
    @Persisted
    @Getter
    @Setter
    private boolean MEOutPutHatch = false;
    @Persisted
    @Getter
    @Setter
    private boolean MEOutPutDual = false;
    @Persisted
    @DescSynced
    @Getter
    @Setter
    public boolean isDistinct = false;
    @Getter
    @Setter
    private RecipeHandlePart distinctHatch;
    @Getter
    @Setter
    private ResourceLocation recipeId;
    @Getter
    private List<RecipeHandlePart> recipeHandleParts = new ObjectArrayList<>();
    @Getter
    protected Map<IO, List<RecipeHandlePart>> capabilities = new EnumMap<>(IO.class);
    @Getter
    protected Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> capabilitiesFlat = new EnumMap<>(IO.class);

    public WorkableMultiblockMachineMixin(IMachineBlockEntity holder) {
        super(holder);
    }

    @Inject(method = "onStructureFormed", at = @At("TAIL"), remap = false)
    public void onStructureFormed(CallbackInfo ci) {
        this.upDate();
    }

    @Inject(method = "onStructureInvalid", at = @At("TAIL"), remap = false)
    public void onStructureInvalid(CallbackInfo ci) {
        MEOutPutBus = false;
        MEOutPutHatch = false;
        MEOutPutDual = false;
        this.capabilities.clear();
        this.capabilitiesFlat.clear();
        this.recipeHandleParts.clear();
    }

    @Inject(method = "onPartUnload", at = @At("TAIL"), remap = false)
    public void onPartUnload(CallbackInfo ci) {
        MEOutPutBus = false;
        MEOutPutHatch = false;
        MEOutPutDual = false;
        this.capabilities.clear();
        this.capabilitiesFlat.clear();
        this.recipeHandleParts.clear();
    }

    @Override
    public boolean isRecipeOutput(GTRecipe recipe) {
        if (MEOutPutDual) return true;
        else if (MEOutPutBus || recipe.getOutputContents(ItemRecipeCapability.CAP).isEmpty()) {
            return MEOutPutHatch || recipe.getOutputContents(FluidRecipeCapability.CAP).isEmpty();
        }
        return false;
    }

    public void upDate() {
        capabilities.clear();
        capabilitiesFlat.clear();
        recipeHandleParts.clear();
        distinctHatch = null;
        recipeId = null;
        var distinctParts = new ObjectArrayList<IRecipeHandler<?>>();
        for (IMultiPart part : this.getParts()) {
            if (part instanceof FluidHatchPartMachine || part instanceof IDistinctPart) {
                if (part instanceof MEOutputPartMachine) {
                    MEOutPutBus = true;
                    MEOutPutHatch = true;
                    MEOutPutDual = true;
                } else if (part instanceof MEOutputBusPartMachine) {
                    MEOutPutBus = true;
                } else if (part instanceof MEOutputHatchPartMachine) {
                    MEOutPutHatch = true;
                }
                List<IRecipeHandler<?>> hatch = new ObjectArrayList<>();
                boolean isOutput = false;
                for (var v : part.getRecipeHandlers()) {
                    if (!v.isProxy()) {
                        if (v.getHandlerIO() == IO.IN) hatch.add(v);
                        else if (v.getHandlerIO() == IO.OUT) {
                            isOutput = true;
                            hatch.add(v);
                        }
                    }
                }
                if (isDistinct) {
                    recipeHandleParts.add(RecipeHandlePart.of(isOutput ? IO.OUT : IO.IN, hatch));
                } else {
                    if (part instanceof IDistinctPart distinctPart && distinctPart.isDistinct()) {
                        recipeHandleParts.add(RecipeHandlePart.of(IO.IN, hatch));
                    } else {
                        if (isOutput) recipeHandleParts.add(RecipeHandlePart.of(IO.OUT, hatch));
                        else distinctParts.addAll(hatch);
                    }
                }
            }
        }
        if (!distinctParts.isEmpty()) recipeHandleParts.add(RecipeHandlePart.of(IO.IN, distinctParts));
        for (var recipeHandle : getRecipeHandleParts()) {
            this.addHandlerList(recipeHandle);
        }
    }
}
