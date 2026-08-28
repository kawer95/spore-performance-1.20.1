package com.arxyt.sporeperformance.ai;

/**
 * Small pure policy used by the Hohlfresser multipart safety patch.
 *
 * <p>Spore can transiently publish an allocated but only partially populated
 * segment array while its child UUID is unavailable.  Treat that state as
 * incomplete; never treat an empty/null slot as a usable physical segment.</p>
 */
public final class HohlfresserMultipartPolicy {
    public static boolean hasMissingSegments(Object[] segments) {
        if (segments == null || segments.length == 0) return true;
        for (Object segment : segments) {
            if (segment == null) return true;
        }
        return false;
    }

    public static boolean validIndex(Object[] segments, int index) {
        return segments != null && index >= 0 && index < segments.length && segments[index] != null;
    }

    private HohlfresserMultipartPolicy() {}
}
