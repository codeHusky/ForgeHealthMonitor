package com.codehusky.healthmonitor.mixin;

import com.codehusky.healthmonitor.HealthMonitor;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * Implement checks to prevent movement into unloaded chunks via methods used by PaperMC
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {
    @Shadow public Connection connection;
    @Shadow public MinecraftServer server;
    @Shadow public ServerPlayer player;
    private static double clampHorizontal(double p_143610_) {
        return Mth.clamp(p_143610_, -3.0E7, 3.0E7);
    }

    private static double clampVertical(double p_143654_) {
        return Mth.clamp(p_143654_, -2.0E7, 2.0E7);
    }

    @Nullable
    public LevelChunk getChunkAtIfLoadedImmediately(ServerLevel serverLevel, int x, int z) {
        LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(x, z);
        if(chunk == null || chunk.isEmpty()){
            return null;
        }
        return chunk;
    }
    public final boolean areChunksLoadedForMove(ServerLevel serverLevel, AABB axisalignedbb) {
        // copied code from collision methods, so that we can guarantee that they wont load chunks (we don't override
        // ICollisionAccess methods for VoxelShapes)
        // be more strict too, add a block (dumb plugins in move events?)
        int minBlockX = Mth.floor(axisalignedbb.minX - 1.0E-7D) - 3;
        int maxBlockX = Mth.floor(axisalignedbb.maxX + 1.0E-7D) + 3;

        int minBlockZ = Mth.floor(axisalignedbb.minZ - 1.0E-7D) - 3;
        int maxBlockZ = Mth.floor(axisalignedbb.maxZ + 1.0E-7D) + 3;

        int minChunkX = minBlockX >> 4;
        int maxChunkX = maxBlockX >> 4;

        int minChunkZ = minBlockZ >> 4;
        int maxChunkZ = maxBlockZ >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                if (this.getChunkAtIfLoadedImmediately(serverLevel, cx, cz) == null) {
                    return false;
                }
            }
        }

        return true;
    }

    @Inject(method = "handleMoveVehicle", at = @At(value = "HEAD"), cancellable = true)
    public void checkChunkLoadedVehicle(ServerboundMoveVehiclePacket packet, CallbackInfo cir) {
        ServerGamePacketListenerImpl mixinThis = (ServerGamePacketListenerImpl)(Object)this;
        PacketUtils.ensureRunningOnSameThread(packet, mixinThis, this.player.serverLevel());

        double toX = MixinServerGamePacketListenerImpl.clampHorizontal(packet.getX());
        double toY = MixinServerGamePacketListenerImpl.clampVertical(packet.getY());
        double toZ = MixinServerGamePacketListenerImpl.clampHorizontal(packet.getZ());
        Entity entity = this.player.getRootVehicle();
        if (
                !this.areChunksLoadedForMove(player.serverLevel(), this.player.getBoundingBox().expandTowards(new Vec3(toX, toY, toZ).subtract(this.player.position()))) ||
                        !this.areChunksLoadedForMove(player.serverLevel(), entity.getBoundingBox().expandTowards(new Vec3(toX, toY, toZ).subtract(entity.position())))
        ) {
            HealthMonitor.getLogger().debug("Blocking move into unloaded chunk by " + entity.getClass().getName());
            this.connection.send(new ClientboundMoveVehiclePacket(entity));
            cir.cancel();
        }

    }

    @Shadow
    public abstract void teleport(double p_9775_, double p_9776_, double p_9777_, float p_9778_, float p_9779_);

    @Inject(method = "handleMovePlayer", at = @At(value = "HEAD"), cancellable = true)
    public void checkChunkLoadedPlayer(ServerboundMovePlayerPacket packet, CallbackInfo cir) {
        ServerGamePacketListenerImpl mixinThis = (ServerGamePacketListenerImpl)(Object)this;
        PacketUtils.ensureRunningOnSameThread(packet, mixinThis, this.player.serverLevel());

        double toX = MixinServerGamePacketListenerImpl.clampHorizontal(packet.getX(this.player.getX()));
        double toY = MixinServerGamePacketListenerImpl.clampVertical(packet.getY(this.player.getY()));
        double toZ = MixinServerGamePacketListenerImpl.clampHorizontal(packet.getZ(this.player.getZ()));

        if ( (this.player.getX() != toX || this.player.getZ() != toZ) && !this.areChunksLoadedForMove(this.player.serverLevel(), this.player.getBoundingBox().expandTowards(new Vec3(toX, toY, toZ).subtract(this.player.position())))) {
            HealthMonitor.getLogger().debug("Blocking move into unloaded chunk by Player(" + this.player.getName() + ")");
            this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), this.player.getYRot(), this.player.getXRot());
            cir.cancel();
        }

    }
}
