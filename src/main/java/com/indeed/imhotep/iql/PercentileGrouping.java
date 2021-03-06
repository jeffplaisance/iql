/*
 * Copyright (C) 2014 Indeed Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
 package com.indeed.imhotep.iql;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.indeed.imhotep.api.ImhotepOutOfMemoryException;
import com.indeed.imhotep.ez.EZImhotepSession;
import com.indeed.imhotep.ez.EZImhotepSession.FTGSCallback;
import com.indeed.imhotep.ez.Field;
import com.indeed.imhotep.ez.GroupKey;
import com.indeed.imhotep.ez.StatReference;
import com.indeed.imhotep.ez.Stats.Stat;
import gnu.trove.TIntObjectHashMap;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author jsgroth
 */
public class PercentileGrouping extends Grouping {
    private final Stat countStat;

    private final List<Field> fields = Lists.newArrayList();
    private final List<Double> percentiles = Lists.newArrayList();
    private final List<Integer> fieldProjectionPositions = Lists.newArrayList();

    public PercentileGrouping(final Stat countStat) {
        this.countStat = countStat;
    }

    public void addPercentileQuery(final Field field, final double percentile, final int fieldProjectionPosition) {
        fields.add(field);
        percentiles.add(percentile);
        fieldProjectionPositions.add(fieldProjectionPosition);
    }

    @Override
    public Map<Integer, GroupKey> regroup(final EZImhotepSession session, final Map<Integer, GroupKey> groupKeys) throws ImhotepOutOfMemoryException {
        throw new UnsupportedOperationException("Percentiles must be used as the last group");
    }

    @Override
    public Iterator<GroupStats> getGroupStats(final EZImhotepSession session, final Map<Integer, GroupKey> groupKeys, final List<StatReference> statRefs, final long timeoutTS) throws ImhotepOutOfMemoryException {
        final StatReference countStatRef = session.pushStat(countStat);
        final long[] counts = getCounts(countStatRef);

        final Int2ObjectMap<Int2LongMap> groupToPositionToStats = getPercentileStats(session, groupKeys, countStatRef, counts);

        final List<GroupStats> result = Lists.newArrayList();

        final int statCount = statRefs.size();
        final int groupCount = session.getNumGroups();

        // get values for the normal stats
        final TIntObjectHashMap<double[]> statsResults = (statCount > 0) ? getGroupStatsValues(session, statRefs, groupCount) : null;

        // combine normal stats with distinct counts
        for (int groupNum = 1; groupNum < groupCount; groupNum++) {
            final Int2LongMap groupPercentileData = groupToPositionToStats.get(groupNum);
            double[] statsVals = statsResults != null ? statsResults.get(groupNum) : null;

            double[] values = new double[statCount + fields.size()];
            for(int i = 0, statsValsIndex = 0; i < values.length; i++) {
                if(groupPercentileData != null && groupPercentileData.containsKey(i)) {    // percentile value
                    values[i] = groupPercentileData.get(i);
                } else if(statsVals != null && statsValsIndex < statsVals.length) {
                    values[i] = statsVals[statsValsIndex++];    // normal stat value available
                } else {
                    values[i] = 0;  // normal stat not in stats array
                }
            }

            GroupKey groupKey = groupKeys.get(groupNum);
            result.add(new GroupStats(groupKey, values));
        }

        return result.iterator();
    }

    private Int2ObjectMap<Int2LongMap> getPercentileStats(final EZImhotepSession session, final Map<Integer, GroupKey> groupKeys, final StatReference countStatRef, final long[] counts) {
        final Set<Field> uniqueFields = Sets.newHashSet(fields);

        final Int2ObjectMap<Int2LongMap> groupToPositionToStats = new Int2ObjectOpenHashMap<Int2LongMap>();
        for (final int group : groupKeys.keySet()) {
            groupToPositionToStats.put(group, new Int2LongOpenHashMap());
        }

        for (final Field f : uniqueFields) {
            final List<Double> fieldPercentiles = Lists.newArrayList();
            final List<Integer> projectionPositions = Lists.newArrayList();

            for (int i = 0; i < fields.size(); ++i) {
                if (f.equals(fields.get(i))) {
                    fieldPercentiles.add(percentiles.get(i));
                    projectionPositions.add(fieldProjectionPositions.get(i));
                }
            }

            final Int2ObjectMap<DoubleList> percentileValues = new Int2ObjectOpenHashMap<DoubleList>();
            for (final int group : groupKeys.keySet()) {
                final DoubleList groupPercentileValues = new DoubleArrayList();
                for (final double percentile : fieldPercentiles) {
                    groupPercentileValues.add(percentile / 100 * counts[group]);
                }
                percentileValues.put(group, groupPercentileValues);
            }

            final PercentileFTGSCallback callback = new PercentileFTGSCallback(session.getStackDepth(), countStatRef, percentileValues);
            // hack for ramses indexes, it's slower to iterate over a string field as an int field but it's better than
            // doing a 2D metric regroup like ramhotep does
            final Field ftgsField = f.isIntField() ? f : Field.intField(f.getFieldName());
            session.ftgsIterate(Arrays.asList(ftgsField), callback);

            final Int2ObjectMap<LongList> groupToPercentileStats = callback.finalizeAndGetGroupToPercentileStats();
            for (final int group : groupToPercentileStats.keySet()) {
                final LongList percentileStats = groupToPercentileStats.get(group);
                for (int i = 0; i < percentileStats.size(); ++i) {
                    final int position = projectionPositions.get(i);
                    groupToPositionToStats.get(group).put(position, percentileStats.getLong(i));
                }
            }
        }

        session.popStat();
        return groupToPositionToStats;
    }

