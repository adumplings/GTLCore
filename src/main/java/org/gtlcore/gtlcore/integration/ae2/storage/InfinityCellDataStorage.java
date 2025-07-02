package org.gtlcore.gtlcore.integration.ae2.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

public class InfinityCellDataStorage {

    public static final InfinityCellDataStorage EMPTY = new InfinityCellDataStorage();

    public ListTag stackKeys;
    public ListTag amounts;
    public double totalAmount;

    public InfinityCellDataStorage() {
        stackKeys = new ListTag();
        amounts = new ListTag();
        totalAmount = 0;
    }

    public InfinityCellDataStorage(ListTag stackKeys, ListTag amounts, double totalAmount) {
        this.stackKeys = stackKeys;
        this.amounts = amounts;
        this.totalAmount = totalAmount;
    }

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.put("keys", stackKeys);
        nbt.put("amounts", amounts);
        if (totalAmount != 0) {
            nbt.putDouble("totalAmount", totalAmount);
        }
        return nbt;
    }

    public static InfinityCellDataStorage fromNbt(CompoundTag nbt) {
        double totalAmount = 0;
        ListTag stackKeys = nbt.getList("keys", 10);
        ListTag amounts = nbt.getList("amounts", 8);
        if (nbt.contains("totalAmount")) {
            totalAmount = nbt.getDouble("totalAmount");
        }
        return new InfinityCellDataStorage(stackKeys, amounts, totalAmount);
    }
}
