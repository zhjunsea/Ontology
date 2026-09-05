package com.ocean.ontopobdahandler;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

public class ConnectionPoolManager {
    private static final Logger log = LoggerFactory.getLogger(ConnectionPoolManager.class);
    private final HikariDataSource dataSource;

    public ConnectionPoolManager(String jdbcUrl, String username, String password) {
        this(jdbcUrl, username, password, 10, 2);
    }

    public ConnectionPoolManager(String jdbcUrl, String username, String password, int maxPoolSize, int minIdle) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setPoolName("PizzaOntologyPool");
        this.dataSource = new HikariDataSource(config);
        log.info("✅ 数据库连接池已初始化: url={}, maxPoolSize={}, minIdle={}", jdbcUrl, maxPoolSize, minIdle);
    }

    public DataSource getDataSource() { return this.dataSource; }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("🔒 数据库连接池已关闭");
        }
    }

    public boolean isAvailable() { return dataSource != null && !dataSource.isClosed(); }
}