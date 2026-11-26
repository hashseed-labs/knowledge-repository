package com.knowledge.kb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.knowledge.kb")
@MapperScan({
        "com.knowledge.kb.mapper",
        "com.knowledge.kb.exam.infra"
})
public class KnowledgeRepositoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeRepositoryApplication.class, args);
    }
}
