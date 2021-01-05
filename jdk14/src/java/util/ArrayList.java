package java.util;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class ArrayList<E> extends AbstractList<E> implements List<E>, RandomAccess, Cloneable, java.io.Serializable {
    @java.io.Serial
    private static final long serialVersionUID = 8683452581122892189L;      // 序列化版本号
    private static final int DEFAULT_CAPACITY = 10;                         // 初始容量10
    private static final Object[] EMPTY_ELEMENTDATA = {};                   // 默认的空数组
    private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};   // 默认空数组常量
    transient Object[] elementData;                                         // 存储数据的数组结构
    private int size;                                                       // 当前元素个数

    /**
     * 构造方法
     */
    public ArrayList() {}                               // 无参构造方法，得到空数组
    public ArrayList(int initialCapacity) {}            // 构造方法传参，自定义初始容量（实际上就是创建一个数组长度为initialCapacity的数组）
    public ArrayList(Collection<? extends E> c) {}      // 传入一个集合作为构造方法的参数


    /**
     * 添加元素
     */
    public boolean add(E e) {}                                      // 添加元素e到arrayList
    public void add(int index, E element) {}                        // 在索引index处添加元素element
    public boolean addAll(Collection<? extends E> c) {}             // 批量添加，传入集合
    public boolean addAll(int index, Collection<? extends E> c) {}  // 在索引处批量插入
    /**
     * 删除元素
     */
    public E remove(int index) {}                                   // 移除索引index的元素
    public boolean remove(Object o) {}                              // 移除集合中的元素o
    public boolean removeAll(Collection<?> c) {}                    // 移除包含在c中的所有元素
    public boolean removeIf(Predicate<? super E> filter) {}         // 条件移除，java8新特性，使用断言型接口
    public boolean retainAll(Collection<?> c) {}                    // 删除c集合中以外的其它元素
    /**
     * 修改元素
     */
    public void replaceAll(UnaryOperator<E> operator) {}            // java8新特性，对每一个元素进行一系列操作,lambda表达式的返回值要和泛型相同
    public E set(int index, E element) {}                           // 将索引index下的元素替换成element元素
    /**
     * 查询元素
     */
    public E get(int index) {}                          // 得到index的元素
    public int indexOf(Object o) {}                     // 查找元素的索引
    public int lastIndexOf(Object o) {}                 // 倒叙查找元素o，如果不存在则返回-1
    public boolean contains(Object o) {}                // 是否包含元素o
    public boolean isEmpty() {}                         // 元素是否为空
    public int size() {}                                // 返回元素个数size

    /**
     * 常用的java8操作，遍历元素
     */
    public void forEach(Consumer<? super E> action) {}  // java8新特性的forEach遍历，传入的是一个Consumer接口
    /**
     * 目的是扩容，防止minCapacity超出扩容规则，扩容至1.5倍原先旧数组长度。
     * 如果超过了则扩容至minCapacity，没超过则1.5倍原长度
     */
    private Object[] grow(int minCapacity) {}           // 扩容，上面注释有详细讲解
    private Object[] grow() {}                          // 实际上调用上面的方法进行扩容，相当于添加一个元素判断扩容。
    public void trimToSize() {}                         // 如果没有元素就将数组elementData设置为{},如果有元素则进行拷贝
    public void ensureCapacity(int minCapacity) {}      // minCapacity>10 并且 minCapacity>原来的长度 进行扩容
    public void clear() {}                              // 清空元素，实际上是数组元素全部置null
    public Object clone() {}                            // 得到另一份arrayList
    public void sort(Comparator<? super E> c) {}        // 排序，<47进行插入排序>47进行快排，>286并且具有"升序趋势的"结构进行归并排序
    public int hashCode() {}                            // hash值
    public Object[] toArray() {}                        // 拷贝一份数组，并返回
    public <T> T[] toArray(T[] a) {}                    // 带泛型拷贝一份数组，并返回
    public boolean equals(Object o) {}                  // 判断两数组是否相等，个别元素不同或者位置不同都返回false
    public Iterator<E> iterator() {}                    // 获取迭代器
    public ListIterator<E> listIterator() {}            // 获取ListIterator迭代器
    public ListIterator<E> listIterator(int index) {}   // 获取index以后的元素的ListIterator迭代器
    public Spliterator<E> spliterator() {}              // 获取Spliterator迭代器
    public List<E> subList(int fromIndex, int toIndex) {}// 获取fromIndex 到 toIndex内的集合


    /**
     * private、protected、默认 方法
     * 下面这些方法一般都用不到
     */
    private void add(E e, Object[] elementData, int s) {}
    private void rangeCheckForAdd(int index) {}
    private void fastRemove(Object[] es, int i) {}
    boolean removeIf(Predicate<? super E> filter, int i, final int end) {}
    boolean batchRemove(Collection<?> c, boolean complement, final int from, final int end) {}
    protected void removeRange(int fromIndex, int toIndex) {} // 移除fromIndex 到 toIndex 内的元素
    private void replaceAllRange(UnaryOperator<E> operator, int i, int end) {} // 对i 到 end 内的元素进行替换
    static <E> E elementAt(Object[] es, int index) {} //得到es在index下的元素
    private boolean equalsArrayList(ArrayList<?> other) {} // 判断数组是否相等
    int lastIndexOfRange(Object o, int start, int end) {} // 得到元素0的索引，如果没有找到则返回-1
    int indexOfRange(Object o, int start, int end) {}   // 从start~end查找元素o是否存在
    private String outOfBoundsMsg(int index) {} // 返回超出索引的提示字符串
    boolean equalsRange(List<?> other, int from, int to) {}
    void checkInvariants() {}       //这个方法在源码中旧没有内容，也没有注释
    int hashCodeRange(int from, int to) {}
    E elementData(int index) {}                 // 得到索引index的元素
    private void checkForComodification(final int expectedModCount) {}
    private void shiftTailOverGap(Object[] es, int lo, int hi) {}
    private static String outOfBoundsMsg(int fromIndex, int toIndex) {} // fromIndex，toIndex超出索引
    private static long[] nBits(int n) {}
    private static void setBit(long[] bits, int i) {}
    private static boolean isClear(long[] bits, int i) {}
    @java.io.Serial // 序列化
    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {}
    @java.io.Serial // 反序列化
    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {}

    /**
     * 以下都是非public类、因此一般也使用不到
     * 只需要指定这些类的目的是封装一些方法，例如迭代器
     * 因此下面的这些目的就是
     */

    private class Itr implements Iterator<E> {
        int cursor;       // index of next element to return
        int lastRet = -1; // index of last element returned; -1 if no such
        int expectedModCount = modCount;
        Itr() {} // 构造方法

        public boolean hasNext() {} // 是否有下一个元素
        @SuppressWarnings("unchecked")
        public E next() {} // 下一个元素
        public void remove() {} // 移除当前元素
        @Override
        public void forEachRemaining(Consumer<? super E> action) {} // 遍历剩余元素
        final void checkForComodification() {}
    }

    /**
     * 集合迭代器
     */
    private class ListItr extends Itr implements ListIterator<E> {
        ListItr(int index) {}
        public boolean hasPrevious() {}
        public int nextIndex() {}
        public int previousIndex() {}
        @SuppressWarnings("unchecked")
        public E previous() {}
        public void set(E e) {}
        public void add(E e) {}
    }

    /**
     * 子集合，随机获取效率高于顺序获取
     */
    private static class SubList<E> extends AbstractList<E> implements RandomAccess {
        private final ArrayList<E> root;
        private final SubList<E> parent;
        private final int offset;
        private int size;
        // 构造方法
        public SubList(ArrayList<E> root, int fromIndex, int toIndex) {}
        private SubList(SubList<E> parent, int fromIndex, int toIndex) {}

        public E set(int index, E element) {}
        public E get(int index) {}
        public int size() {}
        public void add(int index, E element) {}
        public E remove(int index) {}
        protected void removeRange(int fromIndex, int toIndex) {}
        public boolean addAll(Collection<? extends E> c) {}
        public boolean addAll(int index, Collection<? extends E> c) {}
        public void replaceAll(UnaryOperator<E> operator) {}
        public boolean removeAll(Collection<?> c) {}
        public boolean retainAll(Collection<?> c) {}
        private boolean batchRemove(Collection<?> c, boolean complement) {}
        public boolean removeIf(Predicate<? super E> filter) {}
        public Object[] toArray() {}
        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] a) {}
        public boolean equals(Object o) {}
        public int hashCode() {}
        public int indexOf(Object o) {}
        public int lastIndexOf(Object o) {}
        public boolean contains(Object o) {}
        public Iterator<E> iterator() {}
        public ListIterator<E> listIterator(int index) {}
        public List<E> subList(int fromIndex, int toIndex) {}
        private void rangeCheckForAdd(int index) {}
        public Spliterator<E> spliterator() {}
        // 私有方法
        private String outOfBoundsMsg(int index) {}
        private void checkForComodification() {}
        private void updateSizeAndModCount(int sizeChange) {}
    }

    /**
     * 并发的迭代器
     */
    final class ArrayListSpliterator implements Spliterator<E> {
        private int index; // current index, modified on advance/split
        private int fence; // -1 until used; then one past last index
        private int expectedModCount; // initialized when fence set
        // 构造方法
        ArrayListSpliterator(int origin, int fence, int expectedModCount) {}

        public ArrayListSpliterator trySplit() {}
        public boolean tryAdvance(Consumer<? super E> action) {}    // 尝试获取剩余元素
        public void forEachRemaining(Consumer<? super E> action) {} // 遍历剩余元素
        public long estimateSize() {}
        public int characteristics() {}
        private int getFence() {} // 私有方法
    }


}
