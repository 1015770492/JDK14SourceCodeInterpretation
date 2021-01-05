package java.util.concurrent.atomic;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class AtomicMarkableReference<V> {

    private static class Pair<T> {
        final T reference;
        final boolean mark;// 布尔值

        private Pair(T reference, boolean mark) {
            this.reference = reference;
            this.mark = mark;
        }

        static <T> Pair<T> of(T reference, boolean mark) {
            return new Pair<T>(reference, mark);
        }
    }

    private volatile Pair<V> pair;
    private static final VarHandle PAIR;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            PAIR = l.findVarHandle(AtomicMarkableReference.class, "pair",
                    Pair.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private boolean casPair(Pair<V> cmp, Pair<V> val) {
        return PAIR.compareAndSet(this, cmp, val);
    }
    /**
     * 创建一个带初始值和一个布尔型标记
     */
    public AtomicMarkableReference(V initialRef, boolean initialMark) {
        pair = Pair.of(initialRef, initialMark);
    }

    /**
     * 放回当前值
     */
    public V getReference() {
        return pair.reference;
    }

    /**
     * 返回当前标记布尔值
     */
    public boolean isMarked() {
        return pair.mark;
    }

    /**
     * 返回当前值和当前标记布尔值
     */
    public V get(boolean[] markHolder) {
        Pair<V> pair = this.pair;
        markHolder[0] = pair.mark;
        return pair.reference;
    }

    /**
     * cas更新当前值和标记值
     */
    public boolean weakCompareAndSet(V expectedReference,
                                     V newReference,
                                     boolean expectedMark,
                                     boolean newMark) {
        return compareAndSet(expectedReference, newReference,
                expectedMark, newMark);
    }

    /**
     * cas更新当前值和标记值
     */
    public boolean compareAndSet(V expectedReference,
                                 V newReference,
                                 boolean expectedMark,
                                 boolean newMark) {
        Pair<V> current = pair;
        return
                expectedReference == current.reference &&
                        expectedMark == current.mark &&
                        ((newReference == current.reference &&
                                newMark == current.mark) ||
                                casPair(current, Pair.of(newReference, newMark)));
    }

    /**
     * 无条件更新当前值和标记型布尔值
     */
    public void set(V newReference, boolean newMark) {
        Pair<V> current = pair;
        if (newReference != current.reference || newMark != current.mark)
            this.pair = Pair.of(newReference, newMark);
    }

    /**
     * 获取标记型布尔值
     */
    public boolean attemptMark(V expectedReference, boolean newMark) {
        Pair<V> current = pair;
        return
                expectedReference == current.reference &&
                        (newMark == current.mark ||
                                casPair(current, Pair.of(expectedReference, newMark)));
    }


}
