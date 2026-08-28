package com.arxyt.sporeperformance.ai;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.CalamityTrace;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Per-level coordination for the navigation shared by all Spore calamities.
 *
 * <p>This object intentionally retains only UUIDs, immutable positions and primitive path
 * progress values.  It never retains entities, levels or path navigation instances after an
 * entity leaves the level.  It is therefore safe to clear on each level unload without keeping
 * an unloaded chunk alive.</p>
 */
public final class CalamityNavigationRuntime {
    private final ServerLevel level;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<String, Counters> counters = new HashMap<>();

    CalamityNavigationRuntime(ServerLevel level) {
        this.level = level;
    }

    public static boolean enabled(Calamity calamity) {
        return PerformanceConfig.REFACTOR_AI_ENABLED.get()
                && PerformanceConfig.REFACTOR_NAVIGATION_ENABLED.get()
                && PerformanceConfig.REFACTOR_CALAMITY_NAVIGATION_ENABLED.get()
                && !excluded(calamity);
    }

    public static boolean excluded(Calamity calamity) {
        return PerformanceConfig.REFACTOR_CALAMITY_EXCLUDE_VERFALLDRACHEN.get()
                && calamity.getClass().getName().toLowerCase(Locale.ROOT).contains("verfalldrachen");
    }

    /** Called from the living tick after normal entity movement has had one tick to progress. */
    public void tick(Calamity calamity) {
        if (!enabled(calamity)) {
            remove(calamity.getUUID());
            return;
        }
        final long now = level.getGameTime();
        final State state = states.computeIfAbsent(calamity.getUUID(), ignored -> State.initial(calamity, now));
        final PathNavigation navigation = calamity.getNavigation();
        final LivingEntity livingTarget = calamity.getTarget();
        final BlockPos search = normalizedSearch(calamity.getSearchArea());
        final BlockPos navTarget = navigation.getTargetPos();
        final Path path = navigation.getPath();
        final boolean activePath = path != null && !path.isDone();
        final Vec3 nextPathNode = activePath ? nextPathNode(path) : null;
        final long routeSignature = activePath ? routeSignature(path) : Long.MIN_VALUE;
        /*
         * PathNavigation deliberately retains targetPos after a path completes.  It is a
         * historical destination, not an instruction to move.  Treating that stale value as a
         * live intent made idle calamities enter recovery every noProgressTicks, which in turn
         * repeatedly changed their wanted heading.  Only a real entity/SearchArea or an active
         * path is permitted to participate in the progress state machine.
         */
        final ObservedTarget observed = ObservedTarget.of(livingTarget, search, activePath ? navTarget : null);

        boolean targetChanged = targetChanged(state, observed);
        // A fast flying target can move more than two blocks between samples. Comparing it
        // with the last pre-backoff sample would wake recovery every tick and recreate the
        // rotate -> backoff -> rearm loop. Same-UUID motion during backoff is therefore measured
        // from the position that caused the backoff; UUID/kind changes remain immediate.
        if (now < state.backoffUntil && targetChanged
                && !materialBackoffTargetChange(state, observed, calamity)) {
            targetChanged = false;
        }
        boolean hit = calamity.hurtTime > 0;
        if (targetChanged || hit) {
            clearBackoff(state, calamity, targetChanged ? "target_changed" : "hurt");
            state.recoveryStage = 0;
            state.intent = observed.intentName();
            state.holdNavigationYaw = false;
            state.accumulatedUnproductiveTurn = 0.0F;
            state.routeSignature = Long.MIN_VALUE;
        }

        if (now < state.backoffUntil) {
            // A retrying Goal may still call moveTo while recovery is backing off.  Keep
            // backoff authoritative for the whole tick and hold the yaw captured when it
            // started; otherwise the movement controller can keep turning a stationary body.
            state.holdNavigationYaw = true;
            if (!state.heldYawValid) {
                state.heldYaw = calamity.getYRot();
                state.heldYawValid = true;
            }
            navigation.stop();
            state.lastPosition = calamity.position();
            CalamityTrace.INSTANCE.navigationRuntime(calamity, "backoff_active",
                    "until=" + state.backoffUntil + ",intent=" + state.intent);
            PerformanceMetrics.increment("ai_refactor.calamity.backoff_ticks");
            return;
        }

        // A native CalamityPathNavigation deliberately keeps driving toward an entity when
        // A* cannot supply a Path.  That direct-approach fallback is legitimate work, but only
        // for the short lease created by the move request that produced it.  A completed path's
        // old targetPos is never enough by itself.
        boolean hasWork = activePath || state.hasDirectApproach(livingTarget, now) || search != null;
        int nextNode = path == null ? -1 : path.getNextNodeIndex();
        double movedSqr = horizontalDistanceSqr(state.lastPosition, calamity.position());
        boolean nodeAdvanced = nextNode != state.lastNode && nextNode >= 0;
        boolean moved = movedSqr >= movementThresholdSqr(calamity);
        float yawTurn = Math.abs(net.minecraft.util.Mth.wrapDegrees(calamity.getYRot() - state.lastYaw));
        String yawOwner = yawOwner(calamity);
        if (!yawOwner.equals(state.yawOwner)) {
            state.yawOwner = yawOwner;
            CalamityTrace.INSTANCE.navigationRuntime(calamity, "yaw_owner",
                    "owner=" + yawOwner + ",finalYaw=" + round(calamity.getYRot()));
        }
        if (!moved && yawTurn >= 45.0F) {
            Counters count = counters.computeIfAbsent(calamity.getType().toString(), ignored -> new Counters());
            ++count.lowMovementHighTurn;
            PerformanceMetrics.increment("ai_refactor.calamity.low_movement_high_turn");
            CalamityTrace.INSTANCE.navigationRuntime(calamity, "yaw_without_progress",
                    "turn=" + round(yawTurn) + ",owner=" + yawOwner + ",node=" + nextNode);
        }

        boolean routeChanged = activePath && routeSignature != state.routeSignature;
        if (routeChanged) {
            state.routeSignature = routeSignature;
            state.bestRouteDistance = nextPathNode == null ? Double.POSITIVE_INFINITY
                    : horizontalDistance(nextPathNode, calamity.position());
            state.lastRouteProgressTick = now;
            state.accumulatedUnproductiveTurn = 0.0F;
            // A recovery replan is still under the circular-route guard. Releasing the body
            // yaw merely because the replacement Path object has a new signature lets the same
            // impossible node rotate the entity again before progress is observed.
            if (state.recoveryStage == 0) state.holdNavigationYaw = false;
            CalamityTrace.INSTANCE.navigationRuntime(calamity, "route_changed",
                    "node=" + nextNode + ",distance=" + round(state.bestRouteDistance));
        }
        boolean routeDistanceImproved = nextPathNode != null
                && CalamityNavigationPolicy.improvesRouteDistance(state.bestRouteDistance,
                horizontalDistance(nextPathNode, calamity.position()));
        if (routeDistanceImproved) {
            state.bestRouteDistance = horizontalDistance(nextPathNode, calamity.position());
            state.lastRouteProgressTick = now;
            state.accumulatedUnproductiveTurn = 0.0F;
            state.holdNavigationYaw = false;
        } else if (activePath && !nodeAdvanced) {
            state.accumulatedUnproductiveTurn += yawTurn;
        }

        if (!hasWork) {
            state.lastProgressTick = now;
            state.recoveryStage = 0;
            state.holdNavigationYaw = false;
            state.accumulatedUnproductiveTurn = 0.0F;
        } else if (nodeAdvanced || routeDistanceImproved) {
            state.lastProgressTick = now;
            if (nodeAdvanced) {
                state.recoveryStage = 0;
                state.failures = 0;
                state.holdNavigationYaw = false;
                state.accumulatedUnproductiveTurn = 0.0F;
            }
            if (nodeAdvanced || routeDistanceImproved) {
                CalamityTrace.INSTANCE.navigationRuntime(calamity, "progress",
                        "node=" + nextNode + ",routeDistance=" + round(state.bestRouteDistance)
                                + ",moved=" + round(Math.sqrt(movedSqr)) + ",intent=" + state.intent);
            }
        } else if (activePath && PerformanceConfig.REFACTOR_CALAMITY_PROGRESS_RECOVERY.get()
                && CalamityNavigationPolicy.isCircularSteering(
                now - state.lastRouteProgressTick, state.accumulatedUnproductiveTurn)) {
            /*
             * Do not merely slow the rotation.  The route has proven that it is continually
             * changing the requested heading without getting closer to its next node, so stop
             * that route and hand it to the existing controlled recovery state machine.
             */
            state.holdNavigationYaw = true;
            Counters count = counters.computeIfAbsent(calamity.getType().toString(), ignored -> new Counters());
            ++count.circularRoutes;
            PerformanceMetrics.increment("ai_refactor.calamity.circular_route_detected");
            CalamityTrace.INSTANCE.navigationRuntime(calamity, "circular_route",
                    "node=" + nextNode + ",distance=" + round(nextPathNode == null ? -1.0D
                            : horizontalDistance(nextPathNode, calamity.position())) + ",turn="
                            + round(state.accumulatedUnproductiveTurn));
            recover(calamity, state, livingTarget, search, navTarget, now);
        } else if (PerformanceConfig.REFACTOR_CALAMITY_PROGRESS_RECOVERY.get()
                && CalamityNavigationPolicy.noProgress(now, state.lastProgressTick,
                PerformanceConfig.REFACTOR_CALAMITY_NO_PROGRESS_TICKS.get())) {
            recover(calamity, state, livingTarget, search, navTarget, now);
        }

        state.lastNode = nextNode;
        state.lastPosition = calamity.position();
        state.lastChunk = calamity.chunkPosition().toLong();
        state.lastYaw = calamity.getYRot();
        state.observedTarget = observed;
        state.lastSeen = now;
    }

