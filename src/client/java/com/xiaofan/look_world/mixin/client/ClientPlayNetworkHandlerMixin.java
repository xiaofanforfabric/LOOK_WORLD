package com.xiaofan.look_world.mixin.client;

import com.xiaofan.look_world.PreviewConfig;
import com.xiaofan.look_world.client.PreviewInjector;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 虚空模式：把服务端发来的**真实区块数据**全部丢掉。
 *
 * 为什么必须要这个：原版区块一到 FULL 就会打包发给客户端，比我们逐阶段播放快得多，
 * 结果半成品刚播两帧就被完整地形盖掉，"一层一层长出来"根本留不住。
 * 把真包丢掉之后，视距内保持纯虚空，画面里只有我们注入的半成品 —— 干净得多。
 *
 * 代价：这些区块在客户端就真的不存在了（掉下去不会摔，因为玩家是旁观模式）。
 * 想恢复正常地形，关掉虚空模式后重新进一次世界（或飞出视距再飞回来）即可。
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onChunkData", at = @At("HEAD"), cancellable = true)
    private void lookWorld$voidRealChunks(ChunkDataS2CPacket packet, CallbackInfo ci) {
        // 注意不能无脑丢：我们自己的注入也是走这个方法进来的，
        // 否则会把播放器的包一起丢掉，结果整个世界一片虚空、什么都不长。
        if (PreviewConfig.voidRealChunks && !PreviewInjector.isInjectingHere()) {
            ci.cancel();
        }
    }
}
