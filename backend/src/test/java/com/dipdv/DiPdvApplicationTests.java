package com.dipdv;

import com.dipdv.support.PostgresIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DiPdvApplicationTests extends PostgresIntegrationSupport {

	@Test
	void contextLoads() {
	}

}
