package com.mockinterview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class MockInterviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockInterviewApplication.class, args);
    }
}
