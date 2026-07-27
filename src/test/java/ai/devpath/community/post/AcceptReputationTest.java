package ai.devpath.community.post;

import static org.assertj.core.api.Assertions.assertThat;

import ai.devpath.community.post.dto.CreateAnswerRequest;
import ai.devpath.community.post.dto.CreateQuestionRequest;
import ai.devpath.community.reputation.UserReputationRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AcceptReputationTest {
  @Autowired QuestionService questionService;
  @Autowired AnswerService answerService;
  @Autowired UserReputationRepository reputations;

  @Test
  void acceptGrantsFifteenToAnswererAndTwoToAsker() {
    long asker = 9001, answerer = 9002;
    var q = questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
    var a = answerService.add(answerer, q.id(), new CreateAnswerRequest("ans"));

    answerService.accept(asker, a.id());
    assertThat(reputations.findByUserId(answerer).orElseThrow().getTotal()).isEqualTo(15);
    assertThat(reputations.findByUserId(asker).orElseThrow().getTotal()).isEqualTo(2);

    // 재채택 호출 → no-op(중복 가산 없음)
    answerService.accept(asker, a.id());
    assertThat(reputations.findByUserId(answerer).orElseThrow().getTotal()).isEqualTo(15);
  }
}
