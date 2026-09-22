package com.xiaofan.look_world.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 预览控制面板：暂停 / 单步 / 变速。
 *
 * 特意让 shouldPause() 返回 false —— 面板打开时**不暂停游戏**，只暂停"预览播放"。
 * 这样你可以一边冻结画面一边飞过去看半成品区块（暂停游戏的话区块就不会渲染了）。
 */
public class PreviewScreen extends Screen {

    private ButtonWidget pauseButton;
    private ButtonWidget stepButton;

    public PreviewScreen() {
        super(Text.literal("世界生成预览 · 控制面板"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 30;
        int w = 220;

        this.pauseButton = ButtonWidget.builder(pauseLabel(), button -> {
            PreviewInjector.togglePause();
            button.setMessage(pauseLabel());
            this.stepButton.active = PreviewInjector.paused();
        }).dimensions(cx - w / 2, y, w, 20).build();
        this.addDrawableChild(this.pauseButton);

        this.stepButton = ButtonWidget.builder(Text.literal("单步 →（推进一帧）"),
                        button -> PreviewInjector.step())
                .dimensions(cx - w / 2, y + 24, w, 20).build();
        this.stepButton.active = PreviewInjector.paused();
        this.addDrawableChild(this.stepButton);

        ButtonWidget slower = ButtonWidget.builder(Text.literal("慢一点 −"),
                        button -> PreviewInjector.speedDown())
                .dimensions(cx - w / 2, y + 52, w / 2 - 2, 20).build();
        this.addDrawableChild(slower);

        ButtonWidget faster = ButtonWidget.builder(Text.literal("快一点 ＋"),
                        button -> PreviewInjector.speedUp())
                .dimensions(cx + 2, y + 52, w / 2 - 2, 20).build();
        this.addDrawableChild(faster);

        ButtonWidget layered = ButtonWidget.builder(layeredLabel(), button -> {
            PreviewInjector.toggleLayered();
            button.setMessage(layeredLabel());
        }).dimensions(cx - w / 2, y + 80, w, 20).build();
        this.addDrawableChild(layered);

        ButtonWidget radius = ButtonWidget.builder(Text.literal(PreviewInjector.radiusLabel()), button -> {
            PreviewInjector.cycleRadius();
            button.setMessage(Text.literal(PreviewInjector.radiusLabel()));
        }).dimensions(cx - w / 2, y + 108, w, 20).build();
        this.addDrawableChild(radius);

        ButtonWidget replay = ButtonWidget.builder(Text.literal("重播（从头再放一遍）"),
                        button -> PreviewInjector.replay())
                .dimensions(cx - w / 2, y + 136, w, 20).build();
        this.addDrawableChild(replay);

        ButtonWidget voidMode = ButtonWidget.builder(voidLabel(), button -> {
            PreviewInjector.toggleVoidRealChunks();
            button.setMessage(voidLabel());
        }).dimensions(cx - w / 2, y + 164, w, 20).build();
        this.addDrawableChild(voidMode);

        ButtonWidget autoPause = ButtonWidget.builder(autoPauseLabel(), button -> {
            PreviewInjector.toggleAutoPauseOnJoin();
            button.setMessage(autoPauseLabel());
        }).dimensions(cx - w / 2, y + 192, w, 20).build();
        this.addDrawableChild(autoPause);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("关闭"),
                        button -> this.close())
                .dimensions(cx - w / 2, y + 220, w, 20).build());
    }

    private static Text voidLabel() {
        return Text.literal(PreviewInjector.voidingRealChunks()
                ? "虚空模式：开（区外也是空的）"
                : "虚空模式：关（区外是真实地形）");
    }

    private static Text autoPauseLabel() {
        return Text.literal(PreviewInjector.autoPauseOnJoin()
                ? "首次进入时暂停：开"
                : "首次进入时暂停：关");
    }

    private static Text layeredLabel() {
        return Text.literal(PreviewInjector.layered()
                ? "逐层展开：开（从下往上一层一层长）"
                : "逐层展开：关（整块一次性出现）");
    }

    private static Text pauseLabel() {
        return Text.literal(PreviewInjector.paused() ? "继续播放 ▶" : "暂停 ⏸");
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        int cx = this.width / 2;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, this.height / 2 - 62, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(PreviewInjector.statusText()), cx, this.height / 2 + 58, 0xFFFF55);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("加速/减速也可以按速度按钮，面板可随时开关"), cx, this.height / 2 + 72, 0xAAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }
}
