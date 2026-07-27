package ai.devpath.community.post;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ai.devpath.community.reputation.ReputationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VoteGateMockMvcTest {
  @Autowired MockMvc mvc;
  @Autowired ReputationService reputation;

  private long createQuestion(String subject) throws Exception {
    String body = mvc.perform(post("/community/questions").with(jwt().jwt(j -> j.subject(subject)))
        .contentType("application/json").content("{\"title\":\"g\",\"bodyMd\":\"b\",\"tags\":[]}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
  }

  @Test
  void upvoteQuestionRequiresReputation15() throws Exception {
    long qid = createQuestion("8001");
    // 평판 0인 사용자(8002)가 질문 upvote → 거부(403)
    mvc.perform(post("/community/posts/" + qid + "/vote").with(jwt().jwt(j -> j.subject("8002")))
        .contentType("application/json").content("{\"value\":1}"))
        .andExpect(status().isForbidden());
    // 평판 15 부여 후 → 허용(200)
    reputation.applyAcceptance(8002L, 9999L, "ANSWER", 1L, java.util.List.of()); // +15
    mvc.perform(post("/community/posts/" + qid + "/vote").with(jwt().jwt(j -> j.subject("8002")))
        .contentType("application/json").content("{\"value\":1}"))
        .andExpect(status().isOk());
  }
}
