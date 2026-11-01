package com.carepilot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@MapperScan("com.carepilot.**.mapper")
@SpringBootApplication
public class CarePilotApplication {
    public static void main(String[] args) {
        SpringApplication.run(CarePilotApplication.class, args);
    }
}