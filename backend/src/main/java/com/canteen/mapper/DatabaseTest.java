package com.canteen.mapper;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.sql.Connection;

@Slf4j
@Component
public class DatabaseTest implements CommandLineRunner {

    private final DataSource dataSource;

    public DatabaseTest(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String @NonNull ... args) {
        log.info("======= 数据库连接测试开始 =======");
        try (Connection conn = dataSource.getConnection()) {
            log.info("数据库连接成功！");
            log.info("数据库: {}", conn.getMetaData().getURL());
            log.info("用户名: {}", conn.getMetaData().getUserName());
        } catch (Exception e) {
            log.error("数据库连接失败", e);
        }
        log.info("======= 测试结束 =======");
    }
}