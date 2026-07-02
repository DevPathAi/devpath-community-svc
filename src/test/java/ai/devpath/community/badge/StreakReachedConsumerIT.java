package ai.devpath.community.badge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ai.devpath.shared.event.StreakReachedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {StreakReachedEvent.EVENT_TYPE},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class StreakReachedConsumerIT {

  @Autowired KafkaTemplate<String, String> kafka;
  @Autowired JsonMapper jsonMapper;
  @Autowired UserBadgeRepository userBadges;
  @Autowired BadgeRepository badges;

  @Test
  void awardsCommunityBadgeAtDay30() throws Exception {
    long userId = 666001L;
    var event = new StreakReachedEvent(UUID.randomUUID(), Instant.now(), userId, 30);
    kafka.send(StreakReachedEvent.EVENT_TYPE, String.valueOf(userId), jsonMapper.writeValueAsString(event));

    await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
      long communityBadgeId = badges.findByCode("COMMUNITY").orElseThrow().getId();
      assertThat(userBadges.existsByUserIdAndBadgeId(userId, communityBadgeId)).isTrue();
    });
  }

  @Test
  void doesNotAwardBadgeForNonMilestoneDay() throws Exception {
    long userId = 666002L;
    var event = new StreakReachedEvent(UUID.randomUUID(), Instant.now(), userId, 14);
    kafka.send(StreakReachedEvent.EVENT_TYPE, String.valueOf(userId), jsonMapper.writeValueAsString(event));

    // 14일째는 COMMUNITY 배지 대상이 아니므로, 짧게 대기 후 미수여 확인(고정 대기 — Build 1 WelcomeNotificationConsumerIT 패턴과 동일하게 처리하지 않고, 여기서는 음성 케이스라 await 대신 Thread.sleep 사용).
    Thread.sleep(3000);
    long communityBadgeId = badges.findByCode("COMMUNITY").orElseThrow().getId();
    assertThat(userBadges.existsByUserIdAndBadgeId(userId, communityBadgeId)).isFalse();
  }

  @Test
  void duplicateEventForSameUserIsIdempotent() throws Exception {
    long userId = 666003L;
    var event1 = new StreakReachedEvent(UUID.randomUUID(), Instant.now(), userId, 30);
    kafka.send(StreakReachedEvent.EVENT_TYPE, String.valueOf(userId), jsonMapper.writeValueAsString(event1));

    await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
      long communityBadgeId = badges.findByCode("COMMUNITY").orElseThrow().getId();
      assertThat(userBadges.existsByUserIdAndBadgeId(userId, communityBadgeId)).isTrue();
    });

    var event2 = new StreakReachedEvent(UUID.randomUUID(), Instant.now(), userId, 60);
    kafka.send(StreakReachedEvent.EVENT_TYPE, String.valueOf(userId), jsonMapper.writeValueAsString(event2));
    Thread.sleep(3000);

    long communityBadgeId = badges.findByCode("COMMUNITY").orElseThrow().getId();
    long count = userBadges.findAll().stream()
        .filter(ub -> ub.getUserId() == userId && ub.getBadgeId() == communityBadgeId)
        .count();
    assertThat(count).isEqualTo(1); // BadgeService.award()의 기존 멱등성(PK 중복 방지)으로 중복 수여 안 됨
  }
}
