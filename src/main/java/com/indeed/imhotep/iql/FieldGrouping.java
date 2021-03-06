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

import com.google.common.base.Preconditions;
import com.indeed.imhotep.api.ImhotepOutOfMemoryException;
import com.indeed.imhotep.ez.EZImhotepSession;
import com.indeed.imhotep.ez.Field;
import com.indeed.imhotep.ez.GroupKey;
import com.indeed.imhotep.ez.StatReference;
import org.apache.log4j.Logger;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.indeed.imhotep.ez.Stats.Stat;

/**
 * @author jplaisance
 */
public final class FieldGrouping extends Grouping {
    private static final Logger log = Logger.getLogger(FieldGrouping.class);

    private static final Stat DEFAULT_SORT_STAT = EZImhotepSession.counts();

    private final Field field;
    private final int topK;
    private final Stat sortStat;
    private final boolean isBottom;
    private final boolean noExplode;

    public FieldGrouping(final Field field) {
        this(field, 0);
    }

    public FieldGrouping(final Field field, final boolean noExplode) {
        this(field, 0, DEFAULT_SORT_STAT, false, noExplode);
    }

    public FieldGrouping(final Field field, int topK) {
        this(field, topK, false);
    }

    public FieldGrouping(final Field field, int topK, boolean isBottom) {
        this(field, topK, DEFAULT_SORT_STAT, isBottom);
    }

    public FieldGrouping(final Field field, int topK, Stat sortStat) {
        this(field, topK, sortStat, false);
    }

    public FieldGrouping(final Field field, int topK, Stat sortStat, boolean isBottom) {
        this(field, topK, sortStat, isBottom, false);
    }

    public FieldGrouping(final Field field, int topK, Stat sortStat, boolean isBottom, boolean noExplode) {
        this.field = field;
        this.topK = topK;
        this.sortStat = sortStat;
        this.isBottom = isBottom;
        this.noExplode = noExplode;

        // validation
        if(topK > EZImhotepSession.GROUP_LIMIT) {
            DecimalFormat df = new DecimalFormat("###,###");
            throw new IllegalArgumentException("Number of requested top terms (" + df.format(topK) + ") for field " +
                    field.getFieldName() + " exceeds the limit (" + df.format(EZImhotepSession.GROUP_LIMIT) +
                    "). Please simplify the query.");
        }
    }

    public Map<Integer, GroupKey> regroup(final EZImhotepSession session, final Map<Integer, GroupKey> groupKeys) throws ImhotepOutOfMemoryException {
        if (topK > 0) {
            return Preconditions.checkNotNull(session.splitAllTopK(field, groupKeys, topK, sortStat, isBottom));
        } else if(noExplode) {
            return Preconditions.checkNotNull(session.splitAll(field, groupKeys));
        } else {
            return Preconditions.checkNotNull(session.splitAllExplode(field, groupKeys));
        }
    }

    public Iterator<GroupStats> getGroupStats(final EZImhotepSession session, final Map<Integer, GroupKey> groupKeys, final List<StatReference> statRefs, long timeoutTS) throws ImhotepOutOfMemoryException {
        if(groupKeys.isEmpty()) {   // we don't have any parent groups probably because all docs were filtered out
            return Collections.<GroupStats>emptyList().iterator();  // so no point doing FTGS
        }
        if (topK > 0) {
            //TODO have some way of not potentially pushing counts() twice
            final StatReference countStat = session.pushStatGeneric(sortStat);
            final TopKGroupingFTGSCallback callback = new TopKGroupingFTGSCallback(session.getStackDepth(), topK, countStat, statRefs, groupKeys, isBottom);
            session.ftgsIterate(Arrays.asList(field), callback);
            return callback.getResults().iterator();
        } else if(noExplode) {
            final GroupingFTGSCallbackNoExplode callback = new GroupingFTGSCallbackNoExplode(session.getStackDepth(), statRefs, groupKeys);
            return session.ftgsGetIterator(Arrays.asList(field), callback);
        } else {
            final GroupingFTGSCallback callback = new GroupingFTGSCallback(session.getStackDepth(), statRefs, groupKeys);
            session.ftgsIterate(Arrays.asList(field), callback);
            return callback.getResults().iterator();
        }
    }

    public Field getField() {
        return field;
    }

    public int getTopK() {
        return topK;
    }

    public boolean isNoExplode() {
        return noExplode;
    }
}
