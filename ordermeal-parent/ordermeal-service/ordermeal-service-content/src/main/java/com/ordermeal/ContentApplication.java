package com.ordermeal;

import tk.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

@EnableEurekaClient
@SpringBootApplication
@MapperScan(basePackages = {"com.ordermeal.content.dao"})
public class ContentApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContentApplication.class);
    }
}
