package com.codehusky.healthmonitor.mixin;

import com.codehusky.healthmonitor.stackdeobf.util.MixinUtils;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkCache.class)
public class MixinServerChunkCache {

    // From PaperMC
    @Inject(method = "getChunkAtIfLoadedImmediately", at = @At("HEAD"))
    private void getChunkAtIfLoadedImmediatelyHead(int x, int z, CallbackInfo ci){
        MixinUtils.warnUnsafeChunk("getting a faraway chunk (ServerChunkCache).class", x, z);
    }
}
