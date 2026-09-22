package com.xiaofan.look_world.mixin.client;

import com.xiaofan.look_world.PreviewConfig;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 强制全亮。
 *
 * 这不仅是"好看"——半成品区块压根没有光照数据（我们伪造的包不带 LightData），
 * 不强制全亮的话，那些方块会渲染成一片黑。
 *
 * LightmapTextureManager.update(float) 内部会对天空光和方块光各调一次 getBrightness，
 * 直接把它的返回值钉成 1.0，整张光照贴图就是纯白的。
 *
 * 注意：getBrightness 在原版里是 **static** 方法，所以这个 handler 也必须是 static
 * （否则 mixin 会报 "non-static callback method ... targets a static method"）。
 */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {

    @Inject(method = "getBrightness", at = @At("HEAD"), cancellable = true)
    private static void lookWorld$fullBright(DimensionType dimensionType, int lightLevel,
                                             CallbackInfoReturnable<Float> cir) {
        if (PreviewConfig.fullBright) {
            cir.setReturnValue(1.0F);
        }
    }
}
