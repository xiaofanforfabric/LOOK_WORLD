package com.xiaofan.look_world.worldgen;

import com.xiaofan.look_world.Look_world;
import com.xiaofan.look_world.PreviewConfig;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.block.BlockState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.ReadableContainer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 阶段录制器。
 *
 * 纪律（照抄世界生成线程的规矩）：
 *  1. 只在阶段边界调用，且只读本区块的数据；
 *  2. 读出来立刻序列化成 byte[]（不可变快照）；
 *  3. 不在生成线程里碰渲染器 / 客户端状态 / 网络 / 注册表；
 *  4. 所有异常都吞掉，绝不让预览逻辑把世界生成搞崩。
 */
public final class SnapshotRecorder {

    /**
     * 快照仓库：按 (区块, 阶段) 存**槽位**，而不是排队。
     *
     * 为什么用槽位：
     *  1) 内存只和预览面积有关（面积 × 6 个阶段），不会因为玩家暂停久了无限堆积；
     *  2) 播放时可以自由决定顺序 —— 现在是"按阶段，再由中心向外一圈圈扫"，
     *     所以能把半径开大也不会乱；
     *  3) 同一阶段重复录制（比如 carve 会调两次）只保留最后一次。
     */
    private static final Map<Long, StageSnapshot[]> SLOTS = new ConcurrentHashMap<>();

    private static final AtomicLong RECORDED = new AtomicLong();
    private static final Map<GenStage, AtomicLong> PER_STAGE = new ConcurrentHashMap<>();

    /** 出错时只打印前几次，避免刷屏。 */
    private static final AtomicLong ERRORS = new AtomicLong();

    /** 最近一次快照，自检用。 */
    private static volatile StageSnapshot last;

    private SnapshotRecorder() {
    }

    /**
     * 阶段结束时被 mixin 调用。可能运行在任意 worker 线程。
     */
    public static void onStageFinished(Chunk chunk, GenStage stage) {
        if (!PreviewConfig.enabled || chunk == null) {
            return;
        }
        try {
            if (!PreviewConfig.looksLikeOverworld(chunk.getBottomY())) {
                return;
            }
            ChunkPos pos = chunk.getPos();
            if (!PreviewConfig.inRange(pos.x, pos.z)) {
                return;
            }

            byte[][] sectionData = captureSections(chunk);
            int bytes = 0;
            for (byte[] data : sectionData) {
                if (data != null) {
                    bytes += data.length;
                }
            }

            StageSnapshot snapshot = new StageSnapshot(pos, stage, sectionData, bytes);
            last = snapshot;
            SLOTS.computeIfAbsent(ChunkPos.toLong(pos.x, pos.z),
                    key -> new StageSnapshot[GenStage.values().length])[stage.ordinal()] = snapshot;
            RECORDED.incrementAndGet();
            PER_STAGE.computeIfAbsent(stage, s -> new AtomicLong()).incrementAndGet();
        } catch (Throwable t) {
            if (ERRORS.incrementAndGet() <= 5) {
                Look_world.LOGGER.error("[look_world] 录制阶段快照失败: {}", stage, t);
            }
        }
    }

    /** 播放侧取数：某个区块的某个阶段有没有录到。 */
    public static StageSnapshot get(int chunkX, int chunkZ, GenStage stage) {
        StageSnapshot[] arr = SLOTS.get(ChunkPos.toLong(chunkX, chunkZ));
        return arr == null ? null : arr[stage.ordinal()];
    }

    /** 已经录到数据的区块数（用于 HUD）。 */
    public static int recordedChunkCount() {
        return SLOTS.size();
    }


    /**
     * 把区块段序列化成 byte[]（原版格式）。空段记为 null。
     *
     * ⚠ 这里是踩过坑的地方：
     * ChunkSection.toPacket() 内部会给 PalettedContainer **加锁**（LockHelper 是个
     * 不可重入的信号量）。而世界生成期间这把锁正被生成线程（也就是我们所在的线程）
     * 持有，所以直接对原段调 toPacket 会当场自锁死，整个区块生成线程池停摆，
     * 客户端就永远卡在"加载地形"。
     *
     * 正确做法：先用 copy() 复制出独立的段（copy() 不碰锁），再对**副本**序列化
     * —— 副本有自己全新的锁，随便加。
     */
    private static byte[][] captureSections(Chunk chunk) {
        ChunkSection[] src = chunk.getSectionArray();
        byte[][] out = new byte[src.length][];
        for (int i = 0; i < src.length; i++) {
            ChunkSection section = src[i];
            if (section == null || section.isEmpty()) {
                continue;
            }
            ChunkSection copy = copySection(section);
            if (copy == null) {
                continue;
            }
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            try {
                copy.toPacket(buf);
                out[i] = ByteBufUtil.getBytes(buf);
            } finally {
                buf.release();
            }
        }
        return out;
    }

    /** 无锁复制一个区块段。 */
    @SuppressWarnings("unchecked")
    private static ChunkSection copySection(ChunkSection section) {
        ReadableContainer<RegistryEntry<Biome>> biomes = section.getBiomeContainer();
        if (!(biomes instanceof PalettedContainer)) {
            // 实际上不会发生（区块段里的生物群系容器就是 PalettedContainer）。
            // 真发生了宁可少录一段，也绝不去碰原容器的锁。
            return null;
        }
        PalettedContainer<BlockState> blocks = section.getBlockStateContainer().copy();
        PalettedContainer<RegistryEntry<Biome>> biomesCopy =
                ((PalettedContainer<RegistryEntry<Biome>>) biomes).copy();
        return new ChunkSection(blocks, biomesCopy);
    }

    public static void logSummary() {
        Look_world.LOGGER.info("[look_world] 累计录制 {} 个快照，覆盖 {} 个区块",
                RECORDED.get(), SLOTS.size());
        PER_STAGE.forEach((stage, n) -> Look_world.LOGGER.info("[look_world]   {}: {}", stage.cn, n.get()));
    }

    public static long recorded() {
        return RECORDED.get();
    }

    public static StageSnapshot lastSnapshot() {
        return last;
    }

    /** 清空仓库（换世界时用）。 */
    public static void clear() {
        SLOTS.clear();
    }
}
