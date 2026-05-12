package com.example.liveclass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class LiveClassApplication {

	public static void main(String[] args) {
		SpringApplication.run(LiveClassApplication.class, args);
	}

}
