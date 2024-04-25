/*
Sourcecode originally sourced from booky10's StackDeobfuscator, located at https://github.com/booky10/StackDeobfuscator
Code under LGPL v3.0 - https://github.com/booky10/StackDeobfuscator/blob/master/COPYING
 */
package com.codehusky.healthmonitor.stackdeobf.mappings;
// Created by booky10 in StackDeobfuscator (17:04 20.03.23)

import com.codehusky.healthmonitor.HealthMonitor;
import com.codehusky.healthmonitor.stackdeobf.mappings.providers.AbstractMappingProvider;
import com.codehusky.healthmonitor.stackdeobf.mappings.providers.FMLMappingProvider;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CachedMappings {


    // "CLASSES" name has package prefixed (separated by '.')
    private final Int2ObjectMap<String> classes = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
    private final Int2ObjectMap<String> methods = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
    private final Int2ObjectMap<String> fields = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

    private CachedMappings() {
    }

    public static CompletableFuture<CachedMappings> create(Path cacheDir, AbstractMappingProvider provider) {
        HealthMonitor.getLogger().info("Creating asynchronous mapping cache executor...");
        ExecutorService executor = Executors.newSingleThreadExecutor(
                new BasicThreadFactory.Builder().namingPattern("Mappings Cache Thread #%d").daemon(true).build());

        return create(cacheDir, provider, executor)
                // needs to be executed asynchronously, otherwise the
                // executor of the current thread would be shut down
                .whenCompleteAsync(($, throwable) -> {
                    HealthMonitor.getLogger().info("Shutting down asynchronous mapping cache executor...");
                    executor.shutdown();

                    if (throwable != null) {
                        HealthMonitor.getLogger().error("An error occurred while creating mappings cache", throwable);
                    }
                });
    }

    public static CompletableFuture<CachedMappings> create(Path cacheDir, AbstractMappingProvider provider, Executor executor) {
        long start = System.currentTimeMillis();
        CachedMappings mappings = new CachedMappings();

        // visitor expects mappings to be intermediary -> named
        MappingCacheVisitor visitor = new MappingCacheVisitor(mappings.classes, mappings.methods, mappings.fields, (provider instanceof FMLMappingProvider fmlMappingProvider)? fmlMappingProvider : null);
        return provider.cacheMappings(cacheDir, visitor, executor).thenApply($ -> {
            long timeDiff = System.currentTimeMillis() - start;
            HealthMonitor.getLogger().info("Cached mappings have been built (took {}ms)", timeDiff);

            HealthMonitor.getLogger().info("  Classes: " + mappings.classes.size());
            HealthMonitor.getLogger().info("  Methods: " + mappings.methods.size());
            HealthMonitor.getLogger().info("  Fields: " + mappings.fields.size());

            return mappings;
        });
    }

    public @Nullable String remapClass(int id) {
        return this.classes.get(id);
    }

    public @Nullable String remapMethod(int id) {
        return this.methods.get(id);
    }

    public @Nullable String remapField(int id) {
        return this.fields.get(id);
    }

    public String remapClasses(String string) {
        return RemappingUtil.remapClasses(this, string);
    }

    public String remapMethods(String string) {
        return RemappingUtil.remapMethods(this, string);
    }

    public String remapFields(String string) {
        return RemappingUtil.remapFields(this, string);
    }

    public String remapString(String string) {
        return RemappingUtil.remapString(this, string);
    }

    public Throwable remapThrowable(Throwable throwable) {
        return RemappingUtil.remapThrowable(this, throwable);
    }

    public void remapStackTrace(StackTraceElement[] elements) {
        RemappingUtil.remapStackTrace(this, elements);
    }

    public StackTraceElement remapStackTrace(StackTraceElement element) {
        return RemappingUtil.remapStackTrace(this, element);
    }
}
