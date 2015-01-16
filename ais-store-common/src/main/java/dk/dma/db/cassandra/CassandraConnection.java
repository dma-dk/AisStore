/* Copyright (c) 2011 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dk.dma.db.cassandra;

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.List;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SocketOptions;
import com.google.common.util.concurrent.AbstractService;

/**
 * A connection to Cassandra.
 * 
 * @author Kasper Nielsen
 */
public final class CassandraConnection extends AbstractService {

    /** The cluster we are connected to. */
    private final Cluster cluster;

    /** The name of the keyspace. */
    private final String keyspace;

    /** The current session. */
    private volatile Session session;

    /**
     * Creates a new connection.
     * 
     * @param cluster
     *            the cluster to connect to
     * @param keyspace
     *            the Cassandra keyspace
     * @throws NullPointerException
     *             if the cluster or keyspace is null
     */
    private CassandraConnection(Cluster cluster, String keyspace) {
        this.cluster = requireNonNull(cluster);
        this.keyspace = requireNonNull(keyspace);
    }

    /** {@inheritDoc} */
    @Override
    protected void doStart() {
        session = cluster.connect(keyspace);
        notifyStarted();
    }

    /**
     * Asynchronously execute the specified query.
     * 
     * @param builder
     *            the query builder
     * @return the result of the query
     */
    public <T extends CassandraQuery> T execute(CassandraQueryBuilder<T> builder) {
        return builder.execute(getSession());
    }

    /** {@inheritDoc} */
    @Override
    protected void doStop() {
        cluster.close();
        notifyStopped();
    }

    /**
     * Returns the current session.
     * 
     * @return the current session
     * @throws IllegalStateException
     *             if this connection has not yet been started via {@link #start()}
     */
    public Session getSession() {
        Session session = this.session;
        if (session == null) {
            throw new IllegalStateException("The connection has not been started via connection.start() yet");
        }
        return session;
    }

    /**
     * Creates a new Cassandra connection. The connection is not yet connected but must be started via {@link #start()}
     * and to close it use {@link #stop()}
     * 
     * @param keyspace
     *            the name of the keyspace
     * @param connectionPoints
     *            the connection points
     * @return a new connection
     */
    public static CassandraConnection create(String keyspace, String... connectionPoints) {
        return create(keyspace, Arrays.asList(connectionPoints));
    }

    /**
     * Creates a new Cassandra connection. The connection is not yet connected but must be started via {@link #start()}
     * and to close it use {@link #stop()}
     * 
     * @param keyspace
     *            the name of the keyspace
     * @param connectionPoints
     *            the connection points
     * @return a new connection
     */
    public static CassandraConnection create(String keyspace, List<String> connectionPoints) {
        Cluster cluster = Cluster.builder().addContactPoints(connectionPoints.toArray(new String[0])).withSocketOptions(new SocketOptions().setConnectTimeoutMillis(1000*60)).build();
        return new CassandraConnection(cluster, keyspace);
    }
}
