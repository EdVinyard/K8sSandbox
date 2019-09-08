package com.tzahk.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class Producer {
    private final Logger log = LoggerFactory.getLogger(Producer.class);

    private final KafkaTemplate<String, String> template;
    private final Config config;

    public Producer(
        final KafkaTemplate<String, String> template,
        final Config config) 
    {
        this.template = template;
        this.config = config;
    }

    public void send(String message) {
        log.info("Producing: {}", message);
        template.send(config.topic(), message);
    }
}
