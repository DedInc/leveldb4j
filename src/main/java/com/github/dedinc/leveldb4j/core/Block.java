package com.github.dedinc.leveldb4j.core;

import com.github.dedinc.leveldb4j.util.VarintUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Block from an .ldb (table) file.
 * See: https://github.com/google/leveldb/blob/master/doc/table_format.md
 */
public class Block implements Iterable<RawBlockEntry> {
    private final byte[] raw;
    private final boolean wasCompressed;
    private final LdbFile origin;
    private final long offset;
    private final int restartArrayCount;
    private final int restartArrayOffset;
    boolean valid;
    IOException readError;

    public boolean isValid(){
        return valid;
    }

    public IOException getReadError(){
        return readError;
    }

    public Block(byte[] raw, boolean wasCompressed, LdbFile origin, long offset, boolean valid, IOException exception) {
        this(raw, wasCompressed, origin, offset);
        this.valid = valid;
        this.readError = exception;
    }
    
    public Block(byte[] raw, boolean wasCompressed, LdbFile origin, long offset) {
        this.raw = raw;
        this.wasCompressed = wasCompressed;
        this.origin = origin;
        this.offset = offset;

        // Read restart array count from last 4 bytes
        this.restartArrayCount = ByteBuffer.wrap(raw, raw.length - 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();
        this.restartArrayOffset = raw.length - (restartArrayCount + 1) * 4;
    }

    public boolean wasCompressed() {
        return wasCompressed;
    }

    public long getOffset() {
        return offset;
    }

    /**
     * Gets the offset of a restart point by index.
     */
    public int getRestartOffset(int index) {
        int offset = restartArrayOffset + (index * 4);
        return ByteBuffer.wrap(raw, offset, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();
    }

    /**
     * Gets the offset of the first entry in the block.
     */
    public int getFirstEntryOffset() {
        return getRestartOffset(0);
    }

    @Override
    public Iterator<RawBlockEntry> iterator() {
        return new BlockIterator();
    }

    /**
     * Iterator for entries in this block.
     */
    private class BlockIterator implements Iterator<RawBlockEntry> {
        private final ByteArrayInputStream buffer;
        private byte[] key = new byte[0];
        private RawBlockEntry nextEntry;

        BlockIterator() {
            buffer = new ByteArrayInputStream(raw);
            buffer.skip(getFirstEntryOffset());
            advance();
        }

        private void advance() {
            try {
                // Check if we're still in the data section (before restart array)
                int currentPosition = raw.length - buffer.available();
                if (buffer.available() > 0 && currentPosition < restartArrayOffset) {
                    int startOffset = currentPosition;

                    Long sharedLengthObj = VarintUtils.readLeVarint(buffer, true);
                    Long nonSharedLengthObj = VarintUtils.readLeVarint(buffer, true);
                    Long valueLengthObj = VarintUtils.readLeVarint(buffer, true);

                    if (sharedLengthObj == null || nonSharedLengthObj == null || valueLengthObj == null) {
                        nextEntry = null;
                        return;
                    }

                    // Convert to primitives once
                    int sharedLength = sharedLengthObj.intValue();
                    int nonSharedLength = nonSharedLengthObj.intValue();
                    int valueLength = valueLengthObj.intValue();

                    // Sense check - make sure we haven't gone past the restart array
                    currentPosition = raw.length - buffer.available();
                    if (currentPosition > restartArrayOffset) {
                        nextEntry = null;
                        return;
                    }

                    if (sharedLength > key.length) {
                        throw new IOException("Shared key length is larger than the previous key");
                    }

                    // Build the key - optimize for common case where we can reuse array
                    int newKeyLength = sharedLength + nonSharedLength;
                    byte[] newKey;
                    if (key.length == newKeyLength && sharedLength == key.length) {
                        // Key unchanged, reuse
                        newKey = key;
                    } else {
                        newKey = new byte[newKeyLength];
                        if (sharedLength > 0) {
                            System.arraycopy(key, 0, newKey, 0, sharedLength);
                        }
                    }

                    // Read non-shared part directly into key array
                    if (nonSharedLength > 0) {
                        buffer.read(newKey, sharedLength, nonSharedLength);
                    }
                    key = newKey;

                    // Read the value
                    byte[] value = new byte[valueLength];
                    if (valueLength > 0) {
                        buffer.read(value);
                    }

                    nextEntry = new RawBlockEntry(key, value, startOffset);
                } else {
                    nextEntry = null;
                }
            } catch (IOException e) {
                nextEntry = null;
            }
        }

        @Override
        public boolean hasNext() {
            return nextEntry != null;
        }

        @Override
        public RawBlockEntry next() {
            RawBlockEntry current = nextEntry;
            advance();
            return current;
        }
    }

    /**
     * Returns all entries in this block as a list.
     */
    public List<RawBlockEntry> getEntries() {
        List<RawBlockEntry> entries = new ArrayList<>();
        for (RawBlockEntry entry : this) {
            entries.add(entry);
        }
        return entries;
    }
}

