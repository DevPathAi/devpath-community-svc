package ai.devpath.community.post;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 관리자 리비전 조회와 내 활동 목록.
 *
 * <p>관리자 호출은 실제 서명 JWT 로 보낸다 — {@code jwt()} 후처리기는 role -> ROLE_* 변환기를
 * 건너뛰어 {@code claim("role","ADMIN")} 이 권한으로 이어지지 않는다({@code ContentAdminMockMvcTest}
 * 와 같은 이유).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RevisionAndActivityMockMvcTest {

  @Value("${devpath.auth.jwt-secret}") String secret;

  @Autowired MockMvc mvc;

  private String tokenWithRole(String subject, String role) throws Exception {
    JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
        .subject(subject)
        .issueTime(new Date())
        .expirationTime(new Date(System.currentTimeMillis() + 60_000));
    if (role != null) {
      claims.claim("role", role);
    }
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
    jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
    return jwt.serialize();
  }

  private long createFreePost(String subject, String title) throws Exception {
    String body = mvc.perform(post("/community/posts")
            .with(jwt().jwt(j -> j.subject(subject)))
            .contentType("application/json")
            .content("{\"boardType\":\"FREE\",\"title\":\"" + title
                + "\",\"bodyMd\":\"본문\",\"tags\":[]}"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
  }

  @Test
  void adminCanReadRevisionsNewestFirst() throws Exception {
    long id = createFreePost("991001", "원제목");

    mvc.perform(put("/community/posts/" + id).with(jwt().jwt(j -> j.subject("991001")))
            .contentType("application/json")
            .content("{\"title\":\"두번째\",\"bodyMd\":\"두번째본문\"}"))
        .andExpect(status().isOk());
    mvc.perform(put("/community/posts/" + id).with(jwt().jwt(j -> j.subject("991001")))
            .contentType("application/json")
            .content("{\"title\":\"세번째\",\"bodyMd\":\"세번째본문\"}"))
        .andExpect(status().isOk());

    mvc.perform(get("/community/admin/revisions")
            .param("targetType", "POST").param("targetId", String.valueOf(id))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithRole("991009", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].title").value("두번째"))
        .andExpect(jsonPath("$[1].title").value("원제목"));
  }

  @Test
  void nonAdminCannotReadRevisions() throws Exception {
    long id = createFreePost("991002", "제목");

    mvc.perform(get("/community/admin/revisions")
            .param("targetType", "POST").param("targetId", String.valueOf(id))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithRole("991003", "LEARNER")))
        .andExpect(status().isForbidden());
  }

  /**
   * ActivityController 는 제목이 아니라 개수를 낸다
   * ({@code ActivityView(long questionCount, long answerCount)}, 경로 {@code /community/me/activity}).
   * 삭제한 글이 개수에 남아 있으면 "지워지지 않았다" 고 읽힌다.
   */
  @Test
  void deletedPostDisappearsFromMyActivityCount() throws Exception {
    long id = createFreePost("991004", "활동에서사라질글");

    mvc.perform(get("/community/me/activity").with(jwt().jwt(j -> j.subject("991004"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.questionCount").value(1));

    mvc.perform(delete("/community/posts/" + id).with(jwt().jwt(j -> j.subject("991004"))))
        .andExpect(status().isNoContent());

    mvc.perform(get("/community/me/activity").with(jwt().jwt(j -> j.subject("991004"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.questionCount").value(0));
  }
}
