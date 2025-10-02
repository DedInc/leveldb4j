package com.github.dedinc.leveldb4j.core;

/**
 * Tags used in version edit records.
 * See: https://github.com/google/leveldb/blob/master/db/version_edit.cc
 */
public enum VersionEditTag {
    COMPARATOR(1),
    LOG_NUMBER(2),
    NEXT_FILE_NUMBER(3),
    LAST_SEQUENCE(4),
    COMPACT_POINTER(5),
    DELETED_FILE(6),
    NEW_FILE(7),
    // 8 was used for large value refs
    PREV_LOG_NUMBER(9);

    private final int value;

    VersionEditTag(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static VersionEditTag fromValue(int value) {
        for (VersionEditTag tag : values()) {
            if (tag.value == value) {
                return tag;
            }
        }
        return null;
    }
}

