package com.lineless.notification_service.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class QueueJoinedConsumer {

    @KafkaListener(
            topics = "${notification.kafka.queue-joined-topic:queue-joined}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        log.info(
                "Received queue-joined event from topic={}, partition={}, offset={}, key={}, payload={}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                record.value()
        );
    }
}
