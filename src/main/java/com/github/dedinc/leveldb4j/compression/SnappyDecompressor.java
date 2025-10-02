package com.github.dedinc.leveldb4j.compression;

import com.github.dedinc.leveldb4j.util.VarintUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Pure Java implementation of Snappy decompression for LevelDB.
 * Supports both raw and framed Snappy formats.
 */
public class SnappyDecompressor {

    /**
     * Element types in compressed Snappy data.
     */
    private enum ElementType {
        LITERAL(0),
        COPY_ONE_BYTE(1),
        COPY_TWO_BYTE(2),
        COPY_FOUR_BYTE(3);

        private final int value;

        ElementType(int value) {
            this.value = value;
        }

        public static ElementType fromValue(int value) {
            for (ElementType type : values()) {
                if (type.value == value) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Invalid element type: " + value);
        }
    }

    /**
     * Decompresses raw Snappy compressed data.
     *
     * @param stream input stream containing compressed data
     * @return decompressed data
     * @throws IOException if decompression fails
     */
    public static byte[] decompress(InputStream stream) throws IOException {
        Long uncompressedLengthObj = VarintUtils.readLeVarint(stream);
        if (uncompressedLengthObj == null) {
            throw new IOException("Could not read uncompressed length");
        }
        int uncompressedLength = uncompressedLengthObj.intValue();

        // Use a byte array directly instead of ByteArrayOutputStream for better performance
        byte[] output = new byte[uncompressedLength];
        int outputPos = 0;

        while (true) {
            Integer typeByte = VarintUtils.readByte(stream);
            if (typeByte == null) {
                break;
            }

            int tag = typeByte & 0x03;
            ElementType elementType = ElementType.fromValue(tag);

            if (elementType == ElementType.LITERAL) {
                int length;
                int lengthField = (typeByte & 0xFC) >>> 2;

                if (lengthField < 60) {
                    length = 1 + lengthField;
                } else if (lengthField == 60) {
                    Integer b = VarintUtils.readByte(stream);
                    if (b == null) throw new IOException("Unexpected EOF");
                    length = 1 + b;
                } else if (lengthField == 61) {
                    length = 1 + VarintUtils.readUInt16LE(stream);
                } else if (lengthField == 62) {
                    length = 1 + VarintUtils.readUInt24LE(stream);
                } else { // 63
                    length = 1 + (int) VarintUtils.readUInt32LE(stream);
                }

                int bytesRead = stream.read(output, outputPos, length);
                if (bytesRead < length) {
                    throw new IOException("Couldn't read enough literal data");
                }
                outputPos += length;

            } else {
                int length;
                int offset;

                if (elementType == ElementType.COPY_ONE_BYTE) {
                    length = ((typeByte & 0x1C) >>> 2) + 4;
                    Integer b = VarintUtils.readByte(stream);
                    if (b == null) throw new IOException("Unexpected EOF");
                    offset = ((typeByte & 0xE0) << 3) | b;
                } else if (elementType == ElementType.COPY_TWO_BYTE) {
                    length = 1 + ((typeByte & 0xFC) >>> 2);
                    offset = VarintUtils.readUInt16LE(stream);
                } else { // COPY_FOUR_BYTE
                    length = 1 + ((typeByte & 0xFC) >>> 2);
                    offset = (int) VarintUtils.readUInt32LE(stream);
                }

                if (offset == 0) {
                    throw new IOException("Offset cannot be 0");
                }

                int srcPos = outputPos - offset;

                if (srcPos < 0) {
                    throw new IOException("Invalid backreference offset");
                }

                // Handle overlapping copies
                if (offset < length) {
                    // Need to copy incrementally for overlapping regions
                    for (int i = 0; i < length; i++) {
                        output[outputPos++] = output[srcPos + (i % offset)];
                    }
                } else {
                    // Can copy the whole block at once
                    System.arraycopy(output, srcPos, output, outputPos, length);
                    outputPos += length;
                }
            }
        }

        if (outputPos != uncompressedLength) {
            throw new IOException(String.format(
                    "Wrong data length in uncompressed data (expected %d, got %d)",
                    uncompressedLength, outputPos));
        }

        return output;
    }

    /**
     * Exception thrown when no more data is available in framed format.
     */
    public static class NoMoreDataException extends IOException {
        public NoMoreDataException() {
            super("No more data");
        }
    }

    /**
     * Decompresses Snappy framed format data.
     *
     * @param frameStream input stream containing framed data
     * @param mozillaMode if true, use Mozilla's non-standard checksum format
     * @return decompressed data
     * @throws IOException if decompression fails
     */
    public static byte[] decompressFramed(InputStream frameStream, boolean mozillaMode) throws IOException {
        SnappyFrame header = SnappyFrame.read(frameStream);
        if (header.frameId != 0xFF || !Arrays.equals(header.data, SnappyFrame.FRAME_MAGIC)) {
            throw new IOException("Invalid magic");
        }

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();

        while (true) {
            SnappyFrame frame;
            try {
                frame = SnappyFrame.read(frameStream);
            } catch (NoMoreDataException e) {
                break;
            }

            if (frame.frameId == 0x00) { // Compressed
                byte[] crcBytes = Arrays.copyOfRange(frame.data, 0, 4);
                long storedCrc = ByteBuffer.wrap(crcBytes).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;

                byte[] compressedData = Arrays.copyOfRange(frame.data, 4, frame.data.length);
                byte[] decompressed = decompress(new ByteArrayInputStream(compressedData));

                if (!Crc32c.checkMasked(storedCrc, decompressed, mozillaMode ? 0x0L : 0xFFFFFFFFL)) {
                    throw new IOException("CRC mismatch in compressed frame");
                }

                outStream.write(decompressed);

            } else if (frame.frameId == 0x01) { // Uncompressed
                byte[] crcBytes = Arrays.copyOfRange(frame.data, 0, 4);
                long storedCrc = ByteBuffer.wrap(crcBytes).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;

                byte[] uncompressedData = Arrays.copyOfRange(frame.data, 4, frame.data.length);

                if (!Crc32c.checkMasked(storedCrc, uncompressedData, mozillaMode ? 0x0L : 0xFFFFFFFFL)) {
                    throw new IOException("CRC mismatch in uncompressed frame");
                }

                outStream.write(uncompressedData);

            } else if (frame.frameId == 0xFE) { // Padding
                // Skip padding
            } else if (frame.frameId >= 0x02 && frame.frameId <= 0x7F) {
                throw new IOException("Reserved unskippable data");
            } else if (frame.frameId >= 0x80 && frame.frameId <= 0xFD) {
                // Reserved skippable, ignore
            } else {
                throw new IOException("Unexpected frame type: " + frame.frameId);
            }
        }

        return outStream.toByteArray();
    }

    /**
     * Decompresses Snappy framed format data (standard mode).
     */
    public static byte[] decompressFramed(InputStream frameStream) throws IOException {
        return decompressFramed(frameStream, false);
    }
}

