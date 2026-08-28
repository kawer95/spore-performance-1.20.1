package com.arxyt.sporeperformance.client;

/**
 * Selects at most one sporesrp HUD render stage per frame. Gameplay rendering is independently
 * configurable, while an open Screen always takes precedence so the HUD cannot be drawn both
 * below and above the Screen. The policy contains no Minecraft state and is unit-testable.
 */
public final class HudRenderStagePolicy {
    public static boolean useGameplayOverlayStage(boolean screenOpen, boolean renderAboveScreens,
                                                  boolean renderInGameplay) {
        return renderInGameplay && (!screenOpen || !renderAboveScreens);
    }

    public static boolean useScreenForegroundStage(boolean screenOpen, boolean renderAboveScreens) {
        return screenOpen && renderAboveScreens;
    }

    private HudRenderStagePolicy() {}
}
