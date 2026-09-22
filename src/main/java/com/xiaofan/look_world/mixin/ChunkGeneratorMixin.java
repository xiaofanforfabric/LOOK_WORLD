package com.xiaofan.look_world.mixin;

import com.xiaofan.look_world.worldgen.GenStage;
import com.xiaofan.look_world.worldgen.SnapshotRecorder;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 抓 CARVERS / FEATURES 两个阶段的快照。
 * 这两个阶段修改的是同一个 ProtoChunk，所以只能靠快照区分先后。
 *
 * 注意：carve 在 ChunkGenerator 里是 abstract，注入不进去（mixin 会报
 * "Scanned 0 target(s)"），真正的实现在 NoiseChunkGenerator 上，见那个 mixin。
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    /** FEATURES：树、矿、湖、结构。NoiseChunkGenerator 没有覆写这个方法，所以挂在这里正好。 */
    @Inject(
            method = "generateFeatures(Lnet/minecraft/world/StructureWorldAccess;"
                    + "Lnet/minecraft/world/chunk/Chunk;"
                    + "Lnet/minecraft/world/gen/StructureAccessor;)V",
            at = @At("RETURN"))
    private void lookWorld$afterFeatures(StructureWorldAccess world, Chunk chunk,
                                         StructureAccessor structureAccessor, CallbackInfo ci) {
        SnapshotRecorder.onStageFinished(chunk, GenStage.FEATURES);
    }
}
