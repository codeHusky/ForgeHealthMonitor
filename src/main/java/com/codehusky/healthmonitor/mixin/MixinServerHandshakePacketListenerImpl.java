package com.codehusky.healthmonitor.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetAddress;
import java.util.HashMap;

@Mixin(ServerHandshakePacketListenerImpl.class)
public class MixinServerHandshakePacketListenerImpl {

    @Shadow public MinecraftServer server;
    @Shadow public Connection connection;
    private static final HashMap<InetAddress, Long> throttleTracker = new HashMap<InetAddress, Long>();
    private static int throttleCounter = 0;
    private static Component goAway = Component.literal("Connection throttled!").withStyle(ChatFormatting.RED);

    // From PaperMC
    @Inject(method="handleIntention", at=@At("HEAD"), cancellable = true)
    public void handleIntention(ClientIntentionPacket packet, CallbackInfo callbackInfo) {
        if(packet.getIntention() == ConnectionProtocol.LOGIN){
            try {
                if (!(this.connection.channel().localAddress() instanceof io.netty.channel.unix.DomainSocketAddress)) { // Paper - the connection throttle is useless when you have a Unix domain socket
                    long currentTime = System.currentTimeMillis();
                    long connectionThrottle = 5000;
                    InetAddress address = ((java.net.InetSocketAddress) this.connection.getRemoteAddress()).getAddress();

                    synchronized (MixinServerHandshakePacketListenerImpl.throttleTracker) {
                        if (MixinServerHandshakePacketListenerImpl.throttleTracker.containsKey(address) && !"127.0.0.1".equals(address.getHostAddress()) && currentTime - MixinServerHandshakePacketListenerImpl.throttleTracker.get(address) < connectionThrottle) {
                            MixinServerHandshakePacketListenerImpl.throttleTracker.put(address, currentTime);
                            this.connection.send(new ClientboundLoginDisconnectPacket(MixinServerHandshakePacketListenerImpl.goAway));
                            this.connection.disconnect(MixinServerHandshakePacketListenerImpl.goAway);
                            callbackInfo.cancel();
                            return;
                        }

                        MixinServerHandshakePacketListenerImpl.throttleTracker.put(address, currentTime);
                        MixinServerHandshakePacketListenerImpl.throttleCounter++;
                        if (MixinServerHandshakePacketListenerImpl.throttleCounter > 200) {
                            MixinServerHandshakePacketListenerImpl.throttleCounter = 0;

                            // Cleanup stale entries
                            java.util.Iterator iter = MixinServerHandshakePacketListenerImpl.throttleTracker.entrySet().iterator();
                            while (iter.hasNext()) {
                                java.util.Map.Entry<InetAddress, Long> entry = (java.util.Map.Entry) iter.next();
                                if ((currentTime - entry.getValue()) > connectionThrottle) {
                                    iter.remove();
                                }
                            }
                        }
                    }
                } // Paper - add closing bracket for if check above
            } catch (Throwable t) {
                org.apache.logging.log4j.LogManager.getLogger().debug("Failed to check connection throttle", t);
            }
        }
    }
}
