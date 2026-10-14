package com.devflow.devflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class DevflowApplicationTests {
	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
	}

	@Test
	void doesNotConfigureSpringBootGeneratedInMemoryUsers() {
		assertEquals(0, applicationContext.getBeanNamesForType(UserDetailsService.class).length);
	}

}
