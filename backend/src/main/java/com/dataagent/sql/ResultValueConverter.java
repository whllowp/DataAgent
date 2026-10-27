package com.dataagent.sql;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Component
public class ResultValueConverter {

    public Object convert(ResultSet resultSet, int columnIndex, int jdbcType, int maxChars) throws SQLException {
        Object value = resultSet.getObject(columnIndex);
        if (value == null) {
            return null;
        }
        if (value instanceof Clob clob) {
            long length = Math.min(clob.length(), maxChars);
            String text = clob.getSubString(1, (int) length);
            return clob.length() > maxChars ? text + "…" : text;
        }
        if (value instanceof Blob blob) {
            long length = Math.min(blob.length(), 64 * 1024);
            try (InputStream input = blob.getBinaryStream()) {
                return Base64.getEncoder().encodeToString(readLimited(input, (int) length));
            } catch (java.io.IOException e) {
                throw new SQLException("读取二进制字段失败", e);
            }
        }
        if (value instanceof SQLXML sqlxml) {
            return truncate(sqlxml.getString(), maxChars);
        }
        if (value instanceof byte[] bytes) {
            int length = Math.min(bytes.length, 64 * 1024);
            return Base64.getEncoder().encodeToString(length == bytes.length
                    ? bytes
                    : java.util.Arrays.copyOf(bytes, length));
        }
        if (value instanceof Array array) {
            Object arrayValue = array.getArray();
            if (arrayValue instanceof Object[] objects) {
                List<Object> list = new ArrayList<>(objects.length);
                for (Object object : objects) {
                    list.add(simple(object));
                }
                return list;
            }
            return String.valueOf(arrayValue);
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime().toString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toString();
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        if (value instanceof LocalTime time) {
            return time.toString();
        }
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.toString();
        }
        if (value instanceof OffsetTime time) {
            return time.toString();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Double number && (number.isNaN() || number.isInfinite())) {
            return number.toString();
        }
        if (value instanceof Float number && (number.isNaN() || number.isInfinite())) {
            return number.toString();
        }
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        if (value instanceof String string) {
            return truncate(string, maxChars);
        }
        return simple(value);
    }

    private Object simple(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return truncate(String.valueOf(value), 2000);
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars)) + "…";
    }

    private byte[] readLimited(InputStream input, int maxBytes) throws SQLException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0 && total < maxBytes) {
                int toWrite = Math.min(read, maxBytes - total);
                output.write(buffer, 0, toWrite);
                total += toWrite;
            }
            return output.toByteArray();
        } catch (Exception e) {
            throw new SQLException("读取二进制字段失败", e);
        }
    }
}
