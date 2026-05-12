package com.zmail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ZmailApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZmailApplication.class, args);
    }
}