package ai.devpath.community.post;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class CommentEditDeleteMockMvcTest {

  @Autowired MockMvc mvc;

  private long createFreePost(String subject) throws Exception {
    String body = mvc.perform(post("/community/posts")
            .with(jwt().jwt(j -> j.subject(subject)))
            .contentType("application/json")
            .content("{\"boardType\":\"FREE\",\"title\":\"댓글모글\",\"bodyMd\":\"본문\",\"tags\":[]}"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
  }

  private long addComment(long postId, String subject) throws Exception {
    String body = mvc.perform(post("/community/posts/" + postId + "/comments")
            .with(jwt().jwt(j -> j.subject(subject)))
            .contentType("application/json").content("{\"bodyMd\":\"댓글본문\"}"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
  }

  @Test
  void authorCanUpdateComment() throws Exception {
    long pid = createFreePost("970001");
    long cid = addComment(pid, "970002");

    mvc.perform(put("/community/comments/" + cid)
            .with(jwt().jwt(j -> j.subject("970002")))
            .contentType("application/json").content("{\"bodyMd\":\"고친댓글\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bodyMd").value("고친댓글"));
  }

  @Test
  void otherUserCannotDeleteComment() throws Exception {
    long pid = createFreePost("970003");
    long cid = addComment(pid, "970004");

    mvc.perform(delete("/community/comments/" + cid).with(jwt().jwt(j -> j.subject("970005"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void deletedCommentIsTombstoneInBothReadPaths() throws Exception {
    long pid = createFreePost("970006");
    long cid = addComment(pid, "970007");

    mvc.perform(delete("/community/comments/" + cid).with(jwt().jwt(j -> j.subject("970007"))))
        .andExpect(status().isNoContent());

    // 목록 경로
    mvc.perform(get("/community/posts/" + pid + "/comments")
            .with(jwt().jwt(j -> j.subject("970006"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].deleted").value(true))
        .andExpect(jsonPath("$[0].bodyMd").doesNotExist())
        .andExpect(jsonPath("$[0].authorId").doesNotExist());

    // 상세 안에 박힌 경로 — 같은 매핑이 두 곳에 있어 한쪽만 고치면 어긋난다.
    mvc.perform(get("/community/posts/" + pid).with(jwt().jwt(j -> j.subject("970006"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comments.length()").value(1))
        .andExpect(jsonPath("$.comments[0].deleted").value(true))
        .andExpect(jsonPath("$.comments[0].bodyMd").doesNotExist());
  }
}
