package com.pajk.mcpbridge.persistence.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 压缩工具类
 * 用于压缩和解压缩字符串数据，主要用于存储大型响应体
 * 
 * @author MCP Router Team
 * @since 1.0.0
 */
public class CompressionUtils {
    
    private static final Logger log = LoggerFactory.getLogger(CompressionUtils.class);
    
    /**
     * 压缩标记前缀，用于标识压缩后的数据
     */
    private static final String COMPRESSED_PREFIX = "[COMPRESSED]";
    
    /**
     * 压缩阈值（字节），超过此大小才进行压缩
     */
    private static final int COMPRESSION_THRESHOLD = 2048;
    
    /**
     * 压缩字符串
     * 如果字符串长度小于阈值，直接返回原字符串
     * 如果字符串已经压缩（有压缩标记），直接返回原字符串
     * 否则使用 GZIP 压缩，然后 Base64 编码，并添加压缩标记前缀
     * 
     * @param data 原始字符串
     * @return 压缩后的字符串（带压缩标记）或原字符串
     */
    public static String compress(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        
        // 如果已经压缩，直接返回（避免双重压缩）
        if (data.startsWith(COMPRESSED_PREFIX)) {
            return data;
        }
        
        // 计算原始字节大小
        byte[] originalBytes = data.getBytes(StandardCharsets.UTF_8);
        int originalSize = originalBytes.length;
        
        // 如果小于阈值，不压缩
        if (originalSize < COMPRESSION_THRESHOLD) {
            return data;
        }
        
        try {
            // 使用 GZIP 压缩
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
                gzos.write(originalBytes);
            }
            byte[] compressedBytes = baos.toByteArray();
            
            // Base64 编码
            String compressedBase64 = Base64.getEncoder().encodeToString(compressedBytes);
            
            // 添加压缩标记前缀
            String result = COMPRESSED_PREFIX + compressedBase64;
            
            int compressedSize = result.getBytes(StandardCharsets.UTF_8).length;
            double compressionRatio = (1.0 - (double) compressedSize / originalSize) * 100;
            
            log.debug("Compressed response body: original={} bytes, compressed={} bytes, ratio={:.2f}%", 
                    originalSize, compressedSize, String.format("%.2f", compressionRatio));
            
            return result;
        } catch (IOException e) {
            log.warn("Failed to compress data, returning original: {}", e.getMessage());
            return data;
        }
    }
    
    /**
     * 解压缩字符串
     * 如果字符串以压缩标记开头，则解压缩
     * 否则直接返回原字符串
     * 
     * @param data 压缩后的字符串（带压缩标记）或原字符串
     * @return 解压缩后的字符串或原字符串
     */
    public static String decompress(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        
        // 检查是否有压缩标记
        if (!data.startsWith(COMPRESSED_PREFIX)) {
            return data;
        }
        
        try {
            // 移除压缩标记前缀
            String compressedBase64 = data.substring(COMPRESSED_PREFIX.length());
            
            // Base64 解码
            byte[] compressedBytes = Base64.getDecoder().decode(compressedBase64);
            
            // GZIP 解压缩
            ByteArrayInputStream bais = new ByteArrayInputStream(compressedBytes);
            try (GZIPInputStream gzis = new GZIPInputStream(bais);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                
                byte[] buffer = new byte[8192];
                int len;
                while ((len = gzis.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                
                // 转换为字符串
                return baos.toString(StandardCharsets.UTF_8.name());
            }
        } catch (IOException e) {
            log.warn("Failed to decompress data, returning original: {}", e.getMessage());
            return data;
        }
    }
    
    /**
     * 检查字符串是否已压缩
     * 
     * @param data 字符串
     * @return 如果已压缩返回 true，否则返回 false
     */
    public static boolean isCompressed(String data) {
        return data != null && data.startsWith(COMPRESSED_PREFIX);
    }
    
    /**
     * 获取压缩阈值
     * 
     * @return 压缩阈值（字节）
     */
    public static int getCompressionThreshold() {
        return COMPRESSION_THRESHOLD;
    }
}

