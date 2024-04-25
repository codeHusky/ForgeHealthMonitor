/*
Sourcecode originally sourced from booky10's StackDeobfuscator, located at https://github.com/booky10/StackDeobfuscator
Code under LGPL v3.0 - https://github.com/booky10/StackDeobfuscator/blob/master/COPYING
 */
package com.codehusky.healthmonitor.stackdeobf.http;
// Created by booky10 in StackDeobfuscator (18:16 29.03.23)

import org.jetbrains.annotations.ApiStatus;

public class FailedHttpRequestException extends RuntimeException {

    @ApiStatus.Internal
    public FailedHttpRequestException(String message) {
        super(message);
    }
}