    /** Records the source of a dynamic move request before the navigator is asked for a path. */
    public void submitEntityIntent(Calamity calamity, Entity target, double speed) {
        if (!enabled(calamity)) return;
        final long now = level.getGameTime();
        State state = states.computeIfAbsent(calamity.getUUID(), ignored -> State.initial(calamity, now));
        // Do not let a still-running Goal re-arm navigation or clear recovery merely because
        // it retries the same moveTo during backoff.  The navigation mixin rejects the actual
        // move request as well; keeping the state unchanged prevents entity/position intent
        // alternation from producing a target_changed loop.
        if (now < state.backoffUntil) {
            PerformanceMetrics.increment("ai_refactor.calamity.move_request_suppressed_backoff");
            CalamityTrace.INSTANCE.navigationRuntime(calamity, "intent_suppressed_backoff",
                    "source=entity_move_to,target=" + target.getUUID() + ",until=" + state.backoffUntil);
            return;
        }
        state.intent = "entity_move_to";
        state.requestedSpeed = speed;
        if (!target.getUUID().equals(state.requestedTargetId)
                || state.requestedPosition == null
                || state.requestedPosition.distanceToSqr(target.position()) > 4.0D
                || calamity.hurtTime > 0) {
            clearBackoff(state, calamity, "entity_intent");
        }
        state.requestedTargetId = target.getUUID();
        state.requestedPosition = target.position();
        CalamityTrace.INSTANCE.navigationRuntime(calamity, "intent",
                "source=entity_move_to,target=" + target.getUUID() + ",speed=" + round(speed));
    }

