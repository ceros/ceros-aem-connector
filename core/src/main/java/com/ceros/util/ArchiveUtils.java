package com.ceros.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Minimal, dependency-free reader for the gzip-compressed tar ({@code .tar.gz})
 * export archives produced by the Ceros app. Reads the whole archive into
 * memory as a map of archive-root-relative path &rarr; file bytes.
 *
 * <p>Implements just enough of the USTAR / GNU tar format for these archives:
 * regular files, directories (skipped), and GNU long-name ({@code 'L'})
 * extension headers. pax extended headers and other entry types are skipped.
 * The Ceros export uses short, ASCII paths well within the 100-char USTAR name
 * field, so this covers it without pulling in commons-compress (which would add
 * an OSGi bundle dependency to an otherwise dependency-free core).</p>
 */
public final class ArchiveUtils {

    private ArchiveUtils() {
    }

    private static final int BLOCK = 512;
    private static final int NAME_OFFSET = 0;
    private static final int NAME_LEN = 100;
    private static final int SIZE_OFFSET = 124;
    private static final int SIZE_LEN = 12;
    private static final int TYPEFLAG_OFFSET = 156;
    private static final int PREFIX_OFFSET = 345;
    private static final int PREFIX_LEN = 155;

    private static final char TYPE_FILE = '0';
    private static final char TYPE_FILE_ALT = '\0';
    private static final char TYPE_DIR = '5';
    private static final char TYPE_GNU_LONGNAME = 'L';

    /**
     * Reads a {@code .tar.gz} stream into a map keyed by archive-root-relative
     * path.
     *
     * <p>If every entry sits under a single common top-level directory (the
     * Ceros export wraps everything in an experience-id folder), that prefix is
     * stripped so manifest-relative URLs like {@code assets/styles/reset.css}
     * resolve directly against the returned map.</p>
     *
     * @param in            the raw {@code .tar.gz} bytes (caller retains ownership)
     * @param maxTotalBytes hard cap on the summed uncompressed file size; a
     *                      larger archive aborts with {@link IOException} (zip-bomb guard)
     * @return ordered map of relative path to file content
     * @throws IOException on malformed archives, path traversal, or size overflow
     */
    public static Map<String, byte[]> readTarGz(InputStream in, long maxTotalBytes) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long total = 0;
        byte[] header = new byte[BLOCK];
        String pendingLongName = null;

        try (GZIPInputStream gz = new GZIPInputStream(in)) {
            while (readFully(gz, header)) {
                if (isAllZero(header)) {
                    break; // end-of-archive marker (two zero blocks)
                }

                char type = (char) (header[TYPEFLAG_OFFSET] & 0xFF);
                long size = parseOctal(header, SIZE_OFFSET, SIZE_LEN);
                byte[] body = readEntryBody(gz, size);

                if (type == TYPE_GNU_LONGNAME) {
                    // The body of an 'L' entry is the real name of the *next* entry.
                    pendingLongName = trimToNul(new String(body, StandardCharsets.UTF_8));
                    continue;
                }

                String name = pendingLongName != null
                        ? pendingLongName
                        : combineName(header);
                pendingLongName = null;

                boolean isDir = type == TYPE_DIR || name.endsWith("/");
                boolean isFile = type == TYPE_FILE || type == TYPE_FILE_ALT;
                if (isDir || !isFile) {
                    continue; // skip directories, symlinks, pax headers, etc.
                }

                String key = normalizeKey(name);
                if (key.isEmpty() || key.contains("..")) {
                    continue; // defend against path traversal
                }

                total += body.length;
                if (total > maxTotalBytes) {
                    throw new IOException(
                            "Archive exceeds maximum uncompressed size of " + maxTotalBytes + " bytes");
                }
                entries.put(key, body);
            }
        }

