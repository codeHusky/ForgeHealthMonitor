/*
Sourcecode originally sourced from booky10's StackDeobfuscator, located at https://github.com/booky10/StackDeobfuscator
Code under LGPL v3.0 - https://github.com/booky10/StackDeobfuscator/blob/master/COPYING
 */
package com.codehusky.healthmonitor.stackdeobf.util;

import com.codehusky.healthmonitor.HealthMonitor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MixinUtils {
    // Based on getFirstPluginCaller from PaperMC
    @Nullable
    public static IModInfo getFirstModCaller() {
        List<IModInfo> allMods = ModList.get().getMods();

        Optional<IModInfo> foundFrame = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(stream -> stream
                        .map((frame) -> {
                            Class<?> declaringClass = frame.getDeclaringClass();
                            IModInfo modInfo = null;
                            if(declaringClass.isAnnotationPresent(Mod.class)){
                                Mod annotation = declaringClass.getAnnotation(Mod.class);
                                modInfo = allMods.stream().filter(mI -> mI.getModId().equalsIgnoreCase(annotation.value())).findFirst().orElse(null);
                            }
                            return modInfo;
                        })
                        .filter(Objects::nonNull)
                        .findFirst());

        return foundFrame.orElse(null);
    }
    // From PaperMC
    public static void warnUnsafeChunk(String reason, int x, int z) {
        // if any chunk coord is outside of 30 million blocks
        if (x > 1875000 || z > 1875000 || x < -1875000 || z < -1875000) {
            IModInfo modInfo = MixinUtils.getFirstModCaller();
            if (modInfo != null) {
                HealthMonitor.getLogger().warn("Mod %s is %s at (%s, %s), this might cause issues.".formatted(modInfo.getModId(), reason, x, z));
            }
//            if (net.minecraft.server.MinecraftServer.getServer().isDebugging()) {
//                io.papermc.paper.util.TraceUtil.dumpTraceForThread("Dangerous chunk retrieval");
//            }
        }
    }
}
