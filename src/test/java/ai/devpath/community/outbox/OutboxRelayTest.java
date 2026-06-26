package ai.devpath.community.outbox;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"community.question.posted"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class OutboxRelayTest {

  @Autowired OutboxRepository outbox;
  @Autowired OutboxRelay relay;
  @Autowired ConsumerFactory<String, String> cf;
  @Autowired EmbeddedKafkaBroker broker;

  @Test
  void relayPublishesUnpublishedRowAndMarksPublished() {
    OutboxEntry e = new OutboxEntry();
    e.setAggregateType("community_question");
    e.setAggregateId("555");
    e.setEventType("community.question.posted");
    e.setPayload("{\"questionId\":555}");
    e.setCreatedAt(Instant.now());
    Long id = outbox.save(e).getId();

    int published = relay.relayOnce();
    assertTrue(published >= 1);
    assertTrue(outbox.findById(id).orElseThrow().getPublishedAt() != null, "published_at 설정");

    try (Consumer<String, String> c = cf.createConsumer("t-grp", "t")) {
      broker.consumeFromAnEmbeddedTopic(c, "community.question.posted");
      ConsumerRecords<String, String> recs = KafkaTestUtils.getRecords(c);
      boolean found = StreamSupport.stream(recs.spliterator(), false)
          .anyMatch(r -> r.value().contains("555"));
      assertTrue(found, "발행 레코드에 questionId=555 포함");
    }
  }
}
