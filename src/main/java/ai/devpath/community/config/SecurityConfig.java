package ai.devpath.community.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecretKey jwtSecretKey(
      @Value("${devpath.auth.jwt-secret:test-secret-please-change-min-32-bytes-long-0123456789}") String secret) {
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32) {
      throw new IllegalStateException("JWT_SECRET must be >= 32 bytes (HS256), got " + bytes.length);
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  public JwtDecoder jwtDecoder(SecretKey key) {
    return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
  }

  /**
   * JWT {@code role} 클레임(예: {@code "ADMIN"}, {@code "LEARNER"})을 단일 {@code ROLE_<role>}
   * authority 로 변환한다. {@code role} 클레임이 없으면 빈 authority 목록을 반환한다.
   *
   * <p>platform-svc(토큰 발급자)의 SecurityConfig 와 동일한 규칙이다 — 같은 토큰을 두 서비스가
   * 다르게 해석하면 관리자 경로의 보호 수준이 서비스마다 갈린다.
   */
  JwtAuthenticationConverter adminRoleConverter() {
    JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
    conv.setJwtGrantedAuthoritiesConverter(jwt -> {
      String role = jwt.getClaimAsString("role");
      return role == null ? java.util.List.of()
          : java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role));
    });
    return conv;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
            // 실제 관리자 경로는 /community/admin/** 다(게이트웨이가 /admin/** 를 platform-svc 로
            // 보내기 때문 — AdminSearchController 주석 참조). /admin/** 도 함께 막아 두는 것은
            // 방어 목적이다: 라우팅이 바뀌어 이 서비스에 /admin/** 가 닿게 되더라도 무방비로
            // 열리지 않는다.
            .requestMatchers("/community/admin/**", "/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated())
        .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(adminRoleConverter())));
    return http.build();
  }
}
