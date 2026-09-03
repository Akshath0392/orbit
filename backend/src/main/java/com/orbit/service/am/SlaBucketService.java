package com.orbit.service.am;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * The single canonical SLA classification for open AM CRs.
 *
 * Every widget that reports SLA met/near/breached MUST route through this
 * service so the numbers can never diverge between the stage matrix, the
 * client overview, the POD score and the DH-metrics pillar. Before this
 * existed the same concept was computed three different ways (2-bucket in the
 * stage matrix, 3-bucket in the client overview, tracked-only in the POD
 * score) and the widgets silently disagreed.
 *
 * Rule (3-bucket, tracked stages only):
 * <pre>
 *   breached = age &gt; targetDays
 *   near     = age &gt; 0.75 * targetDays   (within the last 25% of the window)
 *   met      = everything else
 *   adherencePct = round(100 * met / (met + near + breached))
 * </pre>
 * Stages with no configured target are "untracked" and skipped entirely
 * (Hold/Unstaged have no target — mock parity).
 */
@Service
public class SlaBucketService {

    /** Within the last 25% of the SLA window counts as an early-warning "near". */
    public static final double NEAR_FRACTION = 0.75;

    public enum Bucket { MET, NEAR, BREACHED }

    /** met + near + breached over the tracked CRs, with the derived adherence %. */
    public record Buckets(long met, long near, long breached) {
        public long tracked() {
            return met + near + breached;
        }

        /** Percentage of tracked CRs comfortably within SLA, or null when nothing is tracked. */
        public Integer adherencePct() {
            long t = tracked();
            return t == 0 ? null : (int) Math.round(100.0 * met / t);
        }
    }

    /** Classify one CR's age against its stage target; null when the stage is untracked. */
    public Bucket classify(long ageDays, Integer targetDays) {
        if (targetDays == null) return null;
        if (ageDays > targetDays) return Bucket.BREACHED;
        if (ageDays > targetDays * NEAR_FRACTION) return Bucket.NEAR;
        return Bucket.MET;
    }

    /** Aggregate buckets over a collection, deriving each row's age and target via accessors. */
    public <T> Buckets compute(Collection<T> rows, ToLongFunction<T> ageDays, Function<T, Integer> targetDays) {
        long met = 0, near = 0, breached = 0;
        for (T r : rows) {
            Bucket b = classify(ageDays.applyAsLong(r), targetDays.apply(r));
            if (b == null) continue;
            switch (b) {
                case MET -> met++;
                case NEAR -> near++;
                case BREACHED -> breached++;
            }
        }
        return new Buckets(met, near, breached);
    }
}
