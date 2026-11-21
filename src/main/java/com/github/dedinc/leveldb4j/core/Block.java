package com.github.dedinc.leveldb4j.core;

import com.github.dedinc.leveldb4j.util.VarintUtils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
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

    boolean valid = true;
    IOException readError = null;

    public boolean isValid(){
        return valid;
    }

    public IOException getReadError(){
        return readError;
    }

    public Block(byte[] raw, boolean wasCompressed, LdbFile origin, long offset, boolean valid, IOException exception) {
        this(raw, wasCompressed, origin, offset);
        this.valid = this.valid && valid;

        if (this.readError == null) {
            this.readError = exception;
        }
    }

    public Block(byte[] raw, boolean wasCompressed, LdbFile origin, long offset) {
        this.raw = raw;
        this.wasCompressed = wasCompressed;
        this.origin = origin;
        this.offset = offset;

        int count = 0;
        int arrayOffset = 0;

        try {
            if (raw == null || raw.length < 8) {
                throw new IOException("Block too small");
            }

            // Last 4 bytes contain the restart count.
            count = ByteBuffer.wrap(raw, raw.length - 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getInt();

            // Standard LevelDB blocks have at least one restart.
            if (count < 1 || count > (raw.length - 4) / 4) {
                throw new IOException("Invalid restart count: " + count);
            }

            // Safe because count has already been bounded.
            arrayOffset = raw.length - (count + 1) * 4;
            
            validateRestartArray(count, arrayOffset);
        } catch (IOException e) {
            valid = false;
            readError = e;
        }

        this.restartArrayCount = count;
        this.restartArrayOffset = arrayOffset;
    }
    
    public void validateRestartArray(int count, int arrayOffset) throws IOException{
        ByteBuffer buf = ByteBuffer.wrap(raw)
                .order(ByteOrder.LITTLE_ENDIAN);

        int i = 0;
        int restart = buf.getInt(arrayOffset);
        if (restart != 0) {
            throw new IOException("First restart offset must be zero");
        }
        
        int previous = restart;
        
        //validate the restart array values ordering and limits
        for (i = 1; i < count; i++) {
            restart = buf.getInt(arrayOffset + i * 4);

            if (restart < 0 ||
                restart > arrayOffset  ||
                (restart == arrayOffset && arrayOffset != 0) ||
                restart <= previous) {

                throw new IOException("Invalid restart offset: " + restart);
            }

            previous = restart;
        }    	
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
        if (!valid || index < 0 || index >= restartArrayCount) {
            throw new IndexOutOfBoundsException(
                "Invalid restart index: " + index);
        }

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
        private int restartIndex = 0;
        private boolean iteratorValid = true;
        private IOException iteratorError;
        
        private void invalidateIterator(String message) {
            iteratorValid = false;
            iteratorError = new IOException(message);
            nextEntry = null;
        }        
        
        BlockIterator() {
            if (valid) {
                buffer = new ByteArrayInputStream(raw, 0, restartArrayOffset);
                advance();
            } else {
                buffer = new ByteArrayInputStream(new byte[0]);
            }
        }

        private void advance() {
            nextEntry = null;
            int startOffset = 0;

            try {
                int currentPosition = restartArrayOffset - buffer.available();

                if (currentPosition >= restartArrayOffset) {
                    boolean validEmptyBlock =
                            restartArrayOffset == 0 &&
                            restartArrayCount == 1 &&
                            restartIndex == 0;

                    if (currentPosition > restartArrayOffset ||
                        (restartIndex != restartArrayCount && !validEmptyBlock)) {

                        invalidateIterator(
                            "Block ended before all restart offsets were reached"
                        );
                    }
                    return;
                }

                startOffset = currentPosition;
                
                if (restartIndex < restartArrayCount) {
                    int restartOffset = getRestartOffset(restartIndex);

                    if (restartOffset < startOffset) {
                    	invalidateIterator("Restart offset " + restartOffset +" does not point to an entry boundary");
                        return;
                    }
                }

                Long sharedObj = VarintUtils.readLeVarint(buffer, true);
                Long nonSharedObj = VarintUtils.readLeVarint(buffer, true);
                Long valueObj = VarintUtils.readLeVarint(buffer, true);
                
                if (sharedObj == null || nonSharedObj == null || valueObj == null) {
                	invalidateIterator("Truncated entry header at offset " + startOffset);
                    return;
                }

                currentPosition = restartArrayOffset - buffer.available();

                if (currentPosition > restartArrayOffset) {
                	invalidateIterator("Entry header exceeds data section at offset " + startOffset);
                    return;
                }

                long shared = sharedObj;
                long nonShared = nonSharedObj;
                long valueLen = valueObj;

                long remaining = (long) restartArrayOffset - currentPosition;

                // Validate decoded lengths before casting.
                if (shared < 0 || nonShared < 0 || valueLen < 0 ||
            	    shared > key.length ||
            	    nonShared > remaining ||
            	    valueLen > remaining - nonShared ||
            	    shared > Integer.MAX_VALUE - nonShared) {

                	invalidateIterator("Invalid entry lengths at offset " + startOffset);
            	    return;
            	}
                
                int sharedLength = (int) shared;
                int nonSharedLength = (int) nonShared;
                int valueLength = (int) valueLen;

                boolean isRestart = false;

                if (restartIndex < restartArrayCount) {
                    int restartOffset = getRestartOffset(restartIndex);

                    if (startOffset == restartOffset) {
                        if (shared != 0) {
                        	invalidateIterator("Nonzero shared prefix at restart offset " + startOffset );
                            return;
                        }

                        isRestart = true;
                    }
                }

                long endOffset = currentPosition + nonShared + valueLen;

                int nextRestartIndex = restartIndex + (isRestart ? 1 : 0);

                if (nextRestartIndex < restartArrayCount) {
                    int nextRestart = getRestartOffset(nextRestartIndex);

                    if (endOffset > nextRestart) {
                    	invalidateIterator("Entry at offset " + startOffset + " crosses restart offset " + nextRestart);
                        return;
                    }
                }
                
                int newKeyLength = sharedLength + nonSharedLength;

                byte[] newKey;

                if (key.length == newKeyLength &&
                    sharedLength == key.length) {

                    newKey = key;

                } else {

                    newKey = new byte[newKeyLength];

                    if (sharedLength > 0) {
                        System.arraycopy(
                            key, 0,
                            newKey, 0,
                            sharedLength
                        );
                    }
                }

                DataInputStream data = new DataInputStream(buffer);

                if (nonSharedLength > 0) {
                    data.readFully(
                        newKey,
                        sharedLength,
                        nonSharedLength
                    );
                }

                byte[] value = new byte[valueLength];

                if (valueLength > 0) {
                    data.readFully(value);
                }

                key = newKey;
                nextEntry = new RawBlockEntry(
                    newKey, value, startOffset
                );
                
                if (isRestart) {
                    restartIndex++;
                }                
            } catch (IOException | IllegalArgumentException e) {
                iteratorValid = false;
                iteratorError = new IOException("Error parsing block entry at offset " + startOffset, e);
                nextEntry = null;
            }
        }
        
        @Override
        public boolean hasNext() {
            return iteratorValid && nextEntry != null;
        }

        @Override
        public RawBlockEntry next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }

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

