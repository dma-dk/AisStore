/*
 * Copyright (c) 2008 Kasper Nielsen.
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
package dk.dma.ais.store;

import static dk.dma.ais.store.AisStoreSchema.TABLE_AREA_CELL1;
import static dk.dma.ais.store.AisStoreSchema.TABLE_AREA_CELL10;
import static dk.dma.ais.store.AisStoreSchema.TABLE_AREA_CELL10_KEY;
import static dk.dma.ais.store.AisStoreSchema.TABLE_AREA_CELL1_KEY;
import static dk.dma.ais.store.AisStoreSchema.TABLE_MMSI;
import static dk.dma.ais.store.AisStoreSchema.TABLE_MMSI_KEY;
import static dk.dma.ais.store.AisStoreSchema.TABLE_TIME;
import static dk.dma.ais.store.AisStoreSchema.TABLE_TIME_KEY;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import org.joda.time.Interval;

import com.datastax.driver.core.Session;

import dk.dma.ais.packet.AisPacket;
import dk.dma.commons.util.Iterators;
import dk.dma.enav.model.geometry.Area;
import dk.dma.enav.model.geometry.grid.Cell;
import dk.dma.enav.model.geometry.grid.Grid;

/**
 * 
 * @author Kasper Nielsen
 */
public final class AisStoreQueryBuilder {

    final Area area;
    int batchLimit = 3000; // magic constant found by trial;

    final int[] mmsi;
    long startInclusive;

    long stopExclusive;

    private AisStoreQueryBuilder(Area area, int[] mmsi) {
        this.area = area;
        this.mmsi = mmsi;
    }

    public AisStoreQueryBuilder setBatchLimit(int limit) {
        this.batchLimit = limit;
        return this;
    }

    public AisStoreQueryBuilder setInterval(Interval interval) {

        return this;
    }

    /**
     * @param start
     *            the start date (inclusive)
     * @param end
     *            the end date (exclusive)
     * @return
     */
    public AisStoreQueryBuilder setInterval(long startMillies, long stopMillies) {

        return this;
    }

    /**
     * Creates a new builder for packets received from within the specified area in the given interval.
     * 
     * @param area
     *            the area
     * @return a new ais store query builder
     * @throws NullPointerException
     *             if the specified area is null
     */
    public static AisStoreQueryBuilder forArea(Area area) {
        return new AisStoreQueryBuilder(requireNonNull(area), null);
    }

    /**
     * Finds all packets for one or more MMSI numbers in the given interval.
     * 
     * @param start
     *            the start date (inclusive)
     * @param end
     *            the end date (exclusive)
     * @param mmsi
     *            one or more MMSI numbers
     * @return an iterable with all packets
     */
    public static AisStoreQueryBuilder forMmsi(int... mmsi) {
        if (mmsi.length == 0) {
            throw new IllegalArgumentException("Must request at least 1 mmsi number");
        }
        return new AisStoreQueryBuilder(null, mmsi);
    }

    /**
     * Finds all packets received in the given interval. Should only be used for small time intervals.
     * 
     * @param start
     *            the start date (inclusive)
     * @param end
     *            the end date (exclusive)
     * @return an iterable with all packets
     */
    public static AisStoreQueryBuilder forTime() {
        return new AisStoreQueryBuilder(null, null);
    }

    AisStoreQueryResult execute(final Session s) {
        requireNonNull(s);
        if (area != null) {
            Set<Cell> cells1 = Grid.GRID_1_DEGREE.getCells(area);
            Set<Cell> cells10 = Grid.GRID_10_DEGREES.getCells(area);

            int factor = 10;// magic constant

            // Determines if use the tables of size 1 degree, or size 10 degrees
            boolean useCell1 = cells10.size() * factor > cells1.size();
            final String tableName = useCell1 ? TABLE_AREA_CELL1 : TABLE_AREA_CELL10;
            final String keyName = useCell1 ? TABLE_AREA_CELL1_KEY : TABLE_AREA_CELL10_KEY;
            final Set<Cell> cells = useCell1 ? cells1 : cells10;

            // We create multiple queries and use a priority queue to return packets from each ship sorted by their
            // timestamp
            return new AisStoreQueryResult() {
                public Iterator<AisPacket> createQuery() {
                    ArrayList<AisStoreQuery> queries = new ArrayList<>();
                    for (Cell c : cells) {
                        queries.add(new AisStoreQuery(s, batchLimit, tableName, keyName, c.getCellId(), startInclusive,
                                stopExclusive));
                    }
                    return Iterators.combine(queries, AisStoreQuery.COMPARATOR);
                }
            };
        } else if (mmsi != null) {
            return new AisStoreQueryResult() {
                public Iterator<AisPacket> createQuery() {
                    ArrayList<AisStoreQuery> queries = new ArrayList<>();
                    for (int m : mmsi) {
                        queries.add(new AisStoreQuery(s, batchLimit, TABLE_MMSI, TABLE_MMSI_KEY, m, startInclusive,
                                stopExclusive));
                    }
                    // Return the actual iterator, if queries only contains 1 query
                    return Iterators.combine(queries, AisStoreQuery.COMPARATOR);
                }
            };
        } else {
            final int start = AisStoreSchema.getTimeBlock(startInclusive);
            final int stop = AisStoreSchema.getTimeBlock(stopExclusive - 1);
            return new AisStoreQueryResult() {
                public Iterator<AisPacket> createQuery() {
                    return new AisStoreQuery(s, batchLimit, TABLE_TIME, TABLE_TIME_KEY, start, stop, startInclusive,
                            stopExclusive);
                }
            };
        }
    }
}