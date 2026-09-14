package com.yuegang.zhihui.system;
import com.yuegang.zhihui.common.mybatis.AuditorProvider;import org.springframework.boot.*;import org.springframework.boot.autoconfigure.*;import org.springframework.boot.autoconfigure.condition.*;import org.springframework.context.annotation.*;
@SpringBootApplication public class SystemApplication{public static void main(String[]a){SpringApplication.run(SystemApplication.class,a);}@Bean @ConditionalOnMissingBean AuditorProvider systemAuditorProvider(){return AuditorProvider.system();}}
