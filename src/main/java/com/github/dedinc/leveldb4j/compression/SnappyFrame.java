package com.github.dedinc.leveldb4j.compression;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Handles reading Snappy framed format.
 */
class SnappyFrame {
    static final byte[] FRAME_MAGIC = new byte[]{0x73, 0x4E, 0x61, 0x50, 0x70, 0x59};

    final int frameId;
    final byte[] data;

    SnappyFrame(int frameId, byte[] data) {
        this.frameId = frameId;
        this.data = data;
    }

    /**
     * Reads a single frame from the stream.
     */
    static SnappyFrame read(InputStream stream) throws IOException {
        byte[] header = new byte[4];
        int bytesRead = stream.read(header);
        if (bytesRead == 0 || bytesRead == -1) {
            throw new SnappyDecompressor.NoMoreDataException();
        }
        if (bytesRead < 4) {
            throw new IOException("Could not read entire frame header");
        }

        int frameId = header[0] & 0xFF;
        int frameLength = ByteBuffer.wrap(new byte[]{header[1], header[2], header[3], 0})
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt() & 0xFFFFFF;

        byte[] data = new byte[frameLength];
        bytesRead = stream.read(data);
        if (bytesRead != frameLength) {
            throw new IOException(String.format(
                    "Could not read all data; wanted: %d; got: %d", frameLength, bytesRead));
        }

        return new SnappyFrame(frameId, data);
    }
}

