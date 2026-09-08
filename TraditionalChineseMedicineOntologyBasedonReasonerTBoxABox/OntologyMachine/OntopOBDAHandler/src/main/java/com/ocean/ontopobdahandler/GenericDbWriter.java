package com.ocean.ontopobdahandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Map;
import java.util.StringJoiner;

public class GenericDbWriter {
    private static final Logger log = LoggerFactory.getLogger(GenericDbWriter.class);
    private final DataSource dataSource;

    public GenericDbWriter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ==================== 非事务版本（保持向后兼容）====================
    public WriteResult insert(String tableName, String primaryKey, Map<String, Object> data) {
        try (Connection conn = dataSource.getConnection()) {
            return doInsert(conn, tableName, primaryKey, data);
        } catch (SQLException e) {
            log.error("❌ INSERT 获取连接失败 | table={}", tableName, e);
            return WriteResult.rejected("获取数据库连接失败: " + e.getMessage());
        }
    }

    public WriteResult update(String tableName, String primaryKey, Object whereValue, Map<String, Object> data) {
        try (Connection conn = dataSource.getConnection()) {
            return doUpdate(conn, tableName, primaryKey, whereValue, data);
        } catch (SQLException e) {
            log.error("❌ UPDATE 获取连接失败 | table={}", tableName, e);
            return WriteResult.rejected("获取数据库连接失败: " + e.getMessage());
        }
    }

    public WriteResult delete(String tableName, String primaryKey, Object whereValue) {
        try (Connection conn = dataSource.getConnection()) {
            return doDelete(conn, tableName, primaryKey, whereValue);
        } catch (SQLException e) {
            log.error("❌ DELETE 获取连接失败 | table={}", tableName, e);
            return WriteResult.rejected("获取数据库连接失败: " + e.getMessage());
        }
    }

    // ==================== ✅ 事务感知版本（核心新增）====================
    /**
     * 事务内 INSERT：使用外部传入的连接，不自行获取/关闭连接
     */
    public WriteResult insert(Connection externalConn, String tableName, String primaryKey, Map<String, Object> data) {
        try {
            return doInsert(externalConn, tableName, primaryKey, data);
        } catch (SQLException e) {
            log.error("❌ 事务INSERT失败 | table={}", tableName, e);
            return WriteResult.rejected("事务INSERT失败: " + e.getMessage());
        }
    }

    /**
     * 事务内 UPDATE
     */
    public WriteResult update(Connection externalConn, String tableName, String primaryKey, Object whereValue, Map<String, Object> data) {
        try {
            return doUpdate(externalConn, tableName, primaryKey, whereValue, data);
        } catch (SQLException e) {
            log.error("❌ 事务UPDATE失败 | table={}", tableName, e);
            return WriteResult.rejected("事务UPDATE失败: " + e.getMessage());
        }
    }

    /**
     * 事务内 DELETE
     */
    public WriteResult delete(Connection externalConn, String tableName, String primaryKey, Object whereValue) {
        try {
            return doDelete(externalConn, tableName, primaryKey, whereValue);
        } catch (SQLException e) {
            log.error("❌ 事务DELETE失败 | table={}", tableName, e);
            return WriteResult.rejected("事务DELETE失败: " + e.getMessage());
        }
    }

    // ==================== 内部执行逻辑（统一复用）====================
    private WriteResult doInsert(Connection conn, String tableName, String primaryKey, Map<String, Object> data) throws SQLException {
        if (data == null || data.isEmpty()) return WriteResult.rejected("插入数据不能为空");

        StringJoiner cols = new StringJoiner(", ");
        StringJoiner placeholders = new StringJoiner(", ");
        for (String col : data.keySet()) {
            cols.add(col);
            placeholders.add("?");
        }

        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, cols, placeholders);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object val : data.values()) {
                ps.setObject(idx++, val);
            }
            int rows = ps.executeUpdate();
            log.info("✅ INSERT 成功 | table={} | rows={}", tableName, rows);
            return WriteResult.accepted("插入成功，影响行数: " + rows);
        }
    }

    private WriteResult doUpdate(Connection conn, String tableName, String primaryKey, Object whereValue, Map<String, Object> data) throws SQLException {
        if (data == null || data.isEmpty()) return WriteResult.rejected("更新数据不能为空");

        StringJoiner setClause = new StringJoiner(", ");
        for (String col : data.keySet()) setClause.add(col + " = ?");

        String sql = String.format("UPDATE %s SET %s WHERE %s = ?", tableName, setClause, primaryKey);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object val : data.values()) ps.setObject(idx++, val);
            ps.setObject(idx, whereValue);
            int rows = ps.executeUpdate();
            log.info("✅ UPDATE 成功 | table={} | rows={}", tableName, rows);
            return WriteResult.accepted("更新成功，影响行数: " + rows);
        }
    }

    private WriteResult doDelete(Connection conn, String tableName, String primaryKey, Object whereValue) throws SQLException {
        String sql = String.format("DELETE FROM %s WHERE %s = ?", tableName, primaryKey);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, whereValue);
            int rows = ps.executeUpdate();
            log.info("✅ DELETE 成功 | table={} | rows={}", tableName, rows);
            return WriteResult.accepted("删除成功，影响行数: " + rows);
        }
    }
    /**
     * 数据库写入动作的函数式接口
     * 用于 safeVerifyAndDBExecution 等需要"先校验再写入"的场景
     * 将 DB 操作封装为可延迟执行的回调，与本体一致性校验解耦
     */
    @FunctionalInterface
    public interface DbWriteAction {
        void execute() throws Exception;
    }
}