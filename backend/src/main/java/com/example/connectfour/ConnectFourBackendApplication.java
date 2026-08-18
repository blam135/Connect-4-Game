package com.example.connectfour;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ConnectFourBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConnectFourBackendApplication.class, args);
	}

}
