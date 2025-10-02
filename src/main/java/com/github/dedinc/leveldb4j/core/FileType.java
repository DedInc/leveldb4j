package com.github.dedinc.leveldb4j.core;

/**
 * Type of LevelDB file.
 */
public enum FileType {
    /**
     * Table file (.ldb or .sst)
     */
    LDB,

    /**
     * Log file (.log)
     */
    LOG
}