    /**
     * Retains the native direct-approach contract when an entity path cannot be built.  The
     * old navigation returns true in this case and keeps its private fallback target; reporting
     * false here made goals retry out of phase while that fallback was still steering the mob.
     */
    public boolean recordEntityPathResult(Calamity calamity, Entity target, @Nullable Path path, double speed) {
        if (!enabled(calamity)) return path != null;
        State state = states.computeIfAbsent(calamity.getUUID(), ignored -> State.initial(calamity, level.getGameTime()));
        state.requestedSpeed = speed;
        state.requestedTargetId = target.getUUID();
        state.requestedPosition = target.position();
        if (path != null) {
            state.directUntil = 0L;
            state.directTargetId = null;
            return true;
        }
        // The lease is renewed only by a real move request.  This lets recovery/backoff take
        // over when native direct steering is unable to make horizontal progress.
        state.directTargetId = target.getUUID();
        state.directUntil = level.getGameTime() + Math.max(20L,
                PerformanceConfig.REFACTOR_CALAMITY_NO_PROGRESS_TICKS.get() * 2L);
        state.intent = "entity_direct_approach";
        CalamityTrace.INSTANCE.navigationRuntime(calamity, "direct_approach",
                "target=" + target.getUUID() + ",leaseUntil=" + state.directUntil + ",speed=" + round(speed));
        PerformanceMetrics.increment("ai_refactor.calamity.direct_approach");
        return true;
    }

