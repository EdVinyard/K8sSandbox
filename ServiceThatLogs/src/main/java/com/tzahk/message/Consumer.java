package com.tzahk.message;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope(value=ConfigurableBeanFactory.SCOPE_SINGLETON)
public class Consumer {
    private final Logger log = LoggerFactory.getLogger(Consumer.class);

    private final Config config;

    public Consumer(final Config config)
    {
        this.config = config;
    }

    private static final Duration CONSUMER_TIMEOUT = Duration.ofSeconds(1);

    public boolean tryConsume(final List<String> outMessages)
    {
        Objects.requireNonNull(outMessages, "outMessages");

        try (final var consumer = 
            new KafkaConsumer<Long, String>(config.consumerProperties())) 
        {
            consumer.subscribe(Collections.singletonList(config.topic()));
            final var records = consumer.poll(CONSUMER_TIMEOUT);
            final var recordCount = records.count();

            for (final var r: records) {
                final var message = r.value();
                log.info("Consumed: {}", message);
                outMessages.add(message);
            }

            consumer.commitAsync();
            return recordCount > 0;
        }
    }    
}
