package ai.devpath.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostDeleteMockMvcTest {

  @Autowired MockMvc mvc;
  @Autowired CommunityPostRepository posts;

  private long createFreePost(String subject) throws Exception {
    String body = mvc.perform(post("/community/posts")
            .with(jwt().jwt(j -> j.subject(subject)))
            .contentType("application/json")
            .content("{\"boardType\":\"FREE\",\"title\":\"삭제대상\",\"bodyMd\":\"본문\",\"tags\":[]}"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
  }

  @Test
  void authorDeleteMarksDeletedAndHidesFromReads() throws Exception {
    long id = createFreePost("950001");

    mvc.perform(delete("/community/posts/" + id).with(jwt().jwt(j -> j.subject("950001"))))
        .andExpect(status().isNoContent());

    assertThat(posts.findById(id).orElseThrow().getStatus()).isEqualTo(ContentStatus.DELETED);

    mvc.perform(get("/community/posts/" + id).with(jwt().jwt(j -> j.subject("950001"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherUserCannotDelete() throws Exception {
    long id = createFreePost("950002");

    mvc.perform(delete("/community/posts/" + id).with(jwt().jwt(j -> j.subject("950003"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void deletingTwiceIsNotFound() throws Exception {
    long id = createFreePost("950004");

    mvc.perform(delete("/community/posts/" + id).with(jwt().jwt(j -> j.subject("950004"))))
        .andExpect(status().isNoContent());
    mvc.perform(delete("/community/posts/" + id).with(jwt().jwt(j -> j.subject("950004"))))
        .andExpect(status().isNotFound());
  }
}