        return stripCommonPrefix(entries);
    }

    /**
     * Looks up an entry by a manifest-relative URL, tolerating the leading-slash
     * and {@code ./} variations and query/fragment suffixes seen across the
     * different manifest fields.
     *
     * @return the file bytes, or {@code null} if no entry matches
     */
    public static byte[] get(Map<String, byte[]> entries, String relativeUrl) {
        if (entries == null || relativeUrl == null) {
            return null;
        }
        return entries.get(normalizeLookup(relativeUrl));
    }

    /**
     * Normalizes a manifest URL to the same archive-root-relative form used as
     * the entry map key — stripping {@code ./} / leading-slash prefixes and any
     * query or fragment. Exposed so callers can derive a stable relative path
     * (e.g. to mirror the archive layout into the DAM).
     */
    public static String normalizeLookup(String relativeUrl) {
        String key = relativeUrl.trim();
        int q = key.indexOf('?');
        if (q >= 0) {
            key = key.substring(0, q);
        }
        int h = key.indexOf('#');
        if (h >= 0) {
            key = key.substring(0, h);
        }
        return normalizeKey(key);
    }

    private static String combineName(byte[] header) {
        String name = readString(header, NAME_OFFSET, NAME_LEN);
        String prefix = readString(header, PREFIX_OFFSET, PREFIX_LEN);
        return prefix.isEmpty() ? name : prefix + "/" + name;
    }

    private static String normalizeKey(String name) {
        String n = name;
        while (n.startsWith("./")) {
            n = n.substring(2);
        }
        while (n.startsWith("/")) {
            n = n.substring(1);
        }
        return n;
    }

    /**
     * If every entry shares one common top-level directory, strips it so the
     * returned keys are relative to the experience root (the directory holding
     * {@code index.manifest.v1.json}). Returns the map unchanged when there is
     * no single wrapper directory.
     */
    private static Map<String, byte[]> stripCommonPrefix(Map<String, byte[]> entries) {
        if (entries.isEmpty()) {
            return entries;
        }
        String common = null;
        for (String key : entries.keySet()) {
            int slash = key.indexOf('/');
            if (slash < 0) {
                return entries; // a top-level file means no single wrapper dir
            }
            String first = key.substring(0, slash);
            if (common == null) {
                common = first;
            } else if (!common.equals(first)) {
                return entries;
            }
        }
        String prefix = common + "/";
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            result.put(e.getKey().substring(prefix.length()), e.getValue());
        }
        return result;
    }

    private static byte[] readEntryBody(InputStream in, long size) throws IOException {
        if (size < 0) {
            throw new IOException("Negative tar entry size");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long remaining = size;
        while (remaining > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
            if (n < 0) {
                throw new IOException("Unexpected end of archive while reading entry body");
            }
            out.write(buf, 0, n);
            remaining -= n;
        }
        // Entry bodies are padded with NULs to the next 512-byte boundary.
        skipFully(in, (BLOCK - (size % BLOCK)) % BLOCK);
        return out.toByteArray();
    }

    private static boolean readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) {
                return false; // clean or truncated EOF — treat as end of archive
            }
            off += n;
        }
        return true;
    }

    private static void skipFully(InputStream in, long n) throws IOException {
        long remaining = n;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    return;
                }
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
    }

    private static boolean isAllZero(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static String readString(byte[] block, int offset, int len) {
        int end = offset;
        int max = offset + len;
        while (end < max && block[end] != 0) {
            end++;
        }
        return new String(block, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static String trimToNul(String s) {
        int nul = s.indexOf('\0');
        return nul >= 0 ? s.substring(0, nul) : s;
    }

    private static long parseOctal(byte[] block, int offset, int len) {
        // GNU base-256 encoding for large sizes sets the high bit of the first byte.
        if ((block[offset] & 0x80) != 0) {
            long value = block[offset] & 0x7F;
            for (int i = 1; i < len; i++) {
                value = (value << 8) | (block[offset + i] & 0xFF);
            }
            return value;
        }
        long value = 0;
        for (int i = 0; i < len; i++) {
            byte b = block[offset + i];
            if (b < '0' || b > '7') {
                continue; // skip leading/trailing spaces and NULs
            }
            value = (value << 3) + (b - '0');
        }
        return value;
    }
}
