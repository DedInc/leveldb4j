package com.github.dedinc.leveldb4j.core;

/**
 * State of a key in LevelDB.
 */
public enum KeyState {
    /**
     * Key has been deleted
     */
    DELETED(0),

    /**
     * Key is live/active
     */
    LIVE(1),

    /**
     * State is unknown
     */
    UNKNOWN(2);

    private final int value;

    KeyState(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static KeyState fromValue(int value) {
        for (KeyState state : values()) {
            if (state.value == value) {
                return state;
            }
        }
        return UNKNOWN;
    }
}

