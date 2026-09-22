package com.xiaofan.look_world.client;

import com.xiaofan.look_world.Look_world;
import com.xiaofan.look_world.PreviewConfig;
import com.xiaofan.look_world.worldgen.GenStage;
import com.xiaofan.look_world.worldgen.PreviewResend;
import com.xiaofan.look_world.worldgen.SnapshotPackets;
import com.xiaofan.look_world.worldgen.SnapshotRecorder;
import com.xiaofan.look_world.worldgen.StageSnapshot;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 客户端播放器：把录制好的槽位，按**阶段 → 由中心向外一圈 → 一层**的顺序喂给原版收包路径。
 *
 * 为什么能直接喂：单机里集成服务器和客户端在同一个 JVM，录制器写的是共享内存。
 * 我们构造一个 ChunkDataS2CPacket（内容是半成品区块）交给
 * ClientPlayNetworkHandler.onChunkData，后面全是原版流程：
 * loadChunkFromPacket → 建 WorldChunk → 渲染。不需要任何网络。
 *
 * 播放顺序的设计：
 *   阶段（石头 → 地表 → 洞穴 → 灌水 → 结构 → 装饰物）
 *   → 圈（切比雪夫距离 0..radius，从玩家/出生点向外推）
 *   → 层（每圈一起往上长 sectionsPerFrame 段）
 * 所以看上去是"一波一波从中心往外蔓延，每一波里再一层层长高"。
 */
public final class PreviewInjector {

    /** 播放顺序：按原版生成顺序；生物群系不产生方块，跳过。 */
    private static final GenStage[] STAGES = {
            GenStage.NOISE,
            GenStage.SURFACE,
            GenStage.CARVERS_AIR,
            GenStage.CARVERS_LIQUID,
            GenStage.STRUCTURES,
            GenStage.FEATURES,
    };

    /** 速度：每 tick 注入多少个区块包。 */
    private static final int[] PACKETS_PER_TICK = {1, 2, 4, 8, 16, 32, 64};
    private static int speedIndex = 3;

    private static volatile boolean paused = false;
    private static double accumulator;
    /** "单步"请求数，以"组"为单位（一个组 = 一个阶段的某一圈某一层）。 */
    private static int pendingGroups;

    /**
     * 正在注入我们自己的包的线程。虚空 mixin 靠这个标记放行。
     * 用线程而不是布尔量：真实区块包走网络线程，我们的注入走客户端主线程。
     */
    private static volatile Thread injectingThread;

    private static ClientWorld lastWorld;

    // ===== 播放游标 =====
    private static int stageIdx;
    private static int ring;
    private static int slice = 1;
    private static int[] ringChunks = new int[0];   // 当前圈的区块坐标（x,z 交替）
    private static int ringCursor;
    private static int sectionCount = 24;
    private static boolean finished;
    /** 组号，组变化时 +1（供"单步走完一整组"判断用）。 */
    private static long groupId;

    private static final AtomicLong INJECTED = new AtomicLong();
    private static final AtomicLong FAILED = new AtomicLong();

    private PreviewInjector() {
    }

    /** 给虚空 mixin 用：当前线程是不是正在注入我们自己的包。 */
    public static boolean isInjectingHere() {
        return injectingThread == Thread.currentThread();
    }

    public static void onClientTick(MinecraftClient client) {
        ClientWorld world = client.world;

        if (world == null) {
            if (lastWorld != null) {
                // 离开世界：把仓库清掉，免得下次进别的世界混进旧数据
                SnapshotRecorder.clear();
                lastWorld = null;
            }
            return;
        }

        if (world != lastWorld) {
            lastWorld = world;
            resetPlayback();
            if (PreviewConfig.autoPauseOnJoin) {
                paused = true;
                pendingGroups = 0;
                client.inGameHud.setTitle(Text.literal("预览已就绪"));
                client.inGameHud.setSubtitle(Text.literal("按 F7 开始逐层生成 · F6 打开面板"));
                client.inGameHud.setTitleTicks(5, 80, 20);
                Look_world.LOGGER.info("[look_world] 已进入世界，预览先暂停（按 F7 开始播放）");
            }
        }

        if (client.player == null || client.getNetworkHandler() == null) {
            return;
        }
        // 游戏被暂停（开着 ESC 菜单之类）时注入也没用：ClientWorld 不 tick，
        // 区块更新只会堆在队列里，等恢复才一起冲出来。
        if (client.currentScreen != null && client.currentScreen.shouldPause()) {
            return;
        }

        if (paused) {
            if (pendingGroups > 0) {
                pendingGroups--;
                runOneGroup(client, world);
            }
            return;
        }
        if (finished) {
            return;
        }

        accumulator += PACKETS_PER_TICK[speedIndex];
        int budget = (int) accumulator;
        if (budget <= 0) {
            return;
        }
        accumulator -= budget;
        for (int i = 0; i < budget; i++) {
            if (!advanceOnePacket(client, world)) {
                return;
            }
        }
    }

