package com.pajk.mcpbridge.persistence.util;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.decoder.Decoder;
import com.aayushatharva.brotli4j.decoder.DecoderJNI;
import com.aayushatharva.brotli4j.decoder.DirectDecompress;
import com.aayushatharva.brotli4j.encoder.Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Brotli 压缩/解压工具（兼容历史 GZIP 数据）。
 */
public final class CompressionUtils {

    private static final Logger log = LoggerFactory.getLogger(CompressionUtils.class);

    private static final String PREFIX_BROTLI = "[BROTLI]";
    private static final String LEGACY_GZIP_PREFIX = "[COMPRESSED]";
    private static final int DEFAULT_THRESHOLD = 2048;

    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    private static final AtomicBoolean BROTLI_AVAILABLE = new AtomicBoolean(false);

    static {
        try {
            Brotli4jLoader.ensureAvailability();
            BROTLI_AVAILABLE.set(true);
            log.info("Brotli4j native library loaded successfully.");
        } catch (Throwable t) {
            log.warn("Failed to load Brotli native library, will fall back to GZIP only: {}", t.getMessage());
        }
    }

    private CompressionUtils() {
    }

    public static String compress(String data) {
        return compress(data, DEFAULT_THRESHOLD);
    }

    public static String compress(String data, int thresholdBytes) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        if (isAlreadyCompressed(data)) {
            return data;
        }

        byte[] sourceBytes = data.getBytes(StandardCharsets.UTF_8);
        if (sourceBytes.length < thresholdBytes) {
            return data;
        }

        String brotli = tryBrotliEncode(sourceBytes);
        if (brotli != null) {
            return brotli;
        }

        // Brotli 不可用或失败 -> 回退 GZIP
        String gzip = tryGzipEncode(sourceBytes);
        if (gzip != null) {
            return gzip;
        }

        log.warn("Compression failed (both Brotli and GZIP). Returning original payload.");
        return data;
    }

    public static String decompress(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        
        // 限制压缩数据的大小，防止解压缩阻塞
        // 如果压缩数据本身超过 1MB，可能解压后会非常大，直接返回截断提示
        final int MAX_COMPRESSED_SIZE = 1024 * 1024; // 1MB
        if (data.length() > MAX_COMPRESSED_SIZE) {
            log.warn("Compressed data too large ({} bytes), skipping decompression to prevent blocking", data.length());
            return "[数据过大，跳过解压缩，原始大小: " + (data.length() / 1024) + "KB]";
        }
        
        if (data.startsWith(PREFIX_BROTLI)) {
            return decodeBrotli(data.substring(PREFIX_BROTLI.length()));
        }
        if (data.startsWith(LEGACY_GZIP_PREFIX)) {
            return decodeGzip(data.substring(LEGACY_GZIP_PREFIX.length()));
        }
        return data;
    }

    public static boolean isCompressed(String data) {
        return data != null && (data.startsWith(PREFIX_BROTLI) || data.startsWith(LEGACY_GZIP_PREFIX));
    }

    public static int getCompressionThreshold() {
        return DEFAULT_THRESHOLD;
    }

    // ------------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------------

    private static boolean isAlreadyCompressed(String data) {
        return data.startsWith(PREFIX_BROTLI) || data.startsWith(LEGACY_GZIP_PREFIX);
    }

    private static String tryBrotliEncode(byte[] data) {
        if (!BROTLI_AVAILABLE.get()) {
            return null;
        }
        try {
            Encoder.Parameters params = new Encoder.Parameters()
                    .setQuality(6)
                    .setMode(Encoder.Mode.TEXT);
            byte[] compressed = Encoder.compress(data, params);
            return PREFIX_BROTLI + BASE64_ENCODER.encodeToString(compressed);
        } catch (Exception ex) {
            log.warn("Brotli compression failed: {}", ex.getMessage());
            return null;
        }
    }

    private static String tryGzipEncode(byte[] data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
            gzip.finish();
            return LEGACY_GZIP_PREFIX + BASE64_ENCODER.encodeToString(baos.toByteArray());
        } catch (IOException e) {
            log.warn("GZIP compression failed: {}", e.getMessage());
            return null;
        }
    }

    // 解压后的最大大小限制：2MB（防止内存溢出和阻塞）
    private static final int MAX_DECOMPRESSED_SIZE = 2 * 1024 * 1024;

    private static String decodeBrotli(String base64) {
        if (!BROTLI_AVAILABLE.get()) {
            log.warn("Brotli library unavailable during decode, returning base64 text.");
            return base64;
        }
        try {
            byte[] compressed = BASE64_DECODER.decode(base64);
            DirectDecompress result = Decoder.decompress(compressed);
            if (result.getResultStatus() == DecoderJNI.Status.DONE) {
                byte[] decompressed = result.getDecompressedData();
                // 检查解压后的大小，防止过大导致阻塞
                if (decompressed.length > MAX_DECOMPRESSED_SIZE) {
                    log.warn("Decompressed data too large ({} bytes), truncating to prevent blocking", decompressed.length);
                    byte[] truncated = new byte[MAX_DECOMPRESSED_SIZE];
                    System.arraycopy(decompressed, 0, truncated, 0, MAX_DECOMPRESSED_SIZE);
                    return new String(truncated, StandardCharsets.UTF_8) + 
                           "\n\n...[解压后数据过大，已截断，仅显示前 " + (MAX_DECOMPRESSED_SIZE / 1024 / 1024) + "MB]";
                }
                return new String(decompressed, StandardCharsets.UTF_8);
            }
            log.warn("Brotli decode returned status {}", result.getResultStatus());
            return base64;
        } catch (Exception ex) {
            log.warn("Brotli decode failed: {}", ex.getMessage());
            return base64;
        }
    }

    private static String decodeGzip(String base64) {
        try {
            byte[] compressed = BASE64_DECODER.decode(base64);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
                 GZIPInputStream gzip = new GZIPInputStream(bais);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                int totalRead = 0;
                while ((len = gzip.read(buffer)) != -1) {
                    // 检查解压后的大小，防止过大导致阻塞
                    if (totalRead + len > MAX_DECOMPRESSED_SIZE) {
                        int remaining = MAX_DECOMPRESSED_SIZE - totalRead;
                        if (remaining > 0) {
                            baos.write(buffer, 0, remaining);
                        }
                        log.warn("GZIP decompressed data exceeds limit ({} bytes), truncating to prevent blocking", 
                                totalRead + len);
                        String result = baos.toString(StandardCharsets.UTF_8);
                        return result + "\n\n...[解压后数据过大，已截断，仅显示前 " + (MAX_DECOMPRESSED_SIZE / 1024 / 1024) + "MB]";
                    }
                    baos.write(buffer, 0, len);
                    totalRead += len;
                }
                return baos.toString(StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            log.warn("GZIP decode failed: {}", ex.getMessage());
            return base64;
        }
    }
}

