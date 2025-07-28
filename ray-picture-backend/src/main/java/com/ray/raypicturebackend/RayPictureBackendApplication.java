package com.ray.raypicturebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan("com.ray.raypicturebackend.mapper")
@EnableAspectJAutoProxy(proxyTargetClass = true) //设置代理
public class RayPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(RayPictureBackendApplication.class, args);
    }

}
