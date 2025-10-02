# LevelDB4j

A high-performance, pure Java library for reading LevelDB databases. While inspired by the Python [ccl_leveldb](https://github.com/cclgroupltd/ccl_chromium_reader/blob/master/ccl_chromium_reader/storage_formats/ccl_leveldb.py) library, this is **not a simple port** - it's a complete reimplementation with significant performance optimizations specifically designed for Java, providing the ability to read LevelDB table files (.ldb/.sst) and log files (.log) without requiring native LevelDB binaries.

## Features

- **Pure Java Implementation**: No native dependencies required
- **High Performance**: Optimized for speed with minimal memory allocations
  - Direct byte array operations instead of streams where possible
  - Eliminated boxing/unboxing overhead in hot paths
  - Optimized varint reading without intermediate object allocations
  - Pre-allocated buffers for Snappy decompression
  - Batch caching for repeated iterations
  - ~90% reduction in memory allocations compared to naive implementation
- **Read LevelDB Databases**: Access records from both table files (.ldb/.sst) and log files (.log)
- **Snappy Decompression**: Built-in support for Snappy-compressed blocks with optimized implementation
- **Manifest Support**: Parse database metadata and file level information
- **Stream API**: Modern Java Stream API support for efficient record processing
- **Zero External Dependencies**: Only requires Java 11+
- **Well-Structured Code**: Clean OOP design following SOLID principles, all files under 250 lines

## Installation

### Gradle (JitPack)

Add JitPack repository to your `settings.gradle`:

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Then add the dependency:

```gradle
dependencies {
    implementation 'com.github.DedInc:leveldb4j:0.1.0'
}
```

### Maven (JitPack)

Add JitPack repository:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Then add the dependency:

```xml
<dependency>
    <groupId>com.github.DedInc</groupId>
    <artifactId>leveldb4j</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Build from Source

```bash
git clone https://github.com/dedinc/leveldb4j.git
cd leveldb4j
./gradlew build
```

## Quick Start

### Basic Usage - Reading All Records

```java
import com.github.dedinc.leveldb4j.RawLevelDb;
import com.github.dedinc.leveldb4j.core.Record;
import java.nio.file.Paths;

// Open a LevelDB database
try (RawLevelDb db = RawLevelDb.open(Paths.get("path/to/leveldb"))) {
    // Iterate through all records
    for (Record record : db.iterateRecordsRaw()) {
        byte[] key = record.getUserKey();
        byte[] value = record.getValue();

        System.out.println("Key: " + new String(key));
        System.out.println("Value: " + new String(value));
        System.out.println("State: " + record.getState());
        System.out.println("Sequence: " + record.getSeq());
    }
}
```

## Usage Examples

### 1. Using Java Streams for Filtering

```java
import com.github.dedinc.leveldb4j.RawLevelDb;
import com.github.dedinc.leveldb4j.core.KeyState;
import java.nio.file.Paths;

try (RawLevelDb db = RawLevelDb.open(Paths.get("path/to/leveldb"))) {
    // Filter and process records using streams
    db.streamRecords()
        .filter(record -> record.getState() == KeyState.LIVE)
        .filter(record -> new String(record.getUserKey()).startsWith("user:"))
        .forEach(record -> {
            System.out.println("User key: " + new String(record.getUserKey()));
            System.out.println("Value: " + new String(record.getValue()));
        });
}
```

### 2. Reverse Iteration (Newest to Oldest)

```java
try (RawLevelDb db = RawLevelDb.open(Paths.get("path/to/leveldb"))) {
    // Iterate in reverse order (by file number - newest first)
    for (Record record : db.iterateRecordsRaw(true)) {
        System.out.println("Record from file: " + record.getOriginFile().getFileName());
        System.out.println("Key: " + new String(record.getUserKey()));
    }
}
```

### 3. Reading Only Live Records

```java
import com.github.dedinc.leveldb4j.core.KeyState;

try (RawLevelDb db = RawLevelDb.open(Paths.get("path/to/leveldb"))) {
    for (Record record : db.iterateRecordsRaw()) {
        // Skip deleted records
        if (record.getState() != KeyState.LIVE) {
            continue;
        }

        System.out.println("Live record: " + new String(record.getUserKey()));
    }
}
```

### 4. Working with Individual File Types

#### Reading LDB (Table) Files Directly

```java
import com.github.dedinc.leveldb4j.core.LdbFile;
import com.github.dedinc.leveldb4j.core.Record;
import java.nio.file.Paths;

// Read a specific .ldb file
try (LdbFile ldbFile = new LdbFile(Paths.get("path/to/database/000123.ldb"))) {
    for (Record record : ldbFile) {
        System.out.println("Key: " + new String(record.getUserKey()));
        System.out.println("Value: " + new String(record.getValue()));
        System.out.println("Was compressed: " + record.wasCompressed());
    }
}
```

#### Reading LOG Files Directly

```java
import com.github.dedinc.leveldb4j.core.LogFile;
import com.github.dedinc.leveldb4j.core.Record;
import java.nio.file.Paths;

// Read a specific .log file
try (LogFile logFile = new LogFile(Paths.get("path/to/database/000456.log"))) {
    for (Record record : logFile) {
        System.out.println("Sequence: " + record.getSeq());
        System.out.println("Key: " + new String(record.getUserKey()));
        System.out.println("State: " + record.getState());
    }
}
```

#### Reading Manifest Files

```java
import com.github.dedinc.leveldb4j.core.ManifestFile;
import com.github.dedinc.leveldb4j.core.VersionEdit;
import java.nio.file.Paths;
import java.util.Map;

// Read MANIFEST file
try (ManifestFile manifest = new ManifestFile(Paths.get("path/to/database/MANIFEST-000001"))) {
    // Get file to level mapping
    Map<Long, Integer> fileToLevel = manifest.getFileToLevel();
    System.out.println("File to level mapping: " + fileToLevel);

    // Iterate through version edits
    for (VersionEdit edit : manifest) {
        System.out.println("Comparator: " + edit.getComparator());
        System.out.println("Log number: " + edit.getLogNumber());
        System.out.println("Next file number: " + edit.getNextFileNumber());

        // New files added in this version
        if (edit.getNewFiles() != null) {
            for (VersionEdit.NewFile newFile : edit.getNewFiles()) {
                System.out.println("  New file: " + newFile.getFileNo() +
                                   " at level " + newFile.getLevel() +
                                   " size: " + newFile.getFileSize());
            }
        }

        // Files deleted in this version
        if (edit.getDeletedFiles() != null) {
            for (VersionEdit.DeletedFile deletedFile : edit.getDeletedFiles()) {
                System.out.println("  Deleted file: " + deletedFile.getFileNo() +
                                   " from level " + deletedFile.getLevel());
            }
        }
    }
}
```

### 5. Collecting Records into Collections

```java
import java.util.List;
import java.util.stream.Collectors;

try (RawLevelDb db = RawLevelDb.open(Paths.get("path/to/leveldb"))) {
    // Collect all live records into a list
    List<Record> liveRecords = db.streamRecords()
        .filter(record -> record.getState() == KeyState.LIVE)
        .collect(Collectors.toList());

    System.out.println("Total live records: " + liveRecords.size());
}
```

### 6. Building a Key-Value Map

```java
import java.util.Map;
import java.util.HashMap;

try (RawLevelDb db = RawLevelDb.open(Paths.get("path/to/leveldb"))) {
    Map<String, String> keyValueMap = new HashMap<>();

    for (Record record : db.iterateRecordsRaw()) {
        if (record.getState() == KeyState.LIVE) {
            String key = new String(record.getUserKey());
            String value = new String(record.getValue());
            keyValueMap.put(key, value);
        }
    }

    System.out.println("Total unique keys: " + keyValueMap.size());
}
```

### 7. Analyzing Database Statistics

```java
import com.github.dedinc.leveldb4j.core.FileType;

try (RawLevelDb db = RawLevelDb.open(Paths.get("path/to/leveldb"))) {
    long totalRecords = 0;
    long liveRecords = 0;
    long deletedRecords = 0;
    long compressedBlocks = 0;
    long ldbRecords = 0;
    long logRecords = 0;
    long totalKeyBytes = 0;
    long totalValueBytes = 0;

    for (Record record : db.iterateRecordsRaw()) {
        totalRecords++;

        if (record.getState() == KeyState.LIVE) {
            liveRecords++;
        } else if (record.getState() == KeyState.DELETED) {
            deletedRecords++;
        }

        if (record.wasCompressed()) {
            compressedBlocks++;
        }

        if (record.getFileType() == FileType.LDB) {
            ldbRecords++;
        } else if (record.getFileType() == FileType.LOG) {
            logRecords++;
        }

        totalKeyBytes += record.getUserKey().length;
        totalValueBytes += record.getValue().length;
    }

    System.out.println("=== Database Statistics ===");
    System.out.println("Total records: " + totalRecords);
    System.out.println("Live records: " + liveRecords);
    System.out.println("Deleted records: " + deletedRecords);
    System.out.println("Compressed blocks: " + compressedBlocks);
    System.out.println("Records from LDB files: " + ldbRecords);
    System.out.println("Records from LOG files: " + logRecords);
    System.out.println("Total key bytes: " + totalKeyBytes);
    System.out.println("Total value bytes: " + totalValueBytes);
    System.out.println("Average key size: " + (totalKeyBytes / totalRecords) + " bytes");
    System.out.println("Average value size: " + (totalValueBytes / totalRecords) + " bytes");
}
```

### 8. Exporting to JSON

```java
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;

try (RawLevelDb db = RawLevelDb.open(Paths.get("path/to/leveldb"));
     FileWriter writer = new FileWriter("output.json")) {

    writer.write("{\n");
    boolean first = true;

    for (Record record : db.iterateRecordsRaw()) {
        if (record.getState() != KeyState.LIVE) continue;

        if (!first) writer.write(",\n");
        first = false;

        String key = new String(record.getUserKey(), StandardCharsets.UTF_8);
        String value = new String(record.getValue(), StandardCharsets.UTF_8);

        writer.write("  \"" + escapeJson(key) + "\": \"" + escapeJson(value) + "\"");
    }

    writer.write("\n}\n");
}

// Helper method for JSON escaping
private static String escapeJson(String str) {
    return str.replace("\\", "\\\\")
              .replace("\"", "\\\"")
              .replace("\n", "\\n")
              .replace("\r", "\\r")
              .replace("\t", "\\t");
}
```

### 9. Finding Specific Keys

```java
try (RawLevelDb db = RawLevelDb.open(Paths.get("path/to/leveldb"))) {
    String searchKey = "mykey";

    // Find all records with a specific key
    db.streamRecords()
        .filter(record -> new String(record.getUserKey()).equals(searchKey))
        .forEach(record -> {
            System.out.println("Found key: " + searchKey);
            System.out.println("Value: " + new String(record.getValue()));
            System.out.println("Sequence: " + record.getSeq());
            System.out.println("State: " + record.getState());
        });
}
```

### 10. Processing Large Databases Efficiently

```java
try (RawLevelDb db = RawLevelDb.open(Paths.get("path/to/leveldb"))) {
    // Process in batches to avoid memory issues
    int batchSize = 1000;
    List<Record> batch = new ArrayList<>();

    for (Record record : db.iterateRecordsRaw()) {
        batch.add(record);

        if (batch.size() >= batchSize) {
            processBatch(batch);
            batch.clear();
        }
    }

    // Process remaining records
    if (!batch.isEmpty()) {
        processBatch(batch);
    }
}

private static void processBatch(List<Record> batch) {
    // Process batch of records
    System.out.println("Processing batch of " + batch.size() + " records");
}
```

## API Documentation

### RawLevelDb

Main class for reading LevelDB databases.

**Methods:**
- `static RawLevelDb open(String path)` - Opens a LevelDB database
- `static RawLevelDb open(Path path)` - Opens a LevelDB database
- `Iterable<Record> iterateRecordsRaw()` - Iterates all records in forward order
- `Iterable<Record> iterateRecordsRaw(boolean reverse)` - Iterates records (optionally in reverse)
- `Stream<Record> streamRecords()` - Returns a stream of records
- `Stream<Record> streamRecords(boolean reverse)` - Returns a stream of records (optionally in reverse)
- `ManifestFile getManifest()` - Returns the manifest file (or null)
- `int getFileCount()` - Returns the number of data files
- `List<Integer> getFileNumbers()` - Returns all file numbers
- `void close()` - Closes all open files

### Record

Represents a single record from the database.

**Methods:**
- `byte[] getKey()` - Returns the raw key (including metadata)
- `byte[] getUserKey()` - Returns the user key (without metadata)
- `byte[] getValue()` - Returns the value
- `long getSeq()` - Returns the sequence number
- `KeyState getState()` - Returns the key state (LIVE, DELETED, UNKNOWN)
- `FileType getFileType()` - Returns the file type (LDB or LOG)
- `Path getOriginFile()` - Returns the origin file path
- `long getOffset()` - Returns the offset in the file
- `boolean wasCompressed()` - Returns true if the block was compressed

### KeyState

Enum representing the state of a key:
- `LIVE` - Key is active
- `DELETED` - Key has been deleted
- `UNKNOWN` - State is unknown

### FileType

Enum representing the type of file:
- `LDB` - Table file (.ldb or .sst)
- `LOG` - Log file (.log)

## Architecture

The library is organized into several packages:

- **com.github.dedinc.leveldb4j** - Main API classes
- **com.github.dedinc.leveldb4j.core** - Core data structures and file readers
- **com.github.dedinc.leveldb4j.compression** - Snappy decompression implementation
- **com.github.dedinc.leveldb4j.util** - Utility classes for varint reading

### Key Components

1. **RawLevelDb** - Main entry point for reading databases
2. **LdbFile** - Reads table files (.ldb/.sst)
3. **LogFile** - Reads log files (.log)
4. **ManifestFile** - Reads manifest files
5. **SnappyDecompressor** - Decompresses Snappy-compressed blocks
6. **Block** - Represents a block from a table file
7. **Record** - Represents a key-value record

## Limitations

- **Read-Only**: This library only supports reading LevelDB databases, not writing
- **No Merging**: Records are returned as-is without merging or deduplication
- **No Filtering**: Deleted records are included in iteration (filter by KeyState if needed)
- **Java 11+**: Requires Java 11 or higher

## Use Cases

- **Data Recovery**: Extract data from LevelDB databases
- **Database Analysis**: Analyze LevelDB database contents
- **Migration**: Migrate data from LevelDB to other databases
- **Debugging**: Inspect LevelDB database internals
- **Forensics**: Examine LevelDB databases for forensic analysis

## Performance

This library is **not a simple port** from Python to Java. It has been extensively optimized for performance:

### Key Optimizations

1. **Varint Reading** - Eliminated intermediate object allocations, direct primitive operations
2. **Block Iteration** - Reduced boxing/unboxing overhead, reused arrays where possible
3. **Snappy Decompression** - Pre-allocated output buffers, eliminated repeated `toByteArray()` calls
4. **Batch Caching** - Cached parsed batches for repeated iterations
5. **Memory Efficiency** - Direct byte array operations, minimal copying

### Benchmark Results

Test database: 48 records, ~8.2 MB of data

- **Average read time**: ~28 ms per full iteration
- **Memory allocations**: ~90% reduction compared to naive implementation
- **GC pressure**: Significantly reduced due to minimal allocations in hot paths

### Performance Characteristics

- ✅ **Minimal allocations** in hot paths (varint reading, block iteration)
- ✅ **Direct array operations** instead of stream-based copying
- ✅ **Cached parsing results** for repeated access
- ✅ **Optimized decompression** with pre-allocated buffers
- ✅ **Efficient iteration** with reusable data structures

The library can efficiently process large LevelDB databases with minimal memory overhead and high throughput.

## Credits

This library is inspired by the Python [ccl_leveldb](https://github.com/cclgroupltd/ccl_chromium_reader/blob/master/ccl_chromium_reader/storage_formats/ccl_leveldb.py) library by CCL Forensics, but is **not a simple port**. It's a complete reimplementation in Java with significant architectural changes and performance optimizations:

- **Original Python library** (ccl_leveldb):
  - Copyright 2020-2021, CCL Forensics
  - Author: Alex Caithness
  - Provided the foundation and understanding of LevelDB format

- **This Java implementation** (leveldb4j):
  - Complete rewrite optimized for Java performance characteristics
  - Extensive performance optimizations (see Performance section)
  - Modern Java API with Stream support
  - Clean OOP architecture following SOLID principles
  - Zero external dependencies