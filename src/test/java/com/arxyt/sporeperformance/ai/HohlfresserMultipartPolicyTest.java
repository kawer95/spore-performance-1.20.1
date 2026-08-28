package com.arxyt.sporeperformance.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HohlfresserMultipartPolicyTest {
    @Test
    void partiallyRebuiltChainIsNeverTreatedAsUsable() {
        assertTrue(HohlfresserMultipartPolicy.hasMissingSegments(null));
        assertTrue(HohlfresserMultipartPolicy.hasMissingSegments(new Object[0]));
        assertTrue(HohlfresserMultipartPolicy.hasMissingSegments(new Object[] {new Object(), null}));
        assertFalse(HohlfresserMultipartPolicy.hasMissingSegments(new Object[] {new Object(), new Object()}));
    }

    @Test
    void indexMustPointAtAnExistingSegment() {
        Object[] parts = {new Object(), null};
        assertTrue(HohlfresserMultipartPolicy.validIndex(parts, 0));
        assertFalse(HohlfresserMultipartPolicy.validIndex(parts, 1));
        assertFalse(HohlfresserMultipartPolicy.validIndex(parts, -1));
        assertFalse(HohlfresserMultipartPolicy.validIndex(parts, 2));
    }
}
