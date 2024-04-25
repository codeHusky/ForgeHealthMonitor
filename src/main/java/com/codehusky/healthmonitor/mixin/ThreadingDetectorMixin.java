/*
Sourcecode originally sourced from booky10's StackDeobfuscator, located at https://github.com/booky10/StackDeobfuscator
Code under LGPL v3.0 - https://github.com/booky10/StackDeobfuscator/blob/master/COPYING
 */
package com.codehusky.healthmonitor.mixin;
// Created by booky10 in StackDeobfuscator (18:50 20.03.23)

import com.codehusky.healthmonitor.HealthMonitor;
import net.minecraft.util.ThreadingDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ThreadingDetector.class)
public class ThreadingDetectorMixin {

    @Redirect(
            method = "stackTrace",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Thread;getStackTrace()[Ljava/lang/StackTraceElement;"
            )
    )
    private static StackTraceElement[] redirStackTrace(Thread thread) {
        StackTraceElement[] stackTrace = thread.getStackTrace();
        HealthMonitor.remap(stackTrace);
        return stackTrace;
    }
}
