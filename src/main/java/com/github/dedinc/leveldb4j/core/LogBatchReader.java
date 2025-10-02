package com.github.dedinc.leveldb4j.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for reading batches from log-formatted files.
 * Used by both LogFile and ManifestFile.
 */
class LogBatchReader {
    static final int LOG_BLOCK_SIZE = 32768;

    /**
     * Batch data with its offset.
     */
    static class Batch {
        final long offset;
        final byte[] data;

        Batch(long offset, byte[] data) {
            this.offset = offset;
            this.data = data;
        }
    }

    /**
     * Reads batches from raw blocks.
     */
    static List<Batch> readBatches(List<byte[]> rawBlocks, Path path) throws IOException {
        List<Batch> batches = new ArrayList<>();

        boolean inRecord = false;
        long startBlockOffset = 0;
        byte[] block = new byte[0];

        for (int idx = 0; idx < rawBlocks.size(); idx++) {
            byte[] chunk = rawBlocks.get(idx);
            ByteArrayInputStream buffer = new ByteArrayInputStream(chunk);

            while (buffer.available() > 6) {
                long currentOffset = idx * (long) LOG_BLOCK_SIZE + (chunk.length - buffer.available());

                byte[] header = new byte[7];
                int headerRead = buffer.read(header);
                if (headerRead < 7) {
                    break;
                }

                ByteBuffer headerBuf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
                int crc = headerBuf.getInt();
                short length = headerBuf.getShort();
                byte blockType = headerBuf.get();

                LogEntryType entryType;
                try {
                    entryType = LogEntryType.fromValue(blockType);
                } catch (IllegalArgumentException e) {
                    // Invalid block type, skip
                    break;
                }

                byte[] data = new byte[length];
                buffer.read(data);

                switch (entryType) {
                    case FULL:
                        if (inRecord) {
                            throw new IOException(String.format(
                                    "Full block whilst still building a block at offset %d in %s",
                                    currentOffset, path));
                        }
                        batches.add(new Batch(currentOffset, data));
                        break;

                    case FIRST:
                        if (inRecord) {
                            throw new IOException(String.format(
                                    "First block whilst still building a block at offset %d in %s",
                                    currentOffset, path));
                        }
                        startBlockOffset = currentOffset;
                        block = data;
                        inRecord = true;
                        break;

                    case MIDDLE:
                        if (!inRecord) {
                            throw new IOException(String.format(
                                    "Middle block whilst not building a block at offset %d in %s",
                                    currentOffset, path));
                        }
                        byte[] newBlock = new byte[block.length + data.length];
                        System.arraycopy(block, 0, newBlock, 0, block.length);
                        System.arraycopy(data, 0, newBlock, block.length, data.length);
                        block = newBlock;
                        break;

                    case LAST:
                        if (!inRecord) {
                            throw new IOException(String.format(
                                    "Last block whilst not building a block at offset %d in %s",
                                    currentOffset, path));
                        }
                        byte[] finalBlock = new byte[block.length + data.length];
                        System.arraycopy(block, 0, finalBlock, 0, block.length);
                        System.arraycopy(data, 0, finalBlock, block.length, data.length);
                        batches.add(new Batch(startBlockOffset, finalBlock));
                        inRecord = false;
                        block = new byte[0];
                        break;

                    case ZERO:
                        // Skip zero entries
                        break;
                }
            }
        }

        return batches;
    }
}

