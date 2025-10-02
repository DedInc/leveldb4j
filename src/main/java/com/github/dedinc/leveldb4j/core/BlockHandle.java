package com.github.dedinc.leveldb4j.core;

import com.github.dedinc.leveldb4j.util.VarintUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A BlockHandle contains an offset and length of a block in an ldb table file.
 * See: https://github.com/google/leveldb/blob/master/doc/table_format.md
 */
public class BlockHandle {
    private final long offset;
    private final long length;

    public BlockHandle(long offset, long length) {
        this.offset = offset;
        this.length = length;
    }

    public long getOffset() {
        return offset;
    }

    public long getLength() {
        return length;
    }

    /**
     * Reads a BlockHandle from an input stream.
     */
    public static BlockHandle fromStream(InputStream stream) throws IOException {
        Long offset = VarintUtils.readLeVarint(stream);
        Long length = VarintUtils.readLeVarint(stream);
        if (offset == null || length == null) {
            throw new IOException("Could not read BlockHandle");
        }
        return new BlockHandle(offset, length);
    }

    /**
     * Reads a BlockHandle from a byte array.
     */
    public static BlockHandle fromBytes(byte[] data) throws IOException {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(data)) {
            return fromStream(stream);
        }
    }

    @Override
    public String toString() {
        return String.format("BlockHandle{offset=%d, length=%d}", offset, length);
    }
}

