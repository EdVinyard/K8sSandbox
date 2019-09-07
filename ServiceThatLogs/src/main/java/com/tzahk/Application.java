package com.tzahk;

import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication
public class Application {
    private final Logger log = LoggerFactory.getLogger(Application.class);
    private static final int OneSecondInMs = 1000;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Scheduled(fixedDelay=OneSecondInMs, initialDelay=OneSecondInMs)
    public void watchConfigFilesForChanges() {
        ConfigDirectory.watchConfigFilesForChanges();
    }

    @Bean
    public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
        return args -> {
            log.error("Logging at level " + LogManager.getRootLogger().getLevel());
            logBeanDefinitionNames(ctx);
        };
    }

    private void logBeanDefinitionNames(ApplicationContext ctx) {
        log.trace("Beans provided by Spring Boot:");

        String[] beanNames = ctx.getBeanDefinitionNames();
        Arrays.sort(beanNames);
        for (String beanName : beanNames) {
            log.trace(beanName);
        }
    }
}
