# Sieger ordinary movement regression

## Evidence and root cause

Spore 2.2.0j uses `CalamityPathNavigation` for `spore:sieger`. The live calamity trace showed ordinary position commands repeatedly returning cached paths from an earlier start position. `FungalPathService` stored a pristine copy with node index zero and returned another index-zero copy on every request while the mob remained in the same four-block cache cell. After Sieger moved beyond the old first node, the next request steered it backward to that node; arrival skipping then advanced the node and forward movement resumed. This produced the observed periodic step-back.

The same trace also showed one-node, non-reaching paths being cached as successful paths. Those paths preserve only an old start node and are useful solely for Spore's direct-destination fallback, so caching them creates stale reverse steering without saving a valid route.

## Correction

- Cached reachable paths now resume at the nearest node in a bounded eight-node forward window.
- Partial/non-reaching paths are returned to the current caller but never stored for later reuse.
- Added regression tests for old-start resumption and bounded scan behavior.
- No combat Goal behavior was changed.

## Verification

- `clean test build` passed, including the two new path-progress tests.
- Version bumped to 1.0.10.
- Live in-world validation is still required after restart.
