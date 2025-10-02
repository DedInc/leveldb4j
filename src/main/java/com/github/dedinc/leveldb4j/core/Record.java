package com.github.dedinc.leveldb4j.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * A record from LevelDB; includes details of the origin file, state, etc.
 */
public class Record {
    private final byte[] key;
    private final byte[] value;
    private final long seq;
    private final KeyState state;
    private final FileType fileType;
    private final Path originFile;
    private final long offset;
    private final boolean wasCompressed;

    public Record(byte[] key, byte[] value, long seq, KeyState state,
                  FileType fileType, Path originFile, long offset, boolean wasCompressed) {
        this.key = key;
        this.value = value;
        this.seq = seq;
        this.state = state;
        this.fileType = fileType;
        this.originFile = originFile;
        this.offset = offset;
        this.wasCompressed = wasCompressed;
    }

    /**
     * Returns the "userkey" which omits the metadata bytes at the end of the raw key.
     */
    public byte[] getUserKey() {
        if (fileType == FileType.LDB) {
            if (key.length < 8) {
                return key;
            } else {
                return Arrays.copyOfRange(key, 0, key.length - 8);
            }
        } else {
            return key;
        }
    }

    /**
     * Creates a Record from an LDB file entry.
     */
    public static Record ldbRecord(byte[] key, byte[] value, Path originFile,
                                   long offset, boolean wasCompressed) {
        long seq;
        KeyState state;

        if (key.length >= 8) {
            byte[] seqBytes = Arrays.copyOfRange(key, key.length - 8, key.length);
            long seqAndType = ByteBuffer.wrap(seqBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
            seq = seqAndType >>> 8;
            state = (key[key.length - 8] == 0) ? KeyState.DELETED : KeyState.LIVE;
        } else {
            seq = 0;
            state = KeyState.UNKNOWN;
        }

        return new Record(key, value, seq, state, FileType.LDB, originFile, offset, wasCompressed);
    }

    /**
     * Creates a Record from a log file entry.
     */
    public static Record logRecord(byte[] key, byte[] value, long seq, KeyState state,
                                   Path originFile, long offset) {
        return new Record(key, value, seq, state, FileType.LOG, originFile, offset, false);
    }

    // Getters
    public byte[] getKey() {
        return key;
    }

    public byte[] getValue() {
        return value;
    }

    public long getSeq() {
        return seq;
    }

    public KeyState getState() {
        return state;
    }

    public FileType getFileType() {
        return fileType;
    }

    public Path getOriginFile() {
        return originFile;
    }

    public long getOffset() {
        return offset;
    }

    public boolean wasCompressed() {
        return wasCompressed;
    }

    @Override
    public String toString() {
        return String.format("Record{key=%s, value=%s, seq=%d, state=%s, fileType=%s, originFile=%s, offset=%d, wasCompressed=%b}",
                Arrays.toString(getUserKey()), Arrays.toString(value), seq, state, fileType,
                originFile.getFileName(), offset, wasCompressed);
    }
}

