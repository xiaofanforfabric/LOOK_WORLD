package com.xiaofan.look_world;

import com.xiaofan.look_world.worldgen.PreviewResend;
import com.xiaofan.look_world.worldgen.SnapshotPackets;
import com.xiaofan.look_world.worldgen.SnapshotRecorder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 说明：这里（src/main）是**公共代码**，不是"服务端专用"。
 *
 * 必须放在这一侧的原因：录制要挂在世界生成里，而 mixin 的目标类
 * ChunkGenerator / NoiseChunkGenerator 是公共类。单机时世界生成跑在集成服务器里，
 * 但和客户端是同一个 JVM，所以这边只做"取数据"，绝不碰任何显示逻辑。
 *
 * 所有显示相关的东西都在 src/client（PreviewInjector / LightmapTextureManagerMixin）。
 */
public class Look_world implements ModInitializer {

    public static final String MOD_ID = "look_world";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 预览中心只定一次。 */
    private static boolean centerInitialized;

    @Override
    public void onInitialize() {
        // 虚空模式开关用的请求通道（客户端 → 服务端：请把真实区块重发一遍）
        PreviewResend.register();

        // 预览中心默认放在出生点所在区块。
        //
        // 必须等到**世界第一个 tick** 才取：ServerWorldEvents.LOAD 触发时
        // MinecraftServer.setupSpawn 还没跑完，getSpawnPos() 还是默认的 (0,0)，
        // 而真正的出生点可能是几百格之外 —— 中心偏了，录制范围就整个偏了，
        // 玩家会觉得自己"站在预览区域边上"。
        // 到第一个 tick 时出生点已经是最终值，而且玩家还没进来、区块还没开始生成。
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (centerInitialized) {
                return;
            }
            centerInitialized = true;
            ServerWorld overworld = server.getOverworld();
            BlockPos spawn = overworld.getSpawnPos();
            PreviewConfig.setCenter(spawn.getX() >> 4, spawn.getZ() >> 4);
            LOGGER.info("[look_world] 预览中心 = 出生点区块 ({}, {})（出生点 {}），半径 {} 区块",
                    PreviewConfig.centerX, PreviewConfig.centerZ, spawn.toShortString(),
                    PreviewConfig.radius);
        });

        // 旁观模式：游戏模式必须由服务端说了算（单机里就是集成服务器），客户端改不了
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // 万一中心还没定（比如玩家是通过 /tp 之类的奇怪路径进来的），用玩家落点兜底
            if (!PreviewConfig.centerSet) {
                PreviewConfig.setCenter(handler.player.getChunkPos().x, handler.player.getChunkPos().z);
            }
            if (!PreviewConfig.autoSpectator) {
                return;
            }
            server.execute(() -> {
                handler.player.changeGameMode(GameMode.SPECTATOR);
                LOGGER.info("[look_world] {} 已切到旁观模式",
                        handler.player.getGameProfile().getName());
            });
        });

        // 无头自检（只在传了 -Dlookworld.selftest=true 时启用）：
        // 用来在没有客户端的环境里回归验证"录制不死锁 + 各阶段都录到 + 拼包合法"。
        // 用法：gradle runServer -Dlookworld.selftest=true
        if (Boolean.getBoolean("lookworld.selftest")) {
            // 自检时**保留**原版的出生点预处理：跳过它之后服务器没玩家就不会生成区块，
            // 而自检恰恰需要区块生成才能拿到快照。
            PreviewConfig.skipSpawnPrep = false;
            ServerTickEvents.END_SERVER_TICK.register(new ServerTickEvents.EndTick() {
                private int ticks;
                private boolean done;

                @Override
                public void onEndTick(MinecraftServer server) {
                    this.ticks++;
                    if (this.done || this.ticks < 100) {
                        return;
                    }
                    ServerWorld world = server.getWorld(ServerWorld.OVERWORLD);
                    if (world == null || SnapshotRecorder.lastSnapshot() == null) {
                        return;
                    }
                    this.done = true;
                    SnapshotPackets.selfTest(world, SnapshotRecorder.lastSnapshot());
                    SnapshotRecorder.logSummary();
                    server.stop(false);
                }
            });
        }
    }
}
