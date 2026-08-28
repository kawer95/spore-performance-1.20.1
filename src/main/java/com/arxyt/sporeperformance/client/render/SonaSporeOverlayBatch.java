package com.arxyt.sporeperformance.client.render;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/** One draw call for Sona's deterministic screen-space infection spores. */
public final class SonaSporeOverlayBatch {
    private static final int MAX_PARTICLES = 54;
    private static final Seed[] SEEDS = createSeeds();
    private static VertexBuffer cachedBuffer;
    private static long cachedFrame = Long.MIN_VALUE;
    private static int cachedWidth = -1;
    private static int cachedHeight = -1;
    private static int cachedParticleCount;

    public static void render(RenderGuiEvent.Pre event, Vec3 color, int width, int height,
                              float time, float weight) {
        boolean scaleEnabled = PerformanceConfig.CLIENT_SONA_OVERLAY_PARTICLE_SCALE_ENABLED.get();
        float scale = PerformanceConfig.CLIENT_SONA_OVERLAY_PARTICLE_SCALE.get().floatValue();
        int count = particleCount(weight, scaleEnabled, scale);
        Matrix4f pose = event.getGuiGraphics().pose().last().pose();

        if (!PerformanceConfig.CLIENT_SONA_OVERLAY_GEOMETRY_LOD.get()) {
            closeCachedBuffer();
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            appendParticles(builder, pose, color, width, height, time, weight, count);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferUploader.drawWithShader(builder.end());
            recordGeometry(count);
            ClientRenderMetrics.increment("sona.overlay.batch_draws");
            return;
        }

        int interval = PerformanceConfig.CLIENT_SONA_OVERLAY_UPDATE_INTERVAL.get();
        long frame = ClientRenderFrameClock.frame();
        boolean rebuild = cachedBuffer == null || cachedWidth != width || cachedHeight != height
                || frame - cachedFrame >= interval || frame < cachedFrame;
        if (rebuild) {
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            // Cached vertices stay in GUI-local coordinates; the current pose is applied at draw time.
            appendParticles(builder, new Matrix4f(), color, width, height, time, weight, count);
            if (cachedBuffer == null) cachedBuffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            cachedBuffer.bind();
            cachedBuffer.upload(builder.end());
            VertexBuffer.unbind();
            cachedFrame = frame;
            cachedWidth = width;
            cachedHeight = height;
            cachedParticleCount = count;
            recordGeometry(count);
            ClientRenderMetrics.increment("sona.overlay.geometry_rebuilt");
        } else {
            recordGeometry(cachedParticleCount);
            ClientRenderMetrics.increment("sona.overlay.geometry_reused");
        }
        ShaderInstance shader = GameRenderer.getPositionColorShader();
        if (shader != null && cachedBuffer != null) {
            cachedBuffer.bind();
            cachedBuffer.drawWithShader(pose, RenderSystem.getProjectionMatrix(), shader);
            VertexBuffer.unbind();
            ClientRenderMetrics.increment("sona.overlay.cached_draws");
        }
    }

