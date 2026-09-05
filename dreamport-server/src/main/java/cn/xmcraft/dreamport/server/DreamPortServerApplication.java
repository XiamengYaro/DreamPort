package cn.xmcraft.dreamport.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * DreamPort 独立后端入口。
 * 端口 18898（REST + SPA），18899（WebSocket，P4 接入）。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DreamPortServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DreamPortServerApplication.class, args);
    }
}
