package com.xiaofan.look_world;

/**
 * 预览配置。先用静态字段，后面再考虑接命令/配置文件。
 */
public final class PreviewConfig {

    /** 总开关。 */
    public static boolean enabled = true;

    /**
     * 预览半径（单位：区块）。8 就是 17x17 个区块，覆盖 272 格宽的范围。
     * 内存只跟这个面积有关（面积 × 6 个阶段），所以可以适当开大。
     */
    public static int radius = 8;

    /** 面板里循环切换的几档半径。 */
    public static final int[] RADIUS_PRESETS = {4, 6, 8, 12, 16};

    /** 预览中心（区块坐标），未设置时用主世界出生点所在区块。 */
    public static boolean centerSet = false;
    public static int centerX = 0;
    public static int centerZ = 0;

    /** 是否把玩家自动切到旁观模式。 */
    public static boolean autoSpectator = true;

    /**
     * 跳过原版的出生点预处理（prepareStartRegion）。
     * 原版会在这里给出生点挂一个半径 11 的 START ticket（23x23 = 441 个区块）
     * 并死等它们全部生成到 FULL —— 这就是“加载地形”卡几十秒的元凶。
     * 跳过之后区块改由玩家自己的 ticket 按需生成，我们就能看着它一层层长出来。
     */
    public static boolean skipSpawnPrep = true;

    /** 客户端是否直接关掉“正在下载地形”界面，进去就是世界。 */
    public static boolean skipLoadingScreen = true;

    /** 客户端是否全亮。 */
    public static boolean fullBright = true;

    /**
     * 虚空模式：丢掉服务端发来的真实区块数据，视距内保持纯虚空。
     * 不丢掉的话，真实区块（FULL）会很快盖掉我们逐阶段播放的半成品。
     */
    public static boolean voidRealChunks = true;

    /** 第一次进入世界时先暂停，等你按“继续”再开始播放。 */
    public static boolean autoPauseOnJoin = true;

    /**
     * 每帧展开几个区块段（段是从下往上一段 16 格）。
     * 一个区块 24 段，设成 4 就是分 6 帧从下往上长出来，"一层一层"的观感就来自这里。
     * 设成很大的值（比如 999）就变回"整块一次性出现"。
     */
    public static int sectionsPerFrame = 4;

    /** 不分层展开时用的值。 */
    public static final int SECTIONS_ALL_AT_ONCE = 999;

    private PreviewConfig() {
    }

    public static void setCenter(int chunkX, int chunkZ) {
        centerX = chunkX;
        centerZ = chunkZ;
        centerSet = true;
    }

    /** 该区块是否落在预览范围内。 */
    public static boolean inRange(int chunkX, int chunkZ) {
        return Math.abs(chunkX - centerX) <= radius && Math.abs(chunkZ - centerZ) <= radius;
    }

    /**
     * 主世界的 chunk 底高是 -64，下界/末地是 0。
     * 阶段钩子拿不到 world 时用这个把下界/末地筛掉。
     */
    public static boolean looksLikeOverworld(int bottomY) {
        return bottomY <= -64;
    }
}
