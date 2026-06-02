package com.canteen.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 管理 C++ 仿真引擎进程的生命周期。
 * Java 启动时自动拉起 C++，停止/重置时 kill 并重新拉起。
 * 支持传入初始状态（队长、占座数），写入临时配置文件传给 C++。
 */
@Slf4j
@Service
public class SimulationLauncher {

    private final String binaryPath;
    private final File defaultConfig;
    private final File tempConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<Process> currentProcess = new AtomicReference<>();
    private final AtomicReference<Thread> readerThread = new AtomicReference<>();
    private volatile boolean autoRestart = true;
    private volatile int consecutiveCrashCount = 0;
    private static final int MAX_CRASH_RESTART = 3;
    private volatile JsonNode baseConfigCache;

    public SimulationLauncher(
            @Value("${simulation.cpp.binary:../build/cafeteria_sim}") String binaryPath,
            @Value("${simulation.cpp.config:../config/default_config.json}") String configPath) {
        this.binaryPath = binaryPath;
        String projectRoot = System.getProperty("user.dir");
        this.defaultConfig = new File(projectRoot, configPath);
        this.tempConfig = new File(projectRoot, "../build/.sim_config_tmp.json");
    }

    public synchronized void start() {
        start(null);
    }

    /**
     * 使用配置覆盖参数启动 C++（用于初始状态 + 仿真参数）。
     */
    public synchronized void start(Map<String, Object> configOverrides) {
        if (currentProcess.get() != null && currentProcess.get().isAlive()) {
            log.info("C++ 仿真已在运行中，跳过启动");
            return;
        }
        autoRestart = true;
        doStart(configOverrides);
    }

    public synchronized void stop() {
        autoRestart = false;
        Process proc = currentProcess.getAndSet(null);
        // Interrupt the reader thread so it doesn't trigger auto-restart
        Thread oldReader = readerThread.getAndSet(null);
        if (oldReader != null) {
            oldReader.interrupt();
        }
        if (proc != null && proc.isAlive()) {
            log.info("正在停止 C++ 仿真进程 (PID={})", proc.pid());
            proc.destroy();
            try { proc.waitFor(); } catch (InterruptedException e) {
                proc.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            log.info("C++ 仿真进程已停止");
        }
    }

    public void restart() {
        restart(null);
    }

    public void restart(Map<String, Object> configOverrides) {
        stop();
        autoRestart = true;
        doStart(configOverrides);
    }

    public boolean isRunning() {
        Process proc = currentProcess.get();
        return proc != null && proc.isAlive();
    }

    private void doStart(Map<String, Object> configOverrides) {
        try {
            String projectRoot = System.getProperty("user.dir");
            File binary = new File(projectRoot, binaryPath);

            if (!binary.exists()) {
                log.warn("C++ 二进制文件不存在: {} (请先编译 C++ 项目)", binary.getAbsolutePath());
                return;
            }

            // 确保 build/ 目录存在（C++ 写入 step_data.csv 需要）
            new File(projectRoot, "../build").mkdirs();

            // 有覆盖参数时写入临时配置文件
            File configFile;
            if (configOverrides != null && !configOverrides.isEmpty()) {
                configFile = writeTempConfig(configOverrides);
                log.info("使用临时配置: {}", configFile.getAbsolutePath());
            } else {
                configFile = defaultConfig;
            }

            ProcessBuilder pb = new ProcessBuilder(
                    binary.getAbsolutePath(),
                    configFile.getAbsolutePath());
            pb.directory(new File(projectRoot));
            pb.redirectErrorStream(true);

            Process proc = pb.start();
            Process old = currentProcess.getAndSet(proc);
            // Kill any orphaned old process
            if (old != null && old.isAlive()) {
                log.warn("发现残留 C++ 进程 (PID={})，正在清理", old.pid());
                old.destroyForcibly();
            }

            log.info("C++ 仿真已启动 (PID={})", proc.pid());

            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        log.debug("[C++] {}", line);
                    }
                } catch (Exception ignored) {}
                int exitCode;
                try { exitCode = proc.waitFor(); } catch (InterruptedException e) { exitCode = -1; }
                log.info("C++ 仿真进程退出 (exitCode={})", exitCode);
                currentProcess.compareAndSet(proc, null);
                if (autoRestart && exitCode != 0 && !Thread.currentThread().isInterrupted()) {
                    consecutiveCrashCount++;
                    if (consecutiveCrashCount > MAX_CRASH_RESTART) {
                        log.error("C++ 仿真连续崩溃 {} 次，停止自动重启", consecutiveCrashCount);
                        return;
                    }
                    log.info("5 秒后自动重启 C++ 仿真 (第 {} 次)...", consecutiveCrashCount);
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) { return; }
                    if (autoRestart) doStart(null);
                }
            }, "cpp-stdout-reader");
            reader.setDaemon(true);
            readerThread.set(reader);
            reader.start();

        } catch (Exception e) {
            log.error("启动 C++ 仿真失败: {}", e.getMessage());
        }
    }

    private File writeTempConfig(Map<String, Object> overrides) throws Exception {
        JsonNode base = getBaseConfig();
        ObjectNode merged = (ObjectNode) base.deepCopy();

        if (overrides.containsKey("initialQueueLengths")) {
            Object val = overrides.get("initialQueueLengths");
            if (val instanceof List<?> list) {
                ArrayNode arr = merged.putArray("initialQueueLengths");
                for (Object o : list) arr.add(o instanceof Number n ? n.intValue() : 0);
            }
        }
        for (String key : new String[]{"initialOccupiedSeats", "arrivalRatePerTick",
                "avgServiceTimeSec", "avgEatTimeSec", "totalTicks"}) {
            if (overrides.containsKey(key)) {
                Object v = overrides.get(key);
                if (v instanceof Number n) merged.put(key, n.doubleValue());
            }
        }

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempConfig, merged);
        return tempConfig;
    }

    private JsonNode getBaseConfig() throws Exception {
        if (baseConfigCache == null) {
            if (defaultConfig.exists()) {
                baseConfigCache = objectMapper.readTree(defaultConfig);
            } else {
                baseConfigCache = objectMapper.createObjectNode();
            }
        }
        return baseConfigCache;
    }

    @PreDestroy
    public void onShutdown() {
        stop();
        tempConfig.delete();
    }
}
