package ai.devpath.community.seed;

import ai.devpath.shared.event.CommunitySeedReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CommunitySeedConsumer {

  private static final Logger log = LoggerFactory.getLogger(CommunitySeedConsumer.class);

  private final CommunitySeedService seedService;
  private final JsonMapper jsonMapper;

  public CommunitySeedConsumer(CommunitySeedService seedService, JsonMapper jsonMapper) {
    this.seedService = seedService;
    this.jsonMapper = jsonMapper;
  }

  @KafkaListener(topics = CommunitySeedReadyEvent.EVENT_TYPE, groupId = "devpath-community")
  public void onSeedReady(String payload) {
    CommunitySeedReadyEvent event;
    try {
      event = jsonMapper.readValue(payload, CommunitySeedReadyEvent.class);
    } catch (Exception e) {
      log.warn("CommunitySeedReadyEvent 역직렬화 실패 — skip: {}", payload, e);
      return; // poison 무한재시도 방지
    }
    seedService.apply(event);
  }
}
