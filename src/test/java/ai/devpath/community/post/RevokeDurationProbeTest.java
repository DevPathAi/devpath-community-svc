package ai.devpath.community.post;

import static org.assertj.core.api.Assertions.assertThat;

import ai.devpath.community.post.dto.CreateQuestionRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ★락을 쥐고 도는 시간이 락 타임아웃 안에 있어야 한다★
 *
 * <p>잠금 경로 중 {@code revokeAllForSource} 만 회수 대상 이벤트 수에 비례한다. 나머지는
 * 단일 행 갱신이라 상수 시간이다. 이벤트가 아주 많은 콘텐츠를 내릴 때 3 초를 넘기면, 그 사이
 * 같은 콘텐츠를 건드리는 다른 요청이 락 타임아웃으로 실패한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class RevokeDurationProbeTest {

  private static final long LO = 9700L;
  private static final long HI = 9999L;

  @Autowired PlatformTransactionManager txm;
  @Autowired JdbcTemplate jdbc;
  @Autowired QuestionService questionService;
  @Autowired VoteService voteService;
  @Autowired ContentAdminService contentAdmin;

  /**
   * ★재실행 안전성이 여기서는 측정 유효성의 문제다★ — 정리하지 않으면 작성자 9702 의
   * <b>오늘자 upvote 획득합</b>이 남아 일일 상한(40)에 걸린다. 상한을 넘으면
   * {@code capDailyUpvote} 가 0 을 돌려주고 {@code if (granted != 0)} 때문에
   * <b>이벤트가 아예 안 쌓인다</b>. 그러면 회수는 순식간에 끝나고 테스트는 green 인데
   * 아무것도 재지 않은 것이 된다.
   */
  @BeforeEach
  void isolate() {
    jdbc.update("DELETE FROM community_votes WHERE user_id BETWEEN ? AND ?", LO, HI);
    jdbc.update("DELETE FROM reputation_events WHERE user_id BETWEEN ? AND ?"
        + " OR actor_id BETWEEN ? AND ?", LO, HI, LO, HI);
    jdbc.update("DELETE FROM user_tag_reputation WHERE user_id BETWEEN ? AND ?", LO, HI);
    jdbc.update("DELETE FROM user_reputation WHERE user_id BETWEEN ? AND ?", LO, HI);
    jdbc.update("DELETE FROM user_badges WHERE user_id BETWEEN ? AND ?", LO, HI);
    jdbc.update("DELETE FROM community_answers WHERE author_id BETWEEN ? AND ?"
        + " OR question_id IN (SELECT id FROM community_posts WHERE author_id BETWEEN ? AND ?)",
        LO, HI, LO, HI);
    jdbc.update("DELETE FROM community_questions WHERE post_id IN"
        + " (SELECT id FROM community_posts WHERE author_id BETWEEN ? AND ?)", LO, HI);
    jdbc.update("DELETE FROM community_posts WHERE author_id BETWEEN ? AND ?", LO, HI);
  }

  /**
   * ★upvote 로는 이 위험을 잴 수 없다★ — 답변 upvote 는 건당 +10 이고 작성자의 일일 획득
   * 상한이 40 이라, 4 명째부터 {@code capDailyUpvote} 가 0 을 돌려주고
   * {@code if (granted != 0)} 때문에 이벤트가 아예 안 쌓인다. 투표자를 200 명 세워도 이벤트는
   * <b>4 건</b>이었다(실측). 그 상태의 소요 측정은 아무것도 재지 않는다.
   *
   * <p>그래서 상한이 없는 경로를 쓴다 — 글 downvote 는 평판 게이트도 일일 상한도 없고,
   * 건당 이벤트를 둘 만든다(작성자 {@code DOWNVOTE_RECEIVED}, 행사자 {@code DOWNVOTE_CAST}).
   */
  @Test
  void takedownOfAHeavilyVotedPostStaysWellUnderTheLockTimeout() {
    TransactionTemplate tx = new TransactionTemplate(txm);
    long postId = tx.execute(st ->
        questionService.create(9701L, new CreateQuestionRequest("t", "b", List.of())).id());
    for (long v = 9800L; v < 10000L; v++) {
      final long voter = v;
      tx.executeWithoutResult(st -> voteService.votePost(voter, postId, -1));
    }

    // ★측정법 유효성을 먼저 세운다★ — 회수가 훑는 것은 이벤트 행 수다. 상한이나 정리 실패로
    // 이벤트가 적으면 아래 소요 측정은 아무것도 재지 않는다. 이 가드가 낮으면 퇴화를 놓친다.
    Integer targets = jdbc.queryForObject(
        "SELECT count(*) FROM reputation_events WHERE source_type = 'POST' AND source_id = ?",
        Integer.class, postId);
    assertThat(targets)
        .as("회수 대상이 충분해야 소요 측정이 의미를 갖는다. 실측: %d 건", targets)
        .isGreaterThanOrEqualTo(300);

    long startedAt = System.nanoTime();
    tx.executeWithoutResult(st -> contentAdmin.hidePost(postId));
    long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

    assertThat(elapsedMs)
        .as("락 타임아웃 3000ms 의 1/3 안에 끝나야 여유가 있다. 실측: %d ms (이벤트 %d 건)",
            elapsedMs, targets)
        .isLessThan(1000L);
  }
}
