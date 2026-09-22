package com.xiaofan.look_world.worldgen;

import net.minecraft.util.math.ChunkPos;

/**
 * 一个区块在某个生成阶段结束时的快照。
 *
 * 段数据用原版 ChunkSection.toPacket 的格式存成 byte[]，空段为 null。
 * 这样一石三鸟：
 *   1) 立刻拿到深拷贝，之后随便在哪个线程读都安全；
 *   2) 正好就是发包需要的格式，客户端 readDataPacket 能直接吃；
 *   3) 录制时不需要碰 Biome 注册表（反序列化放在主线程做）。
 */
public final class StageSnapshot {

    public final ChunkPos pos;
    public final GenStage stage;
    /** 索引 = 段号（0 是最底层），null 表示该段为空。 */
    public final byte[][] sectionData;
    public final int payloadBytes;

    public StageSnapshot(ChunkPos pos, GenStage stage, byte[][] sectionData, int payloadBytes) {
        this.pos = pos;
        this.stage = stage;
        this.sectionData = sectionData;
        this.payloadBytes = payloadBytes;
    }

    public int nonEmptySections() {
        int n = 0;
        for (byte[] data : sectionData) {
            if (data != null) {
                n++;
            }
        }
        return n;
    }

    @Override
    public String toString() {
        return "[" + stage.cn + "] chunk(" + pos.x + "," + pos.z + ") 非空段="
                + nonEmptySections() + " 数据=" + payloadBytes + "B";
    }
}
