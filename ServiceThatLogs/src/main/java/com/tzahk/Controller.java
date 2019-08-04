package com.tzahk;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.base.Strings;

@RestController
public class Controller {
    private final Logger log = LoggerFactory.getLogger(Controller.class);

    private final Cassandra cassandra;

    public Controller(final Cassandra cassandra) {
        this.cassandra = cassandra;
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
}
