package ai.devpath.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.devpath.community.post.dto.CreateAnswerRequest;
import ai.devpath.community.post.dto.CreateQuestionRequest;
import ai.devpath.community.reputation.UserReputationRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * ★내려간 콘텐츠는 더 이상 변경 대상이 아니다★
 *
 * <p>{@code HIDDEN}·{@code DELETED} 를 도입하면서 <b>읽기 경로만</b> 막았고, 평판과
 * {@code solved} 를 바꾸는 기존 경로({@link VoteService}, {@link AnswerService#accept})는
 * 상태를 보지 않았다. 그래서 관리자가 내리고 평판을 회수해도 아무나 upvote 하면 복원됐다.
 *
 * <p>이 테스트가 지키는 것은 "404 를 낸다" 가 아니라 <b>회수된 평판이 그대로 있다</b> 다 —
 * 예외만 단언하면 가드를 예외 없이 no-op 으로 바꿔도 통과할 수 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HiddenContentMutationGuardTest {
  @Autowired QuestionService questionService;
  @Autowired AnswerService answerService;
  @Autowired PostService postService;
  @Autowired VoteService voteService;
  @Autowired ContentAdminService contentAdmin;
  @Autowired UserReputationRepository reputations;

  private int repOf(long userId) {
    return reputations.findByUserId(userId).map(r -> r.getTotal()).orElse(0);
  }

  @Test
  void hiddenAnswerCannotBeUpvotedSoRevokedReputationStaysRevoked() {
    long asker = 9301, answerer = 9302, voter1 = 9303, voter2 = 9304;
    var q = questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
    var a = answerService.add(answerer, q.id(), new CreateAnswerRequest("ans"));

    // 대조군: 내려가기 전에는 upvote 가 실제로 평판을 올린다. 이게 없으면 아래 "0 유지" 가
    // 가드 때문인지 애초에 평판이 안 붙은 것인지 구분할 수 없다.
    voteService.voteAnswer(voter1, a.id(), 1);
    assertThat(repOf(answerer)).isEqualTo(10);

    contentAdmin.hideAnswer(a.id());
    assertThat(repOf(answerer)).isZero();

    assertThatThrownBy(() -> voteService.voteAnswer(voter2, a.id(), 1))
        .isInstanceOf(NotFoundException.class);
    assertThat(repOf(answerer)).as("내려간 답변에는 평판이 다시 붙지 않는다").isZero();
  }

  @Test
  void hiddenAnswerCannotBeAccepted() {
    long asker = 9311, answerer = 9312;
    var q = questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
    var a = answerService.add(answerer, q.id(), new CreateAnswerRequest("ans"));
    contentAdmin.hideAnswer(a.id());

    assertThatThrownBy(() -> answerService.accept(asker, a.id()))
        .isInstanceOf(NotFoundException.class);
    assertThat(repOf(answerer)).as("채택 보상이 나가지 않는다").isZero();
    assertThat(questionService.detail(q.id()).solved())
        .as("비석을 가리키는 solved 가 생기지 않는다").isFalse();
  }

  @Test
  void authorDeletedAnswerCannotBeAccepted() {
    long asker = 9321, answerer = 9322;
    var q = questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
    var a = answerService.add(answerer, q.id(), new CreateAnswerRequest("ans"));
    answerService.delete(answerer, a.id());

    assertThatThrownBy(() -> answerService.accept(asker, a.id()))
        .isInstanceOf(NotFoundException.class);
    assertThat(repOf(answerer)).isZero();
  }

  @Test
  void deletedPostCannotBeVoted() {
    long author = 9331, voter = 9332;
    var q = questionService.create(author, new CreateQuestionRequest("t", "b", List.of()));

    // 대조군: 삭제 전에는 같은 호출이 성공한다(글 downvote 는 평판 게이트가 없다).
    voteService.votePost(voter, q.id(), -1);
    assertThat(repOf(author)).isEqualTo(-2);

    postService.deletePost(author, q.id());
    assertThatThrownBy(() -> voteService.votePost(voter, q.id(), -1))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void answerOfDeletedQuestionCannotBeUpvoted() {
    long asker = 9341, answerer = 9342, voter = 9343;
    var q = questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
    var a = answerService.add(answerer, q.id(), new CreateAnswerRequest("ans"));
    postService.deletePost(asker, q.id());

    // 부모가 사라지면 자식도 변경 대상이 아니다 — 자식 상태 전파를 하지 않기로 한 대신
    // 변경 경로가 부모를 확인한다.
    assertThatThrownBy(() -> voteService.voteAnswer(voter, a.id(), 1))
        .isInstanceOf(NotFoundException.class);
    assertThat(repOf(answerer)).isZero();
  }
}
