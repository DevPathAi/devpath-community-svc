package ai.devpath.community;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * PostgreSQL 연결 + 컨텍스트 로드 검증.
 * 로컬은 docker-compose의 postgres(5432), CI는 postgres service container에 연결한다
 * (DB_URL/DB_USER/DB_PASSWORD 환경변수, 기본은 로컬 compose).
 * test 프로파일로 shared jar의 마이그레이션을 적용한 뒤(@ActiveProfiles("test")) 매핑·연결을 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class DbConnectionTest {

  @Autowired DataSource dataSource;

  @Test
  void connectsToPostgres() throws Exception {
    try (var c = dataSource.getConnection()) {
      assertTrue(c.isValid(2), "PostgreSQL 연결이 유효해야 한다");
    }
  }
}
