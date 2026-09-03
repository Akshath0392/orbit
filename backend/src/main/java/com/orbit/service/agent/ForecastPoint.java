package com.orbit.service.agent;

/**
 * A single forecast data point produced by {@link ManDayForecastAgent}.
 *
 * <p>CI bands:
 * <ul>
 *   <li>80% CI: yhat ± (stddev * 1.28)</li>
 *   <li>95% CI: yhat ± (stddev * 1.96)</li>
 * </ul>
 *
 * @param ds            ISO date string for this forecast point (yyyy-MM-dd)
 * @param yhat          forecasted burned man-days
 * @param yhatLower80   lower bound of 80% confidence interval
 * @param yhatUpper80   upper bound of 80% confidence interval
 * @param yhatLower95   lower bound of 95% confidence interval
 * @param yhatUpper95   upper bound of 95% confidence interval
 */
public record ForecastPoint(
        String ds,
        double yhat,
        double yhatLower80,
        double yhatUpper80,
        double yhatLower95,
        double yhatUpper95
) {}
