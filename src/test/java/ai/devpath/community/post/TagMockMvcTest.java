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
class TagMockMvcTest {
  @Autowired MockMvc mvc;

  @Test
  void autocompleteByPrefix() throws Exception {
    // 질문 작성으로 태그 생성(고유 prefix)
    String pre = "spr" + (System.nanoTime() % 100000);
    mvc.perform(post("/community/questions").with(jwt().jwt(j -> j.subject("400")))
        .contentType("application/json")
        .content("{\"title\":\"t\",\"bodyMd\":\"b\",\"tags\":[\"" + pre + "boot\"]}"))
        .andExpect(status().isCreated());
    mvc.perform(get("/community/tags?q=" + pre).with(jwt().jwt(j -> j.subject("400"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value(pre + "boot"));
  }
}
