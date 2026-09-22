package com.xiaofan.look_world.worldgen;

/**
 * 会产生可见方块的生成阶段，细分到原版自己的执行单元。
 * 顺序（也就是实际生成顺序）：
 *   BIOMES          只填生物群系，不产生方块（注入时会跳过，播出来是空气）
 *   NOISE           噪声整体填成石头（generateFeatures 之前的地形主体）
 *   SURFACE         表层规则：最底下基岩、再深板岩、顶上草地/沙/雪
 *   CARVERS_AIR     雕刻洞穴与峡谷（GenerationStep.Carver.AIR）
 *   CARVERS_LIQUID  往洞穴里灌水/岩浆（GenerationStep.Carver.LIQUID）
 *   STRUCTURES      放置结构（村庄/遗迹/矿井…每个结构一次，StructureStart.place）
 *   FEATURES        装饰物：矿脉、树干、花草、雪冰（PlacedFeature）
 */
public enum GenStage {
    BIOMES("生物群系"),
    NOISE("石头地形"),
    SURFACE("基岩/深板岩/地表"),
    CARVERS_AIR("雕刻洞穴"),
    CARVERS_LIQUID("灌入水与岩浆"),
    STRUCTURES("放置结构"),
    FEATURES("装饰物(矿/树/花)");

    public final String cn;

    GenStage(String cn) {
        this.cn = cn;
    }
}
