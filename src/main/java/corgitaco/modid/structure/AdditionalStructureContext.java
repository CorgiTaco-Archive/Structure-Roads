package corgitaco.modid.structure;

import net.minecraft.nbt.CompoundNBT;

public class AdditionalStructureContext {

    private final String name;
    private int tier;


    public AdditionalStructureContext(String name) {
        this(name, 0);
    }

    public AdditionalStructureContext(String name, int tier) {
        this.name = name;
        this.tier = tier;
    }

    public String getName() {
        return name;
    }

    public static AdditionalStructureContext read(CompoundNBT readNBT) {
        return new AdditionalStructureContext(readNBT.getString("name"), readNBT.getInt("tier"));
    }

    public CompoundNBT write() {
        CompoundNBT compoundNBT = new CompoundNBT();
        compoundNBT.putString("name", this.name);
        compoundNBT.putInt("tier", this.tier);
        return compoundNBT;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }
}
