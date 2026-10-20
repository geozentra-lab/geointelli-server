package com.geointelli.ai.property.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@SpringBootTest
@ActiveProfiles("test")
class PropertyServiceApplicationTests {

	@Autowired
	private DataSource dataSource;

	@MockBean
	private JwtDecoder jwtDecoder;

	@MockBean
	private S3Client s3Client;

	@MockBean
	private S3Presigner s3Presigner;

	@Test
	void contextLoads() throws Exception {
		try (var connection = dataSource.getConnection();
				var statement = connection.createStatement();
				var result = statement.executeQuery("SELECT 1")) {
			assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:"));
			assertTrue(result.next());
			assertEquals(1, result.getInt(1));
		}
	}

}
