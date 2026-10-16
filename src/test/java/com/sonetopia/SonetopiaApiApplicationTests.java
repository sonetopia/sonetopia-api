package com.sonetopia;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/sonetopia",
    "spring.datasource.username=postgres",
    "spring.datasource.password=password",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.show-sql=true",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "spring.storage.audio-path=C:/projects/sonetopia",
    "spring.storage.signed-url-secret=sonetopia-very-secret-key-from-microsoft",
    "spring.security.jwt.secret=sonetopia-jwt-secret-key-for-testing",
    "spring.security.jwt.expiration=86400000",
    "spring.servlet.multipart.max-file-size=50MB",
    "spring.servlet.multipart.max-request-size=55MB",
    "spring.cors.allowed-origins=http://localhost:5173,http://localhost:5174"
  }
)
class SonetopiaApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
