package com.pajk.mcpbridge.persistence.typehandler;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/**
 * MyBatis TypeHandler for response_headers field
 * 压缩阈值：512 字节
 * 
 * @author MCP Router Team
 * @since 1.0.0
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class ResponseHeadersTypeHandler extends ThresholdCompressedStringTypeHandler {
    
    private static final int THRESHOLD = 512;
    
    public ResponseHeadersTypeHandler() {
        super(THRESHOLD);
    }
}


