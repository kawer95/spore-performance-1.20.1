package com.arxyt.sporeperformance.client.render;

import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * Renders only ModelParts whose cube UV rectangles intersect opaque texture pixels. Geometry is
 * still submitted through ModelPart.compile, so AcceleratedRendering can retain its mesh cache.
 */
public final class OpaqueModelPartRenderer {
    private static final IdentityHashMap<EntityModel<?>, Map<ResourceLocation, RenderPlan>> PLANS = new IdentityHashMap<>();
    private static final Map<ResourceLocation, AlphaUvMask> MASKS = new LinkedHashMap<>();
    private static final Set<ResourceLocation> KNOWN_TEXTURES = new LinkedHashSet<>();
    private static final Set<ResourceLocation> FAILED_TEXTURES = new LinkedHashSet<>();
    private static final Map<String, List<String>> VERIFIED_RENDER_ROOTS = Map.of(
            "com.Harbinger.Spore.Client.Models.GazenbrecherModel", List.of("Gazenbrecher"),
            "com.Harbinger.Spore.Client.Models.HowitzerModel", List.of("Howi"),
            "com.Harbinger.Spore.Client.Models.verwahrungModel", List.of("SporePod"),
            "com.Harbinger.Spore.Client.Models.SiegerModel", List.of(
                    "smolleg", "mainbody", "mainbody2", "tail", "RightLegJointY",
                    "LeftLegJointY", "jaw", "BackRightLeg", "BackLeftLeg"));
    private static final RenderPlan FAILED = new RenderPlan(List.of(), 0, 0, true, null);
    private static volatile Introspection introspection;
    private static volatile boolean failureLogged;

    public static boolean render(EntityModel<?> model, ResourceLocation texture, PoseStack stack,
                                 VertexConsumer consumer, int light, int overlay,
                                 float red, float green, float blue, float alpha) {
        // Optimized layers are optional. Invalid compatibility data must fail closed to the caller's
        // original renderer instead of allowing null keys/resources to escape into RenderType code.
        if (model == null || texture == null || stack == null || consumer == null) return false;
        RenderPlan plan = plan(model, texture);
        if (plan == FAILED || plan.failed || plan.roots.isEmpty()) return false;
        try {
            for (Node root : plan.roots) renderNode(root, stack, consumer, light, overlay, red, green, blue, alpha, plan.access);
            ClientRenderMetrics.increment("mask.rendered");
            ClientRenderMetrics.add("mask.parts_total", plan.totalParts);
            ClientRenderMetrics.add("mask.parts_selected", plan.selectedParts);
            return true;
        } catch (Throwable throwable) {
            PLANS.computeIfAbsent(model, ignored -> new LinkedHashMap<>()).put(texture, FAILED);
            logFailure("Opaque model part rendering failed", throwable);
            return false; // Prefer a possible one-frame overdraw to a missing effect layer.
        }
    }

    public static int planCount() {
        int count = 0;
        for (Map<ResourceLocation, RenderPlan> plans : PLANS.values()) count += plans.size();
        return count;
    }

    public static void clear() {
        PLANS.clear();
        MASKS.clear();
        KNOWN_TEXTURES.clear();
        FAILED_TEXTURES.clear();
        introspection = null;
        failureLogged = false;
    }

    /** Re-reads all previously observed alpha maps during the resource reload apply phase. */
    public static void reload(ResourceManager resourceManager) {
        PLANS.clear();
        MASKS.clear();
        FAILED_TEXTURES.clear();
        introspection = null;
        failureLogged = false;
        for (ResourceLocation texture : KNOWN_TEXTURES) {
            AlphaUvMask mask = loadMask(resourceManager, texture);
            if (mask != null) MASKS.put(texture, mask);
            else FAILED_TEXTURES.add(texture);
        }
    }

    private static RenderPlan plan(EntityModel<?> model, ResourceLocation texture) {
        Map<ResourceLocation, RenderPlan> byTexture = PLANS.computeIfAbsent(model, ignored -> new LinkedHashMap<>());
        KNOWN_TEXTURES.add(texture);
        RenderPlan cached = byTexture.get(texture);
        if (cached != null) return cached;
        RenderPlan created;
        try {
            if (FAILED_TEXTURES.contains(texture)) {
                byTexture.put(texture, FAILED);
                return FAILED;
            }
            AlphaUvMask mask = MASKS.computeIfAbsent(texture, OpaqueModelPartRenderer::loadMask);
            if (mask == null) FAILED_TEXTURES.add(texture);
            if (mask == null) created = failed("texture_unavailable");
            else if (!mask.anyOpaque()) created = failed("texture_fully_transparent");
            else created = buildPlan(model, mask);
        } catch (Throwable throwable) {
            logFailure("Opaque model part mask is incompatible for " + model.getClass().getName(), throwable);
            created = FAILED;
        }
        byTexture.put(texture, created);
        return created;
    }

    private static AlphaUvMask loadMask(ResourceLocation texture) {
        return loadMask(Minecraft.getInstance().getResourceManager(), texture);
    }

