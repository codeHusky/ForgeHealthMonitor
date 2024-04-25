package com.codehusky.healthmonitor.mixin;

import com.codehusky.healthmonitor.HealthMonitor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemStack.class)
public abstract class MixinItemstack {

    @Shadow
    private CompoundTag tag;

    @Shadow
    private Item item;

    @Overwrite
    public CompoundTag getOrCreateTag(){
        if (this.tag == null) {
            if(HealthMonitor.isServerRunning() && item != null && item.toString().contains("torch")) HealthMonitor.getLogger().warn("Created Empty Compound Tag for " + item.toString(), new RuntimeException());
            this.setTag(new CompoundTag());
        }

        return this.tag;
    }

    @Overwrite
    public CompoundTag getOrCreateTagElement(String p_41699_) {
        if (this.tag != null && this.tag.contains(p_41699_, 10)) {
            return this.tag.getCompound(p_41699_);
        } else {
            if(HealthMonitor.isServerRunning() && item != null && item.toString().contains("torch")) HealthMonitor.getLogger().warn("Created Empty Compound Tag @ \"" + p_41699_ + "\" for " + item.toString(), new RuntimeException());
            CompoundTag compoundtag = new CompoundTag();
            this.addTagElement(p_41699_, compoundtag);
            return compoundtag;
        }
    }

    @Shadow
    private void setTag(CompoundTag tag){}

    @Shadow
    private void addTagElement(String p_41701_, Tag p_41702_){}
}
