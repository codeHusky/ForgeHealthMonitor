/*
Sourcecode originally sourced from booky10's StackDeobfuscator, located at https://github.com/booky10/StackDeobfuscator
Code under LGPL v3.0 - https://github.com/booky10/StackDeobfuscator/blob/master/COPYING
 */
package com.codehusky.healthmonitor.mixin;
// Created by booky10 in StackDeobfuscator (18:46 20.03.23)

import com.codehusky.healthmonitor.HealthMonitor;
import net.minecraft.CrashReportCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrashReportCategory.class)
public class CrashReportCategoryMixin {

    @Shadow
    private StackTraceElement[] stackTrace;

    @Inject(
            method = "fillInStackTrace",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/System;arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V",
                    shift = At.Shift.AFTER
            )
    )
    public void postStackTraceFill(int i, CallbackInfoReturnable<Integer> cir) {
        HealthMonitor.remap(this.stackTrace);
    }
}
