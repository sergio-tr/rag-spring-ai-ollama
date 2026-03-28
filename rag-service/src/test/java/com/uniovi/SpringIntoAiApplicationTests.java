package com.uniovi;

import com.uniovi.rag.testsupport.TestAiStubConfiguration;
import com.uniovi.rag.testsupport.TestcontainersDatasourceConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = Application.class)
@Import({ TestAiStubConfiguration.class, TestcontainersDatasourceConfiguration.class })
@ActiveProfiles("test")
class SpringIntoAiApplicationTests {

	@Test
	void contextLoads() {
	}
}
