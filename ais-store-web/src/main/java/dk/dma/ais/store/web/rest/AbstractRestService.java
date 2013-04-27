/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.dma.ais.store.web.rest;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import org.joda.time.Interval;

import dk.dma.ais.message.AisMessage;
import dk.dma.ais.packet.AisPacket;
import dk.dma.ais.store.AisQueries;
import dk.dma.ais.store.AisQueryEngine;
import dk.dma.ais.store.cassandra.CassandraAisQueryEngine;
import dk.dma.commons.util.io.OutputStreamSink;
import dk.dma.db.Query;
import dk.dma.db.cassandra.KeySpaceConnection;
import dk.dma.enav.util.function.Predicate;

/**
 * 
 * @author Kasper Nielsen
 */
public abstract class AbstractRestService {

    List<String> cassandraSeeds = Arrays.asList("10.10.5.202");

    AisQueryEngine mqs;

    /** {@inheritDoc} */
    public AbstractRestService() throws Exception {
        // Setup keyspace for cassandra
        KeySpaceConnection con = KeySpaceConnection.connect("aisdata", cassandraSeeds);
        con.start();
        mqs = new CassandraAisQueryEngine(con);
    }

    Interval findInterval(UriInfo info) {
        List<String> intervals = info.getQueryParameters().get("interval");
        if (intervals == null || intervals.size() == 0) {
            return new Interval(0, Long.MAX_VALUE);
        } else if (intervals.size() > 1) {
            throw new IllegalArgumentException("Multiple interval parameters defined: " + intervals);
        }
        return AisQueries.toInterval(intervals.get(0));
    }

    public static Query<AisPacket> applyFilters(UriInfo info, Query<AisPacket> q) {
        q = filterOnMessageType(info, q);
        return q;
    }

    private static Query<AisPacket> filterOnMessageType(UriInfo info, Query<AisPacket> q) {
        List<String> messageTypes = info.getQueryParameters().get("messageType");
        if (messageTypes != null && !messageTypes.isEmpty()) {
            final Set<Integer> allowedTypes = convert(messageTypes);
            q = q.filter(new Predicate<AisPacket>() {
                @Override
                public boolean test(AisPacket element) {
                    AisMessage m = element.tryGetAisMessage();
                    return m != null && allowedTypes.contains(m.getMsgId());
                }
            });
        }

        return q;
    }

    public static Set<Integer> convert(List<String> params) {
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        if (params != null) {
            for (String s : params) {
                String[] ss = s.split(",");
                for (String sss : ss) {
                    result.add(Integer.parseInt(sss));
                }
            }
        }
        return result;
    }

    static <T> StreamingOutput createStreamingOutput(final Query<T> query, final OutputStreamSink<T> sink) {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream paramOutputStream) throws IOException {
                try {
                    try (BufferedOutputStream bos = new BufferedOutputStream(paramOutputStream);) {
                        query.streamResults(bos, sink).get();
                    }
                    paramOutputStream.close();
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Exception e) {
                    throw new WebApplicationException(e);
                }
            }
        };
    }
}
