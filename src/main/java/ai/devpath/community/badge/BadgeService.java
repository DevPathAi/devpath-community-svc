package ai.devpath.community.badge;

import ai.devpath.community.outbox.OutboxEntry;
import ai.devpath.community.outbox.OutboxRepository;
import ai.devpath.shared.event.CommunityBadgeAwardedEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Bronze 배지 자동 수여 엔진. 호출자(post/reputation)의 트랜잭션에 합류한다. */
@Service
public class BadgeService {
  private final BadgeRepository badges;
  private final UserBadgeRepository userBadges;
  private final OutboxRepository outbox;
  private final JsonMapper jsonMapper;

  public BadgeService(BadgeRepository badges, UserBadgeRepository userBadges,
      OutboxRepository outbox, JsonMapper jsonMapper) {
    this.badges = badges; this.userBadges = userBadges;
    this.outbox = outbox; this.jsonMapper = jsonMapper;
  }

  /** code 배지를 userId에게 멱등 수여. 신규 수여 시 true + outbox 발행, 보유 시 false. */
  @Transactional
  public boolean award(long userId, BadgeCode code, String sourceType, long sourceId) {
    Badge badge = badges.findByCode(code.name())
        .orElseThrow(() -> new IllegalStateException("badge not seeded: " + code));
    if (userBadges.existsByUserIdAndBadgeId(userId, badge.getId())) return false;
    userBadges.save(new UserBadge(userId, badge.getId()));
    Instant now = Instant.now();
    CommunityBadgeAwardedEvent event = new CommunityBadgeAwardedEvent(
        UUID.randomUUID(), now, userId, code.name(), badge.getId(), sourceType, sourceId);
    OutboxEntry entry = new OutboxEntry();
    entry.setAggregateType("community_badge");
    entry.setAggregateId(userId + ":" + code.name());
    entry.setEventType(CommunityBadgeAwardedEvent.EVENT_TYPE);
    entry.setPayload(jsonMapper.writeValueAsString(event));
    entry.setCreatedAt(now);
    outbox.save(entry);
    return true;
  }
}
