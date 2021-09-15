package java.nio;

import jdk.internal.HotSpotIntrinsicCandidate;
import jdk.internal.misc.JavaNioAccess;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.misc.Unsafe;

import java.util.Spliterator;



public abstract class Buffer {
    // Unsafe是一个工具类，不提供给开发人员使用，内部的方法基本上都是native方法，通过getUnsafe()获取实例
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    /**
     * 遍历和拆分 Buffers 中维护的元素的 Spliterators 的特性。
     */
    static final int SPLITERATOR_CHARACTERISTICS = Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.ORDERED;

    /**
     * 始终不变的关系:
     *
     * mark <= position <= limit <= capacity
     *
     * 上面这个等式要始终记住，带着这个等式去读源码
     */
    private int mark = -1;// 指针
    private int position = 0;// 起始位置
    private int limit;//
    private int capacity;// 容量
    long address;

    // 创建一个缓存区 mark, position, limit, capacity
    // package访问权限，说明不提供给开发者使用。
    Buffer(int mark, int pos, int lim, int cap) {
        // 容量必须大于0
        if (cap < 0) throw createCapacityException(cap);
        this.capacity = cap;
        limit(lim);
        position(pos);
        if (mark >= 0) {
            if (mark > pos)
                throw new IllegalArgumentException("mark > position: (" + mark + " > " + pos + ")");
            this.mark = mark;
        }
    }

    // 返回limit值
    public final int limit() {
        return limit;
    }
    // 设置新的limit，如果新的limit违背了上诉等式（mark <= position <= limit <= capacity）则抛异常
    public Buffer limit(int newLimit) {
        if (newLimit > capacity | newLimit < 0)
            throw createLimitException(newLimit);
        limit = newLimit;
        // 为什么要更新position呢？
        if (position > limit) position = limit;
        if (mark > limit) mark = -1;
        return this;
    }

    // 返回position值
    public final int position() {
        return position;
    }
    // 设置新的position，
    public Buffer position(int newPosition) {
        // 新的position违背等式则抛异常，为什么呢？---后面慢慢理解就知道为什么要这样设计了
        if (newPosition > limit | newPosition < 0) throw createPositionException(newPosition);
        position = newPosition;
        if (mark > position) mark = -1;
        return this;
    }

    public final int capacity() {
        return capacity;
    }

    static IllegalArgumentException createSameBufferException() {
        return new IllegalArgumentException("The source buffer is this buffer");
    }


    static IllegalArgumentException createCapacityException(int capacity) {
        assert capacity < 0 : "capacity expected to be negative";
        return new IllegalArgumentException("capacity < 0: (" + capacity + " < 0)");
    }

    private IllegalArgumentException createPositionException(int newPosition) {
        String msg = null;
        if (newPosition > limit) {
            msg = "newPosition > limit: (" + newPosition + " > " + limit + ")";
        } else { // assume negative
            assert newPosition < 0 : "newPosition expected to be negative";
            msg = "newPosition < 0: (" + newPosition + " < 0)";
        }
        return new IllegalArgumentException(msg);
    }


    private IllegalArgumentException createLimitException(int newLimit) {
        String msg = null;

        if (newLimit > capacity) {
            msg = "newLimit > capacity: (" + newLimit + " > " + capacity + ")";
        } else { // assume negative
            assert newLimit < 0 : "newLimit expected to be negative";
            msg = "newLimit < 0: (" + newLimit + " < 0)";
        }
        return new IllegalArgumentException(msg);
    }

    public Buffer mark() {
        mark = position;
        return this;
    }


    public Buffer reset() {
        int m = mark;
        if (m < 0)
            throw new InvalidMarkException();
        position = m;
        return this;
    }

    // 还原默认值
    public Buffer clear() {
        limit = capacity;
        position = 0;
        mark = -1;
        return this;
    }

    /**
     * 模式转换
     * 与clear区别在于limit一个是到最末尾，而flip则是将limit指向当前位置position
     */
    public Buffer flip() {
        limit = position;
        position = 0;
        mark = -1;
        return this;
    }

    // 重新读取
    public Buffer rewind() {
        position = 0;
        mark = -1;
        return this;
    }


    public final int remaining() {
        return limit - position;
    }


    public final boolean hasRemaining() {
        return position < limit;
    }


    public abstract boolean isReadOnly();


    public abstract boolean hasArray();


    public abstract Object array();


    public abstract int arrayOffset();


    public abstract boolean isDirect();


    public abstract Buffer slice();


    public abstract Buffer duplicate();

    abstract Object base();


    final int nextGetIndex() {                          // package-private
        if (position >= limit)
            throw new BufferUnderflowException();
        return position++;
    }

    final int nextGetIndex(int nb) {                    // package-private
        if (limit - position < nb)
            throw new BufferUnderflowException();
        int p = position;
        position += nb;
        return p;
    }


    final int nextPutIndex() {                          // package-private
        if (position >= limit)
            throw new BufferOverflowException();
        return position++;
    }

    final int nextPutIndex(int nb) {                    // package-private
        if (limit - position < nb)
            throw new BufferOverflowException();
        int p = position;
        position += nb;
        return p;
    }


    @HotSpotIntrinsicCandidate
    final int checkIndex(int i) {                       // package-private
        if ((i < 0) || (i >= limit))
            throw new IndexOutOfBoundsException();
        return i;
    }

    final int checkIndex(int i, int nb) {               // package-private
        if ((i < 0) || (nb > limit - i))
            throw new IndexOutOfBoundsException();
        return i;
    }

    final int markValue() {                             // package-private
        return mark;
    }

    final void truncate() {                             // package-private
        mark = -1;
        position = 0;
        limit = 0;
        capacity = 0;
    }

    final void discardMark() {                          // package-private
        mark = -1;
    }

    static void checkBounds(int off, int len, int size) { // package-private
        if ((off | len | (off + len) | (size - (off + len))) < 0)
            throw new IndexOutOfBoundsException();
    }

    static {
        // setup access to this package in SharedSecrets
        SharedSecrets.setJavaNioAccess(
            new JavaNioAccess() {
                @Override
                public JavaNioAccess.BufferPool getDirectBufferPool() {
                    return Bits.BUFFER_POOL;
                }
                @Override
                public ByteBuffer newDirectByteBuffer(long addr, int cap, Object ob) {
                    return new DirectByteBuffer(addr, cap, ob);
                }
                @Override
                public void truncate(Buffer buf) {
                    buf.truncate();
                }
            });
    }

}