    /** Records a static SearchArea/path request. */
    public void submitPositionIntent(Calamity calamity, BlockPos target, double speed, String source) {
        if (!enabled(calamity)) return;
        final long now = level.getGameTime();
        State state = states.computeIfAbsent(calamity.getUUID(), ignored -> State.initial(calamity, now));
        if (now < state.backoffUntil) {
            PerformanceMetrics.increment("ai_refactor.calamity.move_request_suppressed_backoff");
            CalamityTrace.INSTANCE.navigationRuntime(calamity, "intent_suppressed_backoff",
                    "source=" + source + ",target=" + target.getX() + ',' + target.getY() + ','
                            + target.getZ() + ",until=" + state.backoffUntil);
            return;
        }
        if (state.recoveryLeg) {
            state.requestedSpeed = speed;
            CalamityTrace.INSTANCE.navigationRuntime(calamity, "intent",
                    "source=recovery_leg_path,target=" + target.getX() + ',' + target.getY() + ',' + target.getZ());
            return;
        }
        state.intent = source;
        state.requestedSpeed = speed;
        Vec3 point = Vec3.atCenterOf(target);
        if (state.requestedPosition == null || state.requestedPosition.distanceToSqr(point) > 4.0D) {
            clearBackoff(state, calamity, "position_intent");
        }
        state.requestedTargetId = null;
        state.requestedPosition = point;
        CalamityTrace.INSTANCE.navigationRuntime(calamity, "intent",
                "source=" + source + ",target=" + target.getX() + ',' + target.getY() + ',' + target.getZ() + ",speed=" + round(speed));
    }

    /** Native CalamityPathNavigation must not recompute for every isStuck() call. */
    public boolean suppressNativeStuckRecompute(Calamity calamity) {
        if (!enabled(calamity)) return false;
        State state = states.computeIfAbsent(calamity.getUUID(), ignored -> State.initial(calamity, level.getGameTime()));
        if (level.getGameTime() < state.backoffUntil) {
            PerformanceMetrics.increment("ai_refactor.calamity.native_stuck_suppressed_backoff");
        } else {
            PerformanceMetrics.increment("ai_refactor.calamity.native_stuck_suppressed");
        }
        return true;
    }

    /**
     * Spore's ExpPathFinder may leave one or more first nodes inside a calamity's own collision
     * volume.  A small vanilla mob can walk onto such a node, but a 10+ block calamity instead
     * keeps receiving MOVE_TO for a point it has already reached and rotates around it forever.
     * Advance those semantically completed nodes before CalamityPathNavigation hands the path to
     * its movement controller.  The destination itself is never discarded unless it too is in
     * the proper arrival volume; the original direct-destination fallback then remains intact.
     */
    public int advanceArrivedPathNodes(Calamity calamity, PathNavigation navigation) {
        if (!enabled(calamity)) return 0;
        Path path = navigation.getPath();
        if (path == null || path.isDone()) return 0;
        // A path node represents a block cell, while the movement controller steers the
        // entity's centre.  For a 3.5-wide Gazenbreacher, a node ~2.1 blocks away is already
        // enclosed by its collision volume plus the cell margin; using only half the width
        // leaves that node as an impossible MOVE_TO target and makes the body orbit it.
        double horizontalArrival = Math.max(0.75D, calamity.getBbWidth() * 0.5D + 0.75D);
        double verticalArrival = Math.max(2.0D, calamity.getBbHeight() * 0.5D);
        int advanced = 0;
        while (!path.isDone()) {
            int index = path.getNextNodeIndex();
            if (index < 0 || index >= path.getNodeCount()) break;
            Node node = path.getNode(index);
            double dx = (node.x + 0.5D) - calamity.getX();
            double dz = (node.z + 0.5D) - calamity.getZ();
            if (dx * dx + dz * dz > horizontalArrival * horizontalArrival
                    || Math.abs(node.y - calamity.getY()) > verticalArrival) break;
            path.advance();
            ++advanced;
        }
        if (advanced > 0) {
            State state = states.computeIfAbsent(calamity.getUUID(), ignored -> State.initial(calamity, level.getGameTime()));
            state.lastNode = path.getNextNodeIndex();
            state.lastProgressTick = level.getGameTime();
            state.lastRouteProgressTick = level.getGameTime();
            state.accumulatedUnproductiveTurn = 0.0F;
            state.holdNavigationYaw = false;
            Counters count = counters.computeIfAbsent(calamity.getType().toString(), ignored -> new Counters());
            count.arrivedNodesSkipped += advanced;
            for (int index = 0; index < advanced; ++index) {
                PerformanceMetrics.increment("ai_refactor.calamity.arrived_nodes_skipped");
            }
            CalamityTrace.INSTANCE.navigationRuntime(calamity, "arrived_nodes_skipped",
                    "count=" + advanced + ",next=" + path.getNextNodeIndex() + ",radius=" + round(horizontalArrival));
        }
        return advanced;
    }

