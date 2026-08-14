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
class FreeBoardMockMvcTest {

  @Autowired MockMvc mvc;

  @Test
  void createFreePost_thenDetail_thenComment() throws Exception {
    String body = mvc.perform(post("/community/posts")
            .with(jwt().jwt(j -> j.subject("300")))
            .contentType("application/json")
            .content("{\"boardType\":\"FREE\",\"title\":\"자유글\",\"bodyMd\":\"본문\",\"tags\":[\"잡담\"]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.boardType").value("FREE"))
        .andExpect(jsonPath("$.title").value("자유글"))
        .andExpect(jsonPath("$.comments.length()").value(0))
        .andReturn().getResponse().getContentAsString();
    long id = com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);

    mvc.perform(post("/community/posts/" + id + "/comments")
            .with(jwt().jwt(j -> j.subject("301")))
            .contentType("application/json").content("{\"bodyMd\":\"댓글내용\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.bodyMd").value("댓글내용"));

    mvc.perform(get("/community/posts/" + id).with(jwt().jwt(j -> j.subject("300"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bodyMd").value("본문"))
        .andExpect(jsonPath("$.comments.length()").value(1));
  }

  @Test
  void createQnaViaPostEndpoint_rejected() throws Exception {
    mvc.perform(post("/community/posts").with(jwt().jwt(j -> j.subject("302")))
            .contentType("application/json")
            .content("{\"boardType\":\"QNA\",\"title\":\"t\",\"bodyMd\":\"b\",\"tags\":[]}"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void unauthenticatedRejected() throws Exception {
    mvc.perform(post("/community/posts").contentType("application/json")
        .content("{\"boardType\":\"FREE\",\"title\":\"t\",\"bodyMd\":\"b\",\"tags\":[]}"))
        .andExpect(status().isUnauthorized());
  }
}
