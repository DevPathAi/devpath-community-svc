package ai.devpath.community.post;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActivityMockMvcTest {

  @Autowired MockMvc mvc;

  @Test
  void activityCountsAuthoredQuestionsAndHumanAnswers() throws Exception {
    String qBody =
        mvc.perform(
                post("/community/questions")
                    .with(jwt().jwt(j -> j.subject("300")))
                    .contentType("application/json")
                    .content("{\"title\":\"내 질문\",\"bodyMd\":\"b\",\"tags\":[]}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long qid = JsonPath.parse(qBody).read("$.id", Long.class);

    mvc.perform(
            post("/community/questions/" + qid + "/answers")
                .with(jwt().jwt(j -> j.subject("300")))
                .contentType("application/json")
                .content("{\"bodyMd\":\"내 답변\"}"))
        .andExpect(status().isCreated());

    mvc.perform(get("/community/me/activity").with(jwt().jwt(j -> j.subject("300"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.questionCount").value(1))
        .andExpect(jsonPath("$.answerCount").value(1));
  }

  @Test
  void unauthenticatedRejected() throws Exception {
    mvc.perform(get("/community/me/activity")).andExpect(status().isUnauthorized());
  }
}
