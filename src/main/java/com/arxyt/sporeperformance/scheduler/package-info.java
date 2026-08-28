/**
 * Cancellable, server-thread-only fungal work queues. Jobs retain UUIDs and coordinate cursors,
 * never entity or Level references, so chunk unload and server shutdown cannot leak worlds.
 */
package com.arxyt.sporeperformance.scheduler;