    private static TIntObjectHashMap<double[]> getGroupStatsValues(EZImhotepSession session, List<StatReference> statRefs, int groupCount) {
        final int statCount = statRefs.size();
        final double[][] statGroupValues = new double[statCount][];
        for (int i = 0; i < statCount; i++) {
            statGroupValues[i] = session.getGroupStats(statRefs.get(i));
        }
        final TIntObjectHashMap<double[]> ret = new TIntObjectHashMap<double[]>(groupCount);
        for (int group = 1; group <= groupCount; group++) {
            final double[] groupStats = new double[statCount];
            for (int statNum = 0; statNum < groupStats.length; statNum++) {
                if(group < statGroupValues[statNum].length) {
                    groupStats[statNum] = statGroupValues[statNum][group];
                }
            }
            ret.put(group, groupStats);
        }
        return ret;
    }

    private static long[] getCounts(final StatReference countStatRef) {
        final double[] doubleGroupStats = countStatRef.getGroupStats();
        final long[] groupStats = new long[doubleGroupStats.length];
        for (int i = 0; i < doubleGroupStats.length; ++i) {
            groupStats[i] = Math.round(doubleGroupStats[i]);
        }
        return groupStats;
    }

    private static class PercentileFTGSCallback extends FTGSCallback {
        private final Int2ObjectMap<LongList> groupToPercentileStats;

        private final StatReference statRef;
        private final Int2ObjectMap<DoubleList> percentileValues;

        private Int2LongMap groupToPrevCount = new Int2LongOpenHashMap();
        private Int2LongMap groupToPrevTerm = new Int2LongOpenHashMap();

        private PercentileFTGSCallback(final int numStats, final StatReference statRef, final Int2ObjectMap<DoubleList> percentileValues) {
            super(numStats);

            this.statRef = statRef;
            this.percentileValues = percentileValues;

            groupToPercentileStats = new Int2ObjectOpenHashMap<LongList>();
            for (final int group : percentileValues.keySet()) {
                final LongList stats = new LongArrayList();
                for (int i = 0; i < percentileValues.get(group).size(); ++i) {
                    stats.add(-1);
                }
                groupToPercentileStats.put(group, stats);
            }
        }

        @Override
        protected void intTermGroup(final String field, final long term, final int group) {
            final long prevCount = groupToPrevCount.get(group);
            final long countForTerm = Math.round(getStat(statRef));
            final long newCount = prevCount + countForTerm;

            final DoubleList groupPercentileValues = percentileValues.get(group);
            for (int i = 0; i < groupPercentileValues.size(); ++i) {
                final double percentileValue = groupPercentileValues.get(i);
                if (percentileValue > prevCount && percentileValue <= newCount) {
                    groupToPercentileStats.get(group).set(i, term);
                }
            }

            groupToPrevCount.put(group, newCount);
            groupToPrevTerm.put(group, term);
        }

        @Override
        protected void stringTermGroup(final String field, final String term, final int group) {
            throw new UnsupportedOperationException("Percentiles do not work with string fields");
        }

        public Int2ObjectMap<LongList> finalizeAndGetGroupToPercentileStats() {
            for (final int group : groupToPercentileStats.keySet()) {
                final LongList stats = groupToPercentileStats.get(group);
                for (int i = 0; i < stats.size(); ++i) {
                    if (stats.getLong(i) == -1) {
                        stats.set(i, groupToPrevTerm.get(i));
                    }
                }
            }

            return groupToPercentileStats;
        }
    }
}
