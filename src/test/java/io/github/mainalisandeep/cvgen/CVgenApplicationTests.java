package io.github.mainalisandeep.cvgen;

import io.github.mainalisandeep.cvgen.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CVgenApplicationTests extends PostgresContainerSupport {

	@Test
	void contextLoads() {
	}
}