    private static AlphaUvMask loadMask(ResourceManager resourceManager, ResourceLocation texture) {
        try {
            var resource = resourceManager.getResource(texture);
            if (resource.isEmpty()) return null;
            try (InputStream input = resource.get().open(); NativeImage image = NativeImage.read(input)) {
                int width = image.getWidth();
                int height = image.getHeight();
                boolean[] opaque = new boolean[width * height];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) opaque[y * width + x] = ((image.getPixelRGBA(x, y) >>> 24) & 255) != 0;
                }
                return new AlphaUvMask(width, height, opaque);
            }
        } catch (Exception exception) {
            logFailure("Could not read Spore effect texture " + texture, exception);
            return null;
        }
    }

    private static RenderPlan buildPlan(EntityModel<?> model, AlphaUvMask mask) throws Throwable {
        Introspection access = introspection();
        Set<ModelPart> all = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<ModelPart> children = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Class<?> type = model.getClass(); type != null && EntityModel.class.isAssignableFrom(type); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!ModelPart.class.isAssignableFrom(field.getType()) || !field.trySetAccessible()) continue;
                ModelPart part = (ModelPart) field.get(model);
                if (part != null) part.getAllParts().forEach(all::add);
            }
        }
        for (ModelPart part : all) children.addAll(access.children(part).values());
        List<ModelPart> discoveredRoots = all.stream().filter(part -> !children.contains(part)).toList();
        List<ModelPart> roots = verifiedRoots(model, discoveredRoots);
        if (roots == null) return failed("root_signature");
        int[] counts = new int[2];
        Set<ModelPart> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Node> selectedRoots = new ArrayList<>(roots.size());
        for (ModelPart root : roots) {
            Node selected = buildNode(root, mask, access, counts, visited);
            if (selected != null) selectedRoots.add(selected);
        }
        if (selectedRoots.isEmpty() || counts[1] == 0) return failed("no_visible_parts");
        if (counts[1] >= counts[0]) return failed("all_parts_selected");
        ClientRenderMetrics.increment(roots.size() > 1 ? "mask.plan_multi_root" : "mask.plan_single_root");
        return new RenderPlan(List.copyOf(selectedRoots), counts[0], counts[1], false, access);
    }

    private static List<ModelPart> verifiedRoots(EntityModel<?> model, List<ModelPart> discoveredRoots)
            throws ReflectiveOperationException {
        List<String> expectedNames = VERIFIED_RENDER_ROOTS.get(model.getClass().getName());
        if (discoveredRoots.size() == 1 && (expectedNames == null || expectedNames.size() == 1)) {
            if (expectedNames != null) {
                Field field = model.getClass().getDeclaredField(expectedNames.get(0));
                if (!field.trySetAccessible() || field.get(model) != discoveredRoots.get(0)) return null;
                ClientRenderMetrics.increment("mask.verified_model_signature");
            }
            return discoveredRoots;
        }
        if (!PerformanceConfig.CLIENT_VERIFIED_MULTI_ROOT_PART_MASK.get() || expectedNames == null) return null;
        List<ModelPart> expected = new ArrayList<>(expectedNames.size());
        for (String name : expectedNames) {
            Field field = model.getClass().getDeclaredField(name);
            if (!ModelPart.class.isAssignableFrom(field.getType()) || !field.trySetAccessible()) return null;
            Object value = field.get(model);
            if (!(value instanceof ModelPart part) || expected.contains(part)) return null;
            expected.add(part);
        }
        Set<ModelPart> expectedIdentity = Collections.newSetFromMap(new IdentityHashMap<>());
        expectedIdentity.addAll(expected);
        Set<ModelPart> discoveredIdentity = Collections.newSetFromMap(new IdentityHashMap<>());
        discoveredIdentity.addAll(discoveredRoots);
        if (!expectedIdentity.equals(discoveredIdentity)) return null;
        ClientRenderMetrics.increment("mask.verified_model_signature");
        return expected;
    }

    private static RenderPlan failed(String reason) {
        ClientRenderMetrics.increment("mask.fallback." + reason);
        return FAILED;
    }

    private static Node buildNode(ModelPart part, AlphaUvMask mask, Introspection access,
                                  int[] counts, Set<ModelPart> visited) throws Throwable {
        if (!visited.add(part)) return null;
        counts[0]++;
        boolean selected = partIntersects(part, mask, access);
        List<Node> childNodes = new ArrayList<>();
        for (ModelPart child : access.children(part).values()) {
            Node node = buildNode(child, mask, access, counts, visited);
            if (node != null) childNodes.add(node);
        }
        if (!selected && childNodes.isEmpty()) return null;
        if (selected) counts[1]++;
        return new Node(part, selected, List.copyOf(childNodes));
    }

    private static boolean partIntersects(ModelPart part, AlphaUvMask mask, Introspection access) throws Throwable {
        for (Object cube : access.cubes(part)) {
            Object polygons = access.polygons.get(cube);
            int polygonCount = Array.getLength(polygons);
            for (int i = 0; i < polygonCount; i++) {
                Object polygon = Array.get(polygons, i);
                Object vertices = access.vertices.get(polygon);
                float minU = Float.POSITIVE_INFINITY, minV = Float.POSITIVE_INFINITY;
                float maxU = Float.NEGATIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
                int vertexCount = Array.getLength(vertices);
                for (int j = 0; j < vertexCount; j++) {
                    Object vertex = Array.get(vertices, j);
                    float u = access.u.getFloat(vertex);
                    float v = access.v.getFloat(vertex);
                    minU = Math.min(minU, u); maxU = Math.max(maxU, u);
                    minV = Math.min(minV, v); maxV = Math.max(maxV, v);
                }
                if (vertexCount > 0 && mask.intersects(minU, minV, maxU, maxV)) return true;
            }
        }
        return false;
    }

    private static void renderNode(Node node, PoseStack stack, VertexConsumer consumer, int light, int overlay,
                                   float red, float green, float blue, float alpha, Introspection access) throws Throwable {
        ModelPart part = node.part;
        if (!part.visible) return;
        stack.pushPose();
        part.translateAndRotate(stack);
        if (node.selected && !part.skipDraw) {
            access.compile.invoke(part, stack.last(), consumer, light, overlay, red, green, blue, alpha);
        }
        for (Node child : node.children) renderNode(child, stack, consumer, light, overlay, red, green, blue, alpha, access);
        stack.popPose();
    }

    private static Introspection introspection() throws ReflectiveOperationException {
        Introspection current = introspection;
        if (current != null) return current;
        Field cubes = null, children = null;
        for (Field field : ModelPart.class.getDeclaredFields()) {
            if (List.class.isAssignableFrom(field.getType())) cubes = field;
            else if (Map.class.isAssignableFrom(field.getType())) children = field;
        }
        if (cubes == null || children == null || !cubes.trySetAccessible() || !children.trySetAccessible()) {
            throw new NoSuchFieldException("ModelPart cubes/children");
        }
        Method compile = null;
        for (Method method : ModelPart.class.getDeclaredMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if (p.length == 8 && p[0] == PoseStack.Pose.class && VertexConsumer.class.isAssignableFrom(p[1])) {
                compile = method;
                break;
            }
        }
        if (compile == null || !compile.trySetAccessible()) throw new NoSuchMethodException("ModelPart compile");
        Object sampleCube = findSampleCube(cubes);
        Field polygons = arrayField(sampleCube.getClass());
        Object polygonArray = polygons.get(sampleCube);
        Object samplePolygon = Array.get(polygonArray, 0);
        Field vertices = arrayField(samplePolygon.getClass());
        Object vertexArray = vertices.get(samplePolygon);
        Object sampleVertex = Array.get(vertexArray, 0);
        List<Field> floats = new ArrayList<>();
        for (Field field : sampleVertex.getClass().getDeclaredFields()) {
            if (field.getType() == float.class && field.trySetAccessible()) floats.add(field);
        }
        if (floats.size() < 2) throw new NoSuchFieldException("ModelPart vertex UV");
        current = new Introspection(cubes, children, polygons, vertices, floats.get(0), floats.get(1),
                MethodHandles.lookup().unreflect(compile));
        introspection = current;
        return current;
    }

    private static Object findSampleCube(Field cubes) throws IllegalAccessException {
        for (EntityModel<?> model : PLANS.keySet()) {
            for (Class<?> type = model.getClass(); type != null && EntityModel.class.isAssignableFrom(type); type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (!ModelPart.class.isAssignableFrom(field.getType()) || !field.trySetAccessible()) continue;
                    ModelPart part = (ModelPart) field.get(model);
                    if (part == null) continue;
                    for (ModelPart candidate : part.getAllParts().toList()) {
                        List<?> list = (List<?>) cubes.get(candidate);
                        if (!list.isEmpty()) return list.get(0);
                    }
                }
            }
        }
        throw new IllegalStateException("No model cube available");
    }

    private static Field arrayField(Class<?> type) throws NoSuchFieldException {
        for (Field field : type.getDeclaredFields()) {
            if (field.getType().isArray() && field.trySetAccessible()) return field;
        }
        throw new NoSuchFieldException(type.getName() + " array field");
    }

    private static void logFailure(String message, Throwable throwable) {
        if (!failureLogged) {
            failureLogged = true;
            SporePerformance.LOGGER.warn(message + "; opaque part masking will fail closed", throwable);
        }
    }

    private record Node(ModelPart part, boolean selected, List<Node> children) {}
    private record RenderPlan(List<Node> roots, int totalParts, int selectedParts, boolean failed, Introspection access) {}

    private record Introspection(Field cubes, Field children, Field polygons, Field vertices,
                                 Field u, Field v, MethodHandle compile) {
        @SuppressWarnings("unchecked")
        private Map<String, ModelPart> children(ModelPart part) throws IllegalAccessException {
            return (Map<String, ModelPart>) children.get(part);
        }

        @SuppressWarnings("unchecked")
        private List<Object> cubes(ModelPart part) throws IllegalAccessException {
            return (List<Object>) cubes.get(part);
        }
    }

    private OpaqueModelPartRenderer() {}
}
