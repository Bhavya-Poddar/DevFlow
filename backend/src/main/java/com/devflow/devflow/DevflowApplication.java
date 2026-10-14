package com.devflow.devflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DevflowApplication {

	public static void main(String[] args) {
		SpringApplication.run(DevflowApplication.class, args);
	}

}
