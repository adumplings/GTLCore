package org.gtlcore.gtlcore.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 部分代码参考自gto
 * &#064;line <a href="https://github.com/GregTech-Odyssey/GTOCore">...</a>
 */

public interface IDistinctMachine {

    @NotNull
    Map<IO, List<RecipeHandlePart>> getCapabilities();

    @NotNull
    Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> getCapabilitiesFlat();

    @NotNull
    default List<IRecipeHandler<?>> getCapabilitiesFlat(IO io, RecipeCapability<?> cap) {
        return getCapabilitiesFlat()
                .getOrDefault(io, Collections.emptyMap())
                .getOrDefault(cap, Collections.emptyList());
    }

    default void addHandlerList(RecipeHandlePart handler) {
        if (handler == RecipeHandlePart.NO_DATA) return;
        IO io = handler.getHandlerIO();
        getCapabilities().computeIfAbsent(io, i -> new ArrayList<>()).add(handler);
        var entrySet = handler.getHandlerMap().entrySet();
        var inner = getCapabilitiesFlat().computeIfAbsent(io, i -> new Reference2ObjectOpenHashMap<>(entrySet.size()));
        for (var entry : entrySet) {
            var entryList = entry.getValue();
            inner.computeIfAbsent(entry.getKey(), c -> new ArrayList<>(entryList.size())).addAll(entryList);
        }
    }

    List<RecipeHandlePart> getRecipeHandleParts();

    RecipeHandlePart getDistinctHatch();

    void setDistinctHatch(RecipeHandlePart hatch);

    ResourceLocation getRecipeId();

    void setRecipeId(ResourceLocation recipeId);

    void upDate();

    boolean isDistinct();

    void setDistinct(boolean isDistinct);

    static void attachConfigurators(ConfiguratorPanel configuratorPanel, WorkableElectricMultiblockMachine machine) {
        if (machine instanceof IDistinctMachine distinctMachine) {
            configuratorPanel.attachConfigurators(new IFancyConfiguratorButton.Toggle(
                    GuiTextures.BUTTON_DISTINCT_BUSES.getSubTexture(0, 0.5, 1, 0.5),
                    GuiTextures.BUTTON_DISTINCT_BUSES.getSubTexture(0, 0, 1, 0.5),
                    distinctMachine::isDistinct, (clickData, pressed) -> {
                        distinctMachine.setDistinct(pressed);
                        distinctMachine.upDate();
                    })
                    .setTooltipsSupplier(pressed -> List.of(Component.translatable("gtceu.multiblock.universal.distinct.all").setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
                            .append(Component.translatable(pressed ? "gtceu.multiblock.universal.distinct.yes" : "gtceu.multiblock.universal.distinct.no")))));
        }
    }
}
