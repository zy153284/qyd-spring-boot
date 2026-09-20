package com.qyd.bootstrap;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.qyd")
@EntityScan("com.qyd")
@EnableJpaRepositories("com.qyd")
@EnableJpaAuditing
@EnableScheduling
@ConfigurationPropertiesScan("com.qyd")
public class QydApplication {
    public static void main(String[] args) {
        SpringApplication.run(QydApplication.class, args);
    }

    @Bean
    OpenAPI qydOpenApi() {
        return new OpenAPI().info(new Info().title("QYD Platform API").version("v1"));
    }
}
