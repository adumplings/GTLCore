package org.gtlcore.gtlcore.mixin.gtm.fix;

import org.gtlcore.gtlcore.api.machine.trait.ICheckPatternMachine;
import org.gtlcore.gtlcore.api.machine.trait.IDistinctMachine;
import org.gtlcore.gtlcore.api.machine.trait.ILockRecipe;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeStatus;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.api.recipe.RecipeText;
import org.gtlcore.gtlcore.utils.NumberUtils;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.OverclockFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.ResearchStationMachine;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import lombok.Getter;
import lombok.Setter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(WorkableElectricMultiblockMachine.class)
public abstract class WorkableElectricMultiblockMachineMixin extends WorkableMultiblockMachine implements IFancyUIMachine, IRecipeStatus, ICheckPatternMachine {

    @Getter
    @Setter
    private RecipeResult recipeStatus;

    public WorkableElectricMultiblockMachineMixin(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Shadow(remap = false)
    protected EnergyContainerList energyContainer;

    @Shadow(remap = false)
    public abstract EnergyContainerList getEnergyContainer();

    /**
     * @author mod_author
     * @reason fix
     */
    @Overwrite(remap = false)
    public long getOverclockVoltage() {
        if (this.energyContainer == null) {
            this.energyContainer = this.getEnergyContainer();
        }
        long voltage;
        long amperage;
        if (energyContainer.getInputVoltage() > energyContainer.getOutputVoltage()) {
            voltage = energyContainer.getInputVoltage();
            amperage = energyContainer.getInputAmperage();
        } else {
            voltage = energyContainer.getOutputVoltage();
            amperage = energyContainer.getOutputAmperage();
        }

        if (amperage == 1) {
            // amperage is 1 when the energy is not exactly on a tier
            // the voltage for recipe search is always on tier, so take the closest lower tier
            if (voltage > Integer.MAX_VALUE) return NumberUtils.getVoltageFromFakeTier(NumberUtils.getFakeVoltageTier(voltage));
            return GTValues.V[GTUtil.getFloorTierByVoltage(voltage)];
        } else {
            // amperage != 1 means the voltage is exactly on a tier
            // ignore amperage, since only the voltage is relevant for recipe search
            // amps are never > 3 in an EnergyContainerList
            return voltage;
        }
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        configuratorPanel.attachConfigurators(new IFancyConfiguratorButton.Toggle(
                GuiTextures.BUTTON_POWER.getSubTexture(0, 0, 1, 0.5),
                GuiTextures.BUTTON_POWER.getSubTexture(0, 0.5, 1, 0.5),
                this::isWorkingEnabled, (clickData, pressed) -> this.setWorkingEnabled(pressed))
                .setTooltipsSupplier(pressed -> List.of(
                        Component.translatable(pressed ? "behaviour.soft_hammer.enabled" : "behaviour.soft_hammer.disabled"))));
        if (this instanceof IOverclockMachine overclockMachine) {
            configuratorPanel.attachConfigurators(new OverclockFancyConfigurator(overclockMachine));
        }
        if (this.self() instanceof ResearchStationMachine) return;
        IDistinctMachine.attachConfigurators(configuratorPanel, (WorkableElectricMultiblockMachine) self());
        ILockRecipe.attachRecipeLockable(configuratorPanel, this.getRecipeLogic());
        ICheckPatternMachine.attachConfigurators(configuratorPanel, self());
    }

    @Inject(method = "addDisplayText", at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/api/machine/multiblock/WorkableElectricMultiblockMachine;getDefinition()Lcom/gregtechceu/gtceu/api/machine/MultiblockMachineDefinition;"), remap = false)
    public void addDisplayText(List<Component> textList, CallbackInfo ci) {
        if (this.isFormed()) {
            if (this.recipeStatus != null && this.recipeStatus.reason() != null) {
                textList.add(this.recipeStatus.reason().copy().withStyle(ChatFormatting.RED));
            }
        }
        if (this.getRecipeLogic() instanceof ILockRecipe iLockRecipe) {
            if (iLockRecipe.isLock() && iLockRecipe.getLockRecipe() != null) {
                textList.add(Component.translatable("gui.gtlcore.recipe_lock.recipe")
                        .withStyle((style -> style.withHoverEvent((new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                RecipeText.getRecipeInputText(iLockRecipe.getLockRecipe())
                                        .append(RecipeText.getRecipeOutputText(iLockRecipe.getLockRecipe()))))))));
            } else {
                textList.add(Component.translatable("gui.gtlcore.recipe_lock.no_recipe"));
            }
        }
    }

    @Override
    public boolean hasButton() {
        return true;
    }
}
