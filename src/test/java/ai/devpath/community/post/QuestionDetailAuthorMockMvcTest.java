package ai.devpath.community.post;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 질문 상세가 작성자 id 를 싣는다.
 *
 * <p>프론트가 「내 질문이면 수정·삭제, 남의 질문이면 신고」를 가르려면 이 값이 필요하다.
 * 없던 시절 웹은 authorId 에 null 을 넘기고 있었다(qna_detail_page.dart 주석 참조).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuestionDetailAuthorMockMvcTest {

  @Autowired MockMvc mvc;

  @Test
  void questionDetailCarriesAuthorId() throws Exception {
    String body = mvc.perform(post("/community/questions")
            .with(jwt().jwt(j -> j.subject("992001")))
            .contentType("application/json")
            .content("{\"title\":\"작성자확인\",\"bodyMd\":\"본문\",\"tags\":[]}"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    long id = com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);

    mvc.perform(get("/community/questions/" + id).with(jwt().jwt(j -> j.subject("992001"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authorId").value(992001));
  }
}
