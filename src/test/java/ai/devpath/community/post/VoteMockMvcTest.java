package ai.devpath.community.post;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ai.devpath.community.reputation.ReputationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VoteMockMvcTest {
  @Autowired MockMvc mvc;
  @Autowired ReputationService reputation;

  @Test
  void upvotePostAccumulatesAndIsIdempotentPerUser() throws Exception {
    String qBody = mvc.perform(post("/community/questions").with(jwt().jwt(j -> j.subject("300")))
        .contentType("application/json").content("{\"title\":\"vote\",\"bodyMd\":\"b\",\"tags\":[]}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    long qid = com.jayway.jsonpath.JsonPath.parse(qBody).read("$.id", Long.class);

    // 질문 upvote 게이트(평판 15 이상) 충족 — 투표자에게 사전 평판 부여(테스트 의도=누적·멱등 검증).
    reputation.applyAcceptance(301L, 9999L, "ANSWER", 9001L, List.of());
    reputation.applyAcceptance(302L, 9999L, "ANSWER", 9002L, List.of());

    mvc.perform(post("/community/posts/" + qid + "/vote").with(jwt().jwt(j -> j.subject("301")))
        .contentType("application/json").content("{\"value\":1}")).andExpect(status().isOk());
    // 같은 사용자 재투표(멱등) → 여전히 upvote 1
    mvc.perform(post("/community/posts/" + qid + "/vote").with(jwt().jwt(j -> j.subject("301")))
        .contentType("application/json").content("{\"value\":1}")).andExpect(status().isOk());
    // 다른 사용자 upvote → 2
    mvc.perform(post("/community/posts/" + qid + "/vote").with(jwt().jwt(j -> j.subject("302")))
        .contentType("application/json").content("{\"value\":1}")).andExpect(status().isOk());

    mvc.perform(get("/community/questions/" + qid).with(jwt().jwt(j -> j.subject("300"))))
        .andExpect(jsonPath("$.upvoteCount").value(2));
  }

  @Test
  void invalidVoteValueRejected() throws Exception {
    String qBody = mvc.perform(post("/community/questions").with(jwt().jwt(j -> j.subject("303")))
        .contentType("application/json").content("{\"title\":\"v2\",\"bodyMd\":\"b\",\"tags\":[]}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    long qid = com.jayway.jsonpath.JsonPath.parse(qBody).read("$.id", Long.class);
    mvc.perform(post("/community/posts/" + qid + "/vote").with(jwt().jwt(j -> j.subject("304")))
        .contentType("application/json").content("{\"value\":5}")).andExpect(status().isBadRequest());
  }
}
