package java.util.concurrent;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class CopyOnWriteArrayList<E> implements List<E>, RandomAccess, Cloneable, java.io.Serializable {
    private static final long serialVersionUID = 8673264195747942595L; // 序列化版本号

    /**
     * 采用读写分离的思想（写时加锁），下面的这个对象是在add方法中使用 synchronized(lock){...}对线程进行同步
     * CopyOnWriteArrayList以前的版本采用ReentrantLock进行加锁，
     * 随着synchronized在底层的优化，发现synchronized的效率高于ReentrantLock
     * 故在新版本的CopyOnWriteArrayList底层都是用synchronized
     */
    final transient Object lock = new Object();

    // 只能通过set、get方法操作
    private transient volatile Object[] array;
    final Object[] getArray() { return array; }
    final void setArray(Object[] a) { array = a; }

    /**
     * 创建一个空数组
     */
    public CopyOnWriteArrayList() {
        setArray(new Object[0]);
    }

    /**
     * 创建一个带元素的集合类，传入集合
     */
    public CopyOnWriteArrayList(Collection<? extends E> c) {
        Object[] es;
        if (c.getClass() == CopyOnWriteArrayList.class)
            es = ((CopyOnWriteArrayList<?>)c).getArray(); // 如果是CopyOnWriteArrayList的实例则直接强转得到数组
        else {
            es = c.toArray(); // 将集合转换成数组
            if (es.getClass() != Object[].class)
                es = Arrays.copyOf(es, es.length, Object[].class);// 使用写时拷贝原理将数组拷贝得到一个信的数组
        }
        setArray(es);// 将数组赋值给成员属性array
    }

    /**
     * 创建一个带元素的的集合，传入数组
     */
    public CopyOnWriteArrayList(E[] toCopyIn) {
        setArray(Arrays.copyOf(toCopyIn, toCopyIn.length, Object[].class));//写时复制得到数组再将数组赋值给成员属性array
    }

    /**
     * 返回数组的长度，因为不存在扩容，写时复制原理就是当添加一个元素则拷贝原数组，再将新元素结合创建一个新的数组
     * 长度是和元素个数相同的
     */
    public int size() {
        return getArray().length;
    }

    /**
     * 集合是否没有元素，不存在扩容所以直接判断size即可
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * 判断元素是否在集合的from - to之间的索引中
     * 本质调用的是对象的equals方法，因此对象需要重写equals方法，否则将调用Object的equals比较对象的hash值
     */
    private static int indexOfRange(Object o, Object[] es, int from, int to) {
        if (o == null) { // 如果是一个null元素，返回该元素的索引
            for (int i = from; i < to; i++)
                if (es[i] == null)
                    return i;
        } else { //不是null元素的情况，返回该元素的索引
            for (int i = from; i < to; i++)
                if (o.equals(es[i]))
                    return i;
        }
        return -1;
    }

    /**
     * 返回最后一个找到的元素的索引，本质就是逆序查找第一个，和前面的indexOfRange差不多的逻辑
     */
    private static int lastIndexOfRange(Object o, Object[] es, int from, int to) {
        if (o == null) {
            for (int i = to - 1; i >= from; i--)
                if (es[i] == null)
                    return i;
        } else {
            for (int i = to - 1; i >= from; i--)
                if (o.equals(es[i]))
                    return i;
        }
        return -1;
    }

    /**
     * 元素o是否在集合中，
     * 本质调用的是上面的indexOfRange方法，通过equals一个个比较集合内的元素找到元素的索引值
     * 再判断索引是否>=0
     */
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    /**
     * 返回元素o在集合中的索引值
     * 本质调用的是上面的indexOfRange方法，通过equals一个个比较集合内的元素
     */
    public int indexOf(Object o) {
        Object[] es = getArray();
        return indexOfRange(o, es, 0, es.length);
    }

    /**
     * 从index开始查找元素o再集合中的索引
     */
    public int indexOf(E e, int index) {
        Object[] es = getArray();
        return indexOfRange(e, es, index, es.length);
    }

    /**
     * 逆序查找第一个元素0的索引（正序最后一个是0的索引）
     */
    public int lastIndexOf(Object o) {
        Object[] es = getArray();
        return lastIndexOfRange(o, es, 0, es.length);
    }

    /**
     * 从index开始向后查找元素e的索引
     */
    public int lastIndexOf(E e, int index) {
        Object[] es = getArray();
        return lastIndexOfRange(e, es, 0, index + 1);
    }

    /**
     * 拷贝数组
     */
    public Object clone() {
        try {
            @SuppressWarnings("unchecked")
            CopyOnWriteArrayList<E> clone = (CopyOnWriteArrayList<E>) super.clone();
            clone.resetLock();
            // Unlike in readObject, here we cannot visibility-piggyback on the
            // volatile write in setArray().
            VarHandle.releaseFence();
            return clone;
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError();
        }
    }

    /**
     * 转换成数组
     */
    public Object[] toArray() {
        return getArray().clone();
    }

    /**
     * 转换成数组有泛型的方式
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        Object[] es = getArray();
        int len = es.length;
        if (a.length < len)
            return (T[]) Arrays.copyOf(es, len, a.getClass());// 将a覆盖原集合es内的元素，没覆盖到的保留
        else {
            System.arraycopy(es, 0, a, 0, len);
            if (a.length > len)
                a[len] = null;
            return a;
        }
    }


    /**
     * 返回a数组中索引index的元素
     */
    @SuppressWarnings("unchecked")
    static <E> E elementAt(Object[] a, int index) {
        return (E) a[index];
    }

    /**
     * 数组越界
     */
    static String outOfBounds(int index, int size) {
        return "Index: " + index + ", Size: " + size;
    }

    /**
     * 获得索引index下的元素
     */
    public E get(int index) {
        return elementAt(getArray(), index);
    }

    /**
     * 更新元素index的元素未element
     */
    public E set(int index, E element) {
        synchronized (lock) {
            Object[] es = getArray();
            E oldValue = elementAt(es, index);

            if (oldValue != element) { //新元素和旧元素不相等则更新
                es = es.clone();
                es[index] = element;
            }
            // Ensure volatile write semantics even when oldvalue == element
            setArray(es); // 将更新后的数组赋值给成员属性array
            return oldValue;
        }
    }

    /**
     * 添加元素
     */
    public boolean add(E e) {
        synchronized (lock) {
            Object[] es = getArray();
            int len = es.length;
            es = Arrays.copyOf(es, len + 1); //扩容一个单位
            es[len] = e;//将元素添加进去
            setArray(es);// 更新成员属性array
            return true;
        }
    }

    /**
     * 在index索引位置中添加元素element
     */
    public void add(int index, E element) {
        synchronized (lock) {
            Object[] es = getArray();// 得到底层的数组
            int len = es.length; // 原数组长度
            if (index > len || index < 0)
                throw new IndexOutOfBoundsException(outOfBounds(index, len));//索引越界异常
            Object[] newElements;
            int numMoved = len - index; //计算原数组长度和index差值
            if (numMoved == 0)
                newElements = Arrays.copyOf(es, len + 1);// 说明只是添加一个元素，和一个参数的add一致
            else {
                newElements = new Object[len + 1];
                System.arraycopy(es, 0, newElements, 0, index);// 先拷贝index以前的元素
                System.arraycopy(es, index, newElements, index + 1, numMoved);//后拷贝index以后的元素，目的就是为了空出index位置用于添加新元素
            }
            newElements[index] = element;
            setArray(newElements);// 更新array
        }
    }

    /**
     * 移除index的元素
     */
    public E remove(int index) {
        synchronized (lock) {
            Object[] es = getArray();// 原数组
            int len = es.length; // 原长度
            E oldValue = elementAt(es, index);//得到index索引的元素
            int numMoved = len - index - 1; // 新长度 - index的值，和前面一样的思想
            Object[] newElements;
            if (numMoved == 0)
                newElements = Arrays.copyOf(es, len - 1);//说明index刚好是末尾元素的索引值，直接拷贝index以前的元素即可
            else {
                // 说明index不是末尾元素，需要将index以前的拷贝，然后将index后的拷贝，将前后数组拼成一个新的数组，相当于去除了index元素
                newElements = new Object[len - 1];
                System.arraycopy(es, 0, newElements, 0, index);
                System.arraycopy(es, index + 1, newElements, index,
                                 numMoved);
            }
            setArray(newElements);
            return oldValue;
        }
    }

    /**
     * 移除元素o
     */
    public boolean remove(Object o) {
        Object[] snapshot = getArray();
        int index = indexOfRange(o, snapshot, 0, snapshot.length);// 先得到元素o的索引，然后将其移除
        return index >= 0 && remove(o, snapshot, index);
    }

    /**
     * 移除索引index的元素，需要判断元素o和index索引的元素是否相等
     */
    private boolean remove(Object o, Object[] snapshot, int index) {
        synchronized (lock) {
            Object[] current = getArray();
            int len = current.length;
            if (snapshot != current) findIndex: {
                int prefix = Math.min(index, len);
                for (int i = 0; i < prefix; i++) {
                    if (current[i] != snapshot[i] && Objects.equals(o, current[i])) {
                        index = i;
                        break findIndex;
                    }
                }
                if (index >= len)
                    return false;
                if (current[index] == o)
                    break findIndex;
                index = indexOfRange(o, current, index, len);
                if (index < 0)
                    return false;
            }
            Object[] newElements = new Object[len - 1];
            System.arraycopy(current, 0, newElements, 0, index);
            System.arraycopy(current, index + 1, newElements, index, len - index - 1);
            setArray(newElements);
            return true;
        }
    }

    /**
     * 移除fromIndex - toIndex内的元素
     */
    void removeRange(int fromIndex, int toIndex) {
        synchronized (lock) {
            Object[] es = getArray();
            int len = es.length;
            if (fromIndex < 0 || toIndex > len || toIndex < fromIndex)
                throw new IndexOutOfBoundsException();
            int newlen = len - (toIndex - fromIndex);
            int numMoved = len - toIndex;
            if (numMoved == 0)
                setArray(Arrays.copyOf(es, newlen));
            else {
                Object[] newElements = new Object[newlen];
                System.arraycopy(es, 0, newElements, 0, fromIndex);
                System.arraycopy(es, toIndex, newElements, fromIndex, numMoved);
                setArray(newElements);
            }
        }
    }

    /**
     * 如果元素e不存在则添加进数组
     */
    public boolean addIfAbsent(E e) {
        Object[] snapshot = getArray();
        return indexOfRange(e, snapshot, 0, snapshot.length) < 0 && addIfAbsent(e, snapshot);//如果没找到则索引返回-1需要执行后面addIfAbsent(e, snapshot)添加元素
    }

    /**
     * 如果元素e不存在则添加元素e
     */
    private boolean addIfAbsent(E e, Object[] snapshot) {
        synchronized (lock) {
            Object[] current = getArray();
            int len = current.length;
            if (snapshot != current) {
                int common = Math.min(snapshot.length, len);
                for (int i = 0; i < common; i++)
                    if (current[i] != snapshot[i]
                        && Objects.equals(e, current[i]))
                        return false;
                if (indexOfRange(e, current, common, len) >= 0)
                        return false;
            }
            Object[] newElements = Arrays.copyOf(current, len + 1);
            newElements[len] = e;
            setArray(newElements);
            return true;
        }
    }

    /**
     * 判断集合c中的元素是否都在数组中
     */
    public boolean containsAll(Collection<?> c) {
        Object[] es = getArray();
        int len = es.length;
        for (Object e : c) {
            if (indexOfRange(e, es, 0, len) < 0)
                return false;// 只要有一个没找到则说明不是所有元素堵在数组中
        }
        return true;
    }

    /**
     * 移除数组中包含在集合c的元素
     */
    public boolean removeAll(Collection<?> c) {
        Objects.requireNonNull(c);
        return bulkRemove(e -> c.contains(e));
    }

    /**
     * 移除不包含在集合c中的元素，相当于求交集（数组和集合c的交集）
     */
    public boolean retainAll(Collection<?> c) {
        Objects.requireNonNull(c);
        return bulkRemove(e -> !c.contains(e));
    }

    /**
     * 如果集合c中的元素都没有在数组中找到则添加c中所有元素到数组中
     */
    public int addAllAbsent(Collection<? extends E> c) {
        Object[] cs = c.toArray();
        if (cs.length == 0)
            return 0;
        synchronized (lock) {
            Object[] es = getArray();
            int len = es.length;
            int added = 0;
            // uniquify and compact elements in cs
            for (int i = 0; i < cs.length; ++i) {
                Object e = cs[i];
                if (indexOfRange(e, es, 0, len) < 0 &&
                    indexOfRange(e, cs, 0, added) < 0)
                    cs[added++] = e;
            }
            if (added > 0) {
                Object[] newElements = Arrays.copyOf(es, len + added);
                System.arraycopy(cs, 0, newElements, len, added);
                setArray(newElements);
            }
            return added;
        }
    }

    /**
     * 清空数组
     */
    public void clear() {
        synchronized (lock) {
            setArray(new Object[0]);
        }
    }

    /**
     * 添加集合c到数组中
     */
    public boolean addAll(Collection<? extends E> c) {
        Object[] cs = (c.getClass() == CopyOnWriteArrayList.class) ?
            ((CopyOnWriteArrayList<?>)c).getArray() : c.toArray();
        if (cs.length == 0)
            return false;
        synchronized (lock) {
            Object[] es = getArray();
            int len = es.length;
            Object[] newElements;
            if (len == 0 && cs.getClass() == Object[].class)
                newElements = cs;
            else {
                newElements = Arrays.copyOf(es, len + cs.length);
                System.arraycopy(cs, 0, newElements, len, cs.length);
            }
            setArray(newElements);
            return true;
        }
    }

    /**
     * 从index处插入集合c中所有元素
     */
    public boolean addAll(int index, Collection<? extends E> c) {
        Object[] cs = c.toArray();
        synchronized (lock) {
            Object[] es = getArray();
            int len = es.length;
            if (index > len || index < 0)
                throw new IndexOutOfBoundsException(outOfBounds(index, len));
            if (cs.length == 0)
                return false;
            int numMoved = len - index;
            Object[] newElements;
            if (numMoved == 0)
                newElements = Arrays.copyOf(es, len + cs.length);
            else {
                newElements = new Object[len + cs.length];
                System.arraycopy(es, 0, newElements, 0, index);
                System.arraycopy(es, index,
                                 newElements, index + cs.length,
                                 numMoved);
            }
            System.arraycopy(cs, 0, newElements, index, cs.length);
            setArray(newElements);
            return true;
        }
    }

    /**
     * java8的forEach循环，传入的是一个消费型的函数式接口
     */
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        for (Object x : getArray()) {
            @SuppressWarnings("unchecked") E e = (E) x;
            action.accept(e);
        }
    }

    /**
     * 断言型接口，遍历每一个元素，如果断言返回true则移除元素
     */
    public boolean removeIf(Predicate<? super E> filter) {
        Objects.requireNonNull(filter);
        return bulkRemove(filter);
    }


    /**
     * java8的二元操作的函数式接口
     */
    public void replaceAll(UnaryOperator<E> operator) {
        synchronized (lock) {
            replaceAllRange(operator, 0, getArray().length);
        }
    }


    /**
     * 将集合c进行排序
     */
    public void sort(Comparator<? super E> c) {
        synchronized (lock) {
            sortRange(c, 0, getArray().length);
        }
    }

    /**
     * 截取 fromIndex - toIndex 内的数组
     */
    public List<E> subList(int fromIndex, int toIndex) {
        synchronized (lock) {
            Object[] es = getArray();
            int len = es.length;
            int size = toIndex - fromIndex;
            if (fromIndex < 0 || toIndex > len || size < 0)
                throw new IndexOutOfBoundsException();
            return new COWSubList(es, fromIndex, size);
        }
    }
    /**
     * toString方法
     */
    public String toString() {
        return Arrays.toString(getArray());
    }

    /**
     * equals方法
     */
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof List))
            return false;

        List<?> list = (List<?>)o;
        Iterator<?> it = list.iterator();
        for (Object element : getArray())
            if (!it.hasNext() || !Objects.equals(element, it.next()))
                return false;
        return !it.hasNext();
    }

    /**
     * 计算from - to 内的元素hash值的和
     */
    private static int hashCodeOfRange(Object[] es, int from, int to) {
        int hashCode = 1;
        for (int i = from; i < to; i++) {
            Object x = es[i];
            hashCode = 31 * hashCode + (x == null ? 0 : x.hashCode());
        }
        return hashCode;
    }

    /**
     * 计算CopyOnWriteArrayList的hash值
     */
    public int hashCode() {
        Object[] es = getArray();
        return hashCodeOfRange(es, 0, es.length);
    }

    /**
     * 返回迭代器
     */
    public Iterator<E> iterator() {
        return new COWIterator<E>(getArray(), 0);
    }

    /**
     * 返回list的迭代器
     */
    public ListIterator<E> listIterator() {
        return new COWIterator<E>(getArray(), 0);
    }

    /**
     * 返回index以后元素的迭代器
     */
    public ListIterator<E> listIterator(int index) {
        Object[] es = getArray();
        int len = es.length;
        if (index < 0 || index > len)
            throw new IndexOutOfBoundsException(outOfBounds(index, len));

        return new COWIterator<E>(es, index);
    }

    /**
     * 返回并行迭代器
     */
    public Spliterator<E> spliterator() {
        return Spliterators.spliterator
            (getArray(), Spliterator.IMMUTABLE | Spliterator.ORDERED);
    }


    /**
     * 迭代器
     */
    static final class COWIterator<E> implements ListIterator<E> {}

    /**
     * 返回子列表的工具类
     */
    private class COWSubList implements List<E>, RandomAccess { }

    /**
     * 子列表迭代器的工具类
     */
    private static class COWSubListIterator<E> implements ListIterator<E> { }



    private static long[] nBits(int n) {
        return new long[((n - 1) >> 6) + 1];
    }
    private static void setBit(long[] bits, int i) {
        bits[i >> 6] |= 1L << i;
    }
    private static boolean isClear(long[] bits, int i) {
        return (bits[i >> 6] & (1L << i)) == 0;
    }

    private boolean bulkRemove(Predicate<? super E> filter) {
        synchronized (lock) {
            return bulkRemove(filter, 0, getArray().length);
        }
    }

    boolean bulkRemove(Predicate<? super E> filter, int i, int end) {
        // assert Thread.holdsLock(lock);
        final Object[] es = getArray();
        // Optimize for initial run of survivors
        for (; i < end && !filter.test(elementAt(es, i)); i++)
            ;
        if (i < end) {
            final int beg = i;
            final long[] deathRow = nBits(end - beg);
            int deleted = 1;
            deathRow[0] = 1L;   // set bit 0
            for (i = beg + 1; i < end; i++)
                if (filter.test(elementAt(es, i))) {
                    setBit(deathRow, i - beg);
                    deleted++;
                }
            // Did filter reentrantly modify the list?
            if (es != getArray())
                throw new ConcurrentModificationException();
            final Object[] newElts = Arrays.copyOf(es, es.length - deleted);
            int w = beg;
            for (i = beg; i < end; i++)
                if (isClear(deathRow, i - beg))
                    newElts[w++] = es[i];
            System.arraycopy(es, i, newElts, w, es.length - i);
            setArray(newElts);
            return true;
        } else {
            if (es != getArray())
                throw new ConcurrentModificationException();
            return false;
        }
    }
    void replaceAllRange(UnaryOperator<E> operator, int i, int end) {
        // assert Thread.holdsLock(lock);
        Objects.requireNonNull(operator);
        final Object[] es = getArray().clone();
        for (; i < end; i++)
            es[i] = operator.apply(elementAt(es, i));
        setArray(es);
    }
    @SuppressWarnings("unchecked")
    void sortRange(Comparator<? super E> c, int i, int end) {
        // assert Thread.holdsLock(lock);
        final Object[] es = getArray().clone();
        Arrays.sort(es, i, end, (Comparator<Object>)c);
        setArray(es);
    }

    /**
     * 序列化
     */
    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        s.defaultWriteObject();
        Object[] es = getArray();
        s.writeInt(es.length);
        for (Object element : es)
            s.writeObject(element);
    }

    /**
     * 反序列化
     */
    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        resetLock();
        int len = s.readInt();
        SharedSecrets.getJavaObjectInputStreamAccess().checkArray(s, Object[].class, len);
        Object[] es = new Object[len];
        for (int i = 0; i < len; i++)
            es[i] = s.readObject();
        setArray(es);
    }


    /** Initializes the lock; for use when deserializing or cloning. */
    private void resetLock() {
        Field lockField = java.security.AccessController.doPrivileged(
            (java.security.PrivilegedAction<Field>) () -> {
                try {
                    Field f = CopyOnWriteArrayList.class
                        .getDeclaredField("lock");
                    f.setAccessible(true);
                    return f;
                } catch (ReflectiveOperationException e) {
                    throw new Error(e);
                }});
        try {
            lockField.set(this, new Object());
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
    }



}
