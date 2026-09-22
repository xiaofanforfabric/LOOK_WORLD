package com.xiaofan.look_world.mixin;

import com.xiaofan.look_world.worldgen.GenStage;
import com.xiaofan.look_world.worldgen.SnapshotRecorder;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把"结构放置"从 FEATURES 里单独拆出来。
 *
 * 原版 generateFeatures 是按 GenerationStep.Feature 循环的，每一步里先放该步的结构、
 * 再放该步的装饰物，中间没有"逐子阶段"的方法可以挂，所以直接挂结构自己的放置入口：
 * StructureStart.place(...) 每个结构调用一次，放在这里录一帧，就能看到
 * "先出现村庄/遗迹，再长出树和花"。
 */
@Mixin(StructureStart.class)
public abstract class StructureStartMixin {

    @Inject(
            method = "place(Lnet/minecraft/world/StructureWorldAccess;"
                    + "Lnet/minecraft/world/gen/StructureAccessor;"
                    + "Lnet/minecraft/world/gen/chunk/ChunkGenerator;"
                    + "Lnet/minecraft/util/math/random/Random;"
                    + "Lnet/minecraft/util/math/BlockBox;"
                    + "Lnet/minecraft/util/math/ChunkPos;)V",
            at = @At("RETURN"))
    private void lookWorld$afterStructurePlaced(StructureWorldAccess world,
                                                StructureAccessor structureAccessor,
                                                ChunkGenerator generator, Random random,
                                                BlockBox box, ChunkPos pos, CallbackInfo ci) {
        try {
            // ChunkRegion.getChunk 只是从它自己那份正在生成的区块列表里取，不会阻塞
            Chunk chunk = world.getChunk(pos.x, pos.z);
            SnapshotRecorder.onStageFinished(chunk, GenStage.STRUCTURES);
        } catch (Throwable ignored) {
            // 拿不到区块就当这帧没录，绝不影响生成
        }
    }
}
