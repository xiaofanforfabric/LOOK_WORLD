package com.xiaofan.look_world.mixin.client;

import com.xiaofan.look_world.PreviewConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 跳过"正在下载地形"界面（就是那个 Loading terrain 的画面）。
 *
 * 原版这个界面有 MIN_LOAD_TIME_MS 的最短显示时间，还要等玩家脚下的区块渲染就绪
 * （对旁观者有特判，但不保险）。我们直接把它关掉：世界一到位就进去。
 *
 * 配套的是服务端那边的 MinecraftServerMixin（跳过 441 个区块的出生点预处理），
 * 两个一起才叫"跳过加载动画"。
 */
@Mixin(DownloadingTerrainScreen.class)
public abstract class DownloadingTerrainScreenMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void lookWorld$skipLoadingTerrain(CallbackInfo ci) {
        if (!PreviewConfig.skipLoadingScreen) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            // 世界/玩家还没到位，这一 tick 先按原版走
            return;
        }
        client.setScreen(null);
        ci.cancel();
    }
}
