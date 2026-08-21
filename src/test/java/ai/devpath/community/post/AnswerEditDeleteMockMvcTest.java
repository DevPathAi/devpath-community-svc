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
class AnswerEditDeleteMockMvcTest {

  @Autowired MockMvc mvc;

  private long createQuestion(String subject) throws Exception {
    String body = mvc.perform(post("/community/questions")
            .with(jwt().jwt(j -> j.subject(subject)))
            .contentType("application/json")
            .content("{\"title\":\"질문\",\"bodyMd\":\"질문본문\",\"tags\":[]}"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
  }

  private long addAnswer(long questionId, String subject) throws Exception {
    String body = mvc.perform(post("/community/questions/" + questionId + "/answers")
            .with(jwt().jwt(j -> j.subject(subject)))
            .contentType("application/json").content("{\"bodyMd\":\"답변본문\"}"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
  }

  @Test
  void authorCanUpdateAnswer() throws Exception {
    long qid = createQuestion("960001");
    long aid = addAnswer(qid, "960002");

    mvc.perform(put("/community/answers/" + aid)
            .with(jwt().jwt(j -> j.subject("960002")))
            .contentType("application/json").content("{\"bodyMd\":\"고친답변\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bodyMd").value("고친답변"));
  }

  @Test
  void otherUserCannotUpdateAnswer() throws Exception {
    long qid = createQuestion("960003");
    long aid = addAnswer(qid, "960004");

    mvc.perform(put("/community/answers/" + aid)
            .with(jwt().jwt(j -> j.subject("960005")))
            .contentType("application/json").content("{\"bodyMd\":\"침입\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void deletedAnswerBecomesTombstoneInQuestionDetail() throws Exception {
    long qid = createQuestion("960006");
    long aid = addAnswer(qid, "960007");

    mvc.perform(delete("/community/answers/" + aid).with(jwt().jwt(j -> j.subject("960007"))))
        .andExpect(status().isNoContent());

    mvc.perform(get("/community/questions/" + qid).with(jwt().jwt(j -> j.subject("960006"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answers.length()").value(1))
        .andExpect(jsonPath("$.answers[0].deleted").value(true))
        .andExpect(jsonPath("$.answers[0].bodyMd").doesNotExist())
        .andExpect(jsonPath("$.answers[0].authorId").doesNotExist());
  }

  @Test
  void acceptedAnswerCannotBeDeletedByAuthor() throws Exception {
    long qid = createQuestion("960008");
    long aid = addAnswer(qid, "960009");

    mvc.perform(post("/community/answers/" + aid + "/accept")
            .with(jwt().jwt(j -> j.subject("960008"))))
        .andExpect(status().isOk());

    mvc.perform(delete("/community/answers/" + aid).with(jwt().jwt(j -> j.subject("960009"))))
        .andExpect(status().isConflict());
  }
}
