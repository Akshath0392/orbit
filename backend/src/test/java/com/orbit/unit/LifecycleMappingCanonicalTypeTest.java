package com.orbit.unit;

import com.orbit.domain.client.LifecycleMapping;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Admin save path persists only the canonical sync vocabulary. */
class LifecycleMappingCanonicalTypeTest {

    @Test
    void canonicalValuesPassThroughUnchanged() {
        for (String t : LifecycleMapping.CANONICAL_TYPES) {
            assertThat(LifecycleMapping.canonicalType(t)).isEqualTo(t);
        }
    }

    @Test
    void displayLabelsTranslateToCanonicalForm() {
        assertThat(LifecycleMapping.canonicalType("Bug")).isEqualTo("PROD_BUG");
        assertThat(LifecycleMapping.canonicalType("Production Bug")).isEqualTo("PROD_BUG");
        assertThat(LifecycleMapping.canonicalType("UAT Bug")).isEqualTo("UAT_BUG");
        assertThat(LifecycleMapping.canonicalType("UAT Defect")).isEqualTo("UAT_BUG");
        assertThat(LifecycleMapping.canonicalType("Task")).isEqualTo("TASK");
        assertThat(LifecycleMapping.canonicalType("All")).isEqualTo("ALL");
        assertThat(LifecycleMapping.canonicalType("Other")).isEqualTo("OTHER");
    }

    @Test
    void trimsAndNormalizesCase() {
        assertThat(LifecycleMapping.canonicalType("  cr  ")).isEqualTo("CR");
        assertThat(LifecycleMapping.canonicalType("prod bug")).isEqualTo("PROD_BUG");
        assertThat(LifecycleMapping.canonicalType("uat_bug")).isEqualTo("UAT_BUG");
        assertThat(LifecycleMapping.canonicalType("all")).isEqualTo("ALL");
    }

    @Test
    void unknownLabelsAreRejectedAsNull() {
        assertThat(LifecycleMapping.canonicalType(null)).isNull();
        assertThat(LifecycleMapping.canonicalType("")).isNull();
        assertThat(LifecycleMapping.canonicalType("Epic")).isNull();
        assertThat(LifecycleMapping.canonicalType("Sub-task")).isNull();
    }
}
