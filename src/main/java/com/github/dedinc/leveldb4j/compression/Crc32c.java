package com.github.dedinc.leveldb4j.compression;

/**
 * CRC32C checksum calculator for Snappy compression.
 */
class Crc32c {
    private static final long CRC_POLY = 0x82F63B78L;
    private static final long[] CRC_TABLE = makeCrcTable(CRC_POLY);

    /**
     * Generates CRC32C lookup table.
     */
    private static long[] makeCrcTable(long poly) {
        long[] table = new long[256];
        for (int i = 0; i < 256; i++) {
            long crc = 0;
            int idx = i;
            for (int j = 0; j < 8; j++) {
                if (((idx ^ crc) & 1) != 0) {
                    crc = (crc >>> 1) ^ poly;
                } else {
                    crc >>>= 1;
                }
                idx >>>= 1;
            }
            table[i] = crc;
        }
        return table;
    }

    /**
     * Calculates CRC32C checksum.
     */
    static long calculate(byte[] data, long xorValue) {
        long value = 0xFFFFFFFFL;
        for (byte b : data) {
            value = CRC_TABLE[(int) ((b ^ value) & 0xFF)] ^ (value >>> 8);
        }
        value ^= xorValue;
        return value & 0xFFFFFFFFL;
    }

    /**
     * Checks masked CRC as used in Snappy framed format.
     */
    static boolean checkMasked(long crc, byte[] data, long xorValue) {
        long check = calculate(data, xorValue);
        // Rotate right by 15 bits
        check = ((check >>> 15) | (check << 17)) & 0xFFFFFFFFL;
        // Add constant
        check = (check + 0xa282ead8L) & 0xFFFFFFFFL;
        return crc == check;
    }
}

