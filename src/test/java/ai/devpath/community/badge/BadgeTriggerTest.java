package ai.devpath.community.badge;

import static org.assertj.core.api.Assertions.assertThat;

import ai.devpath.community.post.AnswerService;
import ai.devpath.community.post.QuestionService;
import ai.devpath.community.post.dto.CreateAnswerRequest;
import ai.devpath.community.post.dto.CreateQuestionRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BadgeTriggerTest {
  @Autowired QuestionService questionService;
  @Autowired AnswerService answerService;
  @Autowired UserBadgeRepository userBadges;
  @Autowired BadgeRepository badges;

  private boolean has(long userId, String code) {
    long badgeId = badges.findByCode(code).orElseThrow().getId();
    return userBadges.existsByUserIdAndBadgeId(userId, badgeId);
  }

  @Test
  void firstQuestionAwardsBadge() {
    long asker = 71001;
    questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
    assertThat(has(asker, "FIRST_QUESTION")).isTrue();
  }

  @Test
  void firstAnswerAwardsBadge() {
    long asker = 71002, answerer = 71003;
    var q = questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
    answerService.add(answerer, q.id(), new CreateAnswerRequest("ans"));
    assertThat(has(answerer, "FIRST_ANSWER")).isTrue();
  }
}
