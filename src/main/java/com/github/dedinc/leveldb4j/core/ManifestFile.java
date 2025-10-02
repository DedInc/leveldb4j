package com.github.dedinc.leveldb4j.core;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a manifest file which contains database metadata.
 * Manifest files are formatted like log files but contain VersionEdit data.
 * <p>
 * Main use is to identify the level of files, use fileToLevel map to look up levels based on file no.
 * <p>
 * See:
 * https://github.com/google/leveldb/blob/master/db/version_edit.h
 * https://github.com/google/leveldb/blob/master/db/version_edit.cc
 */
public class ManifestFile implements Closeable, Iterable<VersionEdit> {
    private static final Pattern MANIFEST_FILENAME_PATTERN = Pattern.compile("MANIFEST-([0-9A-Fa-f]{6})");

    private final Path path;
    private final int fileNo;
    private final RandomAccessFile file;
    private final Map<Long, Integer> fileToLevel;
    private List<LogBatchReader.Batch> cachedBatches;

    public ManifestFile(Path path) throws IOException {
        this.path = path;

        // Parse file number from filename
        String filename = path.getFileName().toString();
        Matcher matcher = MANIFEST_FILENAME_PATTERN.matcher(filename);
        if (!matcher.matches()) {
            throw new IOException("Invalid name for Manifest: " + filename);
        }
        this.fileNo = Integer.parseInt(matcher.group(1), 16);

        this.file = new RandomAccessFile(path.toFile(), "r");

        // Build file to level mapping
        Map<Long, Integer> tempMap = new HashMap<>();
        for (VersionEdit edit : this) {
            if (edit.getNewFiles() != null) {
                for (VersionEdit.NewFile nf : edit.getNewFiles()) {
                    tempMap.put(nf.getFileNo(), nf.getLevel());
                }
            }
        }
        this.fileToLevel = Collections.unmodifiableMap(tempMap);
    }

    /**
     * Reads raw blocks from the file.
     */
    private List<byte[]> getRawBlocks() throws IOException {
        List<byte[]> blocks = new ArrayList<>();
        file.seek(0);

        while (file.getFilePointer() < file.length()) {
            int remaining = (int) (file.length() - file.getFilePointer());
            int toRead = Math.min(LogBatchReader.LOG_BLOCK_SIZE, remaining);
            byte[] chunk = new byte[toRead];
            file.readFully(chunk);
            blocks.add(chunk);
        }

        return blocks;
    }

    /**
     * Reads batches from the manifest file (cached).
     */
    private List<LogBatchReader.Batch> getBatches() throws IOException {
        if (cachedBatches == null) {
            cachedBatches = LogBatchReader.readBatches(getRawBlocks(), path);
        }
        return cachedBatches;
    }

    @Override
    public Iterator<VersionEdit> iterator() {
        try {
            List<LogBatchReader.Batch> batches = getBatches();
            return new ManifestIterator(batches);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read manifest file", e);
        }
    }

    /**
     * Iterator for version edits in this manifest file.
     */
    private static class ManifestIterator implements Iterator<VersionEdit> {
        private final List<LogBatchReader.Batch> batches;
        private int index = 0;

        ManifestIterator(List<LogBatchReader.Batch> batches) {
            this.batches = batches;
        }

        @Override
        public boolean hasNext() {
            return index < batches.size();
        }

        @Override
        public VersionEdit next() {
            try {
                LogBatchReader.Batch batch = batches.get(index++);
                return VersionEdit.fromBuffer(batch.data);
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse VersionEdit", e);
            }
        }
    }

    public Path getPath() {
        return path;
    }

    public int getFileNo() {
        return fileNo;
    }

    /**
     * Returns an immutable map of file numbers to their levels.
     */
    public Map<Long, Integer> getFileToLevel() {
        return fileToLevel;
    }

    @Override
    public void close() throws IOException {
        if (file != null) {
            file.close();
        }
    }
}