    /**
     * May be called by Forge lifecycle/config callbacks on a non-render thread. GL deletion is
     * therefore always deferred to the render queue; direct deletion without a current context
     * aborts the JVM in LWJGL instead of producing a recoverable Java exception.
     */
    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) {
            ClientRenderMetrics.increment("sona.overlay.cleanup_deferred");
            RenderSystem.recordRenderCall(SonaSporeOverlayBatch::clearOnRenderThread);
            return;
        }
        clearOnRenderThread();
    }

    private static void clearOnRenderThread() {
        closeCachedBuffer();
        cachedFrame = Long.MIN_VALUE;
        cachedWidth = -1;
        cachedHeight = -1;
        cachedParticleCount = 0;
    }

    static int particleCount(float weight, boolean scaleEnabled, double configuredScale) {
        float scale = scaleEnabled ? (float) configuredScale : 1.0F;
        return Math.min(MAX_PARTICLES, Math.max(0,
                Mth.floor(Mth.lerp(weight, 16.0F, 54.0F) * scale)));
    }

    private static void recordGeometry(int count) {
        ClientRenderMetrics.add("sona.overlay.particles", count);
        ClientRenderMetrics.add("sona.overlay.vertices", count * 8L);
    }

    private static void appendParticles(BufferBuilder builder, Matrix4f pose, Vec3 color, int width,
                                        int height, float time, float weight, int count) {
        Vec3 bright = new Vec3(
                Mth.clamp((float) color.x * 0.8F + 0.35F, 0.0F, 1.0F),
                Mth.clamp((float) color.y * 0.8F + 0.35F, 0.0F, 1.0F),
                Mth.clamp((float) color.z * 0.8F + 0.35F, 0.0F, 1.0F));
        boolean precomputed = PerformanceConfig.CLIENT_SONA_PRECOMPUTE_OVERLAY_SEEDS.get();
        for (int i = 0; i < count; i++) {
            Seed seed = precomputed ? SEEDS[i] : seed(i);
            float x = fract(seed.x + time * seed.speed + Mth.sin(time * 0.015F + i) * 0.012F) * width;
            float y = fract(seed.y - time * seed.speed * 0.72F + Mth.cos(time * 0.011F + i * 0.7F) * 0.01F) * height;
            float pulse = 0.65F + 0.35F * Mth.sin(time * 0.09F + i * 1.7F);
            float alpha = Mth.clamp(weight * Mth.lerp(seed.alpha, 0.08F, 0.24F) * pulse, 0.0F, 0.26F);
            int outerColor = argb(bright, alpha * 0.22F);
            int innerColor = argb(bright, alpha);
            quad(builder, pose, x - 1.0F, y - 1.0F, x + seed.outerSize, y + seed.outerSize, outerColor);
            quad(builder, pose, x, y, x + seed.innerSize, y + seed.innerSize, innerColor);
        }
    }

    private static void quad(BufferBuilder builder, Matrix4f pose, float x1, float y1,
                             float x2, float y2, int color) {
        builder.vertex(pose, x1, y2, 0.0F).color(color).endVertex();
        builder.vertex(pose, x2, y2, 0.0F).color(color).endVertex();
        builder.vertex(pose, x2, y1, 0.0F).color(color).endVertex();
        builder.vertex(pose, x1, y1, 0.0F).color(color).endVertex();
    }

    private static int argb(Vec3 color, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        int r = (int) (Mth.clamp((float) color.x, 0.0F, 1.0F) * 255.0F);
        int g = (int) (Mth.clamp((float) color.y, 0.0F, 1.0F) * 255.0F);
        int b = (int) (Mth.clamp((float) color.z, 0.0F, 1.0F) * 255.0F);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static Seed[] createSeeds() {
        Seed[] seeds = new Seed[MAX_PARTICLES];
        for (int i = 0; i < seeds.length; i++) seeds[i] = seed(i);
        return seeds;
    }

    private static Seed seed(int index) {
        int inner = 1 + Mth.floor(hash01(index, 71) * 4.0F);
        return new Seed(hash01(index, 11), hash01(index, 29),
                Mth.lerp(hash01(index, 47), 0.0014F, 0.0042F),
                hash01(index, 97), inner, inner + 2);
    }

    private static float hash01(int index, int salt) {
        return fract(Mth.sin(index * 12.9898F + salt * 78.233F) * 43758.547F);
    }

    private static float fract(float value) {
        return value - Mth.floor(value);
    }

    private static void closeCachedBuffer() {
        if (cachedBuffer == null) return;
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("Sona overlay GPU buffer cleanup must run on the render thread");
        }
        VertexBuffer closing = cachedBuffer;
        cachedBuffer = null;
        if (cleanupAction(true, GLFW.glfwGetCurrentContext() != 0L) == CleanupAction.ABANDON) {
            // During final client teardown no context remains. The driver owns the allocation and
            // reclaims it with the process; attempting glDeleteBuffers here would abort the JVM.
            ClientRenderMetrics.increment("sona.overlay.cleanup_abandoned_no_context");
            return;
        }
        closing.close();
        ClientRenderMetrics.increment("sona.overlay.cleanup_closed");
    }

    static CleanupAction cleanupAction(boolean renderThread, boolean contextCurrent) {
        if (!renderThread) return CleanupAction.DEFER;
        return contextCurrent ? CleanupAction.CLOSE : CleanupAction.ABANDON;
    }

    enum CleanupAction { DEFER, CLOSE, ABANDON }

    private record Seed(float x, float y, float speed, float alpha, int innerSize, int outerSize) {}

    private SonaSporeOverlayBatch() {}
}
