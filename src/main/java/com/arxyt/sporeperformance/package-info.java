/**
 * Spore Performance's bootstrap and public operational surface.
 *
 * <p>Server optimisation state is owned by the scheduler and compatibility packages. Client-only
 * presentation optimisations never flow back into server state. Optional integrations fail closed.
 * This package must not expose or mutate Spore's public API.</p>
 */
package com.arxyt.sporeperformance;