    /** Suppresses CalamityPathNavigation's direct fallback during a controlled backoff. */
    public boolean suppressNavigationTick(Calamity calamity) {
        State state = states.get(calamity.getUUID());
        return enabled(calamity) && state != null && level.getGameTime() < state.backoffUntil;
    }

    /**
     * True while a Goal is trying to re-submit navigation during a controlled retry backoff.
     * This is checked before PathNavigation.createPath/moveTo, because Goals can run before the
     * navigator's own tick suppression is reached.
     */
    public boolean suppressMoveRequest(Calamity calamity, @Nullable Entity target) {
        State state = states.get(calamity.getUUID());
        if (!enabled(calamity) || state == null || level.getGameTime() >= state.backoffUntil) return false;
        PerformanceMetrics.increment("ai_refactor.calamity.move_request_suppressed_backoff");
        CalamityTrace.INSTANCE.navigationRuntime(calamity, "move_request_suppressed_backoff",
                "target=" + (target == null ? "position" : target.getUUID()) + ",until=" + state.backoffUntil);
        return true;
    }

    /** Position-path requests obey the same hard backoff as entity moveTo requests. */
    public boolean suppressPositionRequest(Calamity calamity) {
        return suppressMoveRequest(calamity, null);
    }

    /** The SmoothLookControl mixin asks this before restoring movement-owned body yaw. */
    public boolean ownBodyYaw(Calamity calamity) {
        return enabled(calamity) && PerformanceConfig.REFACTOR_CALAMITY_SINGLE_YAW_OWNER.get();
    }

    /**
     * Called from Spore's actual movement controllers.  This is deliberately a binary guard,
     * not a turn-rate limiter: while a route is known to be circular it has no authority to
     * change the body yaw at all.  A new route or actual progress releases the guard.
     */
    public float navigationYaw(Calamity calamity, float requestedYaw) {
        if (!ownBodyYaw(calamity)) return requestedYaw;
        State state = states.get(calamity.getUUID());
        if (state == null || !state.holdNavigationYaw) return requestedYaw;
        Counters count = counters.computeIfAbsent(calamity.getType().toString(), ignored -> new Counters());
        ++count.suppressedCircularYaw;
        PerformanceMetrics.increment("ai_refactor.calamity.circular_yaw_suppressed");
        CalamityTrace.INSTANCE.navigationRuntime(calamity, "circular_yaw_suppressed",
                "requested=" + round(requestedYaw) + ",kept=" + round(state.heldYawValid ? state.heldYaw : calamity.getYRot()));
        return state.heldYawValid ? state.heldYaw : calamity.getYRot();
    }

    public void remove(UUID id) {
        states.remove(id);
    }

    public void removeChunk(long chunk) {
        states.entrySet().removeIf(entry -> entry.getValue().lastChunk == chunk);
    }

    public void clear() {
        states.clear();
        counters.clear();
    }

    public int tracked() { return states.size(); }

