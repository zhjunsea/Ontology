package com.ocean.ontopobdahandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 通用关系型数据写入器
 * 通过查询 information_schema 自动识别自增主键，行为完全由数据库 Schema 驱动
 * ✅ 已集成 WriteResult：业务校验失败返回 rejected，系统异常仍抛出
 */
public class GenericDbWriter {

    private final DataSource dataSource;

    /** 缓存: "tableName" -> 主键是否自增 */
    private final Map<String, Boolean> autoIncrementCache = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(GenericDbWriter.class);

    public GenericDbWriter(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource 不能为 null");
        }
        this.dataSource = dataSource;
    }

    /**
     * 通用 insert/upsert 操作
     * @return WriteResult 业务结果；系统异常（SQLException）仍会抛出
     */
    public WriteResult insert(String tableName, String primaryKey, Map<String, Object> data) throws SQLException {
        // ✅ 业务校验 → rejected
        if (data == null || data.isEmpty()) {
            return WriteResult.rejected("写入数据不能为空");
        }

        boolean isAutoIncrement = isAutoIncrementColumn(tableName, primaryKey);

        if (isAutoIncrement && data.containsKey(primaryKey)) {
            return WriteResult.rejected(
                    String.format("表 %s 的主键 %s 是自增列，data 中不应包含该字段", tableName, primaryKey));
        }
        if (!isAutoIncrement && !data.containsKey(primaryKey)) {
            return WriteResult.rejected(
                    String.format("表 %s 的主键 %s 不是自增列，data 中必须包含该字段", tableName, primaryKey));
        }

        // ✅ SQL 构建与执行 → 系统异常仍抛出
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

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object value : data.values()) {
                ps.setObject(idx++, value);
            }
            int rows = ps.executeUpdate();
            log.info("[DB] {} {} 影响行数: {}",
                    isAutoIncrement ? "auto-insert" : "upsert", tableName, rows);
            return WriteResult.accepted();
        }
        // ❌ catch 块不捕获 SQLException，让系统异常向上抛出
    }

    /**
     * 通用 update 操作
     * @return WriteResult 业务结果；系统异常（SQLException）仍会抛出
     */
    public WriteResult update(String tableName, String primaryKey, Object whereValue,
                              Map<String, Object> data) throws SQLException {
        // ✅ 业务校验 → rejected
        if (data == null || data.isEmpty()) {
            return WriteResult.rejected("更新数据不能为空");
        }
        if (whereValue == null) {
            return WriteResult.rejected("WHERE 条件值不能为 null");
        }
        if (data.containsKey(primaryKey)) {
            return WriteResult.rejected(
                    String.format("更新操作不允许修改主键字段 %s，请从 data 中移除", primaryKey));
        }

        // ✅ SQL 构建与执行
        String setClause = data.keySet().stream()
                .map(col -> col + "=?")
                .collect(Collectors.joining(", "));

        String sql = String.format("UPDATE %s SET %s WHERE %s=?", tableName, setClause, primaryKey);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int idx = 1;
            for (Object value : data.values()) {
                ps.setObject(idx++, value);
            }
            ps.setObject(idx, whereValue);

            int rows = ps.executeUpdate();
            log.info("[DB] update {} WHERE {}={} 影响行数: {}", tableName, primaryKey, whereValue, rows);

            if (rows == 0) {
                log.warn("[WARN] update 未匹配到任何记录，可能记录不存在或 WHERE 值错误");
            }
            return WriteResult.accepted();
        }
    }

    /**
     * 通用 delete 操作
     * @return WriteResult 业务结果；系统异常（SQLException）仍会抛出
     */
    public WriteResult delete(String tableName, String primaryKey, Object whereValue) throws SQLException {
        // ✅ 业务校验 → rejected
        if (whereValue == null) {
            return WriteResult.rejected("WHERE 条件值不能为 null");
        }

        // ✅ SQL 构建与执行
        String sql = String.format("DELETE FROM %s WHERE %s=?", tableName, primaryKey);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1, whereValue);

            int rows = ps.executeUpdate();
            log.info("[DB] delete {} WHERE {}={} 影响行数: {}", tableName, primaryKey, whereValue, rows);

            if (rows == 0) {
                log.warn("[WARN] delete 未匹配到任何记录，可能记录不存在或 WHERE 值错误");
            }
            return WriteResult.accepted();
        }
    }

    /**
     * 查询指定表的主键是否为自增列，结果会被缓存
     * ⚠️ 此方法为内部元数据查询，失败属于系统异常，直接抛出 RuntimeException
     */
    private boolean isAutoIncrementColumn(String tableName, String columnName) throws SQLException {
        return autoIncrementCache.computeIfAbsent(tableName.toLowerCase(), t -> {
            String sql = "SELECT EXTRA FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() " +
                    "AND TABLE_NAME = ? AND COLUMN_NAME = ?";
            try (Connection conn = dataSource.getConnection();
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

    @FunctionalInterface
    public interface DbWriteAction {
        void execute() throws Exception;
    }
}