    /** 走完当前这一整组（一个阶段的某一圈某一层的所有区块）。 */
    private static void runOneGroup(MinecraftClient client, ClientWorld world) {
        if (finished) {
            return;
        }
        long id = groupId;
        int guard = 0;
        do {
            if (!advanceOnePacket(client, world)) {
                return;
            }
            guard++;
        } while (groupId == id && guard < 1024);
    }

    /** 注入一个区块包。返回 false 表示全部播完了。 */
    private static boolean advanceOnePacket(MinecraftClient client, ClientWorld world) {
        if (finished) {
            return false;
        }
        // 注意：ringChunks 是 int[]，每个区块占 2 个位置，所以区块数 = length / 2。
        // （这里之前拿区块序号和数组长度比较过，中心圈只有 1 块时会越界）
        int guard = 0;
        while (!finished && ringCursor >= ringChunks.length / 2 && guard++ < 64) {
            nextGroup();
        }
        if (finished) {
            return false;
        }

        int cx = ringChunks[ringCursor * 2];
        int cz = ringChunks[ringCursor * 2 + 1];
        StageSnapshot snapshot = SnapshotRecorder.get(cx, cz, STAGES[stageIdx]);
        if (snapshot != null) {
            injectSlice(client, world, snapshot, slice * PreviewConfig.sectionsPerFrame);
        }
        ringCursor++;
        return true;
    }

    /** 游标前进一个组：层 → 圈 → 阶段。 */
    private static void nextGroup() {
        slice++;
        if (slice > totalSlices()) {
            slice = 1;
            ring++;
            if (ring > PreviewConfig.radius) {
                ring = 0;
                stageIdx++;
                if (stageIdx >= STAGES.length) {
                    finished = true;
                    ringChunks = new int[0];
                    ringCursor = 0;
                    Look_world.LOGGER.info("[look_world] 预览播放完成");
                    return;
                }
            }
            buildRing();
        }
        ringCursor = 0;
        groupId++;
    }

    private static int totalSlices() {
        return Math.max(1, (int) Math.ceil(sectionCount / (double) PreviewConfig.sectionsPerFrame));
    }

    /** 把当前圈的区块坐标列出来（切比雪夫距离等于 ring 的那一圈）。 */
    private static void buildRing() {
        int d = ring;
        if (d <= 0) {
            ringChunks = new int[]{PreviewConfig.centerX, PreviewConfig.centerZ};
            return;
        }
        int[] out = new int[8 * d * 2];
        int i = 0;
        for (int x = -d; x <= d; x++) {
            out[i++] = PreviewConfig.centerX + x;
            out[i++] = PreviewConfig.centerZ - d;
            out[i++] = PreviewConfig.centerX + x;
            out[i++] = PreviewConfig.centerZ + d;
        }
        for (int z = -d + 1; z < d; z++) {
            out[i++] = PreviewConfig.centerX - d;
            out[i++] = PreviewConfig.centerZ + z;
            out[i++] = PreviewConfig.centerX + d;
            out[i++] = PreviewConfig.centerZ + z;
        }
        ringChunks = out;
    }

    private static void injectSlice(MinecraftClient client, ClientWorld world,
                                    StageSnapshot snapshot, int sectionLimit) {
        try {
            if (snapshot.sectionData.length > 0) {
                sectionCount = snapshot.sectionData.length;
            }
            ChunkDataS2CPacket packet = SnapshotPackets.build(world, snapshot, sectionLimit);
            // 注入期间要打标记，否则虚空 mixin 会把我们自己的包也当成"真实区块"丢掉
            injectingThread = Thread.currentThread();
            try {
                client.getNetworkHandler().onChunkData(packet);
            } finally {
                injectingThread = null;
            }
            INJECTED.incrementAndGet();
        } catch (Throwable t) {
            if (FAILED.incrementAndGet() <= 5) {
                Look_world.LOGGER.error("[look_world] 注入失败: {}", snapshot, t);
            }
        }
    }

    private static void resetPlayback() {
        stageIdx = 0;
        ring = 0;
        slice = 1;
        ringCursor = 0;
        accumulator = 0;
        finished = false;
        groupId = 0;
        buildRing();
    }

    // ===== 给控制面板 / HUD 用的接口 =====

    public static boolean paused() {
        return paused;
    }

    public static boolean finished() {
        return finished;
    }

