package com.codehusky.healthmonitor;

import org.slf4j.Logger;

import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HealthMonitorThread extends Thread {
    private static final long WARN_AFTER_NANOS = TimeUnit.MILLISECONDS.toNanos(200L);
    private static final long WARN_SLEEP_MILLIS = 2000L;
    private static final long KILL_TIMEOUT_MILLIS = 600000L;
    private static final DecimalFormat LOG_FORMAT = new DecimalFormat("#,##0.00");
    public final Thread serverThread;
    private long lastWarnTick;
    private long lastTick;
    private static final String PERIOD_PATTERN = Pattern.quote(".");
    private static final long NETTY_THREAD_CACHE_LENGTH = 10000L;
    private static long nettyThreadCachedTimestamp = -1L;
    private static Set<Thread> nettyThreads = null;

    public void updateLastTick() {
        this.lastTick = System.currentTimeMillis();
    }

    HealthMonitorThread(Thread serverThread) {
        super("HealthMonitor");
        this.serverThread = serverThread;
    }

    public void run() {
        HealthMonitor.getLogger().info("HealthMonitor is now monitoring the main thread.");

        while(this.serverThread.isAlive()) {
            // only check every 100ms
            try {
                Thread.sleep(100L);
            } catch (Throwable e) {
                e.printStackTrace();
            }


            if (this.lastWarnTick != this.lastTick) { // if we're on a tick we haven't warned on yet
                long timeSinceTick = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis() - this.lastTick);
                if (timeSinceTick >= WARN_AFTER_NANOS) { // if tick is taking too long
                    this.lastWarnTick = this.lastTick;
                    this.warn(HealthMonitor.getLogger(), timeSinceTick); // print tick time waerning

                    try {
                        Thread.sleep(WARN_SLEEP_MILLIS);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            } else { // if we have warned on this tick
                HealthMonitor.getLogger().error("Main thread is overloaded!");
                if (this.lastTick > 0L) { // only print stack if the server has started
                    this.warn(HealthMonitor.getLogger(), TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis() - this.lastTick));
                }
                // Wait two seconds between warning checks
                try {
                    Thread.sleep(WARN_SLEEP_MILLIS);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

        HealthMonitor.getLogger().info("Main thread has shut down. Verifying safe shutdown conditions...");
        try {
            Thread.sleep(2000);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        boolean shouldWait = false;
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        Thread currentThread = Thread.currentThread();
        for(Thread thread : threadSet) {
            if (!thread.equals(currentThread) && !thread.isDaemon() && !thread.getName().equalsIgnoreCase("DestroyJavaVM")) {
                if(!shouldWait){
                    HealthMonitor.getLogger().warn("!!! Detected threads that could cause deadlocks !!!");
                    shouldWait = true;
                }
                HealthMonitor.getLogger().warn("\tNon-daemon thread is still active: " + thread.getName() + " (" + thread.getState().name() + ") [" + thread.getThreadGroup().getName() + "]");

            }
        }
        if(shouldWait){
            HealthMonitor.getLogger().info("Allowing 10 seconds for non-daemon threads to shut themselves down...");
            try {
                Thread.sleep(10_000);
            }catch (Throwable e){
                e.printStackTrace();
            }
            boolean hasFailed = false;
            Set<Thread> threadsToKill = new HashSet<>();
            for(Thread thread : threadSet){
                if(!thread.equals(currentThread) && !thread.isDaemon() && !thread.getName().equalsIgnoreCase("DestroyJavaVM")){
                    if(!hasFailed){
                        HealthMonitor.getLogger().error("HealthMonitor has identified threads that may cause shutdown to hang. These threads will be killed to ensure the server does not freeze indefinitely.");
                        hasFailed = true;
                    }
                    HealthMonitor.getLogger().error("\tIdentified deadlock suspect! Thread Name: " + thread.getName() + " State: " + thread.getState().name() + " // Group: " + thread.getThreadGroup().getName() + " // Daemon: " + thread.isDaemon());
                    StackTraceElement[] trace = thread.getStackTrace();
                    for(StackTraceElement element : trace) {
                        HealthMonitor.getLogger().error("\t\t" + element.toString());
                    }

                    threadsToKill.add(thread);
                }
            }


            for(Thread thread : threadsToKill){
                HealthMonitor.getLogger().warn("!!! Killing deadlock suspect thread (" + thread.getName() + ") !!!");
                thread.interrupt();
            }

            if(hasFailed){
                HealthMonitor.getLogger().error(
                        "HealthMonitor has forced your server to shutdown. Please investigate the threads your server was hanging on to avoid data loss.\n" +
                        "\tA useful way to diagnose this is attaching a profiler to your server, such as VisualVM, and profiling CPU usage during \"normal\" use.\n" +
                        "\tStop your profile, save it, then shut down the server. Cross-reference what ran on the threads the server reports in that shutdown to\n" +
                        "\twhat you can see running in your profile, and report the relevant information to a developer."
                );
                System.exit(1);
                return;
            }
        }
        HealthMonitor.getLogger().info("The server has shut down gracefully. Thanks for using HealthMonitor!\n\tHealthMonitor is FREE, OPEN-SOURCE SOFTWARE.\n\tSource code can be found at https://github.com/codeHusky/ForgeHealthMonitor");
        System.exit(0);
    }

    private void warn(Logger logger, long timeSinceTick) {
        logger.error("------------------------------");
        timeSinceTick = TimeUnit.NANOSECONDS.toMillis(timeSinceTick);
        logger.error(
                "Server thread has not completed a tick in "
                        + timeSinceTick
                        + "ms ("
                        + LOG_FORMAT.format((double)timeSinceTick / 1000.0)
                        + "s)."
        );

        logger.error("Stack:");
        StackTraceElement[] elements = this.serverThread.getStackTrace();
        HealthMonitor.remap(elements);
        boolean foundSleepThread = printStackTrace(logger, elements);

        if (foundSleepThread) {
            logger.error(" ");
            logger.error("Server hung on packet handling - printing all Netty threads");
            logger.error(" ");
            this.dumpNettyStacks(logger);
        }

        logger.error("------------------------------");
        if (timeSinceTick >= KILL_TIMEOUT_MILLIS) {
            logger.error("Thread has not completed a tick for " + timeSinceTick + "ms - dumping thread info and then doing what we can to kill the process...");
            Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
            Thread currentThread = Thread.currentThread();
            for(Thread thread : threadSet){

                if(!thread.equals(currentThread)){
                    logger.warn("\tThread: " + thread.getName() + " // Group: " + thread.getThreadGroup().getName() + " // State: " + thread.getState().name() + " // Daemon: " + thread.isDaemon());
                    StackTraceElement[] trace = thread.getStackTrace();
                    for(StackTraceElement element : trace) {
                        logger.warn("\t\t" + element.toString());
                    }
                    if(!thread.isDaemon()) {
                        logger.error("[[ Killing non-daemon thread to try to avoid infinite deadlock - " + thread.getName() + " ]]");
                        thread.interrupt();
                    }
                }
            }
            logger.error("Your server has been killed due to a hard freeze. You should look through your logs to see which mods locked up the server's threads");
            System.exit(1);
        }
    }

    private boolean printStackTrace(Logger logger, StackTraceElement[] elements){


        boolean foundSleepThread = false;

        for(int i = 0; i < elements.length; ++i) {
            StackTraceElement element = elements[i];
            if (i == 0) {
                try {
                    String[] classNameSplit = element.getClassName().split(PERIOD_PATTERN);
                    if (classNameSplit[classNameSplit.length - 1].equals("Thread") && element.getMethodName().equals("sleep")) {
                        foundSleepThread = true;
                    }
                } catch (Throwable var9) {
                }
            }

            logger.error("\t" + element);
        }
        return foundSleepThread;
    }

    private void dumpNettyStacks(Logger logger) {
        if (nettyThreads == null || System.currentTimeMillis() - nettyThreadCachedTimestamp >= NETTY_THREAD_CACHE_LENGTH) {
            nettyThreadCachedTimestamp = System.currentTimeMillis();
            nettyThreads = Thread.getAllStackTraces().keySet().stream().filter(e -> e.getName().contains("Netty")).collect(Collectors.toSet());
        }

        nettyThreads.forEach(thread -> {
            StackTraceElement[] trace = thread.getStackTrace();
            HealthMonitor.remap(trace);
            logger.error("Thread: " + thread.getName() + " [" + trace.length + "]");

            for(StackTraceElement element : trace) {
                logger.error(element.toString());
            }
        });
    }

}

