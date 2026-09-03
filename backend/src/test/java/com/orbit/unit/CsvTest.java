package com.orbit.unit;

import com.orbit.util.Csv;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTest {

    @Test
    void neutralizesFormulaLeadingChars() {
        assertThat(Csv.escape("=cmd|'/C calc'!A1")).startsWith("'=");
        assertThat(Csv.escape("+1+1")).isEqualTo("'+1+1");
        assertThat(Csv.escape("-2")).isEqualTo("'-2");
        assertThat(Csv.escape("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    }

    @Test
    void quotesFieldsWithDelimiters() {
        assertThat(Csv.escape("a,b")).isEqualTo("\"a,b\"");
        assertThat(Csv.escape("she said \"hi\"")).isEqualTo("\"she said \"\"hi\"\"\"");
    }

    @Test
    void formulaCharInsideQuotedCellIsAlsoNeutralized() {
        // leading '=' plus an embedded comma -> prefixed AND quoted
        assertThat(Csv.escape("=a,b")).isEqualTo("\"'=a,b\"");
    }

    @Test
    void passesThroughOrdinaryValues() {
        assertThat(Csv.escape("DEMO-123")).isEqualTo("DEMO-123");
        assertThat(Csv.escape(null)).isEqualTo("");
        assertThat(Csv.escape(42)).isEqualTo("42");
    }
}
