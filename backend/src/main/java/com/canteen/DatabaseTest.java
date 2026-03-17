package com.canteen;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import jakarta.sql.DataSource;
import java.sql.Connection;

@Component
public class DatabaseTest implements CommandLineRunner {

    private final DataSource dataSource;

    public DatabaseTest(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("======= 数据库连接测试开始 =======");
        try (Connection conn = dataSource.getConnection()) {
            System.out.println("数据库连接成功！");
            System.out.println("数据库: " + conn.getMetaData().getURL());
            System.out.println("用户名: " + conn.getMetaData().getUserName());
        } catch (Exception e) {
            System.out.println("数据库连接失败！");
            e.printStackTrace();
        }
        System.out.println("======= 测试结束 =======");
    }
}