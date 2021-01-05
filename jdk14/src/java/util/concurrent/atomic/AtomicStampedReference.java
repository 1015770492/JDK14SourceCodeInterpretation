package java.util.concurrent.atomic;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class AtomicStampedReference<V> {

    private static class Pair<T> {
        final T reference; // 当前值
        final int stamp;   // 当前时间戳

        private Pair(T reference, int stamp) {
            this.reference = reference;
            this.stamp = stamp;
        }

        // 得到一个Pair实例
        static <T> Pair<T> of(T reference, int stamp) {
            return new Pair<T>(reference, stamp);
        }
    }

    private volatile Pair<V> pair;
    private static final VarHandle PAIR;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            PAIR = l.findVarHandle(AtomicStampedReference.class, "pair", Pair.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private boolean casPair(Pair<V> cmp, Pair<V> val) {
        return PAIR.compareAndSet(this, cmp, val);
    }

    /**
     * 创建一个有初始值和初始版本号的AtomicStampedReference实例
     */
    public AtomicStampedReference(V initialRef, int initialStamp) {
        pair = Pair.of(initialRef, initialStamp);
    }

    /**
     * 返回当前值
     */
    public V getReference() {
        return pair.reference;
    }

    /**
     * 返回当前版本号
     */
    public int getStamp() {
        return pair.stamp;
    }

    /**
     * 返回当前值并将数组中的时间戳更新为当前的时间戳
     */
    public V get(int[] stampHolder) {
        Pair<V> pair = this.pair;
        stampHolder[0] = pair.stamp;
        return pair.reference;
    }

    /**
     * cas操作，多个期望的时间戳和更新后的时间戳两个参数
     */
    public boolean weakCompareAndSet(V expectedReference, V newReference, int expectedStamp, int newStamp) {
        return compareAndSet(expectedReference, newReference, expectedStamp, newStamp);
    }

    /**
     * cas操作被上面方法调用，意味着两个方法效果一致
     */
    public boolean compareAndSet(V expectedReference, V newReference, int expectedStamp, int newStamp) {
        Pair<V> current = pair;
        return
                expectedReference == current.reference &&
                        expectedStamp == current.stamp &&
                        ((newReference == current.reference &&
                                newStamp == current.stamp) ||
                                casPair(current, Pair.of(newReference, newStamp)));
    }

    /**
     * 无条件的更新当前值和当前时间戳
     */
    public void set(V newReference, int newStamp) {
        Pair<V> current = pair;
        if (newReference != current.reference || newStamp != current.stamp)
            this.pair = Pair.of(newReference, newStamp);
    }

    /**
     * 如果是预期值则更新时间戳
     */
    public boolean attemptStamp(V expectedReference, int newStamp) {
        Pair<V> current = pair;
        return
                expectedReference == current.reference &&
                        (newStamp == current.stamp ||
                                casPair(current, Pair.of(expectedReference, newStamp)));
    }

}
