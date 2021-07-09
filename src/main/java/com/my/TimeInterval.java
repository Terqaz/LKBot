package com.my;

public enum TimeInterval {
    HALF_DAY(12L * 3600 * 1000),
    HALF_HOUR(1800 * 1000);

    private long value;

    TimeInterval (long value) {
        this.value = value;
    }

    public long getValue () {
        return value;
    }
}
