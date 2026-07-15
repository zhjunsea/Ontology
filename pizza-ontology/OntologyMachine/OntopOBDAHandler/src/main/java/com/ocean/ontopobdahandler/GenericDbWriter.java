package com.ocean.ontopobdahandler;

import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 通用关系型数据写入器
 * 通过查询 information_schema 自动识别自增主键，行为完全由数据库 Schema 驱动
 */
public class GenericDbWriter {

    private final String dbUrl;
    private final String dbUser;
    private final String dbPass;

    /** 缓存: "tableName" -> 主键是否自增 */
    private final Map<String, Boolean> autoIncrementCache = new ConcurrentHashMap<>();

    public GenericDbWriter(String dbUrl, String dbUser, String dbPass) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPass = dbPass;
    }

    /**
     * 通用 insert/upsert 操作
     * @param tableName  目标表名
     * @param primaryKey 主键字段名
     * @param data       字段名 -> 值的有序映射（自增主键时不应包含主键字段）
     */
    public void insert(String tableName, String primaryKey, Map<String, Object> data) throws SQLException {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("写入数据不能为空");
        }

        boolean isAutoIncrement = isAutoIncrementColumn(tableName, primaryKey);

        // ✅ 自增主键时，data 中不允许包含主键字段
        if (isAutoIncrement && data.containsKey(primaryKey)) {
            throw new IllegalArgumentException(
                    String.format("表 %s 的主键 %s 是自增列，data 中不应包含该字段", tableName, primaryKey));
        }
        // ✅ 非自增主键时，data 中必须包含主键字段
        if (!isAutoIncrement && !data.containsKey(primaryKey)) {
            throw new IllegalArgumentException(
                    String.format("表 %s 的主键 %s 不是自增列，data 中必须包含该字段", tableName, primaryKey));
        }

        String columns = String.join(", ", data.keySet());
        String placeholders = data.keySet().stream().map(k -> "?").collect(Collectors.joining(", "));

        String sql;
        if (isAutoIncrement) {
            sql = String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columns, placeholders);
        } else {
            String updateClause = data.keySet().stream()
                    .filter(col -> !col.equals(primaryKey))
                    .map(col -> col + "=VALUES(" + col + ")")
                    .collect(Collectors.joining(", "));
            sql = String.format(
                    "INSERT INTO %s (%s) VALUES (%s) ON DUPLICATE KEY UPDATE %s",
                    tableName, columns, placeholders,
                    updateClause.isEmpty() ? primaryKey + "=" + primaryKey : updateClause);
        }

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object value : data.values()) {
                ps.setObject(idx++, value);
            }
            int rows = ps.executeUpdate();
            System.out.println("   [DB] " + (isAutoIncrement ? "auto-insert" : "upsert")
                    + " " + tableName + " 影响行数: " + rows);
        }
    }

    /**
     * 查询指定表的主键是否为自增列，结果会被缓存
     */
    private boolean isAutoIncrementColumn(String tableName, String columnName) throws SQLException {
        return autoIncrementCache.computeIfAbsent(tableName.toLowerCase(), t -> {
            String sql = "SELECT EXTRA FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() " +
                    "AND TABLE_NAME = ? AND COLUMN_NAME = ?";
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, t);
                ps.setString(2, columnName.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String extra = rs.getString("EXTRA");
                        return extra != null && extra.toLowerCase().contains("auto_increment");
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("查询自增主键元数据失败: " + t + "." + columnName, e);
            }
            return false;
        });
    }
}