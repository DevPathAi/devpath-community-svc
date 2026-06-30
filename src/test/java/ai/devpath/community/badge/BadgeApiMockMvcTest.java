package ai.devpath.community.badge;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ai.devpath.community.post.QuestionService;
import ai.devpath.community.post.dto.CreateQuestionRequest;
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
class BadgeApiMockMvcTest {
  @Autowired MockMvc mvc;
  @Autowired QuestionService questionService;

  @Test
  void listsUserBadges() throws Exception {
    long user = 74001;
    questionService.create(user, new CreateQuestionRequest("t", "b", List.of())); // FIRST_QUESTION 수여
    mvc.perform(get("/community/users/" + user + "/badges").with(jwt().jwt(j -> j.subject("999"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.code=='FIRST_QUESTION')]").exists())
        .andExpect(jsonPath("$[0].name").exists())
        .andExpect(jsonPath("$[0].tier").value("BRONZE"));
  }
}
