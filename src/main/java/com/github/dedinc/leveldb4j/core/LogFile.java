package com.github.dedinc.leveldb4j.core;

import com.github.dedinc.leveldb4j.util.VarintUtils;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A LevelDB log (.log) file.
 */
public class LogFile implements Closeable, Iterable<Record> {
    private final Path path;
    private final int fileNo;
    private final RandomAccessFile file;
    private List<LogBatchReader.Batch> cachedBatches;

    public LogFile(Path path) throws IOException {
        this.path = path;

        // Extract file number from filename (hex format)
        String filename = path.getFileName().toString();
        String nameWithoutExt = filename.substring(0, filename.lastIndexOf('.'));
        this.fileNo = Integer.parseInt(nameWithoutExt, 16);

        this.file = new RandomAccessFile(path.toFile(), "r");
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
     * Reads batches from the log file (cached).
     */
    private List<LogBatchReader.Batch> getBatches() throws IOException {
        if (cachedBatches == null) {
            cachedBatches = LogBatchReader.readBatches(getRawBlocks(), path);
        }
        return cachedBatches;
    }

    @Override
    public Iterator<Record> iterator() {
        try {
            List<LogBatchReader.Batch> batches = getBatches();
            return new LogFileIterator(batches);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read log file", e);
        }
    }

    /**
     * Iterator for records in this log file.
     */
    private class LogFileIterator implements Iterator<Record> {
        private final List<LogBatchReader.Batch> batches;
        private int batchIndex = 0;
        private List<Record> currentBatchRecords = new ArrayList<>();
        private int recordIndex = 0;

        LogFileIterator(List<LogBatchReader.Batch> batches) {
            this.batches = batches;
            advance();
        }

        private void advance() {
            if (recordIndex < currentBatchRecords.size()) {
                return;
            }

            if (batchIndex >= batches.size()) {
                return;
            }

            try {
                LogBatchReader.Batch batch = batches.get(batchIndex++);
                currentBatchRecords = parseBatch(batch);
                recordIndex = 0;
            } catch (IOException e) {
                currentBatchRecords = new ArrayList<>();
            }
        }

        private List<Record> parseBatch(LogBatchReader.Batch batch) throws IOException {
            List<Record> records = new ArrayList<>();
            ByteArrayInputStream buffer = new ByteArrayInputStream(batch.data);

            byte[] header = new byte[12];
            buffer.read(header);
            ByteBuffer headerBuf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            long seq = headerBuf.getLong();
            int count = headerBuf.getInt();

            for (int i = 0; i < count; i++) {
                long startOffset = batch.offset + (batch.data.length - buffer.available());

                int stateByte = buffer.read();
                if (stateByte == -1) break;
                KeyState state = KeyState.fromValue(stateByte);

                Long keyLength = VarintUtils.readLeVarint(buffer, true);
                if (keyLength == null) break;

                byte[] key = new byte[keyLength.intValue()];
                buffer.read(key);

                byte[] value;
                if (state != KeyState.DELETED) {
                    Long valueLength = VarintUtils.readLeVarint(buffer, true);
                    if (valueLength == null) break;
                    value = new byte[valueLength.intValue()];
                    buffer.read(value);
                } else {
                    value = new byte[0];
                }

                records.add(Record.logRecord(key, value, seq + i, state, path, startOffset));
            }

            return records;
        }

        @Override
        public boolean hasNext() {
            return recordIndex < currentBatchRecords.size();
        }

        @Override
        public Record next() {
            Record record = currentBatchRecords.get(recordIndex++);
            advance();
            return record;
        }
    }

    public Path getPath() {
        return path;
    }

    public int getFileNo() {
        return fileNo;
    }

    @Override
    public void close() throws IOException {
        if (file != null) {
            file.close();
        }
    }
}

