package com.atguigu.exam;

import com.atguigu.exam.config.properties.KimiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
public class ExamSystemServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamSystemServerApplication.class, args);
    }

}
