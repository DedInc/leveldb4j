package com.github.dedinc.leveldb4j.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility class for reading variable-length integers (varints) and length-prefixed data.
 * Implements little-endian varint encoding as used by LevelDB.
 */
public class VarintUtils {

    /**
     * Result of reading a varint, containing both the value and the raw bytes.
     */
    public static class VarintResult {
        private final long value;
        private final byte[] rawBytes;

        public VarintResult(long value, byte[] rawBytes) {
            this.value = value;
            this.rawBytes = rawBytes;
        }

        public long getValue() {
            return value;
        }

        public byte[] getRawBytes() {
            return rawBytes;
        }
    }

    /**
     * Reads a little-endian varint from an input stream.
     * Returns both the value and the raw bytes that made up the varint.
     *
     * @param stream        the input stream to read from
     * @param isGoogle32Bit if true, limits the varint to 32 bits (5 bytes max)
     * @return VarintResult containing the value and raw bytes, or null if EOF
     * @throws IOException if an I/O error occurs
     */
    public static VarintResult readLeVarintWithBytes(InputStream stream, boolean isGoogle32Bit) throws IOException {
        int i = 0;
        long result = 0;
        byte[] underlyingBytes = new byte[isGoogle32Bit ? 5 : 10];
        int limit = isGoogle32Bit ? 5 : 10;

        while (i < limit) {
            int rawByte = stream.read();
            if (rawByte == -1) {
                return null;
            }

            byte tmp = (byte) rawByte;
            underlyingBytes[i] = tmp;
            result |= ((long) (tmp & 0x7f) << (i * 7));

            if ((tmp & 0x80) == 0) {
                // Copy only the bytes we actually used
                byte[] actualBytes = new byte[i + 1];
                System.arraycopy(underlyingBytes, 0, actualBytes, 0, i + 1);
                return new VarintResult(result, actualBytes);
            }
            i++;
        }

        return new VarintResult(result, underlyingBytes);
    }

    /**
     * Reads a little-endian varint from an input stream.
     * Convenience method that only returns the value.
     * Optimized version that doesn't allocate byte arrays.
     *
     * @param stream        the input stream to read from
     * @param isGoogle32Bit if true, limits the varint to 32 bits (5 bytes max)
     * @return the varint value, or null if EOF
     * @throws IOException if an I/O error occurs
     */
    public static Long readLeVarint(InputStream stream, boolean isGoogle32Bit) throws IOException {
        int i = 0;
        long result = 0;
        int limit = isGoogle32Bit ? 5 : 10;

        while (i < limit) {
            int rawByte = stream.read();
            if (rawByte == -1) {
                return i == 0 ? null : result;
            }

            result |= ((long) (rawByte & 0x7f) << (i * 7));

            if ((rawByte & 0x80) == 0) {
                return result;
            }
            i++;
        }

        return result;
    }

    /**
     * Reads a little-endian varint from an input stream (standard 64-bit version).
     *
     * @param stream the input stream to read from
     * @return the varint value, or null if EOF
     * @throws IOException if an I/O error occurs
     */
    public static Long readLeVarint(InputStream stream) throws IOException {
        return readLeVarint(stream, false);
    }

    /**
     * Reads a blob of data which is prefixed with a varint length.
     *
     * @param stream the input stream to read from
     * @return the data blob
     * @throws IOException if an I/O error occurs or if not enough data can be read
     */
    public static byte[] readLengthPrefixedBlob(InputStream stream) throws IOException {
        Long length = readLeVarint(stream);
        if (length == null) {
            throw new IOException("Could not read length prefix");
        }

        byte[] data = new byte[length.intValue()];
        int bytesRead = stream.read(data);
        if (bytesRead != length) {
            throw new IOException(String.format("Could not read all data (expected %d, got %d)", length, bytesRead));
        }

        return data;
    }

    /**
     * Reads a 16-bit unsigned integer in little-endian format.
     *
     * @param stream the input stream to read from
     * @return the unsigned 16-bit value as an int
     * @throws IOException if an I/O error occurs
     */
    public static int readUInt16LE(InputStream stream) throws IOException {
        byte[] bytes = new byte[2];
        int bytesRead = stream.read(bytes);
        if (bytesRead != 2) {
            throw new IOException("Could not read 2 bytes for UInt16");
        }
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
    }

    /**
     * Reads a 24-bit unsigned integer in little-endian format.
     *
     * @param stream the input stream to read from
     * @return the unsigned 24-bit value as an int
     * @throws IOException if an I/O error occurs
     */
    public static int readUInt24LE(InputStream stream) throws IOException {
        byte[] bytes = new byte[4];
        int bytesRead = stream.read(bytes, 0, 3);
        if (bytesRead != 3) {
            throw new IOException("Could not read 3 bytes for UInt24");
        }
        bytes[3] = 0;
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFF;
    }

    /**
     * Reads a 32-bit unsigned integer in little-endian format.
     *
     * @param stream the input stream to read from
     * @return the unsigned 32-bit value as a long
     * @throws IOException if an I/O error occurs
     */
    public static long readUInt32LE(InputStream stream) throws IOException {
        byte[] bytes = new byte[4];
        int bytesRead = stream.read(bytes);
        if (bytesRead != 4) {
            throw new IOException("Could not read 4 bytes for UInt32");
        }
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
    }

    /**
     * Reads a 64-bit unsigned integer in little-endian format.
     *
     * @param stream the input stream to read from
     * @return the 64-bit value as a long
     * @throws IOException if an I/O error occurs
     */
    public static long readUInt64LE(InputStream stream) throws IOException {
        byte[] bytes = new byte[8];
        int bytesRead = stream.read(bytes);
        if (bytesRead != 8) {
            throw new IOException("Could not read 8 bytes for UInt64");
        }
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    /**
     * Reads a single byte from the stream.
     *
     * @param stream the input stream to read from
     * @return the byte value, or null if EOF
     * @throws IOException if an I/O error occurs
     */
    public static Integer readByte(InputStream stream) throws IOException {
        int b = stream.read();
        return b == -1 ? null : b;
    }
}

