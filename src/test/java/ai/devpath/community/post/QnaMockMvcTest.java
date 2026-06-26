package ai.devpath.community.post;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class QnaMockMvcTest {

  @Autowired MockMvc mvc;

  @Test
  void createThenGetQuestion() throws Exception {
    String body = mvc.perform(post("/community/questions")
            .with(jwt().jwt(j -> j.subject("100")))
            .contentType("application/json")
            .content("{\"title\":\"JPA N+1\",\"bodyMd\":\"본문\",\"tags\":[\"jpa\",\"spring\"]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("JPA N+1"))
        .andExpect(jsonPath("$.solved").value(false))
        .andExpect(jsonPath("$.tags.length()").value(2))
        .andReturn().getResponse().getContentAsString();
    long id = com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);

    mvc.perform(get("/community/questions/" + id).with(jwt().jwt(j -> j.subject("100"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bodyMd").value("본문"))
        .andExpect(jsonPath("$.answers.length()").value(0));
  }

  @Test
  void listQuestionsByBoard() throws Exception {
    mvc.perform(post("/community/questions").with(jwt().jwt(j -> j.subject("101")))
        .contentType("application/json").content("{\"title\":\"목록테스트\",\"bodyMd\":\"b\",\"tags\":[]}"))
        .andExpect(status().isCreated());
    mvc.perform(get("/community/posts?board=QNA&sort=newest").with(jwt().jwt(j -> j.subject("101"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").isNumber());
  }

  @Test
  void unauthenticatedRejected() throws Exception {
    mvc.perform(get("/community/posts?board=QNA")).andExpect(status().isUnauthorized());
  }
}
