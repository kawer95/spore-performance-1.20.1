package com.arxyt.sporeperformance.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies gameplay hiding and the single-stage invariant for sporesrp HUD rendering. */
class HudRenderStagePolicyTest {
    @Test
    void noScreenHidesHudByDefault() {
        assertFalse(HudRenderStagePolicy.useGameplayOverlayStage(false, true, false));
        assertFalse(HudRenderStagePolicy.useScreenForegroundStage(false, true));
    }

    @Test
    void openScreenUsesOnlyForegroundStageWhenGameplayIsHidden() {
        assertFalse(HudRenderStagePolicy.useGameplayOverlayStage(true, true, false));
        assertTrue(HudRenderStagePolicy.useScreenForegroundStage(true, true));
    }

    @Test
    void explicitGameplayRenderingUsesOverlayOnlyWithoutScreen() {
        assertTrue(HudRenderStagePolicy.useGameplayOverlayStage(false, true, true));
        assertFalse(HudRenderStagePolicy.useScreenForegroundStage(false, true));
    }

    @Test
    void openScreenTakesPrecedenceWhenBothRenderModesAreEnabled() {
        assertFalse(HudRenderStagePolicy.useGameplayOverlayStage(true, true, true));
        assertTrue(HudRenderStagePolicy.useScreenForegroundStage(true, true));
    }

    @Test
    void foregroundDisabledCanKeepConventionalGameplayOverlay() {
        assertTrue(HudRenderStagePolicy.useGameplayOverlayStage(true, false, true));
        assertFalse(HudRenderStagePolicy.useScreenForegroundStage(true, false));
    }

    @Test
    void disablingBothRenderModesDrawsNothing() {
        assertFalse(HudRenderStagePolicy.useGameplayOverlayStage(true, false, false));
        assertFalse(HudRenderStagePolicy.useScreenForegroundStage(true, false));
    }
}
