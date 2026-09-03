package com.orbit.unit;

import com.orbit.domain.client.ManDayBudget;
import com.orbit.domain.client.Project;
import com.orbit.domain.capacity.ManDaySnapshot;
import com.orbit.repository.ManDayBudgetRepository;
import com.orbit.repository.ManDaySnapshotRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.controller.ManDayController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Unit tests for man-day forecast generation.
 */
@ExtendWith(MockitoExtension.class)
class ManDayForecastTest {

    @Mock ManDayBudgetRepository budgets;
    @Mock ManDaySnapshotRepository snapshots;
    @Mock ProjectRepository projects;

    @InjectMocks ManDayController controller;

    @BeforeEach
    void setUp() {
        ManDayBudget budget = ManDayBudget.builder()
            .purchasedDays(BigDecimal.valueOf(120))
            .alertThresholdPct(80)
            .build();

        ManDaySnapshot snap = new ManDaySnapshot();
        org.springframework.test.util.ReflectionTestUtils.setField(snap, "burnedDays", BigDecimal.valueOf(80));
        org.springframework.test.util.ReflectionTestUtils.setField(snap, "remainingDays", BigDecimal.valueOf(40));
        org.springframework.test.util.ReflectionTestUtils.setField(snap, "burnRatePerDay", BigDecimal.valueOf(1.5));
        org.springframework.test.util.ReflectionTestUtils.setField(snap, "forecastExhaustion", LocalDate.now().plusDays(27));

        when(budgets.findByProjectId(anyLong())).thenReturn(Optional.of(budget));
        when(snapshots.findTop14ByProjectIdOrderBySnapshotDateDesc(anyLong())).thenReturn(List.of(snap));
    }

    @Test
    @SuppressWarnings("unchecked")
    void forecastReturns31DataPoints() {
        var result = controller.forecast(1L);
        var points = (List<Map<String, Object>>) result.get("forecast");
        assertThat(points).hasSize(31);
    }

    @Test
    @SuppressWarnings("unchecked")
    void forecastPointsHaveRequiredFields() {
        var result = controller.forecast(1L);
        var points = (List<Map<String, Object>>) result.get("forecast");
        var first = points.get(0);
        assertThat(first).containsKeys("ds", "yhat", "yhatLower80", "yhatUpper80");
    }

    @Test
    void forecastContainsInterpretation() {
        var result = controller.forecast(1L);
        assertThat(result.get("interpretation")).isNotNull();
        assertThat(result.get("interpretation").toString()).isNotBlank();
    }

    @Test
    void forecastStartsFromCurrentBurn() {
        var result = controller.forecast(1L);
        assertThat(result.get("burned")).isEqualTo(80.0);
        assertThat(result.get("purchased")).isEqualTo(120);
    }

    @Test
    void upperBoundAlwaysAboveLowerBound() {
        var result = controller.forecast(1L);
        @SuppressWarnings("unchecked")
        var points = (List<Map<String, Object>>) result.get("forecast");
        for (var pt : points) {
            double lo = ((Number) pt.get("yhatLower80")).doubleValue();
            double hi = ((Number) pt.get("yhatUpper80")).doubleValue();
            assertThat(hi).isGreaterThanOrEqualTo(lo);
        }
    }

    @Test
    void noBudgetReturnsDefaultForecast() {
        when(budgets.findByProjectId(anyLong())).thenReturn(Optional.empty());
        when(snapshots.findTop14ByProjectIdOrderBySnapshotDateDesc(anyLong())).thenReturn(List.of());
        var result = controller.forecast(99L);
        assertThat(result.get("forecast")).isNotNull();
    }
}
