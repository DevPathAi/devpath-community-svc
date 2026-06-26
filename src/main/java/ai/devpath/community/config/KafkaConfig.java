package ai.devpath.community.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/** 시드 컨슈머 에러 핸들러: 일시 오류 지수백오프 재시도(3회), 소진 시 로그(멱등 영속이라 별도 보상 불요). */
@Configuration
public class KafkaConfig {

  private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

  @Bean
  public DefaultErrorHandler communityErrorHandler() {
    ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
    backOff.setMaxAttempts(3L);
    return new DefaultErrorHandler(this::recover, backOff);
  }

  private void recover(ConsumerRecord<?, ?> record, Exception ex) {
    log.error("community seed consume 재시도 소진 — skip offset={} value={}",
        record.offset(), record.value(), ex);
  }
}
