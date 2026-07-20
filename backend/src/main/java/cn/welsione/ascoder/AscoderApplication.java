package cn.welsione.ascoder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ascoder 后端应用入口。
 */
@SpringBootApplication
@EnableScheduling
public class AscoderApplication {

    public static void main(String[] args) {
        SpringApplication.run(AscoderApplication.class, args);
    }
}
