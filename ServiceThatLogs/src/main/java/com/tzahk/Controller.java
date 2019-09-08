package com.tzahk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Strings;
import com.tzahk.message.Consumer;
import com.tzahk.message.Producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {
    private final Logger log = LoggerFactory.getLogger(Controller.class);

    private final Cassandra cassandra;
    private final Producer producer;
    private final Consumer consumer;

    public Controller(
        final Cassandra cassandra,
        final Producer producer,
        final Consumer consumer) 
    {
        this.cassandra = cassandra;
        this.producer = producer;
        this.consumer = consumer;
    }

    @GetMapping("/")
    public String index(
        @RequestHeader(name="User-Agent", required=false) final String userAgent)
    {
        log.info("request from " + userAgent);

        if (Strings.isNullOrEmpty(userAgent)) {
            return "Howdy, stranger!";
        } else {
            return "Howdy, " + userAgent + "!";
        }
    }

    @GetMapping("/v")
    public String version() {
        return cassandra.version();
    }

    @PostMapping("/message")
    public ResponseEntity<String> postMessage(
        @RequestBody String message) 
    {
        producer.send(message);
        return ResponseEntity
            .created(null)
            .body(message);
    }

    @GetMapping("/messge")
    public ResponseEntity<List<String>> getMessages() {
        final var messages = new ArrayList<String>();
        if (consumer.tryConsume(messages)) {
            return ResponseEntity.ok(messages);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
