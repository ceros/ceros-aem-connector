package com.ceros.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ArchiveUtilsTest {

    private static final long MAX = 50L * 1024 * 1024;

    @Test
    void readsEntriesAndStripsCommonTopLevelDirectory() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("exp-123/index.manifest.v1.json", "{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        files.put("exp-123/assets/styles/reset.css", "body{}".getBytes(StandardCharsets.UTF_8));
        files.put("exp-123/assets/scripts/flex-client.js", "console.log(1)".getBytes(StandardCharsets.UTF_8));

        Map<String, byte[]> result = ArchiveUtils.readTarGz(new ByteArrayInputStream(tarGz(files)), MAX);

        assertEquals(3, result.size());
        assertArrayEquals("{\"a\":1}".getBytes(StandardCharsets.UTF_8),
                result.get("index.manifest.v1.json"));
        assertArrayEquals("body{}".getBytes(StandardCharsets.UTF_8),
                result.get("assets/styles/reset.css"));
        assertNull(result.get("exp-123/index.manifest.v1.json"), "common prefix should be stripped");
    }

    @Test
    void keepsPathsWhenNoSingleCommonDirectory() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("index.manifest.v1.json", "{}".getBytes(StandardCharsets.UTF_8));
        files.put("assets/reset.css", "x".getBytes(StandardCharsets.UTF_8));

        Map<String, byte[]> result = ArchiveUtils.readTarGz(new ByteArrayInputStream(tarGz(files)), MAX);

        assertTrue(result.containsKey("index.manifest.v1.json"));
        assertTrue(result.containsKey("assets/reset.css"));
    }

    @Test
    void getToleratesLeadingSlashAndQueryString() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("exp/page-1.manifest.v1.json", "{}".getBytes(StandardCharsets.UTF_8));
        files.put("exp/assets/scripts/flex-client.js", "js".getBytes(StandardCharsets.UTF_8));

        Map<String, byte[]> result = ArchiveUtils.readTarGz(new ByteArrayInputStream(tarGz(files)), MAX);

        assertNotNull(ArchiveUtils.get(result, "/page-1.manifest.v1.json"));
        assertNotNull(ArchiveUtils.get(result, "assets/scripts/flex-client.js?v=2"));
        assertNotNull(ArchiveUtils.get(result, "./assets/scripts/flex-client.js"));
        assertNull(ArchiveUtils.get(result, "nope.css"));
    }

    @Test
    void skipsPathTraversalEntries() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("exp/index.manifest.v1.json", "{}".getBytes(StandardCharsets.UTF_8));
        files.put("exp/../escape.txt", "evil".getBytes(StandardCharsets.UTF_8));

        Map<String, byte[]> result = ArchiveUtils.readTarGz(new ByteArrayInputStream(tarGz(files)), MAX);

        for (String key : result.keySet()) {
            assertFalse(key.contains(".."), "traversal entry must be dropped: " + key);
        }
    }

    @Test
    void enforcesMaxUncompressedSize() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("exp/big.bin", new byte[2048]);

        byte[] archive = tarGz(files);
        IOException ex = assertThrows(IOException.class,
                () -> ArchiveUtils.readTarGz(new ByteArrayInputStream(archive), 1024));
        assertTrue(ex.getMessage().toLowerCase().contains("maximum"));
    }

    // ---- minimal in-memory USTAR + gzip writer (test-only, no deps) ----

    private static byte[] tarGz(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            writeEntry(tar, e.getKey(), e.getValue());
        }
        tar.write(new byte[1024]); // two zero blocks = end of archive

        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream g = new GZIPOutputStream(gz)) {
            g.write(tar.toByteArray());
        }
        return gz.toByteArray();
    }

    private static void writeEntry(ByteArrayOutputStream tar, String name, byte[] data) throws IOException {
        byte[] header = new byte[512];
        byte[] nb = name.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(nb, 0, header, 0, Math.min(nb.length, 100));
        putOctal(header, 100, 8, 0644); // mode
        putOctal(header, 108, 8, 0);    // uid
        putOctal(header, 116, 8, 0);    // gid
        putOctal(header, 124, 12, data.length);
        putOctal(header, 136, 12, 0);   // mtime
        header[156] = '0';              // typeflag: regular file
        byte[] magic = "ustar\0".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(magic, 0, header, 257, magic.length);
        header[263] = '0';
        header[264] = '0';

        // checksum: treat chksum field as spaces, sum all bytes, then write octal.
        for (int i = 148; i < 156; i++) {
            header[i] = ' ';
        }
        int sum = 0;
        for (byte b : header) {
            sum += (b & 0xFF);
        }
        byte[] cs = String.format("%06o", sum).getBytes(StandardCharsets.UTF_8);
        System.arraycopy(cs, 0, header, 148, 6);
        header[154] = 0;
        header[155] = ' ';

        tar.write(header, 0, 512);
        if (data.length > 0) {
            tar.write(data, 0, data.length);
            int pad = (512 - (data.length % 512)) % 512;
            tar.write(new byte[pad], 0, pad);
        }
    }

    private static void putOctal(byte[] buf, int off, int len, long value) {
        String s = Long.toOctalString(value);
        int digits = len - 1; // last byte is NUL
        StringBuilder sb = new StringBuilder();
        while (sb.length() + s.length() < digits) {
            sb.append('0');
        }
        sb.append(s);
        byte[] b = sb.toString().getBytes(StandardCharsets.UTF_8);
        System.arraycopy(b, 0, buf, off, Math.min(b.length, digits));
        buf[off + len - 1] = 0;
    }
}
