package com.github.dedinc.leveldb4j.core;

import com.github.dedinc.leveldb4j.util.VarintUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Represents a version edit record from a manifest file.
 * See:
 * https://github.com/google/leveldb/blob/master/db/version_edit.h
 * https://github.com/google/leveldb/blob/master/db/version_edit.cc
 */
public class VersionEdit {
    private final String comparator;
    private final Long logNumber;
    private final Long prevLogNumber;
    private final Long lastSequence;
    private final Long nextFileNumber;
    private final List<CompactionPointer> compactionPointers;
    private final List<DeletedFile> deletedFiles;
    private final List<NewFile> newFiles;

    /**
     * Compaction pointer entry.
     */
    public static class CompactionPointer {
        private final int level;
        private final byte[] pointer;

        public CompactionPointer(int level, byte[] pointer) {
            this.level = level;
            this.pointer = pointer;
        }

        public int getLevel() {
            return level;
        }

        public byte[] getPointer() {
            return pointer;
        }

        @Override
        public String toString() {
            return String.format("CompactionPointer{level=%d, pointer=%s}", level, Arrays.toString(pointer));
        }
    }

    /**
     * Deleted file entry.
     */
    public static class DeletedFile {
        private final int level;
        private final long fileNo;

        public DeletedFile(int level, long fileNo) {
            this.level = level;
            this.fileNo = fileNo;
        }

        public int getLevel() {
            return level;
        }

        public long getFileNo() {
            return fileNo;
        }

        @Override
        public String toString() {
            return String.format("DeletedFile{level=%d, fileNo=%d}", level, fileNo);
        }
    }

    /**
     * New file entry.
     */
    public static class NewFile {
        private final int level;
        private final long fileNo;
        private final long fileSize;
        private final byte[] smallestKey;
        private final byte[] largestKey;

        public NewFile(int level, long fileNo, long fileSize, byte[] smallestKey, byte[] largestKey) {
            this.level = level;
            this.fileNo = fileNo;
            this.fileSize = fileSize;
            this.smallestKey = smallestKey;
            this.largestKey = largestKey;
        }

        public int getLevel() {
            return level;
        }

        public long getFileNo() {
            return fileNo;
        }

        public long getFileSize() {
            return fileSize;
        }

        public byte[] getSmallestKey() {
            return smallestKey;
        }

        public byte[] getLargestKey() {
            return largestKey;
        }

        @Override
        public String toString() {
            return String.format("NewFile{level=%d, fileNo=%d, fileSize=%d}", level, fileNo, fileSize);
        }
    }

    private VersionEdit(String comparator, Long logNumber, Long prevLogNumber, Long lastSequence,
                        Long nextFileNumber, List<CompactionPointer> compactionPointers,
                        List<DeletedFile> deletedFiles, List<NewFile> newFiles) {
        this.comparator = comparator;
        this.logNumber = logNumber;
        this.prevLogNumber = prevLogNumber;
        this.lastSequence = lastSequence;
        this.nextFileNumber = nextFileNumber;
        this.compactionPointers = compactionPointers;
        this.deletedFiles = deletedFiles;
        this.newFiles = newFiles;
    }

    /**
     * Parses a VersionEdit from a byte buffer.
     */
    public static VersionEdit fromBuffer(byte[] buffer) throws IOException {
        String comparator = null;
        Long logNumber = null;
        Long prevLogNumber = null;
        Long lastSequence = null;
        Long nextFileNumber = null;
        List<CompactionPointer> compactionPointers = new ArrayList<>();
        List<DeletedFile> deletedFiles = new ArrayList<>();
        List<NewFile> newFiles = new ArrayList<>();

        ByteArrayInputStream stream = new ByteArrayInputStream(buffer);

        while (stream.available() > 0) {
            Long tagValue = VarintUtils.readLeVarint(stream, true);
            if (tagValue == null) {
                break;
            }

            VersionEditTag tag = VersionEditTag.fromValue(tagValue.intValue());
            if (tag == null) {
                // Unknown tag, skip
                continue;
            }

            switch (tag) {
                case COMPARATOR:
                    byte[] comparatorBytes = VarintUtils.readLengthPrefixedBlob(stream);
                    comparator = new String(comparatorBytes, "UTF-8");
                    break;

                case LOG_NUMBER:
                    logNumber = VarintUtils.readLeVarint(stream);
                    break;

                case PREV_LOG_NUMBER:
                    prevLogNumber = VarintUtils.readLeVarint(stream);
                    break;

                case NEXT_FILE_NUMBER:
                    nextFileNumber = VarintUtils.readLeVarint(stream);
                    break;

                case LAST_SEQUENCE:
                    lastSequence = VarintUtils.readLeVarint(stream);
                    break;

                case COMPACT_POINTER:
                    Long level = VarintUtils.readLeVarint(stream, true);
                    byte[] pointer = VarintUtils.readLengthPrefixedBlob(stream);
                    if (level != null) {
                        compactionPointers.add(new CompactionPointer(level.intValue(), pointer));
                    }
                    break;

                case DELETED_FILE:
                    Long delLevel = VarintUtils.readLeVarint(stream, true);
                    Long fileNo = VarintUtils.readLeVarint(stream);
                    if (delLevel != null && fileNo != null) {
                        deletedFiles.add(new DeletedFile(delLevel.intValue(), fileNo));
                    }
                    break;

                case NEW_FILE:
                    Long newLevel = VarintUtils.readLeVarint(stream, true);
                    Long newFileNo = VarintUtils.readLeVarint(stream);
                    Long fileSize = VarintUtils.readLeVarint(stream);
                    byte[] smallest = VarintUtils.readLengthPrefixedBlob(stream);
                    byte[] largest = VarintUtils.readLengthPrefixedBlob(stream);
                    if (newLevel != null && newFileNo != null && fileSize != null) {
                        newFiles.add(new NewFile(newLevel.intValue(), newFileNo, fileSize, smallest, largest));
                    }
                    break;
            }
        }

        return new VersionEdit(comparator, logNumber, prevLogNumber, lastSequence, nextFileNumber,
                Collections.unmodifiableList(compactionPointers),
                Collections.unmodifiableList(deletedFiles),
                Collections.unmodifiableList(newFiles));
    }

    // Getters
    public String getComparator() {
        return comparator;
    }

    public Long getLogNumber() {
        return logNumber;
    }

    public Long getPrevLogNumber() {
        return prevLogNumber;
    }

    public Long getLastSequence() {
        return lastSequence;
    }

    public Long getNextFileNumber() {
        return nextFileNumber;
    }

    public List<CompactionPointer> getCompactionPointers() {
        return compactionPointers;
    }

    public List<DeletedFile> getDeletedFiles() {
        return deletedFiles;
    }

    public List<NewFile> getNewFiles() {
        return newFiles;
    }

    @Override
    public String toString() {
        return String.format("VersionEdit{comparator='%s', logNumber=%d, prevLogNumber=%d, " +
                        "lastSequence=%d, nextFileNumber=%d, newFiles=%d, deletedFiles=%d}",
                comparator, logNumber, prevLogNumber, lastSequence, nextFileNumber,
                newFiles.size(), deletedFiles.size());
    }
}

