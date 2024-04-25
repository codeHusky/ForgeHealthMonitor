/*
Sourcecode originally sourced from booky10's StackDeobfuscator, located at https://github.com/booky10/StackDeobfuscator
Code under LGPL v3.0 - https://github.com/booky10/StackDeobfuscator/blob/master/COPYING
 */
package com.codehusky.healthmonitor.stackdeobf.mappings;
// Created by booky10 in StackDeobfuscator (18:03 20.03.23)

/**
 * Internal wrapper class.
 */
public class RemappedThrowable extends Throwable {

    private final Throwable original;
    private final String className;

    public RemappedThrowable(String message, Throwable cause,
                             Throwable original, String className) {
        super(message, cause);
        this.original = original;
        this.className = className;
    }

    public Throwable getOriginal() {
        return this.original;
    }

    public String getClassName() {
        return this.className;
    }

    @Override
    public String getMessage() {
        String message = super.getMessage();
        if (message == null) {
            return this.className;
        }
        return this.className + ": " + message;
    }

    @Override
    public String getLocalizedMessage() {
        String message = super.getLocalizedMessage();
        if (message == null) {
            return this.className;
        }
        return this.className + ": " + message;
    }

    @Override
    public String toString() {
        return this.getLocalizedMessage();
    }
}
