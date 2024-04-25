package com.codehusky.healthmonitor;

import com.codehusky.healthmonitor.stackdeobf.mappings.providers.AbstractMappingProvider;
import com.codehusky.healthmonitor.stackdeobf.mappings.providers.FMLMappingProvider;
import com.codehusky.healthmonitor.stackdeobf.util.RemappingRewritePolicy;
import com.codehusky.healthmonitor.stackdeobf.util.VersionData;
import com.mojang.logging.LogUtils;
import com.codehusky.healthmonitor.stackdeobf.mappings.CachedMappings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.registries.NewRegistryEvent;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;

import java.nio.file.Path;

@Mod(HealthMonitor.MODID)
public class HealthMonitor
{
    public static final String MODID = "healthmonitor";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static HealthMonitor instance;
    private HealthMonitorThread healthMonitorThread;

    private static VersionData VERSION_DATA;
    private static CachedMappings mappings;
    public static AbstractMappingProvider mappingProvider;

    private static boolean serverInit = false;
    private static boolean serverRunning = false;

    public static boolean isServerInit() {
        return serverInit;
    }

    public static boolean isServerRunning() {
        return serverRunning;
    }

    public static VersionData getVersionData() {
        if(VERSION_DATA == null) VERSION_DATA = VersionData.fromClasspath();
        return VERSION_DATA;
    }

    public static Throwable remap(Throwable throwable) {
        if (mappings != null) {
            return mappings.remapThrowable(throwable);
        }
        return throwable;
    }

    public static void remap(StackTraceElement[] elements) {
        if (mappings != null) {
            mappings.remapStackTrace(elements);
        }else{
            getLogger().error("Failed to remap stacktrace due to no mappings found");
        }
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    public HealthMonitor() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        instance = this;
    }

    public static HealthMonitor getInstance() {
        return instance;
    }

    private void commonSetup(final NewRegistryEvent event) {
        HealthMonitor.getLogger().info("Initializing stacktrace remapping");
        Path cacheDir = Path.of("./stackdeobf_mappings");
        mappingProvider = new FMLMappingProvider(getVersionData(), FMLLoader.getDist() == Dist.CLIENT ? "client": "server");

        // don't need to print errors, already done after loading
        CachedMappings.create(cacheDir, mappingProvider).thenAccept(mappings -> {
            HealthMonitor.mappings = mappings;


            HealthMonitor.getLogger().info("Injecting into root logger...");

            RemappingRewritePolicy policy = new RemappingRewritePolicy(
                    mappings, true);
            policy.inject((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger());

        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        serverInit = true;
        HealthMonitor.getLogger().info("Starting monitoring on Thread \"" + Thread.currentThread().getName() + "\"");
        healthMonitorThread = new HealthMonitorThread(Thread.currentThread());
        healthMonitorThread.start();
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event){
        serverRunning = true;
        HealthMonitor.getLogger().info("HealthMonitor is now active.");
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event){
        healthMonitorThread.updateLastTick();
    }



}
