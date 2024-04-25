/*
Sourcecode originally sourced from booky10's StackDeobfuscator, located at https://github.com/booky10/StackDeobfuscator
Code under LGPL v3.0 - https://github.com/booky10/StackDeobfuscator/blob/master/COPYING
 */
package com.codehusky.healthmonitor.mixin;
// Created by booky10 in StackDeobfuscator (18:35 20.03.23)

import com.codehusky.healthmonitor.HealthMonitor;
import com.codehusky.healthmonitor.stackdeobf.mappings.RemappedThrowable;
import net.minecraft.CrashReport;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrashReport.class)
public class CrashReportMixin {

    @Mutable
    @Shadow
    @Final
    private Throwable exception;

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    public void postInit(String title, Throwable throwable, CallbackInfo ci) {
        this.exception = HealthMonitor.remap(throwable);
    }

    @Inject(
            method = "getException",
            at = @At("HEAD"),
            cancellable = true
    )
    public void preExceptionGet(CallbackInfoReturnable<Throwable> cir) {
        // redirect calls to getException to the original, unmapped Throwable
        //
        // this method is called in the ReportedException, which
        // caused the "RemappedThrowable" name to show up in the logger

        if (this.exception instanceof RemappedThrowable remapped) {
            cir.setReturnValue(remapped.getOriginal());
        }
    }
}
