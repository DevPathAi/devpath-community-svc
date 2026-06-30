package ai.devpath.community.badge;

import static org.assertj.core.api.Assertions.assertThat;

import ai.devpath.community.outbox.OutboxRepository;
import ai.devpath.shared.event.CommunityBadgeAwardedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BadgeServiceTest {
  @Autowired BadgeService svc;
  @Autowired UserBadgeRepository userBadges;
  @Autowired BadgeRepository badges;
  @Autowired OutboxRepository outbox;

  @Test
  void awardsBadgeOnceAndIsIdempotent() {
    long user = 70001;
    long badgeId = badges.findByCode("FIRST_QUESTION").orElseThrow().getId();

    boolean first = svc.award(user, BadgeCode.FIRST_QUESTION, "POST", 5001);
    assertThat(first).isTrue();
    assertThat(userBadges.existsByUserIdAndBadgeId(user, badgeId)).isTrue();

    boolean second = svc.award(user, BadgeCode.FIRST_QUESTION, "POST", 5001);
    assertThat(second).isFalse(); // 멱등 — 재수여 안 함
    assertThat(userBadges.findByUserIdOrderByAwardedAtDesc(user)).hasSize(1);
  }

  @Test
  void awardEmitsBadgeAwardedOutbox() {
    long user = 70002;
    long before = outbox.count();
    svc.award(user, BadgeCode.CRITIC, "ANSWER", 6001);
    assertThat(outbox.count()).isEqualTo(before + 1);
    assertThat(outbox.findAll().stream()
        .anyMatch(e -> CommunityBadgeAwardedEvent.EVENT_TYPE.equals(e.getEventType())
            && e.getPayload().contains("\"badgeCode\":\"CRITIC\""))).isTrue();
  }

  @Test
  void seedCatalogHasNineBronze() {
    assertThat(badges.count()).isGreaterThanOrEqualTo(9);
    assertThat(badges.findByCode("PHILANTHROPIST")).isPresent();
  }
}
