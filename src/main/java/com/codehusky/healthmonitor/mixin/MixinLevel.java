package com.codehusky.healthmonitor.mixin;

import com.codehusky.healthmonitor.stackdeobf.util.MixinUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class MixinLevel {
    @Inject(method = "getChunk (IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;", at = @At("HEAD"))
    private void getChunk(int p_46502_, int p_46503_, ChunkStatus p_46504_, boolean p_46505_, CallbackInfoReturnable<ChunkAccess> ci){
        MixinUtils.warnUnsafeChunk("getting a faraway chunk (Level.class)", p_46502_, p_46503_);
    }
}
