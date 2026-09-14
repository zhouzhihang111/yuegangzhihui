package com.yuegang.zhihui.system;
import org.springframework.boot.WebApplicationType;import org.springframework.boot.builder.SpringApplicationBuilder;
public final class SystemMigrationApplication{private SystemMigrationApplication(){}public static void main(String[]a){try(var ignored=new SpringApplicationBuilder(SystemApplication.class).web(WebApplicationType.NONE).properties("spring.cloud.nacos.discovery.enabled=false").run(a)){}}}
