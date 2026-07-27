package ai.devpath.community.post;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SelfVoteMockMvcTest {
  @Autowired MockMvc mvc;

  private long createQuestion(String subject) throws Exception {
    String body = mvc.perform(post("/community/questions").with(jwt().jwt(j -> j.subject(subject)))
        .contentType("application/json").content("{\"title\":\"t\",\"bodyMd\":\"b\",\"tags\":[]}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
  }

  private long createAnswer(String subject, long questionId) throws Exception {
    String body = mvc.perform(post("/community/questions/" + questionId + "/answers")
        .with(jwt().jwt(j -> j.subject(subject)))
        .contentType("application/json").content("{\"bodyMd\":\"ans\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
  }

  @Test
  void cannotVoteOwnPost() throws Exception {
    long asker = 91001;
    long qid = createQuestion(String.valueOf(asker));
    mvc.perform(post("/community/posts/" + qid + "/vote").with(jwt().jwt(j -> j.subject(String.valueOf(asker))))
        .contentType("application/json").content("{\"value\":-1}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void cannotVoteOwnAnswer() throws Exception {
    long asker = 91002, answerer = 91003;
    long qid = createQuestion(String.valueOf(asker));
    long aid = createAnswer(String.valueOf(answerer), qid);
    mvc.perform(post("/community/answers/" + aid + "/vote").with(jwt().jwt(j -> j.subject(String.valueOf(answerer))))
        .contentType("application/json").content("{\"value\":1}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void canVoteOthersPost() throws Exception {
    long asker = 91004, voter = 91005;
    long qid = createQuestion(String.valueOf(asker));
    mvc.perform(post("/community/posts/" + qid + "/vote").with(jwt().jwt(j -> j.subject(String.valueOf(voter))))
        .contentType("application/json").content("{\"value\":-1}"))
        .andExpect(status().isOk());
  }
}
