package com.xiaofan.look_world.mixin;

import com.xiaofan.look_world.worldgen.GenStage;
import com.xiaofan.look_world.worldgen.SnapshotRecorder;
import net.minecraft.registry.Registry;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 挂在地形生成器上，抓 BIOMES / NOISE / SURFACE 三个阶段结束时的快照。
 * 都注入在同步实现方法上（而不是返回 CompletableFuture 的外层包装），
 * 保证注入点执行时该阶段真的做完了。
 */
@Mixin(NoiseChunkGenerator.class)
public abstract class NoiseChunkGeneratorMixin {

    /** BIOMES：只填生物群系。注意类里有两个 populateBiomes（公开的异步版和私有的同步版）。 */
    @Inject(
            method = "populateBiomes(Lnet/minecraft/world/gen/chunk/Blender;"
                    + "Lnet/minecraft/world/gen/noise/NoiseConfig;"
                    + "Lnet/minecraft/world/gen/StructureAccessor;"
                    + "Lnet/minecraft/world/chunk/Chunk;)V",
            at = @At("RETURN"))
    private void lookWorld$afterBiomes(Blender blender, NoiseConfig noiseConfig,
                                       StructureAccessor structureAccessor, Chunk chunk,
                                       CallbackInfo ci) {
        SnapshotRecorder.onStageFinished(chunk, GenStage.BIOMES);
    }

    /** NOISE：地形主体（石头/深板岩/水/空气）。同样有重载，写全描述符。 */
    @Inject(
            method = "populateNoise(Lnet/minecraft/world/gen/chunk/Blender;"
                    + "Lnet/minecraft/world/gen/StructureAccessor;"
                    + "Lnet/minecraft/world/gen/noise/NoiseConfig;"
                    + "Lnet/minecraft/world/chunk/Chunk;II)"
                    + "Lnet/minecraft/world/chunk/Chunk;",
            at = @At("RETURN"))
    private void lookWorld$afterNoise(Blender blender, StructureAccessor structureAccessor,
                                      NoiseConfig noiseConfig, Chunk chunk, int startY, int endY,
                                      CallbackInfoReturnable<Chunk> cir) {
        SnapshotRecorder.onStageFinished(chunk, GenStage.NOISE);
    }

    /** SURFACE：地表方块（草/沙/雪/水下表层）。 */
    @Inject(
            method = "buildSurface(Lnet/minecraft/world/chunk/Chunk;"
                    + "Lnet/minecraft/world/gen/HeightContext;"
                    + "Lnet/minecraft/world/gen/noise/NoiseConfig;"
                    + "Lnet/minecraft/world/gen/StructureAccessor;"
                    + "Lnet/minecraft/world/biome/source/BiomeAccess;"
                    + "Lnet/minecraft/registry/Registry;"
                    + "Lnet/minecraft/world/gen/chunk/Blender;)V",
            at = @At("RETURN"))
    private void lookWorld$afterSurface(Chunk chunk, HeightContext heightContext,
                                        NoiseConfig noiseConfig, StructureAccessor structureAccessor,
                                        BiomeAccess biomeAccess, Registry<Biome> biomeRegistry,
                                        Blender blender, CallbackInfo ci) {
        SnapshotRecorder.onStageFinished(chunk, GenStage.SURFACE);
    }

    /** CARVERS_AIR / CARVERS_LIQUID：CARVERS 阶段对每个 carver step 各调一次，正好可以分开录。 */
    @Inject(
            method = "carve(Lnet/minecraft/world/ChunkRegion;J"
                    + "Lnet/minecraft/world/gen/noise/NoiseConfig;"
                    + "Lnet/minecraft/world/biome/source/BiomeAccess;"
                    + "Lnet/minecraft/world/gen/StructureAccessor;"
                    + "Lnet/minecraft/world/chunk/Chunk;"
                    + "Lnet/minecraft/world/gen/GenerationStep$Carver;)V",
            at = @At("RETURN"))
    private void lookWorld$afterCarve(ChunkRegion region, long seed, NoiseConfig noiseConfig,
                                      BiomeAccess biomeAccess, StructureAccessor structureAccessor,
                                      Chunk chunk, GenerationStep.Carver carver, CallbackInfo ci) {
        SnapshotRecorder.onStageFinished(chunk,
                carver == GenerationStep.Carver.AIR ? GenStage.CARVERS_AIR : GenStage.CARVERS_LIQUID);
    }
}
