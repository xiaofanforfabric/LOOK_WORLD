package com.xiaofan.look_world.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端表现层：一个按键 + 一行 HUD 状态。
 * 按键默认 F6（原版没占用），打开控制面板；HUD 常显当前播放状态。
 */
public final class PreviewOverlay {

    private static KeyBinding panelKey;
    private static KeyBinding pauseKey;
    private static KeyBinding stepKey;

    private PreviewOverlay() {
    }

    public static void init() {
        panelKey = register("key.look_world.control", GLFW.GLFW_KEY_F6);
        pauseKey = register("key.look_world.pause", GLFW.GLFW_KEY_F7);
        stepKey = register("key.look_world.step", GLFW.GLFW_KEY_F8);

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null || client.options.hudHidden) {
                return;
            }
            String hint = PreviewInjector.paused() ? "  ＜按 F7 开始播放＞" : "";
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal("[预览] " + PreviewInjector.statusText() + hint),
                    6, 6, PreviewInjector.paused() ? 0xFFAA00 : 0xFFFF55);
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal("F6 面板 · F7 暂停/继续 · F8 单步"), 6, 18, 0xAAAAAA);
        });
    }

    private static KeyBinding register(String id, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                id, InputUtil.Type.KEYSYM, code, "key.categories.look_world"));
    }

    public static void onClientTick(MinecraftClient client) {
        while (panelKey.wasPressed()) {
            client.setScreen(new PreviewScreen());
        }
        while (pauseKey.wasPressed()) {
            PreviewInjector.togglePause();
        }
        while (stepKey.wasPressed()) {
            PreviewInjector.step();
        }
    }
}
