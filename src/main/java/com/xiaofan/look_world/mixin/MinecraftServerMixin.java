package com.xiaofan.look_world.mixin;

import com.xiaofan.look_world.Look_world;
import com.xiaofan.look_world.PreviewConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 跳过原版的"出生点预处理"，这是"跳过加载动画"的关键。
 *
 * 原版 MinecraftServer.prepareStartRegion 会：
 *   1. 给出生点区块挂一个 ChunkTicketType.START、半径 11 的 ticket（23x23 = 441 个区块）
 *   2. 死等 getTotalChunksLoadedCount() == 441
 *   3. 期间不停打印 "Preparing spawn area: N%"
 * 这正是新建世界要等 40 秒的原因 —— 你会发现它甚至**在世界生成完之后**才开始，
 * 而我们要的是反过来：人在世界里，看着区块一层层长出来。
 *
 * 安全性：loading 这个标志只在 runServer() 里读写，不在这里，所以整段跳过是安全的。
 * 区块改由玩家自己的 ticket 按需生成（视距内，也就是玩家真正看得见的那部分）。
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Inject(method = "prepareStartRegion", at = @At("HEAD"), cancellable = true)
    private void lookWorld$skipSpawnPrep(WorldGenerationProgressListener listener, CallbackInfo ci) {
        if (!PreviewConfig.skipSpawnPrep) {
            return;
        }
        MinecraftServer server = (MinecraftServer) (Object) this;
        // 保持进度监听器的生命周期平衡（原版也是 start(...) 开头、stop() 结尾），
        // 否则客户端的加载界面可能收不到结束信号。
        BlockPos spawnPos = server.getOverworld().getSpawnPos();
        listener.start(new ChunkPos(spawnPos));
        listener.stop();
        Look_world.LOGGER.info("[look_world] 已跳过出生点预处理（原版会预生成 441 个区块），"
                + "区块改为按需生成");
        ci.cancel();
    }
}
