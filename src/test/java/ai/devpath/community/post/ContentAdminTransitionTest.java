package ai.devpath.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.devpath.community.outbox.OutboxEntry;
import ai.devpath.community.outbox.OutboxRepository;
import ai.devpath.community.post.dto.CreateAnswerRequest;
import ai.devpath.community.post.dto.CreateQuestionRequest;
import ai.devpath.community.reputation.UserReputationRepository;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 내리기의 두 정합성 계약.
 *
 * <p>1) ★작성자 삭제가 모더레이션을 선점하면 안 된다★ — {@code DELETED} 는 설계상 평판을
 * 유지하므로, 어뷰저가 upvote 를 모은 뒤 스스로 지우는 것만으로 평판을 굳힐 수 있었다.
 *
 * <p>2) ★내려간 답변의 부모가 이미 사라졌으면 색인을 되살리면 안 된다★ — 채택 연결을 푸는
 * 김에 내던 upsert 가 삭제된 질문을 검색에 다시 올렸다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContentAdminTransitionTest {
  @Autowired QuestionService questionService;
  @Autowired AnswerService answerService;
  @Autowired PostService postService;
  @Autowired VoteService voteService;
  @Autowired ContentAdminService contentAdmin;
  @Autowired CommunityPostRepository posts;
  @Autowired UserReputationRepository reputations;
  @Autowired OutboxRepository outbox;
  @Autowired JsonMapper jsonMapper;

  private int repOf(long userId) {
    return reputations.findByUserId(userId).map(r -> r.getTotal()).orElse(0);
  }

  /** 그 글에 대한 마지막 색인 이벤트의 deleted 값. 색인의 최종 상태가 곧 이 값이다. */
  private boolean lastIndexDeletedFlag(long postId) {
    OutboxEntry last = outbox.findAll().stream()
        .filter(e -> "community.post.changed".equals(e.getEventType()))
        .filter(e -> String.valueOf(postId).equals(e.getAggregateId()))
        .max(Comparator.comparing(OutboxEntry::getId))
        .orElseThrow(() -> new AssertionError("색인 이벤트가 하나도 없다"));
    return jsonMapper.readTree(last.getPayload()).get("deleted").asBoolean();
  }

  @Test
  void adminCanStillHideWhatTheAuthorAlreadyDeleted() {
    long author = 9401, voter = 9402;
    var q = questionService.create(author, new CreateQuestionRequest("t", "b", List.of()));
    voteService.votePost(voter, q.id(), -1);
    assertThat(repOf(author)).as("대조군: 평판이 실제로 붙어 있다").isEqualTo(-2);

    postService.deletePost(author, q.id());
    contentAdmin.hidePost(q.id());

    assertThat(posts.findById(q.id()).orElseThrow().getStatus())
        .as("작성자 삭제를 관리자 판단이 덮어쓴다").isEqualTo(ContentStatus.HIDDEN);
    assertThat(repOf(author)).as("선점 삭제로 평판을 굳힐 수 없다").isZero();
  }

  @Test
  void hidingAlreadyHiddenContentIsRejected() {
    long author = 9411;
    var q = questionService.create(author, new CreateQuestionRequest("t", "b", List.of()));
    contentAdmin.hidePost(q.id());

    // 이미 내려간 것만 거부한다 — 두 번 회수하면 평판이 음수로 흘러내린다.
    assertThatThrownBy(() -> contentAdmin.hidePost(q.id()))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void adminCanStillHideAnAnswerTheAuthorAlreadyDeleted() {
    long asker = 9421, answerer = 9422, voter = 9423;
    var q = questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
    var a = answerService.add(answerer, q.id(), new CreateAnswerRequest("ans"));
    voteService.voteAnswer(voter, a.id(), 1);
    assertThat(repOf(answerer)).as("대조군").isEqualTo(10);

    answerService.delete(answerer, a.id());
    contentAdmin.hideAnswer(a.id());

    assertThat(repOf(answerer)).isZero();
  }

  @Test
  void hidingAnAnswerOfADeletedQuestionDoesNotResurrectItInSearch() {
    long asker = 9431, answerer = 9432;
    var q = questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
    var a = answerService.add(answerer, q.id(), new CreateAnswerRequest("ans"));
    answerService.accept(asker, a.id());

    postService.deletePost(asker, q.id());
    assertThat(lastIndexDeletedFlag(q.id())).as("대조군: 삭제가 색인에서 내렸다").isTrue();

    contentAdmin.hideAnswer(a.id());
    assertThat(lastIndexDeletedFlag(q.id()))
        .as("채택 해제 때문에 삭제된 질문이 색인에 되살아나면 안 된다").isTrue();
  }

  @Test
  void hidingAnAcceptedAnswerOfALiveQuestionStillRefreshesTheIndex() {
    long asker = 9441, answerer = 9442;
    var q = questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
    var a = answerService.add(answerer, q.id(), new CreateAnswerRequest("ans"));
    answerService.accept(asker, a.id());

    contentAdmin.hideAnswer(a.id());
    assertThat(lastIndexDeletedFlag(q.id()))
        .as("살아 있는 질문은 isSolved 갱신을 위해 upsert 가 나가야 한다").isFalse();
  }
}