    public static void togglePause() {
        paused = !paused;
        accumulator = 0;
    }

    /** 暂停状态下往前走一整组。 */
    public static void step() {
        if (!paused) {
            paused = true;
        }
        pendingGroups = Math.min(pendingGroups + 1, 8);
    }

    /** 从头重播（仓库里的快照都还在）。 */
    public static void replay() {
        resetPlayback();
        paused = false;
        lastWorld = null;   // 强制下一 tick 重新走一遍"进入世界"的初始化
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            lastWorld = client.world;
        }
        Look_world.LOGGER.info("[look_world] 重新播放");
    }

    public static void speedUp() {
        speedIndex = Math.min(speedIndex + 1, PACKETS_PER_TICK.length - 1);
    }

    public static void speedDown() {
        speedIndex = Math.max(speedIndex - 1, 0);
    }

    public static String speedLabel() {
        return PACKETS_PER_TICK[speedIndex] + " 包/tick";
    }

    /** 循环切换预览半径（区块）。改完需要重新进世界才会重新录制那块区域。 */
    public static void cycleRadius() {
        int[] presets = PreviewConfig.RADIUS_PRESETS;
        int idx = 0;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i] == PreviewConfig.radius) {
                idx = i;
                break;
            }
        }
        PreviewConfig.radius = presets[(idx + 1) % presets.length];
        resetPlayback();
    }

    public static String radiusLabel() {
        int r = PreviewConfig.radius;
        return "预览半径：" + r + " 区块（" + (r * 2 + 1) + "×" + (r * 2 + 1) + "）";
    }

    /** 是否"从下往上一层一层展开"。 */
    public static boolean layered() {
        return PreviewConfig.sectionsPerFrame != PreviewConfig.SECTIONS_ALL_AT_ONCE;
    }

    public static void toggleLayered() {
        PreviewConfig.sectionsPerFrame = layered()
                ? PreviewConfig.SECTIONS_ALL_AT_ONCE
                : 4;
        resetPlayback();
    }

    /** 虚空模式：丢掉服务端发来的真实区块。 */
    public static boolean voidingRealChunks() {
        return PreviewConfig.voidRealChunks;
    }

    /**
     * 切换虚空模式，并让它**立刻生效**：
     *  - 打开：本地把预览区之外的区块卸掉，马上变空
     *  - 关掉：请求服务端重发真实区块（服务端不会主动重发已发过的区块）
     */
    public static void toggleVoidRealChunks() {
        PreviewConfig.voidRealChunks = !PreviewConfig.voidRealChunks;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }
        if (PreviewConfig.voidRealChunks) {
            clearRealChunksAround(client);
        } else {
            ClientPlayNetworking.send(PreviewResend.CHANNEL, new PacketByteBuf(Unpooled.buffer()));
            Look_world.LOGGER.info("[look_world] 已请求服务端重发真实区块");
        }
    }

    private static void clearRealChunksAround(MinecraftClient client) {
        ClientChunkManager manager = client.world.getChunkManager();
        ChunkPos center = client.player.getChunkPos();
        int radius = Math.min(client.options.getViewDistance().getValue(), 12);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = center.x + dx;
                int cz = center.z + dz;
                if (PreviewConfig.inRange(cx, cz)) {
                    continue;
                }
                manager.unload(cx, cz);
            }
        }
        Look_world.LOGGER.info("[look_world] 预览区之外已清空（半径 {} 区块）", radius);
    }

    public static boolean autoPauseOnJoin() {
        return PreviewConfig.autoPauseOnJoin;
    }

    public static void toggleAutoPauseOnJoin() {
        PreviewConfig.autoPauseOnJoin = !PreviewConfig.autoPauseOnJoin;
    }

    public static long injected() {
        return INJECTED.get();
    }

    public static String statusText() {
        StringBuilder sb = new StringBuilder();
        sb.append(paused ? "已暂停" : (finished ? "播放完成" : "运行中"))
                .append(" · ").append(speedLabel())
                .append(layered() ? " · 逐层" : " · 整块")
                .append(voidingRealChunks() ? " · 虚空" : "")
                .append(" · 已录 ").append(SnapshotRecorder.recordedChunkCount())
                .append(" 块 · 已播 ").append(INJECTED.get()).append(" 包");
        if (!finished && stageIdx < STAGES.length) {
            sb.append(" · ").append(STAGES[stageIdx].cn)
                    .append(' ').append(stageIdx + 1).append('/').append(STAGES.length)
                    .append(" · 圈 ").append(ring + 1).append('/').append(PreviewConfig.radius + 1)
                    .append(" · 层 ").append(slice).append('/').append(totalSlices());
        }
        return sb.toString();
    }
}
