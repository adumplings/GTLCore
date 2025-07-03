package org.gtlcore.gtlcore.api.recipe.ingredient;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.Getter;

public class ItemIngredientMap {

    @Getter
    private Object2LongOpenHashMap<Ingredient> ingredientMap;
    private Object2ObjectOpenHashMap<Item, Ingredient> itemStackMap;

    public ItemIngredientMap() {
        ingredientMap = new Object2LongOpenHashMap<>();
        itemStackMap = new Object2ObjectOpenHashMap<>();
    }

    public long addTo(Ingredient ingredient, long count) {
        for (ItemStack stack : ingredient.getItems()) {
            Ingredient existingIngredient = itemStackMap.get(stack.getItem());
            if (existingIngredient != null) {
                return ingredientMap.addTo(existingIngredient, count);
            } else {
                ingredientMap.put(ingredient, count);
                itemStackMap.put(stack.getItem(), ingredient);
            }
        }
        return 0;
    }

    public void clear() {
        ingredientMap.clear();
        itemStackMap.clear();
    }
}
