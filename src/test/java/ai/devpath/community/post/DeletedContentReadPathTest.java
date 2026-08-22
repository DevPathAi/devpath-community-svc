package ai.devpath.community.post;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 비공개 콘텐츠의 읽기·신고 우회 경로를 막는다.
 *
 * <p>★deletedPostIsNotReadableById 는 구현 전에 200 을 반환한다★ — postDetail 이 findById 만
 * 쓰기 때문이다. 목록 쿼리 3종은 status='PUBLISHED' 를 거는데 상세만 걸지 않아, 목록에서
 * 사라진 글도 ID 로 직접 열면 그대로 읽혔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeletedContentReadPathTest {

  @Autowired MockMvc mvc;
  @Autowired CommunityPostRepository posts;

  private long createFreePost(String subject) throws Exception {
    String body = mvc.perform(post("/community/posts")
            .with(jwt().jwt(j -> j.subject(subject)))
            .contentType("application/json")
            .content("{\"boardType\":\"FREE\",\"title\":\"읽기경로\",\"bodyMd\":\"본문\",\"tags\":[]}"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
  }

  private void markDeleted(long postId) {
    CommunityPost p = posts.findById(postId).orElseThrow();
    p.setStatus(ContentStatus.DELETED);
    posts.save(p);
  }

  @Test
  void deletedPostIsNotReadableById() throws Exception {
    long id = createFreePost("920001");
    mvc.perform(get("/community/posts/" + id).with(jwt().jwt(j -> j.subject("920001"))))
        .andExpect(status().isOk());

    markDeleted(id);

    mvc.perform(get("/community/posts/" + id).with(jwt().jwt(j -> j.subject("920001"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void commentsOfDeletedPostAreNotReachable() throws Exception {
    long id = createFreePost("920002");
    mvc.perform(post("/community/posts/" + id + "/comments")
            .with(jwt().jwt(j -> j.subject("920003")))
            .contentType("application/json").content("{\"bodyMd\":\"댓글\"}"))
        .andExpect(status().isCreated());

    markDeleted(id);

    mvc.perform(get("/community/posts/" + id + "/comments")
            .with(jwt().jwt(j -> j.subject("920003"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void deletedPostCannotBeReported() throws Exception {
    long id = createFreePost("920004");
    markDeleted(id);

    mvc.perform(post("/community/reports")
            .with(jwt().jwt(j -> j.subject("920005")))
            .contentType("application/json")
            .content("{\"targetType\":\"POST\",\"targetId\":" + id + ",\"category\":\"SPAM\"}"))
        .andExpect(status().isNotFound());
  }
}