    public String status() {
        StringBuilder result = new StringBuilder("calamityNavigation{tracked=").append(states.size());
        if (!counters.isEmpty()) {
            result.append(", types=");
            boolean first = true;
            for (Map.Entry<String, Counters> entry : counters.entrySet()) {
                if (!first) result.append('|');
                Counters count = entry.getValue();
                result.append(entry.getKey()).append("[stuck=").append(count.stuck)
                        .append(",recompute=").append(count.recompute).append(",waypoint=").append(count.waypoint)
                        .append(",backoff=").append(count.backoff).append(",lowMoveHighTurn=")
                        .append(count.lowMovementHighTurn).append(",circular=").append(count.circularRoutes)
                        .append(",yawSuppressed=").append(count.suppressedCircularYaw)
                        .append(",arrivedNodes=").append(count.arrivedNodesSkipped).append(']');
                first = false;
            }
        }
        return result.append('}').toString();
    }

    private void recover(Calamity calamity, State state, @Nullable LivingEntity livingTarget,
                         @Nullable BlockPos search, @Nullable BlockPos navTarget, long now) {
        PathNavigation navigation = calamity.getNavigation();
        Counters count = counters.computeIfAbsent(calamity.getType().toString(), ignored -> new Counters());
        ++count.stuck;
        PerformanceMetrics.increment("ai_refactor.calamity.stuck_detected");
        CalamityTrace.INSTANCE.navigationRuntime(calamity, "stuck",
                "stage=" + state.recoveryStage + ",node=" + state.lastNode + ",intent=" + state.intent);
        state.lastProgressTick = now;
        // A recovery leg gets its own observation window.  Without resetting this timestamp,
        // the next tick would immediately classify the just-replanned route as circular again.
        state.lastRouteProgressTick = now;
        state.accumulatedUnproductiveTurn = 0.0F;
        navigation.stop();

        if (state.recoveryStage == 0) {
            state.recoveryStage = 1;
            navigation.recomputePath();
            ++count.recompute;
            PerformanceMetrics.increment("ai_refactor.calamity.recompute");
            CalamityTrace.INSTANCE.navigationRuntime(calamity, "recovery_recompute", "source=progress_state_machine");
            return;
        }

        if (state.recoveryStage == 1) {
            state.recoveryStage = 2;
            BlockPos desired = livingTarget != null ? dynamicSideStep(calamity, livingTarget)
                    : staticWaypoint(calamity, search != null ? search : navTarget);
            if (desired != null) {
                double speed = state.requestedSpeed > 0.0D ? state.requestedSpeed : 1.0D;
                // Keep the original entity/SearchArea in State. The alternate point is only a
                // recovery leg; treating it as a new destination would reset the next stage on
                // the following tick and reproduce the retry loop we are trying to eliminate.
                state.intent = livingTarget == null ? "static_recovery_waypoint" : "dynamic_recovery_side_step";
                state.recoveryLeg = true;
                try {
                    navigation.moveTo(desired.getX() + 0.5D, desired.getY(), desired.getZ() + 0.5D, speed);
                } finally {
                    state.recoveryLeg = false;
                }
                ++count.waypoint;
                PerformanceMetrics.increment("ai_refactor.calamity.recovery_waypoint");
                CalamityTrace.INSTANCE.navigationRuntime(calamity, "recovery_waypoint",
                        "target=" + desired.getX() + ',' + desired.getY() + ',' + desired.getZ());
                return;
            }
        }

        int failures = Math.min(3, state.failures + 1);
        state.failures = failures;
        int delay = CalamityNavigationPolicy.retryDelay(failures, PerformanceConfig.REFACTOR_CALAMITY_RETRY_TICKS.get(),
                PerformanceConfig.REFACTOR_CALAMITY_MAX_RETRY_TICKS.get());
        state.backoffUntil = now + delay;
        state.holdNavigationYaw = true;
        state.heldYaw = calamity.getYRot();
        state.heldYawValid = true;
        state.backoffTargetPosition = livingTarget != null ? livingTarget.position()
                : search != null ? Vec3.atCenterOf(search) : null;
        state.recoveryStage = 0;
        ++count.backoff;
        PerformanceMetrics.increment("ai_refactor.calamity.backoff_started");
        CalamityTrace.INSTANCE.navigationRuntime(calamity, "backoff",
                "delay=" + delay + ",failures=" + failures + ",intent=" + state.intent);
    }

