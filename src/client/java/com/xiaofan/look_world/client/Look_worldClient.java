package com.xiaofan.look_world.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class Look_worldClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 显示侧的全部逻辑都在客户端：按预算把半成品区块喂给原版收包路径
        ClientTickEvents.END_CLIENT_TICK.register(PreviewInjector::onClientTick);
        // 控制面板（F6）+ HUD 状态行
        PreviewOverlay.init();
        ClientTickEvents.END_CLIENT_TICK.register(PreviewOverlay::onClientTick);
    }
}
