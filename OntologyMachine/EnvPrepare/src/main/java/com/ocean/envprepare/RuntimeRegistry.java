package com.ocean.envprepare;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 环境归属运行记录：{@code start} 启动时把「本次由本进程启动（OWNED）/ 复用外部环境（REUSED）」逐个服务落盘，
 * 供独立的 {@code stop} 子命令 / {@code stop-env.cmd} 在另一个进程里安全判定「哪些该停、哪些是复用不能停」。
 *
 * <p>落盘位置：{@code <模块根>/.envprepare/runtime.properties}（每个模块各自一份，互不干扰）。
 *
 * <p>停止协议（gracefulStop）会就地改写 {@link State}：
 * <ul>
 *   <li>{@link State#OWNED}：本次由本工具启动，按「是否仍被使用」决定直接终止或提示确认；</li>
 *   <li>{@link State#REUSED}：启动时该服务已在运行（复用外部环境）——<b>任何情况下都不终止</b>，仅退出；</li>
 *   <li>{@link State#STOPPED}：已停止（再次 stop 时跳过，避免重复处理/重复回滚端口）；</li>
 *   <li>{@link State#KEPT}：因「仍有人在使用」被保留（下次 stop 会重新评估，若已无人使用则可正常停止）。</li>
 * </ul>
 */
final class RuntimeRegistry {

    /** 单个服务的归属状态。 */
    enum State {
        OWNED, REUSED, STOPPED, KEPT
    }

    /** 单个受管服务的运行记录。 */
    static final class Svc {
        final String name;
        State state;
        final List<Integer> ports = new ArrayList<>();
        /** Ontop 因端口冲突被自动改端口时：原端口（停止时据此回滚）。 */
        String ontopOriginalPort;
        /** Ontop 因端口冲突被自动改端口时：本次实际使用的新端口。 */
        String ontopChangedPort;

        Svc(String name, State state) {
            this.name = name;
            this.state = state;
        }
    }

    private final Path file;
    long supervisorPid;
    long startedAt;
    final Map<String, Svc> services = new LinkedHashMap<>();

    private RuntimeRegistry(Path file) {
        this.file = file;
    }

    /** 运行记录的落盘路径：{@code <模块根>/.envprepare/runtime.properties}。 */
    static Path fileFor(EnvConfig cfg) {
        return cfg.moduleRoot().resolve(".envprepare").resolve("runtime.properties");
    }

    /** 新建一份「本进程即 supervisor」的空记录（调用方随后填充 services 再 {@link #save()}）。 */
    static RuntimeRegistry create(EnvConfig cfg) {
        RuntimeRegistry r = new RuntimeRegistry(fileFor(cfg));
        r.supervisorPid = ProcessHandle.current().pid();
        r.startedAt = System.currentTimeMillis();
        return r;
    }

    /** 读取已落盘的运行记录；不存在或读取失败返回 {@code null}。 */
    static RuntimeRegistry load(EnvConfig cfg) {
        Path f = fileFor(cfg);
        if (!Files.isRegularFile(f)) {
            return null;
        }
        RuntimeRegistry r = new RuntimeRegistry(f);
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) {
            p.load(in);
        } catch (IOException e) {
            return null;
        }
        r.supervisorPid = parseLong(p.getProperty("supervisorPid"), -1L);
        r.startedAt = parseLong(p.getProperty("startedAt"), 0L);
        int n = (int) parseLong(p.getProperty("service.count"), 0L);
        for (int i = 0; i < n; i++) {
            String pre = "service." + i + ".";
            String name = p.getProperty(pre + "name");
            if (name == null || name.isBlank()) {
                continue;
            }
            Svc s = new Svc(name, parseState(p.getProperty(pre + "state")));
            for (String po : split(p.getProperty(pre + "ports"))) {
                try {
                    s.ports.add(Integer.parseInt(po.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
            s.ontopOriginalPort = p.getProperty(pre + "ontopOriginalPort");
            s.ontopChangedPort = p.getProperty(pre + "ontopChangedPort");
            r.services.put(name, s);
        }
        return r;
    }

    /** 落盘（覆盖写）。 */
    void save() {
        Properties p = new Properties();
        p.setProperty("supervisorPid", String.valueOf(supervisorPid));
        p.setProperty("startedAt", String.valueOf(startedAt));
        p.setProperty("service.count", String.valueOf(services.size()));
        int i = 0;
        for (Svc s : services.values()) {
            String pre = "service." + i + ".";
            p.setProperty(pre + "name", s.name);
            p.setProperty(pre + "state", s.state.name());
            StringBuilder ports = new StringBuilder();
            for (Integer po : s.ports) {
                if (ports.length() > 0) {
                    ports.append(',');
                }
                ports.append(po);
            }
            p.setProperty(pre + "ports", ports.toString());
            if (s.ontopOriginalPort != null) {
                p.setProperty(pre + "ontopOriginalPort", s.ontopOriginalPort);
            }
            if (s.ontopChangedPort != null) {
                p.setProperty(pre + "ontopChangedPort", s.ontopChangedPort);
            }
            i++;
        }
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, "EnvPrepare runtime registry (managed by EnvPrepare; do not edit)");
            }
        } catch (IOException e) {
            System.out.println("[WARN] 写运行记录失败: " + e.getMessage());
        }
    }

    /** 删除运行记录。 */
    void delete() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    /** 是否仍有「本次启动、尚未处理」的 OWNED 服务。 */
    boolean hasOwned() {
        return services.values().stream().anyMatch(s -> s.state == State.OWNED);
    }

    /** 是否有因「仍在使用」被保留（KEPT）/尚未处理的 OWNED 服务（决定停后是否保留运行记录以备后续 stop）。 */
    boolean hasKept() {
        return services.values().stream().anyMatch(s -> s.state == State.KEPT || s.state == State.OWNED);
    }

    private static long parseLong(String s, long def) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static State parseState(String s) {
        try {
            return State.valueOf(s.trim());
        } catch (Exception e) {
            return State.OWNED;
        }
    }

    private static List<String> split(String s) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        return List.of(s.split(","));
    }
}
