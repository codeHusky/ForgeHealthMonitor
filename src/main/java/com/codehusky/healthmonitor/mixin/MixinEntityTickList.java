package com.codehusky.healthmonitor.mixin;

import com.codehusky.healthmonitor.HealthMonitor;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Mixin(EntityTickList.class)
public class MixinEntityTickList {
    @Shadow public Int2ObjectMap<Entity> active;
    @Shadow public Int2ObjectMap<Entity> passive;
    @Shadow public Int2ObjectMap<Entity> iterated;
    @Inject(method = "forEach", at=@At("HEAD"))
    public void forEach(Consumer<Entity> entityConsumer, CallbackInfo callbackInfo) {
        // check entity list lengths
        if(active.size() >= 8000){
            boolean announce = false;
            ObjectIterator<Entity> iterator = active.values().iterator();
            Map<String, Integer> masses = new HashMap<>();
            Map<String, Integer> purging = new HashMap<>();
            Set<Entity> toKill = new HashSet<>();
            while(iterator.hasNext()){
                Entity entity = iterator.next();
                if(entity == null){
                    HealthMonitor.getLogger().error("Entity null??");
                    continue;
                }
                int countOfType = masses.compute(entity.getType().toString(),(key, old) -> {
                    if(old == null) return 1;
                    return old + 1;
                });
                if(countOfType > 1500){
                    if(!announce){
                        HealthMonitor.getLogger().warn("Found " + active.size() + " entities with excessive duplicates, purging excess entities");
                        announce = true;
                    }
                    HealthMonitor.getLogger().debug("Marking " + entity.getType() + " @ "
                            + entity.getBlockX() + " " + entity.getBlockY() + " " + entity.getBlockZ() +
                            " (" + entity.level() + ") due to count=" + countOfType
                    );
                    purging.compute(entity.getType().toString(),(key, old) -> {
                        if(old == null) return 1;
                        return old + 1;
                    });
                    toKill.add(entity);
                }
            }
            if(!toKill.isEmpty()) {
                HealthMonitor.getLogger().info("Entity purge stats:");
                purging.forEach((id, count) -> {
                    HealthMonitor.getLogger().info("\t" + id + ": " + count);
                });
                HealthMonitor.getLogger().info("Purging " + toKill.size() + " entities...");
                toKill.forEach(e -> e.remove(Entity.RemovalReason.DISCARDED));
                HealthMonitor.getLogger().info("Purge complete");
            }
        }

    }
}
