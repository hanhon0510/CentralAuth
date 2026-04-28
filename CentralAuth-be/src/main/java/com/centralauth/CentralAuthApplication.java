package com.centralauth;

import com.centralauth.config.KafkaTopicProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(KafkaTopicProperties.class)
public class CentralAuthApplication {

	public static void main(String[] args) {
		SpringApplication.run(CentralAuthApplication.class, args);
	}

}
