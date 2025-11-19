package com.pajk.mcpbridge.persistence.typehandler;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/**
 * MyBatis TypeHandler for response_body field
 * 压缩阈值：1024 字节
 * 
 * @author MCP Router Team
 * @since 1.0.0
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class ResponseBodyTypeHandler extends ThresholdCompressedStringTypeHandler {
    
    private static final int THRESHOLD = 1024;
    
    public ResponseBodyTypeHandler() {
        super(THRESHOLD);
    }
}

