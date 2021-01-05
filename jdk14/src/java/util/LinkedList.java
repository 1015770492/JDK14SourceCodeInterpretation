package java.util;

import java.util.function.Consumer;

public class LinkedList<E> extends AbstractSequentialList<E> implements List<E>, Deque<E>, Cloneable, java.io.Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 876323262645176354L; // 序列化版本号
    transient int size = 0;     // 当前元素个数0
    transient Node<E> first;    // 指向第一个节点数据
    transient Node<E> last;     // 指向最后一个节点


    /**
     * 存储数据的
     * Node数据结构
     */
    private static class Node<E> {
        E item;
        Node<E> next;
        Node<E> prev;

        Node(Node<E> prev, E element, Node<E> next) {
            this.item = element;
            this.next = next;
            this.prev = prev;
        }
    }
    /**
     * LinkedList构造方法
     */
    public LinkedList() {}                          // 构造一个空集合
    public LinkedList(Collection<? extends E> c) {} // 构造一个带有c集合所有元素的集合


    /**
     * 添加元素
     */
    public void addFirst(E e) {}            // 将节点e加入作为第一个节点
    public void addLast(E e) { }            // 将节点e加入作为尾节点
    public boolean add(E e) {}              // 添加元素，会加入到链表末尾
    public boolean offer(E e) {}            // 添加元素，调用的是add(e);就是上面一个方法
    public void add(int index, E element) {}// index位置加入元素element
    public boolean addAll(Collection<? extends E> c) {}             // 添加集合元素
    public boolean addAll(int index, Collection<? extends E> c) {}  // 指定位置后添加元素
    public boolean offerFirst(E e) {}       // 添加e成为首元素
    public boolean offerLast(E e) {}        // 添加e成为尾元素元素
    public void push(E e) {}                // 添加e并成为首元素
    /**
     * 移除元素
     */
    public E removeFirst() {}               // 移除首元素
    public E removeLast() {}                // 移除最后一个节点
    public E pollFirst() {}                 // 返回并移除首元素
    public E pollLast() {}                  // 返回并移除尾元素
    public E remove() {}                    // 移除首元素
    public E pop() {}                       // 移除首元素并返回首元素
    public E poll() {}                      // 返回首元素并从链表中剔除首元素，或者返回null
    public boolean remove(Object o) {}      // 移除元素o，如果成功返回true，失败（说明不存在）返回false
    public E remove(int index) {}           // 移除index位置的元素
    public boolean removeFirstOccurrence(Object o) {}   // 从头开始找元素o，并移除元素o
    public boolean removeLastOccurrence(Object o) {}    // 从尾往前找元素o，并移除元素
    /**
     * 修改元素
     */
    public E set(int index, E element) {}   // 替换index索引元素存的内容
    /**
     * 查询元素
     */
    public E getFirst() {}                  // 获取第一个节点
    public E getLast() {}                   // 获取最后一个节点
    public E peek() {}                      // 返回表头元素或者null
    public E element() {}                   // 返回表头元素，如果为null则抛异常，不会返回null元素
    public E peekLast() {}                  // 返回尾元素，可以返回null
    public E get(int index) {}              // 获得index索引下的元素
    public int indexOf(Object o) {}         // 从首元素往后找，返回元素o的索引值，如果不存在则返回-1
    public int lastIndexOf(Object o) {}     // 从尾元素往前找，返回元素索引
    public boolean contains(Object o) {}    // 是否包含o


    /**
     * 其它对集合操作的方法
     * 链表转数组
     * 集合大小、清空集合、克隆集合、迭代器
     */
    public Object[] toArray() {}            // 链表转数组，不带泛型
    public <T> T[] toArray(T[] a) {}        // 链表转数组，传入泛型
    public int size() {}                    // 返回链表长度
    public void clear() {}                  // 清除所有元素
    private LinkedList<E> superClone() {}   // 克隆一个LinkedList对象
    public Object clone() {}                // 将所有元素都克隆一份，并返回
    public Spliterator<E> spliterator() {}  // 迭代器，允许并行处理
    public ListIterator<E> listIterator(int index) {} // 迭代器

    /**
     * 后面的可以不用看，主要需要掌握前面对外提供的方法
     *
     * 不对外使用的方法
     */
    E unlink(Node<E> x) {}                  // 移除节点x
    Node<E> node(int index) {}              // 返回index下的节点
    void linkLast(E e) {}                   // 链接到最后一个节点上
    void linkBefore(E e, Node<E> succ) {}   // 链接到succ节点的上一个节点
    private void linkFirst(E e) {}          // 链接到第一个节点上
    private E unlinkFirst(Node<E> f) {}     // 移除第一个节点
    private E unlinkLast(Node<E> l) {}      // 移除最后一个节点
    private boolean isElementIndex(int index) {}    // 判断index是否在链表长度内
    private boolean isPositionIndex(int index) {}   // 判断index是否是正向索引
    private String outOfBoundsMsg(int index) {}     // 返回超出索引范围的提示字符串："Index: "+index+", Size: "+size;
    private void checkElementIndex(int index) {}    // 检查元素索引index是否在链表长度以内，不是则抛异常
    private void checkPositionIndex(int index) {}   // 检查正向索引
    /**
     * 序列化
     */
    @java.io.Serial
    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {}
    /**
     * 反序列化
     */
    @SuppressWarnings("unchecked")
    @java.io.Serial
    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {}


    public Iterator<E> descendingIterator() {
        return new DescendingIterator();
    }

    private class DescendingIterator implements Iterator<E> {
        private final ListItr itr = new ListItr(size());
        public boolean hasNext() {} // 是否有下一个元素
        public E next() {}          // 返回下一个元素
        public void remove() {}     // 移除当前迭代器返回的元素
    }
    private class ListItr implements ListIterator<E> {
        private Node<E> lastReturned;           // 上一个元素节点
        private Node<E> next;                   // 下一个节点元素
        private int nextIndex;                  // 上一个元素索引值
        private int expectedModCount = modCount;// 并发修改异常计数
        ListItr(int index) {}                   // 构造器
        public void add(E e) {}                 // 添加
        public void remove() {}                 // 删除
        public void set(E e) {}                 // 修改
        public boolean hasNext() {}             // 判断是否有下一个元素
        public E next() {}                      // 返回下一个元素
        public boolean hasPrevious() {}         // 是否有上一个值
        public E previous() {}                  // 返回上一个元素
        public int nextIndex() {}               // 下一个元素的索引值
        public int previousIndex() {}           // 上一个元素的索引值
        public void forEachRemaining(Consumer<? super E> action) {} // 遍历迭代器剩余元素
        final void checkForComodification() {}                      // 检查并发修改异常
    }
    
    static final class LLSpliterator<E> implements Spliterator<E> {
        static final int BATCH_UNIT = 1 << 10;  // batch array size increment
        static final int MAX_BATCH = 1 << 25;   // max batch array size;
        final LinkedList<E> list; // null OK unless traversed
        Node<E> current;      // current node; null until initialized
        int est;              // size estimate; -1 until first needed
        int expectedModCount; // initialized when est set
        int batch;            // batch size for splits
        LLSpliterator(LinkedList<E> list, int est, int expectedModCount) {} // 构造方法
        public void forEachRemaining(Consumer<? super E> action) {} // 遍历剩余元素
        public boolean tryAdvance(Consumer<? super E> action) {}    // 尝试获取剩余元素，如果没有剩余元素返回false
        final int getEst() {}
        public long estimateSize() {}
        public Spliterator<E> trySplit() {}
        public int characteristics() {}
    }
    
}
