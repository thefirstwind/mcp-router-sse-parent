package com.pajk.mcpbridge.persistence.typehandler;

import com.pajk.mcpbridge.persistence.util.CompressionUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis TypeHandler for compressed strings
 * 自动处理字符串的压缩和解压缩
 * 
 * 存储时：如果字符串超过阈值，自动压缩
 * 读取时：如果字符串有压缩标记，自动解压缩
 * 
 * @author MCP Router Team
 * @since 1.0.0
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class CompressedStringTypeHandler extends BaseTypeHandler<String> {
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        // 存储时：如果超过阈值，自动压缩
        String compressed = CompressionUtils.compress(parameter);
        ps.setString(i, compressed);
    }
    
    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        // 读取时：如果有压缩标记，自动解压缩
        return CompressionUtils.decompress(value);
    }
    
    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        // 读取时：如果有压缩标记，自动解压缩
        return CompressionUtils.decompress(value);
    }
    
    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        // 读取时：如果有压缩标记，自动解压缩
        return CompressionUtils.decompress(value);
    }
}

