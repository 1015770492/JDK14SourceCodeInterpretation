package java.util.concurrent;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class CopyOnWriteArraySet<E> extends AbstractSet<E> implements java.io.Serializable {
    private static final long serialVersionUID = 5457747651344034263L;

    private final java.util.concurrent.CopyOnWriteArrayList<E> al;

    /**
     * 空的set集合，底层是new了一个CopyOnWriteArrayList对象
     */
    public CopyOnWriteArraySet() {
        al = new java.util.concurrent.CopyOnWriteArrayList<E>();
    }

    /**
     * 带集合的方式创建一个Set集合
     */
    public CopyOnWriteArraySet(Collection<? extends E> c) {
        if (c.getClass() == CopyOnWriteArraySet.class) {
            @SuppressWarnings("unchecked") CopyOnWriteArraySet<E> cc =
                (CopyOnWriteArraySet<E>)c;
            al = new java.util.concurrent.CopyOnWriteArrayList<E>(cc.al);
        }
        else {
            al = new CopyOnWriteArrayList<E>();
            al.addAllAbsent(c);
        }
    }

    /**
     * 返回元素个数
     */
    public int size() {
        return al.size();
    }

    /**
     * 判空
     */
    public boolean isEmpty() {
        return al.isEmpty();
    }

    /**
     * 是否包含元素o，底层用元素o的equals方法做比较
     */
    public boolean contains(Object o) {
        return al.contains(o);
    }

    /**
     * 转换成数组
     */
    public Object[] toArray() {
        return al.toArray();
    }

    /**
     * 转换成带泛型的数组
     */
    public <T> T[] toArray(T[] a) {
        return al.toArray(a);
    }

    /**
     * 清空集合
     */
    public void clear() {
        al.clear();
    }

    /**
     * 移除元素o
     */
    public boolean remove(Object o) {
        return al.remove(o);
    }

    /**
     * 添加元素o
     */
    public boolean add(E e) {
        return al.addIfAbsent(e);
    }

    /**
     * c中所有元素是否独占集合中
     */
    public boolean containsAll(Collection<?> c) {
        return (c instanceof Set)
            ? compareSets(al.getArray(), (Set<?>) c) >= 0
            : al.containsAll(c);
    }



    /**
     * 添加索引元素
     */
    public boolean addAll(Collection<? extends E> c) {
        return al.addAllAbsent(c) > 0;
    }

    /**
     * 求 A - A交B，A中保留B没有的元素
     */
    public boolean removeAll(Collection<?> c) {
        return al.removeAll(c);
    }

    /**
     * 求交集 set集合和c的交集
     */
    public boolean retainAll(Collection<?> c) {
        return al.retainAll(c);
    }

    /**
     * 获取迭代器
     */
    public Iterator<E> iterator() {
        return al.iterator();
    }

    /**
     * 两集合是否相等
     */
    public boolean equals(Object o) {
        return (o == this)
            || ((o instanceof Set)
                && compareSets(al.getArray(), (Set<?>) o) == 0);
    }

    /**
     * 断言型接口
     */
    public boolean removeIf(Predicate<? super E> filter) {
        return al.removeIf(filter);
    }

    /**
     * java8的forEach循环
     */
    public void forEach(Consumer<? super E> action) {
        al.forEach(action);
    }

    /**
     * 并行迭代器
     */
    public Spliterator<E> spliterator() {
        return Spliterators.spliterator
            (al.getArray(), Spliterator.IMMUTABLE | Spliterator.DISTINCT);
    }
    /** 私有方法
     * 比较两集合
     */
    private static int compareSets(Object[] snapshot, Set<?> set) {
        // Uses O(n^2) algorithm, that is only appropriate for small
        // sets, which CopyOnWriteArraySets should be.
        //
        // Optimize up to O(n) if the two sets share a long common prefix,
        // as might happen if one set was created as a copy of the other set.

        final int len = snapshot.length;
        // Mark matched elements to avoid re-checking
        final boolean[] matched = new boolean[len];

        // j is the largest int with matched[i] true for { i | 0 <= i < j }
        int j = 0;
        outer: for (Object x : set) {
            for (int i = j; i < len; i++) {
                if (!matched[i] && Objects.equals(x, snapshot[i])) {
                    matched[i] = true;
                    if (i == j)
                        do { j++; } while (j < len && matched[j]);
                    continue outer;
                }
            }
            return -1;
        }
        return (j == len) ? 0 : 1;
    }
}
