package java.util.concurrent.atomic;

/**
 * jdk14
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

public class AtomicReference<V> implements java.io.Serializable {
    private static final long serialVersionUID = -1848883965231344442L;
    private static final VarHandle VALUE;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            VALUE = l.findVarHandle(AtomicReference.class, "value", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("serial") // Conditionally serializable
    private volatile V value;

    /**
     * 有参的构造，传入初始值
     */
    public AtomicReference(V initialValue) {
        value = initialValue;
    }

    /**
     * 无参构造
     */
    public AtomicReference() {
    }

    // get方法
    public final V get() {
        return value;
    }
    // set方法
    public final void set(V newValue) {
        value = newValue;
    }

    /**
     * Sets the value to {@code newValue},
     * with memory effects as specified by {@link VarHandle#setRelease}.
     *
     * @param newValue the new value
     * @since 1.6
     */
    public final void lazySet(V newValue) {
        VALUE.setRelease(this, newValue);
    }

    /**
     * 如果value值等于expectedValue则将value更新为newValue
     */
    public final boolean compareAndSet(V expectedValue, V newValue) {
        return VALUE.compareAndSet(this, expectedValue, newValue);
    }

    /**
     * 被废弃
     */
    @Deprecated(since="9")
    public final boolean weakCompareAndSet(V expectedValue, V newValue) {
        return VALUE.weakCompareAndSetPlain(this, expectedValue, newValue);
    }

    /**
     * 将值更新为newValue如果当前值是expectedValue
     */
    public final boolean weakCompareAndSetPlain(V expectedValue, V newValue) {
        return VALUE.weakCompareAndSetPlain(this, expectedValue, newValue);
    }

    /**
     * 更新值并返回旧值
     */
    @SuppressWarnings("unchecked")
    public final V getAndSet(V newValue) {
        return (V)VALUE.getAndSet(this, newValue);
    }

    /**
     * 更新新值并返回旧值，使用的是函数是接口UnaryOperator
     */
    public final V getAndUpdate(UnaryOperator<V> updateFunction) {
        V prev = get(), next = null;
        for (boolean haveNext = false;;) {
            if (!haveNext)
                next = updateFunction.apply(prev);
            if (weakCompareAndSetVolatile(prev, next))
                return prev;
            haveNext = (prev == (prev = get()));
        }
    }

    /**
     * 返回更新后的值，使用函数式接口
     */
    public final V updateAndGet(UnaryOperator<V> updateFunction) {
        V prev = get(), next = null;
        for (boolean haveNext = false;;) {
            if (!haveNext)
                next = updateFunction.apply(prev);
            if (weakCompareAndSetVolatile(prev, next))
                return next;
            haveNext = (prev == (prev = get()));
        }
    }

    /**
     * 返回旧值并用新值替代，函数式接口BinaryOperator
     */
    public final V getAndAccumulate(V x, BinaryOperator<V> accumulatorFunction) {
        V prev = get(), next = null;
        for (boolean haveNext = false;;) {
            if (!haveNext)
                next = accumulatorFunction.apply(prev, x);
            if (weakCompareAndSetVolatile(prev, next))
                return prev;
            haveNext = (prev == (prev = get()));
        }
    }

    /**
     * 返回更新后的值，函数式接口BinaryOperator
     */
    public final V accumulateAndGet(V x, BinaryOperator<V> accumulatorFunction) {
        V prev = get(), next = null;
        for (boolean haveNext = false;;) {
            if (!haveNext)
                next = accumulatorFunction.apply(prev, x);
            if (weakCompareAndSetVolatile(prev, next))
                return next;
            haveNext = (prev == (prev = get()));
        }
    }

    /**
     * 返会对象的字符串
     */
    public String toString() {
        return String.valueOf(get());
    }


    /**
     * 返回当前值
     */
    public final V getPlain() {
        return (V)VALUE.get(this);
    }

    /**
     * 更新当前值
     */
    public final void setPlain(V newValue) {
        VALUE.set(this, newValue);
    }

    /**
     * 返回当前值
     */
    public final V getOpaque() {
        return (V)VALUE.getOpaque(this);
    }

    /**
     * 设置新值
     */
    public final void setOpaque(V newValue) {
        VALUE.setOpaque(this, newValue);
    }

    /**
     * 返回当前值
     */
    public final V getAcquire() {
        return (V)VALUE.getAcquire(this);
    }

    /**
     *
     */
    public final void setRelease(V newValue) {
        VALUE.setRelease(this, newValue);
    }

    /**
     *
     */
    public final V compareAndExchange(V expectedValue, V newValue) {
        return (V)VALUE.compareAndExchange(this, expectedValue, newValue);
    }

    /**
     *
     */
    public final V compareAndExchangeAcquire(V expectedValue, V newValue) {
        return (V)VALUE.compareAndExchangeAcquire(this, expectedValue, newValue);
    }

    /**
     *
     */
    public final V compareAndExchangeRelease(V expectedValue, V newValue) {
        return (V)VALUE.compareAndExchangeRelease(this, expectedValue, newValue);
    }

    /**
     * 将
     */
    public final boolean weakCompareAndSetVolatile(V expectedValue, V newValue) {
        return VALUE.weakCompareAndSet(this, expectedValue, newValue);
    }

    /**
     *
     */
    public final boolean weakCompareAndSetAcquire(V expectedValue, V newValue) {
        return VALUE.weakCompareAndSetAcquire(this, expectedValue, newValue);
    }

    /**
     * 
     */
    public final boolean weakCompareAndSetRelease(V expectedValue, V newValue) {
        return VALUE.weakCompareAndSetRelease(this, expectedValue, newValue);
    }

}
