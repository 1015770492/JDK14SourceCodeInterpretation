package java.util.concurrent.atomic;

import java.io.Serializable;

public class LongAdder extends Striped64 implements Serializable {
    private static final long serialVersionUID = 7249069246863182397L;
    // 构造方法
    public LongAdder() {
    }


    public void add(long x) {
        Cell[] cs; long b, v; int m; Cell c;
        if ((cs = cells) != null || !casBase(b = base, b + x)) {
            boolean uncontended = true;
            if (cs == null || (m = cs.length - 1) < 0 ||
                (c = cs[getProbe() & m]) == null ||
                !(uncontended = c.cas(v = c.value, v + x)))
                longAccumulate(x, null, uncontended);
        }
    }


    public void increment() {
        add(1L);
    }
    public void decrement() {
        add(-1L);
    }

    public long sum() {
        Cell[] cs = cells;
        long sum = base;
        if (cs != null) {
            for (Cell c : cs)
                if (c != null)
                    sum += c.value;
        }
        return sum;
    }

    public void reset() {
        Cell[] cs = cells;
        base = 0L;
        if (cs != null) {
            for (Cell c : cs)
                if (c != null)
                    c.reset();
        }
    }


    public long sumThenReset() {
        Cell[] cs = cells;
        long sum = getAndSetBase(0L);
        if (cs != null) {
            for (Cell c : cs) {
                if (c != null)
                    sum += c.getAndSet(0L);
            }
        }
        return sum;
    }


    public String toString() {
        return Long.toString(sum());
    }

    public long longValue() {
        return sum();
    }


    public int intValue() {
        return (int)sum();
    }

    public float floatValue() {
        return (float)sum();
    }


    public double doubleValue() {
        return (double)sum();
    }


    private static class SerializationProxy implements Serializable {
        private static final long serialVersionUID = 7249069246863182397L;

        private final long value;

        SerializationProxy(LongAdder a) {
            value = a.sum();
        }
        private Object readResolve() {
            LongAdder a = new LongAdder();
            a.base = value;
            return a;
        }
    }


    private Object writeReplace() {
        return new SerializationProxy(this);
    }

    private void readObject(java.io.ObjectInputStream s)
        throws java.io.InvalidObjectException {
        throw new java.io.InvalidObjectException("Proxy required");
    }

}
