package com.xiaofan.look_world.worldgen;

import com.xiaofan.look_world.Look_world;
import com.xiaofan.look_world.PreviewConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

/**
 * 客户端 → 服务端的唯一一个包：请求把"真实区块"重发一遍。
 *
 * 为什么非要有它：虚空模式会把服务端发来的区块数据丢掉，但服务端**不会主动重发**
 * 已经发过的区块（它记着"这个玩家我已经发过了"）。所以只把开关关掉是没用的，
 * 新数据永远不会再来 —— 玩家看到的就是"开了关了都一样"。
 * 关掉虚空模式时客户端发这个请求，服务端把预览区之外的、已加载的真实区块重发一遍。
 */
public final class PreviewResend {

    public static final Identifier CHANNEL = new Identifier("look_world", "resend_real_chunks");

    private PreviewResend() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) ->
                        server.execute(() -> resend(server, player)));
    }

    /** 服务端主线程执行：把玩家周围已加载的真实区块重发一遍（预览区跳过，留给预览）。 */
    private static void resend(MinecraftServer server, ServerPlayerEntity player) {
        try {
            ServerWorld world = player.getServerWorld();
            ChunkPos center = player.getChunkPos();
            int radius = Math.min(server.getPlayerManager().getViewDistance(), 10);
            int sent = 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int cx = center.x + dx;
                    int cz = center.z + dz;
                    if (PreviewConfig.inRange(cx, cz)) {
                        continue;
                    }
                    // create = false：只取已经加载好的，不会阻塞主线程
                    Chunk chunk = world.getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (!(chunk instanceof WorldChunk worldChunk)) {
                        continue;
                    }
                    player.networkHandler.sendPacket(new ChunkDataS2CPacket(
                            worldChunk, world.getLightingProvider(), null, null));
                    sent++;
                }
            }
            Look_world.LOGGER.info("[look_world] 已重发 {} 个真实区块给 {}",
                    sent, player.getName().getString());
        } catch (Throwable t) {
            Look_world.LOGGER.error("[look_world] 重发真实区块失败", t);
        }
    }
}
