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
class FeedMockMvcTest {

  @Autowired MockMvc mvc;

  private long createFree(String title) throws Exception {
    String body = mvc.perform(post("/community/posts")
            .with(jwt().jwt(j -> j.subject("500")))
            .contentType("application/json")
            .content("{\"boardType\":\"FREE\",\"title\":\"" + title + "\",\"bodyMd\":\"b\",\"tags\":[]}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
  }

  @Test
  void feed_withoutBoard_returnsAllBoards_withBoardTypeAndReplyCount() throws Exception {
    long freeId = createFree("자유피드글");
    mvc.perform(post("/community/posts/" + freeId + "/comments")
            .with(jwt().jwt(j -> j.subject("501")))
            .contentType("application/json").content("{\"bodyMd\":\"댓글\"}"))
        .andExpect(status().isCreated());

    // board 미지정 = 전 보드. 방금 만든 FREE 글이 boardType=FREE·replyCount>=1로 포함.
    mvc.perform(get("/community/posts").with(jwt().jwt(j -> j.subject("500"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == " + freeId + ")].boardType").value(
            org.hamcrest.Matchers.hasItem("FREE")))
        .andExpect(jsonPath("$[?(@.id == " + freeId + ")].replyCount").value(
            org.hamcrest.Matchers.hasItem(1)));
  }

  @Test
  void feed_boardFilter_returnsOnlyThatBoard() throws Exception {
    createFree("자유필터글");
    mvc.perform(get("/community/posts").param("board", "FREE")
            .with(jwt().jwt(j -> j.subject("500"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].boardType").value(
            org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("FREE"))));
  }
}
