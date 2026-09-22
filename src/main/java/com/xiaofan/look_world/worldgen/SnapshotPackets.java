package com.xiaofan.look_world.worldgen;

import com.xiaofan.look_world.Look_world;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.fluid.Fluid;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.tick.ChunkTickScheduler;

import java.util.Set;

/**
 * 把快照组装成"半成品区块"的原版包 —— 也就是方案甲。
 *
 * 原理：客户端收到 ChunkDataS2CPacket 后，原版会直接
 *   ClientChunkManager.loadChunkFromPacket(...)
 * 把区块建出来并渲染；它**只检查坐标在不在视距内**，不检查区块是哪个阶段的。
 * 所以我们只要拼一个合法的包，客户端就会把半成品画出来，等真正的 FULL 区块到达时
 * 会自动覆盖 —— 交接免费完成。
 *
 * 这里只依赖共同类（World / WorldChunk / ChunkSection），所以放在 main（公共代码）里，
 * 单机时客户端可以直接调用，不用经过网络。
 *
 * 注意：这个包不带光照数据、光照引擎里也没有这些区块的记录，所以方块默认是黑的。
 * 全亮（LightmapTextureManagerMixin）是这条路线的必要组成部分，不是可选装饰。
 */
public final class SnapshotPackets {

    /** 原版 FULL 区块会带的几种高度图，伪造区块也得给上，否则客户端拿不到地表高度。 */
    private static final Set<Heightmap.Type> HEIGHTMAPS = Set.of(Heightmap.Type.values());

    private SnapshotPackets() {
    }

    /** 用快照 + 世界（客户端世界或集成服务器世界都行）拼一个包。 */
    public static ChunkDataS2CPacket build(World world, StageSnapshot snapshot) {
        return build(world, snapshot, snapshot.sectionData.length);
    }

    /**
     * 只填 [0, sectionLimit) 这些段，其余留空。
     * 客户端每次收到包都是**整块替换**，所以这里必须是累加的（包含之前展示过的段），
     * 否则后一帧会把前一帧的方块擦掉。段号 0 是最底层，所以 limit 递增就是“从下往上长”。
     */
    public static ChunkDataS2CPacket build(World world, StageSnapshot snapshot, int sectionLimit) {
        Registry<Biome> biomeRegistry = world.getRegistryManager().get(RegistryKeys.BIOME);
        ChunkSection[] sections = deserializeSections(biomeRegistry, snapshot, sectionLimit);

        WorldChunk fake = new WorldChunk(
                world,
                snapshot.pos,
                UpgradeData.NO_UPGRADE_DATA,
                new ChunkTickScheduler<Block>(),
                new ChunkTickScheduler<Fluid>(),
                0L,
                sections,
                null,
                null);
        Heightmap.populateHeightmaps(fake, HEIGHTMAPS);

        // null 的 BitSet 表示"没有光照数据"，原版构造器自己会兜住（ThreadedAnvilChunkStorage 也这么传）
        return new ChunkDataS2CPacket(fake, world.getLightingProvider(), null, null);
    }

    /** 把录制时序列化的段数据还原成真正的 ChunkSection（这一步需要注册表，所以放在消费侧做）。 */
    public static ChunkSection[] deserializeSections(Registry<Biome> biomeRegistry,
                                                     StageSnapshot snapshot) {
        return deserializeSections(biomeRegistry, snapshot, snapshot.sectionData.length);
    }

    public static ChunkSection[] deserializeSections(Registry<Biome> biomeRegistry,
                                                     StageSnapshot snapshot, int sectionLimit) {
        ChunkSection[] sections = new ChunkSection[snapshot.sectionData.length];
        int limit = Math.min(sectionLimit, snapshot.sectionData.length);
        for (int i = 0; i < limit; i++) {
            byte[] data = snapshot.sectionData[i];
            if (data == null) {
                continue;
            }
            ChunkSection section = new ChunkSection(biomeRegistry);
            section.readDataPacket(new PacketByteBuf(Unpooled.wrappedBuffer(data)));
            sections[i] = section;
        }
        return sections;
    }

    /** 包体大小，用于日志。 */
    public static int measure(ChunkDataS2CPacket packet) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            packet.write(buf);
            return buf.readableBytes();
        } finally {
            buf.release();
        }
    }

    /** 自检：把"拼包 + 序列化"整条链路跑一遍，不需要客户端。 */
    public static void selfTest(World world, StageSnapshot snapshot) {
        try {
            ChunkDataS2CPacket packet = build(world, snapshot);
            int size = measure(packet);
            ChunkPos pos = snapshot.pos;
            Look_world.LOGGER.info("[look_world] 自检通过：chunk({}, {}) {} -> 包体 {} 字节",
                    pos.x, pos.z, snapshot.stage.cn, size);
        } catch (Throwable t) {
            Look_world.LOGGER.error("[look_world] 自检失败：拼包/序列化抛异常", t);
        }
    }
}
