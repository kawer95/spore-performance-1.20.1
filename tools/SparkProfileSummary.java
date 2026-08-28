import me.lucko.spark.proto.SparkSamplerProtos;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only Spark sampler summary for side-by-side profile comparison. */
public final class SparkProfileSummary {
    private static final int TREE_DEPTH = 7;
    private static final int TREE_WIDTH = 4;
    private static final int TREE_BUDGET = 120;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Pass one or more .sparkprofile paths or sampler URLs.");
        }
        for (String source : args) summarize(source);
    }

    private static void summarize(String source) throws Exception {
        try (InputStream input = open(source)) {
            SparkSamplerProtos.SamplerData data = SparkSamplerProtos.SamplerData.parseFrom(input);
            System.out.println("\n=== " + source + " ===");
            System.out.printf(Locale.ROOT, "samples interval=%dms ticks=%d threads=%d duration=%.1fs%n",
                    data.getMetadata().getInterval(), data.getMetadata().getNumberOfTicks(), data.getThreadsCount(),
                    (data.getMetadata().getEndTime() - data.getMetadata().getStartTime()) / 1000.0D);
            for (SparkSamplerProtos.ThreadNode thread : data.getThreadsList()) {
                double total = time(thread.getTimesList());
                System.out.printf(Locale.ROOT, "%n-- %s: %.3fs --%n", thread.getName(), total / 1000.0D);
                List<SparkSamplerProtos.StackTraceNode> flat = thread.getChildrenList();
                printChildren(thread.getChildrenRefsList(), total, flat, 0, new ArrayList<>(), new int[] {TREE_BUDGET});
                printSubtree("tick pipeline", flat, total, "net.minecraft.server.MinecraftServer", "m_5703_");
                printSubtree("world tick", flat, total, "net.minecraft.server.level.ServerLevel", "m_8793_");
                printSubtree("entity tick", flat, total, "net.minecraft.server.level.ServerLevel", "m_8647_");
                printSubtree("Forge event post", flat, total, "net.minecraftforge.eventbus.EventBus", "post");
                printSubtree("Spore infected AI", flat, total,
                        "com.Harbinger.Spore.Sentities.BaseEntities.Infected", "m_8107_");
                printSubtree("GastGeber tick", flat, total,
                        "com.Harbinger.Spore.Sentities.Utility.GastGeber", "m_8119_");
                printSubtree("Mound tick", flat, total,
                        "com.Harbinger.Spore.Sentities.Organoids.Mound", "m_8119_");
                printSubtree("Sieger tick", flat, total,
                        "com.Harbinger.Spore.Sentities.Calamities.Sieger", "m_8119_");
                printOuterPrefix("Spore boundary branches", flat, thread.getChildrenRefsList(), total, "com.Harbinger.Spore.");
                printOuterPrefix("sporesrp boundary branches", flat, thread.getChildrenRefsList(), total, "com.maha_fish.sporesrp.");
                printOuterPrefix("TerritoryControl boundary branches", flat, thread.getChildrenRefsList(), total, "com.arxyt.territorycontrol.");
                printOuterPrefix("Orcz boundary branches", flat, thread.getChildrenRefsList(), total, "com.duke.orcz.");
                printOuterPrefix("Goblins Tyranny boundary branches", flat, thread.getChildrenRefsList(), total, "goblinstyranny.");
                printOuterPrefix("Superb Warfare boundary branches", flat, thread.getChildrenRefsList(), total, "com.atsuishio.superbwarfare.");
                printOuterPrefix("Dominion Sword boundary branches", flat, thread.getChildrenRefsList(), total, "com.arxyt.dominionsword.");
                printOuterPrefix("United Tribes boundary branches", flat, thread.getChildrenRefsList(), total, "com.exhuashan.unitedtribes.");
                printMatchingMethods("cross-mod hot-path matches", flat, total,
                        "tacz$onTickServerSide", "com.tacz.guns.entity.sync.",
                        "net.snackbag.tt20.", "com.atsuishio.superbwarfare.",
                        "com.arxyt.dominionsword.", "com.duke.orcz.",
                        "com.exhuashan.unitedtribes.");
                printHotClasses(flat, total);
            }
        }
    }

    private static InputStream open(String source) throws Exception {
        if (source.startsWith("http://") || source.startsWith("https://")) return URI.create(source).toURL().openStream();
        return Files.newInputStream(Path.of(source));
    }

    private static void printChildren(List<Integer> refs, double rootTime,
                                      List<SparkSamplerProtos.StackTraceNode> flat, int depth,
                                      List<Integer> lineage, int[] remaining) {
        if (depth >= TREE_DEPTH || refs.isEmpty() || remaining[0] <= 0) return;
        List<Integer> sorted = refs.stream()
                .filter(index -> index >= 0 && index < flat.size())
                .sorted(Comparator.comparingDouble((Integer index) -> nodeTime(flat.get(index))).reversed())
                .limit(TREE_WIDTH)
                .toList();
        for (int index : sorted) {
            if (lineage.contains(index) || remaining[0]-- <= 0) continue;
            SparkSamplerProtos.StackTraceNode node = flat.get(index);
            double ownTime = nodeTime(node);
            System.out.printf(Locale.ROOT, "%s%6.2f%% %8.3fms %s.%s%s%n",
                    "  ".repeat(depth + 1), rootTime == 0D ? 0D : ownTime * 100D / rootTime, ownTime,
                    node.getClassName(), node.getMethodName(), node.getLineNumber() == 0 ? "" : ":" + node.getLineNumber());
            List<Integer> nextLineage = new ArrayList<>(lineage);
            nextLineage.add(index);
            printChildren(node.getChildrenRefsList(), rootTime, flat, depth + 1, nextLineage, remaining);
        }
    }

    private static void printSubtree(String label, List<SparkSamplerProtos.StackTraceNode> flat,
                                     double rootTime, String className, String methodName) {
        List<SparkSamplerProtos.StackTraceNode> roots = flat.stream()
                .filter(node -> node.getClassName().equals(className) && node.getMethodName().equals(methodName))
                .sorted(Comparator.comparingDouble(SparkProfileSummary::nodeTime).reversed())
                .limit(1)
                .toList();
        if (roots.isEmpty()) return;
        SparkSamplerProtos.StackTraceNode root = roots.get(0);
        double time = nodeTime(root);
        System.out.printf(Locale.ROOT, "  %s: %6.2f%% %8.3fms %s.%s%n", label,
                rootTime == 0D ? 0D : time * 100D / rootTime, time, className, methodName);
        printChildren(root.getChildrenRefsList(), time, flat, 0, new ArrayList<>(), new int[] {60});
    }

    private static void printOuterPrefix(String label, List<SparkSamplerProtos.StackTraceNode> flat,
                                         List<Integer> refs, double rootTime, String prefix) {
        Map<String, Double> totals = new HashMap<>();
        collectOuterPrefix(flat, refs, prefix, false, new ArrayList<>(), totals);
        if (totals.isEmpty()) return;
        System.out.println("  " + label + " (non-overlapping outer calls):");
        totals.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(12)
                .forEach(entry -> System.out.printf(Locale.ROOT, "    %6.2f%% %8.3fms %s%n",
                        rootTime == 0D ? 0D : entry.getValue() * 100D / rootTime, entry.getValue(), entry.getKey()));
    }

    private static void collectOuterPrefix(List<SparkSamplerProtos.StackTraceNode> flat, List<Integer> refs,
                                           String prefix, boolean insidePrefix, List<Integer> lineage,
                                           Map<String, Double> totals) {
        for (int index : refs) {
            if (index < 0 || index >= flat.size() || lineage.contains(index)) continue;
            SparkSamplerProtos.StackTraceNode node = flat.get(index);
            boolean matches = node.getClassName().startsWith(prefix);
            if (matches && !insidePrefix) {
                totals.merge(node.getClassName() + '.' + node.getMethodName(), nodeTime(node), Double::sum);
                continue;
            }
            List<Integer> nextLineage = new ArrayList<>(lineage);
            nextLineage.add(index);
            collectOuterPrefix(flat, node.getChildrenRefsList(), prefix, insidePrefix || matches, nextLineage, totals);
        }
    }

    private static void printHotClasses(List<SparkSamplerProtos.StackTraceNode> nodes, double rootTime) {
        Map<String, Double> totals = new HashMap<>();
        for (SparkSamplerProtos.StackTraceNode node : nodes) {
            String key = node.getClassName() + '.' + node.getMethodName();
            totals.merge(key, nodeTime(node), Double::sum);
        }
        System.out.println("  top sampled methods (inclusive occurrences; paths may overlap):");
        totals.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(20)
                .forEach(entry -> System.out.printf(Locale.ROOT, "    %6.2f%% %8.3fms %s%n",
                        rootTime == 0D ? 0D : entry.getValue() * 100D / rootTime, entry.getValue(), entry.getKey()));
    }

    private static void printMatchingMethods(String label, List<SparkSamplerProtos.StackTraceNode> nodes,
                                             double rootTime, String... fragments) {
        Map<String, Double> totals = new HashMap<>();
        for (SparkSamplerProtos.StackTraceNode node : nodes) {
            String key = node.getClassName() + '.' + node.getMethodName();
            for (String fragment : fragments) {
                if (key.contains(fragment)) {
                    totals.merge(key, nodeTime(node), Double::sum);
                    break;
                }
            }
        }
        if (totals.isEmpty()) return;
        System.out.println("  " + label + " (inclusive occurrences; paths may overlap):");
        totals.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(40)
                .forEach(entry -> System.out.printf(Locale.ROOT, "    %6.2f%% %8.3fms %s%n",
                        rootTime == 0D ? 0D : entry.getValue() * 100D / rootTime, entry.getValue(), entry.getKey()));
    }

    private static double nodeTime(SparkSamplerProtos.StackTraceNode node) {
        return time(node.getTimesList());
    }

    private static double time(List<Double> windows) {
        double result = 0D;
        for (double value : windows) result += value;
        return result;
    }

    private SparkProfileSummary() {}
}