    @Nullable
    private static BlockPos staticWaypoint(Calamity calamity, @Nullable BlockPos target) {
        if (target == null) return null;
        int radius = PerformanceConfig.REFACTOR_CALAMITY_RECOVERY_WAYPOINT_RADIUS.get();
        int seed = calamity.getUUID().hashCode();
        int x = ((seed & 1) == 0 ? radius : -radius);
        int z = ((seed & 2) == 0 ? radius : -radius);
        return target.offset(x, 0, z);
    }

    private static BlockPos dynamicSideStep(Calamity calamity, LivingEntity target) {
        double dx = target.getX() - calamity.getX();
        double dz = target.getZ() - calamity.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001D) {
            dx = 1.0D;
            dz = 0.0D;
            length = 1.0D;
        }
        int radius = PerformanceConfig.REFACTOR_CALAMITY_RECOVERY_WAYPOINT_RADIUS.get();
        double sign = (calamity.getUUID().getLeastSignificantBits() & 1L) == 0L ? 1.0D : -1.0D;
        return BlockPos.containing(calamity.getX() - dz / length * radius * sign,
                calamity.getY(), calamity.getZ() + dx / length * radius * sign);
    }

    private static boolean targetChanged(State state, ObservedTarget current) {
        ObservedTarget previous = state.observedTarget;
        if (previous.kind != current.kind) return true;
        if (current.targetId != null && !current.targetId.equals(previous.targetId)) return true;
        if (current.search != null && !current.search.equals(previous.search)) return true;
        // Entity targets such as phantoms can legitimately move several blocks per tick. Treat
        // every sample as a new destination and recovery will never get past its first stage.
        // UUID changes, target loss and search-point changes above remain immediate; same-UUID
        // motion only invalidates a route after a material eight-block relocation.
        if (current.targetId != null && current.targetId.equals(previous.targetId)) {
            return current.position != null && previous.position != null
                    && current.position.distanceToSqr(previous.position) > 64.0D;
        }
        return current.position != null && previous.position != null
                && current.position.distanceToSqr(previous.position) > 4.0D;
    }

    private static boolean materialBackoffTargetChange(State state, ObservedTarget current,
                                                        Calamity calamity) {
        ObservedTarget previous = state.observedTarget;
        if (previous.kind != current.kind) return true;
        if (current.targetId != null && !current.targetId.equals(previous.targetId)) return true;
        if (current.search != null && !current.search.equals(previous.search)) return true;
        if (current.position == null || state.backoffTargetPosition == null) return true;
        // This is deliberately larger than the normal two-block per-sample invalidation. It
        // still wakes the state machine when the destination genuinely relocates, while a fast
        // phantom circling a calamity cannot clear backoff every tick.
        double wakeDistance = Math.max(8.0D, calamity.getBbWidth() * 1.5D);
        return current.position.distanceToSqr(state.backoffTargetPosition)
                > wakeDistance * wakeDistance;
    }

    private static void clearBackoff(State state, Calamity calamity, String reason) {
        if (state.backoffUntil > 0L) {
            CalamityTrace.INSTANCE.navigationRuntime(calamity, "backoff_cleared", "reason=" + reason);
            PerformanceMetrics.increment("ai_refactor.calamity.backoff_cleared");
        }
        state.backoffUntil = 0L;
        state.failures = 0;
        state.backoffTargetPosition = null;
        state.heldYawValid = false;
        state.heldYaw = 0.0F;
    }

    @Nullable
    private static BlockPos normalizedSearch(@Nullable BlockPos value) {
        return value == null || value.equals(BlockPos.ZERO) ? null : value.immutable();
    }

    private static double movementThresholdSqr(Calamity calamity) {
        // This is a per-tick *progress detector*, not an arrival radius.  The previous
        // width-scaled 0.025–0.35 block threshold classified large calamities moving at their
        // normal 0.04–0.15 block/tick speed as stationary and repeatedly sent them into
        // recovery.  Use a small noise floor and keep the upper bound below normal movement.
        return CalamityNavigationPolicy.progressThresholdSqr(calamity.getBbWidth());
    }

    private static double horizontalDistanceSqr(@Nullable Vec3 previous, Vec3 current) {
        if (previous == null) return Double.MAX_VALUE;
        double x = previous.x - current.x;
        double z = previous.z - current.z;
        return x * x + z * z;
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return Math.sqrt(x * x + z * z);
    }

    @Nullable
    private static Vec3 nextPathNode(Path path) {
        int index = path.getNextNodeIndex();
        if (index < 0 || index >= path.getNodeCount()) return null;
        Node node = path.getNode(index);
        return new Vec3(node.x + 0.5D, node.y, node.z + 0.5D);
    }

    private static long routeSignature(Path path) {
        int index = path.getNextNodeIndex();
        Node node = index >= 0 && index < path.getNodeCount() ? path.getNode(index) : null;
        long result = 31L * path.getNodeCount() + index;
        result = 31L * result + path.getTarget().asLong();
        if (node != null) {
            result = 31L * result + BlockPos.asLong(node.x, node.y, node.z);
        }
        return result;
    }

    private static String round(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String yawOwner(Calamity calamity) {
        String name = calamity.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (name.contains("hinderburg")) return "hinden_look_control";
        if (name.contains("hohlfresser")) return "underground_move_control";
        if (name.contains("stahl")) return "wall_move_control";
        if (name.contains("grakensenker")) return "hybrid_skill_or_move_control";
        return "calamity_move_control";
    }

    private static final class State {
        private Vec3 lastPosition;
        private ObservedTarget observedTarget = ObservedTarget.NONE;
        private Vec3 requestedPosition;
        private UUID requestedTargetId;
        private UUID directTargetId;
        private long directUntil;
        private long lastProgressTick;
        private long lastSeen;
        private long backoffUntil;
        private long lastChunk;
        private int lastNode = -1;
        private int recoveryStage;
        private int failures;
        private boolean recoveryLeg;
        private boolean holdNavigationYaw;
        private boolean heldYawValid;
        private float heldYaw;
        private Vec3 backoffTargetPosition;
        private long routeSignature = Long.MIN_VALUE;
        private long lastRouteProgressTick;
        private double bestRouteDistance = Double.POSITIVE_INFINITY;
        private float accumulatedUnproductiveTurn;
        private float lastYaw;
        private String yawOwner = "initial";
        private double requestedSpeed = 1.0D;
        private String intent = "initial";

        static State initial(Calamity calamity, long now) {
            State result = new State();
            result.lastPosition = calamity.position();
            result.lastProgressTick = now;
            result.lastSeen = now;
            result.lastChunk = calamity.chunkPosition().toLong();
            result.lastYaw = calamity.getYRot();
            result.lastRouteProgressTick = now;
            return result;
        }

        private boolean hasDirectApproach(@Nullable LivingEntity target, long now) {
            return target != null && target.isAlive() && directTargetId != null
                    && directTargetId.equals(target.getUUID()) && now <= directUntil;
        }
    }

    /**
     * Snapshot of an instruction that is actually live on this tick.  Requested paths are kept
     * separately in State: PathNavigation's stale targetPos must never make a completed path
     * appear active again.
     */
    private record ObservedTarget(Kind kind, @Nullable UUID targetId, @Nullable BlockPos search,
                                  @Nullable Vec3 position) {
        private static final ObservedTarget NONE = new ObservedTarget(Kind.NONE, null, null, null);

        static ObservedTarget of(@Nullable LivingEntity living, @Nullable BlockPos search,
                                 @Nullable BlockPos activeNavigationTarget) {
            if (living != null) return new ObservedTarget(Kind.ENTITY, living.getUUID(), null, living.position());
            if (search != null) return new ObservedTarget(Kind.SEARCH_AREA, null, search, Vec3.atCenterOf(search));
            if (activeNavigationTarget != null) {
                return new ObservedTarget(Kind.ACTIVE_PATH, null, activeNavigationTarget,
                        Vec3.atCenterOf(activeNavigationTarget));
            }
            return NONE;
        }

        String intentName() {
            return switch (kind) {
                case ENTITY -> "entity_goal";
                case SEARCH_AREA -> "search_area";
                case ACTIVE_PATH -> "active_path";
                case NONE -> "idle";
            };
        }
    }

    private enum Kind { NONE, ENTITY, SEARCH_AREA, ACTIVE_PATH }

    private static final class Counters {
        private long stuck;
        private long recompute;
        private long waypoint;
        private long backoff;
        private long lowMovementHighTurn;
        private long circularRoutes;
        private long suppressedCircularYaw;
        private long arrivedNodesSkipped;
    }
}
