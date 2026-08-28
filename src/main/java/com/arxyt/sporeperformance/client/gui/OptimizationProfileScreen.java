package com.arxyt.sporeperformance.client.gui;

import com.arxyt.sporeperformance.client.render.ClientRenderLifecycle;
import com.arxyt.sporeperformance.config.OptimizationProfiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Small, discoverable preset screen for players who do not want to edit TOML
 * files.  A multiplayer client only changes its client spec; the server-side
 * command remains the authoritative way to change common settings remotely.
 */
public final class OptimizationProfileScreen extends Screen {
    private final Screen parent;
    private String resultMessage = "";
    private boolean resultWarning;

    private OptimizationProfileScreen(Screen parent) {
        super(Component.literal("Spore Performance 优化预设"));
        this.parent = parent;
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new OptimizationProfileScreen(minecraft.screen));
    }

    @Override
    protected void init() {
        clearWidgets();
        int buttonWidth = 150;
        int gap = 8;
        int totalWidth = buttonWidth * 3 + gap * 2;
        int left = (width - totalWidth) / 2;
        int top = Math.max(78, height / 2 - 26);
        OptimizationProfiles.Profile current = OptimizationProfiles.detectCombinedSafely();

        addRenderableWidget(Button.builder(Component.literal("常规"), button -> apply(OptimizationProfiles.Profile.NORMAL))
                .bounds(left, top, buttonWidth, 22).build());
        addRenderableWidget(Button.builder(Component.literal("激进"), button -> apply(OptimizationProfiles.Profile.AGGRESSIVE))
                .bounds(left + buttonWidth + gap, top, buttonWidth, 22).build());
        addRenderableWidget(Button.builder(Component.literal("全部"), button -> apply(OptimizationProfiles.Profile.ALL))
                .bounds(left + (buttonWidth + gap) * 2, top, buttonWidth, 22).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), button -> onClose())
                .bounds(width / 2 - 50, height - 30, 100, 20).build());

        if (current != null) {
            resultMessage = "当前预设：" + current.displayName();
            resultWarning = false;
        } else if (resultMessage.isEmpty()) {
            resultMessage = "当前配置为自定义组合";
            resultWarning = false;
        }
    }

    private void apply(OptimizationProfiles.Profile profile) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hasSingleplayerServer()) {
            // The integrated server owns the common spec.  Apply the client
            // half now, then serialize the common write onto the server thread
            // so a render-thread button click cannot race entity ticks.
            OptimizationProfiles.Result clientResult = OptimizationProfiles.applyClient(profile);
            ClientRenderLifecycle.clearAll();
            resultMessage = clientResult.summary() + "；通用配置将在服务器线程立即应用。";
            resultWarning = clientResult.restartRequired();
            minecraft.getSingleplayerServer().execute(() -> {
                OptimizationProfiles.Result commonResult = OptimizationProfiles.applyCommon(profile);
                minecraft.execute(() -> {
                    resultMessage = "客户端：" + clientResult.summary() + "；服务端：" + commonResult.summary();
                    resultWarning = clientResult.restartRequired() || commonResult.restartRequired();
                });
            });
            return;
        }

        // From the title screen there is no remote server, so both local specs
        // can be written together.  In multiplayer only the client spec is
        // writable; the server command is shown below.
        boolean localMenu = minecraft.level == null;
        OptimizationProfiles.Result result = localMenu
                ? OptimizationProfiles.applyBoth(profile)
                : OptimizationProfiles.applyClient(profile);
        ClientRenderLifecycle.clearAll();
        resultMessage = result.summary();
        if (!localMenu) {
            if (minecraft.player != null) {
                // The command is permission-gated on the server.  Sending it
                // here keeps the preset one-click for operators while a normal
                // player simply receives the server's usual permission reply.
                minecraft.player.connection.sendCommand("sporeperformance profile " + commandName(profile));
                resultMessage += "；已请求服务器应用通用配置（需要管理员权限）。";
            } else {
                resultMessage += "；请管理员执行 /sporeperformance profile " + commandName(profile) + "。";
            }
        }
        resultWarning = result.restartRequired() || !localMenu;
    }

    private static String commandName(OptimizationProfiles.Profile profile) {
        return switch (profile) {
            case NORMAL -> "normal";
            case AGGRESSIVE -> "aggressive";
            case ALL -> "all";
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int panelWidth = Math.min(560, width - 24);
        int panelLeft = (width - panelWidth) / 2;
        int panelTop = Math.max(28, height / 2 - 105);
        int panelBottom = Math.min(height - 8, height / 2 + 85);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelBottom, 0xF20A0D12);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 1, 0xFFFFC83D);
        graphics.drawCenteredString(font, title, width / 2, panelTop + 12, 0xFFFFFFFF);
        graphics.drawCenteredString(font,
                Component.literal("选择预设后会写入 common/client TOML；数值微调和诊断开关保持不变。"),
                width / 2, panelTop + 31, 0xFFB8C2CC);

        int buttonWidth = 150;
        int gap = 8;
        int totalWidth = buttonWidth * 3 + gap * 2;
        int left = (width - totalWidth) / 2;
        int buttonTop = Math.max(78, height / 2 - 26);
        OptimizationProfiles.Profile[] profiles = OptimizationProfiles.Profile.values();
        for (int i = 0; i < 3; i++) {
            int center = left + i * (buttonWidth + gap) + buttonWidth / 2;
            graphics.drawCenteredString(font, Component.literal(profiles[i].description()),
                    center, buttonTop - 15, 0xFFD5DCE3);
        }

        int textTop = panelTop + 55;
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(resultMessage), panelWidth - 24);
        int color = resultWarning ? 0xFFFFC857 : 0xFF9BE7A5;
        for (int i = 0; i < lines.size() && i < 3; i++) {
            graphics.drawCenteredString(font, lines.get(i), width / 2, textTop + i * 12, color);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
