# LOOK_WORLD

把 Minecraft 世界生成的过程"拆开看"的 Fabric 模组（MC 1.20.1）。

原版区块要到 `ChunkStatus.FULL` 才发给客户端，所以你只能看到"一瞬间全部出现"的成品。
这个模组把生成过程按阶段录下来，再按**阶段 → 由中心向外一圈 → 从下往上一层**的顺序
慢慢重放给你看，可以暂停、单步、变速。

## 你能看到什么

| 阶段 | 内容 |
|---|---|
| 石头地形 | 噪声生成的整体岩石/水/空气 |
| 基岩 / 深板岩 / 地表 | 表层规则：底部基岩、深板岩带、顶部草地沙地雪 |
| 雕刻洞穴 | 洞穴与峡谷（挖出空气） |
| 灌入水与岩浆 | 往洞穴里填水/岩浆 |
| 放置结构 | 村庄、遗迹、矿井…（每个结构单独一帧） |
| 装饰物 | 矿脉、树干、花草、雪冰 |

## 操作

| 键 | 作用 |
|---|---|
| `F6` | 打开控制面板 |
| `F7` | 暂停 / 继续（只暂停预览播放，不暂停游戏，可以边冻结边飞着看） |
| `F8` | 单步（推进一整组：某阶段某圈某层） |

控制面板里还有：慢/快一点、逐层展开开关、预览半径（4/6/8/12/16 区块）、重播、
虚空模式、首次进入时暂停。

进入世界时默认：**旁观模式 + 全亮 + 虚空 + 预览暂停**，按 `F7` 开始生长。

## 原理

- **录制侧**（公共代码）：在 `ChunkGenerator` / `NoiseChunkGenerator` / `StructureStart`
  的阶段边界挂 mixin，把区块段用原版 `ChunkSection.toPacket` 格式快照下来，
  按 **(区块, 阶段)** 存成槽位。
  ⚠ 取数前必须 `PalettedContainer.copy()` 复制一份再序列化 —— 生成期间原容器的锁
  被当前线程持有，直接 `toPacket` 会自锁死。
- **显示侧**（客户端）：单机里集成服务器和客户端在同一个 JVM，所以不走网络，
  客户端直接拼一个合法的 `ChunkDataS2CPacket` 交给 `ClientPlayNetworkHandler.onChunkData`，
  后面完全是原版流程（建区块 → 渲染）。真正的 `FULL` 区块被"虚空模式"挡在门外，
  免得一瞬间把半成品盖掉。
- **跳过加载**：`MinecraftServer.prepareStartRegion` 会预生成出生点周围 441 个区块
  （新建世界卡几十秒就卡在这），整段跳过；`DownloadingTerrainScreen` 也直接关掉。

## 构建

需要 **JDK 17**。

```bash
./gradlew build          # 产物在 build/libs/
./gradlew runClient      # 开发环境启动客户端
```

> 开发环境启动客户端时如果下载依赖慢，可以给 Gradle 配代理：
> `./gradlew build -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890`

## 许可

MIT，见 [LICENSE](LICENSE)。
