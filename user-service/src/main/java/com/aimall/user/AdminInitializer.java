package com.aimall.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** 启动时确保存在管理员账号 admin (用于上传商品知识库文档)。 */
@Component
public class AdminInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final UserRepository repo;
    private final String adminPassword;

    public AdminInitializer(UserRepository repo, @Value("${mall.admin.password:admin123}") String adminPassword) {
        this.repo = repo;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repo.findByUsername("admin").isEmpty()) {
            repo.insert("admin", new BCryptPasswordEncoder().encode(adminPassword), "管理员");
            log.info("已创建管理员账号 admin (请通过 ADMIN_PASSWORD 修改默认密码)");
        }
    }
}
