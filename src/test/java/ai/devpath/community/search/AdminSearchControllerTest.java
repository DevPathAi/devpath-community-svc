package ai.devpath.community.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.devpath.community.search.dto.SearchResponse;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code POST /admin/community/reindex} 의 권한 계약을 검증한다.
 *
 * <p>{@code jwt()} 후처리기 대신 <b>실제 서명 JWT</b>를 보낸다. 후처리기는 authority 를 직접
 * 주입해 {@code SecurityConfig} 의 {@code role} 클레임 → {@code ROLE_*} 변환기를 건너뛰므로,
 * "role=ADMIN 토큰이 실제로 통과하는가"라는 핵심 질문에 답하지 못한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSearchControllerTest {

  /** application-test.yml 의 devpath.auth.jwt-secret 과 동일해야 서명 검증을 통과한다. */
  private static final String SECRET = "test-secret-please-change-min-32-bytes-long-0123456789";

  @Autowired MockMvc mvc;
  @MockitoBean ReindexService reindexService;
  @MockitoBean PostSearchService searchService;

  @Test
  void reindexReturnsIndexedCountForAdmin() throws Exception {
    when(reindexService.reindexAll()).thenReturn(7);

    mvc.perform(post("/admin/community/reindex")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithRole("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.indexed").value(7));
  }

  @Test
  void reindexIsForbiddenForNonAdminRole() throws Exception {
    mvc.perform(post("/admin/community/reindex")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithRole("LEARNER")))
        .andExpect(status().isForbidden());

    verify(reindexService, never()).reindexAll();
  }

  @Test
  void reindexIsForbiddenWhenRoleClaimIsMissing() throws Exception {
    mvc.perform(post("/admin/community/reindex")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithRole(null)))
        .andExpect(status().isForbidden());

    verify(reindexService, never()).reindexAll();
  }

  /**
   * {@code /admin/**} 보호를 위해 도입한 {@code role} 클레임 변환기가 <b>일반 경로</b>를 깨지
   * 않는지 확인한다. 기존 사용자 토큰에는 {@code role} 클레임이 없어 authority 가 비는데,
   * {@code anyRequest().authenticated()} 는 authority 가 아니라 인증 여부만 보므로 통과해야 한다.
   * (기존 컨트롤러 테스트는 {@code jwt()} 후처리기를 써 이 변환기를 우회하므로 여기서만 검증된다.)
   */
  @Test
  void ordinaryRoutesStillPassWithoutRoleClaim() throws Exception {
    when(searchService.search(any(), any(), any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(new SearchResponse(List.of(), 0, 0, 20));

    mvc.perform(get("/community/search?q=검색어")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithRole(null)))
        .andExpect(status().isOk());
  }

  @Test
  void reindexIsUnauthorizedWithoutToken() throws Exception {
    mvc.perform(post("/admin/community/reindex"))
        .andExpect(status().isUnauthorized());

    verify(reindexService, never()).reindexAll();
  }

  /** HS256 으로 서명한 액세스 토큰. {@code role} 이 null 이면 클레임 자체를 넣지 않는다. */
  private String tokenWithRole(String role) throws Exception {
    JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
        .subject("1")
        .issueTime(new Date())
        .expirationTime(new Date(System.currentTimeMillis() + 60_000));
    if (role != null) {
      claims.claim("role", role);
    }
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
    jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
    return jwt.serialize();
  }
}
