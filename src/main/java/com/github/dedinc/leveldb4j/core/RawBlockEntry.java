package com.github.dedinc.leveldb4j.core;

import java.util.Arrays;

/**
 * Raw key, value for a record in a LDB file Block, along with the offset within the block.
 * See: https://github.com/google/leveldb/blob/master/doc/table_format.md
 */
public class RawBlockEntry {
    private final byte[] key;
    private final byte[] value;
    private final int blockOffset;

    public RawBlockEntry(byte[] key, byte[] value, int blockOffset) {
        this.key = key;
        this.value = value;
        this.blockOffset = blockOffset;
    }

    public byte[] getKey() {
        return key;
    }

    public byte[] getValue() {
        return value;
    }

    public int getBlockOffset() {
        return blockOffset;
    }

    @Override
    public String toString() {
        return String.format("RawBlockEntry{key=%s, value=%s, blockOffset=%d}",
                Arrays.toString(key), Arrays.toString(value), blockOffset);
    }
}

