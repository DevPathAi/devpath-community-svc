package ai.devpath.community.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.devpath.community.outbox.OutboxEntry;
import ai.devpath.community.outbox.OutboxRepository;
import ai.devpath.community.post.dto.CreateQuestionRequest;
import ai.devpath.shared.event.CommunityQuestionPostedEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
class QuestionOutboxTest {

  @Autowired QuestionService questions;
  @Autowired OutboxRepository outbox;
  @Autowired JsonMapper jsonMapper;

  @Test
  void createPersistsQuestionPostedOutboxEntry() throws Exception {
    long before = outbox.count();
    var view = questions.create(4242L,
        new CreateQuestionRequest("아웃박스질문", "본문내용", List.of("kafka")));
    assertTrue(outbox.count() > before, "outbox 1건 이상 적재");

    OutboxEntry entry = outbox.findAll().stream()
        .filter(e -> "community.question.posted".equals(e.getEventType()))
        .filter(e -> e.getAggregateId().equals(String.valueOf(view.id())))
        .findFirst().orElseThrow();
    assertEquals("community_question", entry.getAggregateType());
    CommunityQuestionPostedEvent ev =
        jsonMapper.readValue(entry.getPayload(), CommunityQuestionPostedEvent.class);
    assertEquals(view.id(), ev.questionId());
    assertEquals(4242L, ev.userId());
    assertEquals("아웃박스질문", ev.title());
    assertEquals("본문내용", ev.bodyMd());
  }
}
