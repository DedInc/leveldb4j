package com.github.dedinc.leveldb4j;

import com.github.dedinc.leveldb4j.core.LdbFile;
import com.github.dedinc.leveldb4j.core.LogFile;
import com.github.dedinc.leveldb4j.core.ManifestFile;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Main class for reading LevelDB databases.
 * Provides access to records from both table files (.ldb/.sst) and log files (.log).
 */
public class RawLevelDb implements Closeable {
    private static final Pattern DATA_FILE_PATTERN = Pattern.compile("[0-9]{6}\\.(ldb|log|sst)");

    private final Path inDir;
    private final List<Object> files; // List of LdbFile or LogFile
    private final ManifestFile manifest;

    /**
     * Opens a LevelDB database directory.
     *
     * @param inDir path to the LevelDB directory
     * @throws IOException if the directory cannot be read or is invalid
     */
    public RawLevelDb(Path inDir) throws IOException {
        if (!Files.isDirectory(inDir)) {
            throw new IOException("Path is not a directory: " + inDir);
        }

        this.inDir = inDir;
        this.files = new ArrayList<>();

        // Find latest manifest
        int latestManifestNo = 0;
        Path latestManifestPath = null;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inDir)) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }

                String filename = file.getFileName().toString();

                // Check for data files
                Matcher dataMatcher = DATA_FILE_PATTERN.matcher(filename);
                if (dataMatcher.matches()) {
                    String extension = filename.substring(filename.lastIndexOf('.')).toLowerCase();
                    if (extension.equals(".log")) {
                        files.add(new LogFile(file));
                    } else if (extension.equals(".ldb") || extension.equals(".sst")) {
                        files.add(new LdbFile(file));
                    }
                }

                // Check for manifest files
                if (filename.startsWith("MANIFEST-")) {
                    Pattern manifestPattern = Pattern.compile("MANIFEST-([0-9A-Fa-f]{6})");
                    Matcher manifestMatcher = manifestPattern.matcher(filename);
                    if (manifestMatcher.matches()) {
                        int manifestNo = Integer.parseInt(manifestMatcher.group(1), 16);
                        if (manifestNo > latestManifestNo) {
                            latestManifestNo = manifestNo;
                            latestManifestPath = file;
                        }
                    }
                }
            }
        }

        // Load manifest if found
        this.manifest = (latestManifestPath != null) ? new ManifestFile(latestManifestPath) : null;
    }

    /**
     * Returns the path to the LevelDB directory.
     */
    public Path getInDirPath() {
        return inDir;
    }

    /**
     * Returns the manifest file, or null if not found.
     */
    public ManifestFile getManifest() {
        return manifest;
    }

    /**
     * Iterates through all records in the database.
     *
     * @param reverse if true, iterate in reverse order by file number
     * @return iterable of records
     */
    public Iterable<com.github.dedinc.leveldb4j.core.Record> iterateRecordsRaw(boolean reverse) {
        // Sort files by file number
        List<Object> sortedFiles = new ArrayList<>(files);
        sortedFiles.sort((a, b) -> {
            int fileNoA = getFileNo(a);
            int fileNoB = getFileNo(b);
            return reverse ? Integer.compare(fileNoB, fileNoA) : Integer.compare(fileNoA, fileNoB);
        });

        return () -> new Iterator<>() {
            private int fileIndex = 0;
            private Iterator<com.github.dedinc.leveldb4j.core.Record> currentIterator = null;

            @Override
            public boolean hasNext() {
                while (currentIterator == null || !currentIterator.hasNext()) {
                    if (fileIndex >= sortedFiles.size()) {
                        return false;
                    }
                    Object file = sortedFiles.get(fileIndex++);
                    if (file instanceof LdbFile) {
                        currentIterator = ((LdbFile) file).iterator();
                    } else if (file instanceof LogFile) {
                        currentIterator = ((LogFile) file).iterator();
                    }
                }
                return true;
            }

            @Override
            public com.github.dedinc.leveldb4j.core.Record next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return currentIterator.next();
            }
        };
    }

    /**
     * Iterates through all records in the database (forward order).
     */
    public Iterable<com.github.dedinc.leveldb4j.core.Record> iterateRecordsRaw() {
        return iterateRecordsRaw(false);
    }

    /**
     * Returns a stream of all records in the database.
     *
     * @param reverse if true, iterate in reverse order by file number
     * @return stream of records
     */
    public Stream<com.github.dedinc.leveldb4j.core.Record> streamRecords(boolean reverse) {
        Iterable<com.github.dedinc.leveldb4j.core.Record> iterable = iterateRecordsRaw(reverse);
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Returns a stream of all records in the database (forward order).
     */
    public Stream<com.github.dedinc.leveldb4j.core.Record> streamRecords() {
        return streamRecords(false);
    }

    /**
     * Gets the file number from a file object.
     */
    private int getFileNo(Object file) {
        if (file instanceof LdbFile) {
            return ((LdbFile) file).getFileNo();
        } else if (file instanceof LogFile) {
            return ((LogFile) file).getFileNo();
        }
        return 0;
    }

    /**
     * Returns the number of data files in the database.
     */
    public int getFileCount() {
        return files.size();
    }

    /**
     * Returns a list of all file numbers in the database.
     */
    public List<Integer> getFileNumbers() {
        List<Integer> fileNumbers = new ArrayList<>();
        for (Object file : files) {
            fileNumbers.add(getFileNo(file));
        }
        Collections.sort(fileNumbers);
        return fileNumbers;
    }

    @Override
    public void close() throws IOException {
        IOException firstException = null;

        for (Object file : files) {
            try {
                if (file instanceof LdbFile) {
                    ((LdbFile) file).close();
                } else if (file instanceof LogFile) {
                    ((LogFile) file).close();
                }
            } catch (IOException e) {
                if (firstException == null) {
                    firstException = e;
                }
            }
        }

        if (manifest != null) {
            try {
                manifest.close();
            } catch (IOException e) {
                if (firstException == null) {
                    firstException = e;
                }
            }
        }

        if (firstException != null) {
            throw firstException;
        }
    }

    /**
     * Convenience method to use with try-with-resources.
     */
    public static RawLevelDb open(Path inDir) throws IOException {
        return new RawLevelDb(inDir);
    }

    /**
     * Convenience method to use with try-with-resources (string path).
     */
    public static RawLevelDb open(String inDir) throws IOException {
        return new RawLevelDb(Path.of(inDir));
    }
}

