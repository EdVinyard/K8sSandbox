package com.tzahk;

import java.net.InetSocketAddress;

import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;

@Service
@Scope(value=ConfigurableBeanFactory.SCOPE_SINGLETON)
public class Cassandra {
    private final Logger log = LoggerFactory.getLogger(Application.class);
    private final CqlSession session;
    private final InetSocketAddress serverAddress = 
        InetSocketAddress.createUnresolved("cassandra", 9042);

    public Cassandra() {
        session = CqlSession.builder()
            .withLocalDatacenter("local")
            .addContactPoint(serverAddress)
            .build();
    }

    /**
    * See https://github.com/datastax/java-driver/blob/4.x/examples/src/main/java/com/datastax/oss/driver/examples/basic/ReadCassandraVersion.java
    */
    public String version() {
        // We use execute to send a query to Cassandra. This returns a 
        // ResultSet, which is essentially a collection of Row objects.
        ResultSet rs = session.execute(
            "select release_version from system.local");

        // Extract the first row (which is the only one in this case).
        Row row = rs.one();

        // Extract the value of the first (and only) column from the row.
        assert row != null;
        String releaseVersion = row.getString("release_version");
        return releaseVersion;
    }
}
