package com.indeed.imhotep.iql;

import com.indeed.imhotep.api.ImhotepOutOfMemoryException;
import com.indeed.imhotep.ez.EZImhotepSession;
import com.indeed.imhotep.ez.Field;
import com.indeed.imhotep.ez.GroupKey;
import com.indeed.imhotep.ez.StatReference;
import com.indeed.imhotep.ez.Stats;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author vladimir
 */

public class DiffGrouping extends Grouping {
    private final Field field;
    private final Stats.Stat filter1;
    private final Stats.Stat filter2;
    private final int topK;

    public DiffGrouping(Field field, Stats.Stat filter1, Stats.Stat filter2, int topK) {
        this.field = field;
        this.filter1 = filter1;
        this.filter2 = filter2;
        this.topK = topK;
    }

    public Field getField() {
        return field;
    }

    public Stats.Stat getFilter1() {
        return filter1;
    }

    public Stats.Stat getFilter2() {
        return filter2;
    }

    public int getTopK() {
        return topK;
    }

    @Override
    public Map<Integer, GroupKey> regroup(EZImhotepSession session, Map<Integer, GroupKey> groupKeys) throws ImhotepOutOfMemoryException {
        throw new UnsupportedOperationException();  // This should always be rewritten in the IQLTranslator so that it never gets invoked
    }

    @Override
    public Iterator<GroupStats> getGroupStats(EZImhotepSession session, Map<Integer, GroupKey> groupKeys, List<StatReference> statRefs, long timeoutTS) throws ImhotepOutOfMemoryException {
        throw new UnsupportedOperationException();  // This should always be rewritten in the IQLTranslator so that it never gets invoked
    }
}
