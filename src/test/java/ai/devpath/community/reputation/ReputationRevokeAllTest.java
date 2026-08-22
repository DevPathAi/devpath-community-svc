package ai.devpath.community.reputation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ReputationRevokeAllTest {

  @Autowired ReputationService reputation;
  @Autowired ReputationEventRepository events;
  @Autowired UserReputationRepository reputations;

  private static final List<Long> SOURCE_IDS = List.of(980100L, 980101L, 980102L);
  private static final List<Long> USER_IDS =
      List.of(980001L, 980002L, 980003L, 980004L, 980005L, 980006L);

  /**
   * 이 레포의 테스트는 롤백하지 않는다. 그런데 평판은 「지난 실행이 남긴 이벤트」에 의존해
   * 계산된다 — {@code reverseVote} 가 actorId 별 과거 이벤트를 전부 되돌리기 때문이다.
   * 게다가 {@code revokeAllForSource} 가 남기는 보정 이벤트는 actorId 가 null 이라 총점은
   * 0 이 돼도 actor 별 합은 +10 으로 남는다. 격리하지 않으면 두 번째 실행부터 시작 상태가
   * 어긋난다. ★CI 는 매번 새 DB 라 드러나지 않고 로컬에서만 터진다★(실측: 2·3 회차 red).
   */
  @BeforeEach
  void isolate() {
    events.deleteAll(events.findAll().stream()
        .filter(e -> "ANSWER".equals(e.getSourceType())
            && e.getSourceId() != null && SOURCE_IDS.contains(e.getSourceId()))
        .toList());
    USER_IDS.forEach(u -> reputations.findByUserId(u).ifPresent(reputations::delete));
  }

  private long eventCountFor(long sourceId) {
    return events.findAll().stream()
        .filter(e -> "ANSWER".equals(e.getSourceType())
            && Objects.equals(Long.valueOf(sourceId), e.getSourceId()))
        .count();
  }

  /**
   * 투표 → 취소 → 재투표로 이벤트가 여러 겹 쌓인 상태에서도 순합만큼 정확히 회수된다.
   *
   * <p>★판별력 주의 — 2026-08-21 실측★ 이 시나리오는 「이벤트별 역산」 구현과 「순합 역산」
   * 구현을 <b>구분하지 못한다</b>. 모든 이벤트의 {@code -delta} 합은 정의상 {@code -(순합)} 이고
   * {@code addTotal} 이 선형이라 두 구현은 총점에서 항상 같은 값을 낸다. 계획 초판은 이 테스트가
   * 순진한 구현을 red 로 만든다고 적었으나 실제로 돌려 보니 green 이었다. 두 구현이 실제로
   * 갈리는 곳은 <b>보정 이벤트의 개수</b>이고, 그 판별은 {@link #revokeAllIsIdempotent()} 가 한다.
   *
   * <p>그래도 이 테스트는 남긴다 — "취소된 투표가 섞인 이력에서도 전액 회수된다" 는 계약 자체는
   * 지켜져야 하고, 순합 쿼리의 {@code group by} 가 망가지면 여기서 잡힌다.
   */
  @Test
  void revokeAllRemovesNetReputationEvenAfterVoteChurn() {
    long author = 980001L;
    long voter = 980002L;
    long sourceId = 980100L;
    List<Long> tagIds = List.of();

    reputation.applyVote(author, voter, "ANSWER", sourceId, 0, 1, tagIds);   // upvote
    reputation.applyVote(author, voter, "ANSWER", sourceId, 1, 0, tagIds);   // 취소
    reputation.applyVote(author, voter, "ANSWER", sourceId, 0, 1, tagIds);   // 재투표

    int before = reputation.reputationOf(author);
    assertThat(before).isGreaterThan(0);

    reputation.revokeAllForSource("ANSWER", sourceId, tagIds);

    assertThat(reputation.reputationOf(author)).isZero();
  }

  /**
   * 회수는 멱등하다 — 순합이 0 이 된 뒤 다시 돌려도 총점도, ★이벤트 로그도★ 그대로다.
   *
   * <p>★이 이벤트 수 단언이 이 태스크의 회귀 가드다★ — 「이벤트별 역산」 구현은 총점은
   * 맞추지만 순합이 이미 0 인 소스에도 이벤트 수만큼 보정을 또 쓴다(실측: 2 건 → 4 건).
   * 관리자가 같은 콘텐츠를 반복해 내리면 이벤트 로그가 끝없이 불어나고, 그 로그는 담합 탐지와
   * 일일 상한 산출이 읽는 자료다.
   */
  @Test
  void revokeAllIsIdempotent() {
    long author = 980003L;
    long voter = 980004L;
    long sourceId = 980101L;
    List<Long> tagIds = List.of();

    reputation.applyVote(author, voter, "ANSWER", sourceId, 0, 1, tagIds);
    reputation.revokeAllForSource("ANSWER", sourceId, tagIds);
    int afterFirst = reputation.reputationOf(author);
    long eventsAfterFirst = eventCountFor(sourceId);

    reputation.revokeAllForSource("ANSWER", sourceId, tagIds);

    assertThat(reputation.reputationOf(author)).isEqualTo(afterFirst).isZero();
    assertThat(eventCountFor(sourceId))
        .as("순합이 0 이면 되돌릴 것이 없으므로 보정 이벤트가 늘지 않아야 한다")
        .isEqualTo(eventsAfterFirst);
  }

  /** 수용 보너스도 같은 (sourceType, sourceId) 에 쌓이므로 순합에 자연히 들어온다. */
  @Test
  void revokeAllAlsoRemovesAcceptanceBonus() {
    long answerAuthor = 980005L;
    long questionAuthor = 980006L;
    long sourceId = 980102L;
    List<Long> tagIds = List.of();

    reputation.applyAcceptance(answerAuthor, questionAuthor, "ANSWER", sourceId, tagIds);
    assertThat(reputation.reputationOf(answerAuthor)).isGreaterThan(0);
    assertThat(reputation.reputationOf(questionAuthor)).isGreaterThan(0);

    reputation.revokeAllForSource("ANSWER", sourceId, tagIds);

    assertThat(reputation.reputationOf(answerAuthor)).isZero();
    assertThat(reputation.reputationOf(questionAuthor)).isZero();
  }
}
