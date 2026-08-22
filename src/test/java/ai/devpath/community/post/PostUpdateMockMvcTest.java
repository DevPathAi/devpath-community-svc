package ai.devpath.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class PostUpdateMockMvcTest {

  @Autowired MockMvc mvc;
  @Autowired ContentRevisionRepository revisions;
  @Autowired CommunityPostRepository posts;

  private long createFreePost(String subject) throws Exception {
    String body = mvc.perform(post("/community/posts")
            .with(jwt().jwt(j -> j.subject(subject)))
            .contentType("application/json")
            .content("{\"boardType\":\"FREE\",\"title\":\"원제목\",\"bodyMd\":\"원본문\",\"tags\":[]}"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
  }

  @Test
  void authorCanUpdateAndPreviousBodyIsKept() throws Exception {
    long id = createFreePost("940001");

    mvc.perform(put("/community/posts/" + id)
            .with(jwt().jwt(j -> j.subject("940001")))
            .contentType("application/json")
            .content("{\"title\":\"새제목\",\"bodyMd\":\"새본문\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("새제목"))
        .andExpect(jsonPath("$.bodyMd").value("새본문"));

    mvc.perform(get("/community/posts/" + id).with(jwt().jwt(j -> j.subject("940001"))))
        .andExpect(jsonPath("$.title").value("새제목"));

    assertThat(revisions.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("POST", id))
        .singleElement()
        .satisfies(r -> {
          assertThat(r.getTitle()).isEqualTo("원제목");
          assertThat(r.getBodyMd()).isEqualTo("원본문");
          assertThat(r.getEditedBy()).isEqualTo(940001L);
        });
  }

  @Test
  void otherUserCannotUpdate() throws Exception {
    long id = createFreePost("940002");

    mvc.perform(put("/community/posts/" + id)
            .with(jwt().jwt(j -> j.subject("940003")))
            .contentType("application/json")
            .content("{\"title\":\"침입\",\"bodyMd\":\"침입\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void deletedPostCannotBeUpdated() throws Exception {
    long id = createFreePost("940004");
    CommunityPost p = posts.findById(id).orElseThrow();
    p.setStatus(ContentStatus.DELETED);
    posts.save(p);

    mvc.perform(put("/community/posts/" + id)
            .with(jwt().jwt(j -> j.subject("940004")))
            .contentType("application/json")
            .content("{\"title\":\"부활\",\"bodyMd\":\"부활\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void emptyBodyIsRejected() throws Exception {
    long id = createFreePost("940005");

    mvc.perform(put("/community/posts/" + id)
            .with(jwt().jwt(j -> j.subject("940005")))
            .contentType("application/json")
            .content("{\"title\":\"제목\",\"bodyMd\":\"   \"}"))
        .andExpect(status().isBadRequest());
  }
}
