package com.canteen.config;

import com.canteen.service.SimulationLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Java 启动后自动拉起 C++ 仿真进程。
 */
@Component
public class SimulationAutoStarter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulationAutoStarter.class);

    private final SimulationLauncher launcher;

    public SimulationAutoStarter(SimulationLauncher launcher) {
        this.launcher = launcher;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("自动启动 C++ 仿真引擎...");
        launcher.start();
    }
}
