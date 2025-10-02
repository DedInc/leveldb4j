package com.github.dedinc.leveldb4j.core;

/**
 * Type of entry in a log file.
 */
public enum LogEntryType {
    ZERO(0),
    FULL(1),
    FIRST(2),
    MIDDLE(3),
    LAST(4);

    private final int value;

    LogEntryType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static LogEntryType fromValue(int value) {
        for (LogEntryType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid log entry type: " + value);
    }
}

