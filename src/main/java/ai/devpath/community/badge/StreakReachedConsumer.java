package ai.devpath.community.badge;

import ai.devpath.shared.event.StreakReachedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class StreakReachedConsumer {

  private static final int COMMUNITY_BADGE_MILESTONE_DAYS = 30;

  private static final Logger log = LoggerFactory.getLogger(StreakReachedConsumer.class);

  private final BadgeService badgeService;
  private final JsonMapper jsonMapper;

  public StreakReachedConsumer(BadgeService badgeService, JsonMapper jsonMapper) {
    this.badgeService = badgeService;
    this.jsonMapper = jsonMapper;
  }

  @KafkaListener(topics = StreakReachedEvent.EVENT_TYPE, groupId = "devpath-community")
  public void onStreakReached(String payload) {
    StreakReachedEvent event;
    try {
      event = jsonMapper.readValue(payload, StreakReachedEvent.class);
    } catch (Exception e) {
      log.warn("StreakReachedEvent 역직렬화 실패 — skip: {}", payload, e);
      return; // poison 무한재시도 방지
    }
    if (event.days() != COMMUNITY_BADGE_MILESTONE_DAYS) {
      return; // 30일 마일스톤만 COMMUNITY 배지 대상
    }
    badgeService.award(event.userId(), BadgeCode.COMMUNITY, "streak", event.userId());
  }
}
