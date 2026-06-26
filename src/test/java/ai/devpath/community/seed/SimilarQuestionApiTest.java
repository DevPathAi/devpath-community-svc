package ai.devpath.community.seed;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ai.devpath.community.post.QuestionService;
import ai.devpath.community.post.dto.CreateQuestionRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SimilarQuestionApiTest {

  @Autowired MockMvc mvc;
  @Autowired QuestionService questions;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean EmbeddingClient embeddingClient;

  private static List<Double> vec(double b) {
    List<Double> v = new ArrayList<>();
    for (int i = 0; i < 768; i++) {
      v.add(b);
    }
    return v;
  }

  @Test
  void similarReturnsMatchesWhenEmbedSucceeds() throws Exception {
    String title = "유사질문대상 " + System.nanoTime();
    long qid = questions.create(90L, new CreateQuestionRequest(title, "b", List.of())).id();
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < 768; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(0.05);
    }
    sb.append(']');
    jdbc.update("update community_questions set question_embedding = cast(? as vector) where post_id = ?",
        sb.toString(), qid);
    when(embeddingClient.embed(anyString())).thenReturn(vec(0.05));

    mvc.perform(get("/community/questions/similar?q=" + title).with(jwt().jwt(j -> j.subject("90"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.questionId==" + qid + ")]").exists());
  }

  @Test
  void similarReturnsEmptyWhenEmbedFails() throws Exception {
    when(embeddingClient.embed(anyString()))
        .thenThrow(new EmbeddingUnavailableException("down"));
    mvc.perform(get("/community/questions/similar?q=anything").with(jwt().jwt(j -> j.subject("90"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void blankQueryReturnsEmpty() throws Exception {
    mvc.perform(get("/community/questions/similar?q=").with(jwt().jwt(j -> j.subject("90"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
