package ai.devpath.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.devpath.community.reputation.ReputationService;
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
 * 관리자 콘텐츠 내리기의 권한·부수효과 계약.
 *
 * <p>관리자 호출에는 {@code jwt()} 후처리기 대신 <b>실제 서명 JWT</b>를 쓴다 — 후처리기는
 * authority 를 직접 주입해 {@code SecurityConfig} 의 {@code role} → {@code ROLE_*} 변환기를
 * 건너뛰므로 {@code claim("role","ADMIN")} 이 권한으로 이어지지 않는다({@code AdminReportControllerTest}
 * 가 같은 이유로 이미 이 관례를 쓴다). ★계획 초판은 후처리기로 적었고, 그대로 두면 관리자
 * 요청이 영원히 403 이라 구현이 끝나도 green 이 되지 않는다.★
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContentAdminMockMvcTest {

  @Value("${devpath.auth.jwt-secret}") String secret;

  @Autowired MockMvc mvc;
  @Autowired CommunityAnswerRepository answers;
  @Autowired CommunityQuestionRepository questions;
  @Autowired ReputationService reputation;

  /** HS256 서명 토큰. role 이 null 이면 클레임 자체를 넣지 않는다. */
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

  private long createQuestion(String subject) throws Exception {
    String body = mvc.perform(post("/community/questions")
            .with(jwt().jwt(j -> j.subject(subject)))
            .contentType("application/json")
            .content("{\"title\":\"관리자대상\",\"bodyMd\":\"본문\",\"tags\":[]}"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
  }

  private long addAnswer(long questionId, String subject) throws Exception {
    String body = mvc.perform(post("/community/questions/" + questionId + "/answers")
            .with(jwt().jwt(j -> j.subject(subject)))
            .contentType("application/json").content("{\"bodyMd\":\"답변\"}"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
  }

  @Test
  void nonAdminIsRejected() throws Exception {
    long qid = createQuestion("990001");

    mvc.perform(delete("/community/admin/posts/" + qid)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithRole("990002", "LEARNER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminDeleteMarksHiddenAndRevokesReputation() throws Exception {
    long qid = createQuestion("990003");
    long aid = addAnswer(qid, "990004");

    mvc.perform(post("/community/answers/" + aid + "/vote")
            .with(jwt().jwt(j -> j.subject("990005")))
            .contentType("application/json").content("{\"value\":1}"))
        .andExpect(status().isOk());
    assertThat(reputation.reputationOf(990004L)).isGreaterThan(0);

    mvc.perform(delete("/community/admin/answers/" + aid)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithRole("990009", "ADMIN")))
        .andExpect(status().isNoContent());

    assertThat(answers.findById(aid).orElseThrow().getStatus()).isEqualTo(ContentStatus.HIDDEN);
    assertThat(reputation.reputationOf(990004L)).isZero();
  }

  /**
   * ★수용된 답변을 내리면 질문의 연결을 풀어야 한다★
   * 그러지 않으면 질문이 "해결됨" 인데 답이 없는 상태가 되고, 그 값이 검색 문서에도 실린다.
   */
  @Test
  void adminDeleteOfAcceptedAnswerUnsolvesQuestion() throws Exception {
    long qid = createQuestion("990006");
    long aid = addAnswer(qid, "990007");

    mvc.perform(post("/community/answers/" + aid + "/accept")
            .with(jwt().jwt(j -> j.subject("990006"))))
        .andExpect(status().isOk());
    assertThat(questions.findById(qid).orElseThrow().isSolved()).isTrue();

    mvc.perform(delete("/community/admin/answers/" + aid)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithRole("990009", "ADMIN")))
        .andExpect(status().isNoContent());

    CommunityQuestion q = questions.findById(qid).orElseThrow();
    assertThat(q.isSolved()).isFalse();
    assertThat(q.getAcceptedAnswerId()).isNull();

    mvc.perform(get("/community/questions/" + qid).with(jwt().jwt(j -> j.subject("990006"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.solved").value(false));
  }
}
