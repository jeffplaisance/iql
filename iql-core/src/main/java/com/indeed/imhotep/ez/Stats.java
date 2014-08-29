package com.indeed.imhotep.ez;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.List;

/**
 * @author jwolfe
 */
public class Stats {
    public static abstract class Stat {
        protected abstract List<String> pushes(EZImhotepSession session);
    }

    public static class IntFieldStat extends Stat {
        private final String fieldName;
        IntFieldStat(String fieldName) {
            this.fieldName = fieldName;
        }
        @Override
        protected List<String> pushes(EZImhotepSession session) {
            return Lists.newArrayList(fieldName);
        }
        @Override
        public String toString() {
            return "int:"+fieldName;
        }
    }

    public static class DynamicMetricStat extends Stat {
        private final DynamicMetric metric;
        DynamicMetricStat(DynamicMetric metric) {
            requireValid(metric);
            this.metric = metric;
        }
        @Override
        protected List<String> pushes(EZImhotepSession session) {
            requireValid(metric);
            return Lists.newArrayList("dynamic "+metric.name);
        }
        @Override
        public String toString() {
            if (metric.valid) {
                return "dynamic \""+metric.name+"\"";
            } else {
                return "invalid dynamic metric stat";
            }
        }
        public DynamicMetricStat of(DynamicMetric metric) {
            return new DynamicMetricStat(metric);
        }
    }

    static void requireValid(StatReference ref) {
        if (!ref.isValid()) {
            throw new IllegalArgumentException("Stat reference is no longer valid!");
        }
    }

    static void requireValid(DynamicMetric metric) {
        if (!metric.valid) {
            throw new IllegalArgumentException("Dynamic metric "+metric+"is not valid anymore.");
        }
    }

    static class BinOpStat extends Stat {
        private final String op;
        private final List<Stat> stats;

        public BinOpStat(String op, Stat... stats) {
            this.op = op;
            this.stats = Arrays.asList(stats);
            for(Stat stat : stats) {
                // TODO proper separation between client side and server side operations
                if(stat instanceof AggregateBinOpStat) {
                    throw new UnsupportedOperationException("Result of aggregate operations like / can't be used as input for further calculations");
                }
            }
        }

        @Override
        protected List<String> pushes(EZImhotepSession session) {
            boolean first = true;
            final List<String> ret = Lists.newArrayList();
            for (Stat stat : stats) {
                ret.addAll(stat.pushes(session));
                if (!first) ret.add(op);
                first = false;
            }
            return ret;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('(');
            boolean first = true;
            for (Stat stat : stats) {
                if (!first) {
                    sb.append(' ').append(op).append(' ');
                }
                sb.append(stat.toString());
                first = false;
            }
            sb.append(')');
            return sb.toString();
        }
    }

    static class AggregateBinOpStat extends Stat {
        private final String op;
        Stat statLeft;
        Stat statRight;

        public AggregateBinOpStat(String op, Stat statLeft, Stat statRight) {
            this.op = op;
            this.statLeft = statLeft;
            this.statRight = statRight;
        }

        @Override
        protected List<String> pushes(EZImhotepSession session) {
            return Lists.newArrayList(Iterables.concat(statLeft.pushes(session), statRight.pushes(session)));
        }

        @Override
        public String toString() {
            return "(" + statLeft.toString() + ") " + op + " (" + statRight.toString() + ")";
        }
    }

    static class ConstantStat extends Stat {
        private final long value;
        public ConstantStat(long value) {
            this.value = value;
        }
        @Override
        protected List<String> pushes(EZImhotepSession session) {
            return Lists.newArrayList(Long.toString(value));
        }
        @Override
        public String toString() {
            return Long.toString(value);
        }
    }

    static class ExpStat extends Stat {
        private final Stat stat;
        public ExpStat(Stat stat) {
            this.stat = stat;
        }
        @Override
        protected List<String> pushes(EZImhotepSession session) {
            List<String> prev = Lists.newArrayList(stat.pushes(session));
            prev.add("exp");
            return prev;
        }
        @Override
        public String toString() {
            return "exp("+stat.toString()+")";
        }
    }

    static class HasIntStat extends Stat {
        private final String field;
        private final long value;
        public HasIntStat(String field, long value) {
            this.field = field;
            this.value = value;
        }
        @Override
        protected List<String> pushes(EZImhotepSession session) {
            return Lists.newArrayList("hasint " + field + ":" + Long.toString(value));
        }
        @Override
        public String toString() {
            return "hasint:"+ field + ":" + Long.toString(value);
        }
    }

    static class HasStringStat extends Stat {
        private final String field;
        private final String value;
        public HasStringStat(String field, String value) {
            this.field = field;
            this.value = value;
        }
        @Override
        protected List<String> pushes(EZImhotepSession session) {
            return Lists.newArrayList("hasstr " + field + ":" + value);
        }
        @Override
        public String toString() {
            return "hasstr:" + field + ":" + value;
        }
    }

    static class StatRefStat extends Stat {
        private final SingleStatReference ref;
        StatRefStat(SingleStatReference ref) {
            this.ref = ref;
        }
        @Override
        protected List<String> pushes(EZImhotepSession session) {
            requireValid(ref);
            return Lists.newArrayList("ref "+(session.getStackDepth()-ref.depth-1));
        }
        @Override
        public String toString() {
            return "ref:"+ref.toString();
        }
    }
    static class CountStat extends Stat {
        CountStat() {
        }
        @Override
        protected List<String> pushes(EZImhotepSession session) {
            return Lists.newArrayList("count()");
        }
        @Override
        public String toString() {
            return "count()";
        }
    }

    public static class CachedStat extends Stat {
        private final Stat stat;
        CachedStat(Stat stat) {
            this.stat = stat;
        }
        @Override
        protected List<String> pushes(EZImhotepSession session) {
            List<String> ret = Lists.newArrayList(stat.pushes(session));
            ret.add("cached()");
            return ret;
        }
    }

    public static class AbsoluteValueStat extends Stat {
        private final Stat stat;
        AbsoluteValueStat(Stat stat) {
            this.stat = stat;
        }
        @Override
        protected List<String> pushes(EZImhotepSession session) {
            List<String> ret = Lists.newArrayList(stat.pushes(session));
            ret.add("abs()");
            return ret;
        }
    }

    public static class FloatScaleStat extends Stat {
        private final String fieldName;
        private final int mult;
        private final int add;

        FloatScaleStat(String fieldName, int mult, int add) {
            this.fieldName = fieldName;
            this.mult = mult;
            this.add = add;
        }
        @Override
        protected List<String> pushes(EZImhotepSession session) {
            return Lists.newArrayList("floatscale "+fieldName+" * "+mult+" + "+add);
        }
    }
}