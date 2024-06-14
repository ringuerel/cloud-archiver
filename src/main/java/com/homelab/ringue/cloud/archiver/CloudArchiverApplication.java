package com.homelab.ringue.cloud.archiver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CloudArchiverApplication {

	public static void main(String[] args) {
		SpringApplication.run(CloudArchiverApplication.class, args);
	}

}
