package com.arxyt.sporeperformance.ai;

import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import net.minecraft.server.level.ServerLevel;
import com.google.common.base.Predicate;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;

import java.util.List;

/** Event-driven threat propagation and stable partner selection over the shared section index. */
public final class FungalGroupCoordinator {
    private final SharedPerceptionService perception;

    FungalGroupCoordinator(SharedPerceptionService perception) { this.perception = perception; }

    public int propagateLinked(Infected source) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get() || !PerformanceConfig.REFACTOR_GROUP_COORDINATION.get()) return -1;
        double range = Math.min(32.0D, source.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE));
        AABB bounds = source.getBoundingBox().inflate(range);
        List<Infected> nearby = perception.candidates(source.level().getGameTime(), source, bounds, Infected.class);
        int changed = 0;
        for (Infected candidate : nearby) {
            if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(candidate)) continue;
            if (candidate.getTarget() == null && source.getTarget() != null && source.getTarget().isAlive()
                    && !source.getTarget().isInvulnerable()) {
                candidate.setTarget(source.getTarget());
                changed++;
            } else if (candidate.getSearchPos() == null && source.getSearchPos() != null) {
                candidate.setSearchPos(source.getSearchPos());
                changed++;
            }
        }
        PerformanceMetrics.increment("ai_refactor.group.broadcasts");
        PerformanceMetrics.add("ai_refactor.group.broadcast_fanout", changed);
        if (DebugTrace.enabled(DebugTrace.Category.GROUP) && source.level() instanceof ServerLevel level)
            DebugTrace.event(DebugTrace.Category.GROUP, level, DebugTrace.trace(source), source,
                    "linked_propagation", "candidates=" + nearby.size() + ",changed=" + changed
                            + ",target=" + (source.getTarget() == null ? "" : source.getTarget().getUUID()));
        return changed;
    }

    public int propagateHurt(Mob source, LivingEntity attacker, Class<?>[] ignored) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get() || !PerformanceConfig.REFACTOR_EVENT_THREATS.get()
                || attacker == null) return -1;
        double range = source.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        AABB bounds = AABB.unitCubeFromLowerCorner(source.position()).inflate(range, 10.0D, range);
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<? extends Mob> nearby = (List) perception.candidates(source.level().getGameTime(), source, bounds, source.getClass());
        int changed = 0;
        for (Mob candidate : nearby) {
            if (candidate == source || candidate.getTarget() != null || candidate.isAlliedTo(attacker)) continue;
            if (source instanceof TamableAnimal tameSource && candidate instanceof TamableAnimal tameCandidate
                    && tameSource.getOwner() != tameCandidate.getOwner()) continue;
            boolean skip = false;
            if (ignored != null) for (Class<?> type : ignored) if (candidate.getClass() == type) { skip = true; break; }
            if (skip) continue;
            candidate.setTarget(attacker);
            changed++;
        }
        PerformanceMetrics.increment("ai_refactor.threat.events");
        PerformanceMetrics.add("ai_refactor.threat.fanout", changed);
        if (DebugTrace.enabled(DebugTrace.Category.THREAT) && source.level() instanceof ServerLevel level)
            DebugTrace.event(DebugTrace.Category.THREAT, level, DebugTrace.trace(source), source,
                    "hurt_propagation", "attacker=" + attacker.getUUID() + ",candidates=" + nearby.size() + ",changed=" + changed);
        return changed;
    }

    public <T extends LivingEntity> T nearestPartner(Infected source, Class<T> type, Predicate<LivingEntity> predicate, double range) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get() || !PerformanceConfig.REFACTOR_GROUP_COORDINATION.get()) return null;
        AABB bounds = source.getBoundingBox().inflate(range);
        T nearest = null;
        double best = Double.MAX_VALUE;
        for (T candidate : perception.candidates(source.level().getGameTime(), source, bounds, type)) {
            if (candidate == source || !candidate.isAlive() || predicate != null && !predicate.apply(candidate)) continue;
            double distance = source.distanceToSqr(candidate);
            if (distance < best || distance == best && nearest != null && candidate.getId() < nearest.getId()) {
                nearest = candidate;
                best = distance;
            }
        }
        PerformanceMetrics.increment("ai_refactor.group.partner_queries");
        if (DebugTrace.enabled(DebugTrace.Category.GROUP) && source.level() instanceof ServerLevel level)
            DebugTrace.event(DebugTrace.Category.GROUP, level, DebugTrace.trace(source), source,
                    "partner_selected", "type=" + type.getName() + ",partner=" + (nearest == null ? "" : nearest.getUUID())
                            + ",distanceSqr=" + best);
        return nearest;
    }

    /** Handles the cheap part of following before vanilla navigation builds a path. */
    public boolean tryDirectFollow(PathNavigation navigation, net.minecraft.world.entity.Entity target, double speed) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get() || !PerformanceConfig.REFACTOR_FOLLOW_GROUP_PATHING.get()
                || !(navigation instanceof PathNavigationView view) || !(view.sporeperformance$getMob() instanceof Infected follower)
                || !(target instanceof LivingEntity partner) || !(follower.level() instanceof ServerLevel level)) return false;
        double distance = follower.distanceToSqr(partner);
        double stopRadius = 3.0D;
        if (PerformanceConfig.REFACTOR_FOLLOW_SIZE_AWARE_ARRIVAL.get()) {
            stopRadius = Math.max(stopRadius, follower.getBbWidth() * 0.5D + partner.getBbWidth() * 0.5D + 0.75D);
        }
        if (distance <= stopRadius * stopRadius) {
            navigation.stop();
            PerformanceMetrics.increment("ai.follow.arrival_stop");
            return true;
        }
        double directDistance = PerformanceConfig.REFACTOR_FOLLOW_DIRECT_STEERING_DISTANCE.get();
        if (distance > directDistance * directDistance) return false;
        double dx = partner.getX() - follower.getX();
        double dy = partner.getY() - follower.getY();
        double dz = partner.getZ() - follower.getZ();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4D) return true;
        double step = Math.min(1.0D, length);
        AABB probe = follower.getBoundingBox().move(dx / length * step, dy / length * step, dz / length * step);
        if (!level.noCollision(follower, probe)) return false;
        follower.getMoveControl().setWantedPosition(partner.getX(), partner.getY(), partner.getZ(), speed);
        PerformanceMetrics.increment("ai.follow.direct_steering");
        return true;
    }

    /** Returns an already-built shared corridor waypoint without creating a new path. */
    public BlockPos sharedWaypoint(Infected follower, net.minecraft.world.entity.Entity target) {
        if (!(follower.level() instanceof ServerLevel level)) return null;
        BlockPos waypoint = FungalAiRuntime.INSTANCE.get(level).paths.corridorWaypoint(follower, target);
        if (waypoint != null) PerformanceMetrics.increment("ai.follow.corridor_waypoint");
        return waypoint;
    }
}
