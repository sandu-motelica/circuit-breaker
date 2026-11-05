package com.sm.cb;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

public final class MutableClock extends Clock {
    private Instant currentInstant;
    private final ZoneId zone;

    public MutableClock(Instant initialInstant) {
        this(initialInstant, ZoneId.of("UTC"));
    }

    private MutableClock(Instant initialInstant, ZoneId zone) {
        this.currentInstant = Objects.requireNonNull(initialInstant);
        this.zone = Objects.requireNonNull(zone);
    }

    public void advance(Duration duration) {
        currentInstant = currentInstant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(currentInstant, zone);
    }

    @Override
    public Instant instant() {
        return currentInstant;
    }
}
