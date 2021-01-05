package java.util;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;


public class HashMap<K, V> extends AbstractMap<K, V> implements Map<K, V>, Cloneable, Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 362498820763181265L;   // 序列化版本号
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4;                 // 常量 16
    static final int MAXIMUM_CAPACITY = 1 << 30;                        // 最大容量2的30次方
    static final float DEFAULT_LOAD_FACTOR = 0.75f;                     // 默认加载因子（扩容因子）
    static final int MIN_TREEIFY_CAPACITY = 64;                         // 树化条件：map容器最小的容量值64
    static final int TREEIFY_THRESHOLD = 8;                             // 树化保持的阈值8
    static final int UNTREEIFY_THRESHOLD = 6;                           // 树转链表的节点数6
    transient Node<K, V>[] table;                                       // 底层真正存储数据的结构
    transient Set<Entry<K, V>> entrySet;                                // key的集合
    transient int size;                                                 // 元素个数size
    transient int modCount;                                             // 并发修改异常计数
    int threshold;                                                      // 触发map扩容的容量
    final float loadFactor;                                             // 加载因子

    static class Node<K, V> implements Entry<K, V> {
        final int hash;     // hash值
        final K key;        // 存储的key
        V value;            // 存储的value
        Node<K, V> next;     // 解决hash冲突的链表

        /**
         * 构造方法
         */
        Node(int hash, K key, V value, Node<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }
        // 返回key
        public final K getKey() {
            return key;
        }
        // 返回value
        public final V getValue() {
            return value;
        }
        // Node的toString方法
        public final String toString() {
            return key + "=" + value;
        }

        public final int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);// 得到hash值（key和value的hash值得异或运算）
        }

        // 设置新值并返回旧值
        public final V setValue(V newValue) {
            V oldValue = value; // 得到旧值
            value = newValue;   // 新值替换旧值
            return oldValue;    // 返回旧值
        }

        /**
         * 比较当前Node和o是否相等
         */
        public final boolean equals(Object o) {
            if (o == this)
                return true; // 地址相等说明是同一个对象，直接返回true
            if (o instanceof Map.Entry) { // 先判断类型是否相同
                Entry<?, ?> e = (Entry<?, ?>) o;// 将o强转为Entry
                if (Objects.equals(key, e.getKey()) && Objects.equals(value, e.getValue()))// 判断key和value是否相等
                    return true;// 相等就返回true
            }
            return false;// 说明不相等
        }
    }


    /**
     * 返回key的hash码然后(与自己的无符号右移16为的值)异或运算
     */
    static final int hash(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }

    /**
     * 如果x没有实现Comparable接口就会返回null，
     */
    static java.lang.Class<?> comparableClassFor(Object x) {
        if (x instanceof Comparable) { // 如果实现了Comparable接口
            java.lang.Class<?> c;
            Type[] ts, as;
            ParameterizedType p;
            if ((c = x.getClass()) == String.class)// 如果x是String则返回改类型
                return c;
            if ((ts = c.getGenericInterfaces()) != null) { // 得到所有接口类并且不为null
                for (Type t : ts) { // 遍历x的所有接口
                    if ((t instanceof ParameterizedType) &&
                            ((p = (ParameterizedType) t).getRawType() == Comparable.class) &&
                            (as = p.getActualTypeArguments()) != null &&
                            as.length == 1 && as[0] == c) {
                        return c;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 如果对象为null直接返回0，不为null则进行比较返回比较的值
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static int compareComparables(java.lang.Class<?> kc, Object k, Object x) {
        return (x == null || x.getClass() != kc ? 0 : ((Comparable) k).compareTo(x));
    }

    /**
     * 得到容量，目的是取出cap为负值的情况
     */
    static final int tableSizeFor(int cap) {
        // 返回cap-1值的前导0个数，例如 0000 0000 0000 0000 0000 0000 1111 1110返回得就是32-8=24，后面如果有0则不算。
        // -1在内存中是32位1，因此下面其实就是为了得到能够通过&容量运算得到非负的容量值
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);//
        // 下面的n除了cap等于0的情况基本上都是返回后面的n+1
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1; // 返回容量，实际上就是传入的cap，前面-1然后这里+1等于没有变
    }


    /**
     * 带初始容量和加载因子的构造方法
     */
    public HashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0) // 负数需要抛异常
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;// 最大的容量为2的30次方
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal load factor: " + loadFactor);
        this.loadFactor = loadFactor; // 设置自定义的加载因子
        this.threshold = tableSizeFor(initialCapacity);// 设置扩容阈值，如果为0，那么阈值也是0
    }


    /**
     * 带初始容量的构造方法
     */
    public HashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }


    /**
     * 空参构造
     */
    public HashMap() {
        this.loadFactor = DEFAULT_LOAD_FACTOR; // 设置默认加载因子
    }


    /**
     * 带map的构造方法
     */
    public HashMap(Map<? extends K, ? extends V> m) {
        this.loadFactor = DEFAULT_LOAD_FACTOR;// 0.75
        putMapEntries(m, false);
    }

    /**
     *
     */
    final void putMapEntries(Map<? extends K, ? extends V> m, boolean evict) {
        int s = m.size(); // 得到元素Entry个数
        if (s > 0) { // 有元素
            if (table == null) { // 如果是第一次
                float ft = ((float) s / loadFactor) + 1.0F;// 注意这个式子（原长度/0.75 最后+1），面试会可能问道，得到新的容量ft
                int t = ((ft < (float) MAXIMUM_CAPACITY) ? (int) ft : MAXIMUM_CAPACITY);// 如果新容量没有超过最大值限制，直接强转为int得到容量t
                if (t > threshold)
                    threshold = tableSizeFor(t);// 给map设置容量
            } else {
                // table中有元素，如果s的值大于容器并且原map没有超过最大容量就进行扩容
                while (s > threshold && table.length < MAXIMUM_CAPACITY)
                    resize();// 扩容
            }
            // 前面已经将容量threshold弄好了，下面就是将map存入当前hashMap中
            for (Entry<? extends K, ? extends V> e : m.entrySet()) {// 增强for遍历m
                K key = e.getKey();     // 得到key
                V value = e.getValue(); // 得到value
                putVal(hash(key), key, value, false, evict);// 添加一个节点
            }
        }
    }

    /**
     * 得到已存入元素个数
     */
    public int size() {
        return size;
    }

    /**
     * 判断元素是否为空
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 得到value
     */
    public V get(Object key) {
        Node<K, V> e;
        return (e = getNode(hash(key), key)) == null ? null : e.value;// 先得到存储的Node节点，然后返回value
    }

    /**
     * 得到key所对应的Node节点
     * 传入hash只是为了快速得到索引，可能有链表和红黑树，因此还需要根据key遍历链表和红黑树那部分
     */
    final Node<K, V> getNode(int hash, Object key) {
        Node<K, V>[] tab;
        Node<K, V> first, e;
        int n;
        K k;
        // 下面的first其实就是hash值所对应的数组索引值得Node节点，判断一下这个节点不能为null
        if ((tab = table) != null && (n = tab.length) > 0 && (first = tab[(n - 1) & hash]) != null) {
            // 如果这个位置刚好是当前节点就直接返回，也就是下面两种情况key相等
            if (first.hash == hash && ((k = first.key) == key || (key != null && key.equals(k))))
                return first;
            // 到这里说明有链表结构或红黑树结构，当前位置不是我们要得那个元素，需要遍历链表那部分
            if ((e = first.next) != null) {
                if (first instanceof TreeNode) // 是树的情况下遍历树
                    return ((TreeNode<K, V>) first).getTreeNode(hash, key);
                do {                           // 是链表的情况下遍历链表查找
                    if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k))))
                        return e;
                } while ((e = e.next) != null);
            }
        }
        return null;// 没找到则返回null
    }

    /**
     * 判断key是否存在
     */
    public boolean containsKey(Object key) {
        return getNode(hash(key), key) != null;
    }

    /**
     * 添加key，value
     */
    public V put(K key, V value) {
        return putVal(hash(key), key, value, false, true);
    }

    /**
     * 实际上真正得添加key，value
     */
    final V putVal(int hash, K key, V value, boolean onlyIfAbsent, boolean evict) {
        Node<K, V>[] tab;
        Node<K, V> p;
        int n, i;
        // 如果table为null 或者长度为0，就进行扩容，因为需要添加了一个元素
        if ((tab = table) == null || (n = tab.length) == 0)
            n = (tab = resize()).length;// 调用扩容得到长度为16
        // 如果原来没有元素，则直接占用这个位置
        if ((p = tab[i = (n - 1) & hash]) == null) { // 通过 容量-1 &运算得到hash值所对应的节点如果为null则直接存储
            tab[i] = newNode(hash, key, value, null);// 直接new一个节点将数据存储进去
        } else {
            /**
             * 到这里说明key所在的这个位置有元素
             * 产生了hash冲撞
             */
            Node<K, V> e;
            K k;
            // hash值相等并且key也相等 则将新值替换旧值
            if (p.hash == hash && ((k = p.key) == key || (key != null && key.equals(k)))) {
                e = p; // 得到该节点
            } else if (p instanceof TreeNode) {
                // 如果这个位置的节点是树节点，将当前节点添加到这个树上
                // 进行了强转
                e = ((TreeNode<K, V>) p).putTreeVal(this, tab, hash, key, value);
            } else {
                // 这里则说明是链表结构，需要将元素添加到链表末尾
                // 遍历找到最后一个元素p并加入到末尾
                for (int binCount = 0; ; ++binCount) {
                    if ((e = p.next) == null) {
                        // 找到末尾元素p，新的节点数据成为链表末尾
                        p.next = newNode(hash, key, value, null);// 创建新节点并使这个新节点成为链表末尾节点
                        // 是否达到了树化的形成条件，是则链表转换成树
                        /**
                         * TREEIFY_THRESHOLD=8，因此相当于链表长度binCount要 >= 8，treeifyBin(又要求容器的数组长度要 >= 64)
                         */
                        if (binCount >= TREEIFY_THRESHOLD - 1) {
                            treeifyBin(tab, hash);
                        }
                        break;// 插入完成后需要退出
                    }
                    if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k)))) {
                        break;// key如果相同
                    }
                    p = e; // 通过下一次进入循环的p.next得到下一个节点
                }
            }

            /**
             * hash冲撞的处理方式
             */
            if (e != null) {
                V oldValue = e.value;// 得到旧节点的value
                if (!onlyIfAbsent || oldValue == null) {
                    e.value = value;// 更新value
                }
                afterNodeAccess(e); // 允许LinkedHashMap回调
                return oldValue;    // 返回旧值
            }
        }
        ++modCount; // 并发修改计数+1
        if (++size > threshold) { // 如果添加一个元素达到了扩容条件值，进行扩容
            resize();// 扩容
        }
        afterNodeInsertion(evict);
        return null;//
    }


    /**
     * 扩容
     */
    final Node<K, V>[] resize() {
        Node<K, V>[] oldTab = table; // 原数组
        int oldCap = (oldTab == null) ? 0 : oldTab.length;// 得到原数组的长度（原容量）
        int oldThr = threshold;// 旧的扩容条件值
        int newCap, newThr = 0;// 定义新的容量和新的扩容值
        /**
         * 下面的不需要考虑超过容器最大值的情况，因为所有要求调用扩容的地方都校验了不会超过Integer.MAX_VALUE
         * 下面的这些分支只是为了得到新容器长度和新阈值，为后面扩容做准备
         * 1.原来容器长度>0的情况下：
         *      ①容器长度>=int所能表示的最大值，不能进行扩容需要直接返回容器，
         *      ②可以进行扩容调整容器大小和扩容阈值变为原来的两倍（就是右移一位）
         * 2.原来容器长度=0的情况下：（实际这种情况应该不会发生，除非反射操作HashMap，通过构造方法来的都不会执行这个分支）
         *      2.1.扩容阈值>0则得到旧容器扩容阈值（只需要判断size和这个阈值旧知道要不要扩容了，这种情况会执行的前提是旧容器长度为0）
         * 3.容器长度=0且 扩容阈值=0：设置容器长度为16，阈值为 16*0.75 =12
         */
        if (oldCap > 0) {      // 旧的容量是否大于0
            if (oldCap >= MAXIMUM_CAPACITY) { // 旧容量大于等于2的30次方
                threshold = Integer.MAX_VALUE;// 将容量设置为2的31次方
                return oldTab;                // 返回旧数组
            } else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY && oldCap >= DEFAULT_INITIAL_CAPACITY) {
                newThr = oldThr << 1;// if条件中新容量变为原来的两倍 // 新数组长度变为原来的2倍
            }
        } else if (oldThr > 0) { // 旧的扩容值是否 > 0
            newCap = oldThr;     // 得到旧的扩容值
        } else {
            // 初始为0的情况下，也就是空参构造，第一次添加元素触发的扩容。
            newCap = DEFAULT_INITIAL_CAPACITY;// 16
            newThr = (int) (DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);// 12，得到默认的扩容条件值
        }

        /**
         * 下面这种情况是避免上面第二个分支没有对newThr赋值的情况
         */
        if (newThr == 0) {
            float ft = (float) newCap * loadFactor;// 新的扩容条件值=新容量*0.75
            // 下面的只是校验，实际上ft就是扩容条件值，然后进行强转int
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float) MAXIMUM_CAPACITY ? (int) ft : Integer.MAX_VALUE);
        }
        threshold = newThr; // 设置扩容阈值
        @SuppressWarnings({"rawtypes", "unchecked"})
        Node<K, V>[] newTab = (Node<K, V>[]) new Node[newCap];  // 得到新容器的数组对象
        table = newTab;                                         // 存入成员变量table

        /**
         * 前面已经进行了扩容，那么剩下的就是将旧容器元素存入新容器‘
         * 这里面应该会涉及到扩容后的再hash，因为扩容后原先造成hash冲突的情况可能有一个新位置可以存储它了，没必要形成链表或树结构
         */

        if (oldTab != null) { // 旧容器存在才进行，不存在则直接返回上面的新容器对象
            for (int j = 0; j < oldCap; ++j) { // 遍历旧容器
                Node<K, V> e;
                if ((e = oldTab[j]) != null) { // 当前元素存在的情况进入分支存入新容器中
                    oldTab[j] = null;
                    /**
                     * 三个分支，分布对应e是 普通数组节点、树节点、链表节点
                     * 1.普通数组节点，重新计算位置
                     * 2.树节点
                     * 3.链表节点
                     */
                    if (e.next == null)
                        newTab[e.hash & (newCap - 1)] = e; // 再hash得到新索引，将元素存入这个新位置
                    else if (e instanceof TreeNode)
                        ((TreeNode<K, V>) e).split(this, newTab, j, oldCap);
                    else {
                        Node<K, V> loHead = null, loTail = null;
                        Node<K, V> hiHead = null, hiTail = null;
                        Node<K, V> next;
                        do {
                            next = e.next;
                            if ((e.hash & oldCap) == 0) {
                                if (loTail == null)
                                    loHead = e;
                                else
                                    loTail.next = e;
                                loTail = e;
                            } else {
                                if (hiTail == null)
                                    hiHead = e;
                                else
                                    hiTail.next = e;
                                hiTail = e;
                            }
                        } while ((e = next) != null);
                        
                        if (loTail != null) {
                            loTail.next = null;
                            newTab[j] = loHead;
                        }
                        if (hiTail != null) {
                            hiTail.next = null;
                            newTab[j + oldCap] = hiHead;
                        }
                    }
                }
            }
        }
        return newTab;
    }


    /**
     * 前提是链表长度 >= 8 才会执行这个方法
     * 进行树化
     */
    final void treeifyBin(Node<K, V>[] tab, int hash) {
        int n, index;
        Node<K, V> e;
        /**
         * 这是树化的第二个条件的由来
         * MIN_TREEIFY_CAPACITY=64 ，数组长度 < 64就进行扩容，换而言之,要使链表转换成树则需要容器数组长度 >= 64
         */
        if (tab == null || (n = tab.length) < MIN_TREEIFY_CAPACITY) {
            resize(); // 如果没有元素或者数组长度小于8就进行扩容
        } else if ((e = tab[index = (n - 1) & hash]) != null) {
            /**
             * 判断过程中得到hash值所对应的Node节点并且节点不为null（就是得到链表的头节点）
             */
            TreeNode<K, V> hd = null, tl = null;// hd就是head，tl就是tail，就是首尾字母
            // 遍历链表节点转换成TreeNode类型，并形成树
            do {
                TreeNode<K, V> p = replacementTreeNode(e, null); // 转换为TreeNode类型
                if (tl == null) {
                    hd = p;// 存储头节点head
                } else {
                    /**
                     * 形成双向链表
                     */
                    p.prev = tl;
                    tl.next = p;
                }
                tl = p;// p成为了新的链表末尾节点
            } while ((e = e.next) != null);// 如果链表部分没有遍历完则继续（e实际就是更具hash得到需要数化的其实节点、也就是说成员属性table会变成Node和TreeNode的一个数组）
            /**
             * 前面只是将链表转换成了双向链表，并没树化
             * 真正进行树化的操作在if内
             */
            if ((tab[index] = hd) != null) {
                /**
                 * 从双向链表头节点开始，进行遍历树化
                 */
                hd.treeify(tab); // 真正进行树化的方法
            }
        }
    }


    public void putAll(Map<? extends K, ? extends V> m) {
        putMapEntries(m, true);
    }


    public V remove(Object key) {
        Node<K, V> e;
        return (e = removeNode(hash(key), key, null, false, true)) == null ? null : e.value;
    }


    final Node<K, V> removeNode(int hash, Object key, Object value, boolean matchValue, boolean movable) {
        Node<K, V>[] tab;
        Node<K, V> p;
        int n, index;
        if ((tab = table) != null && (n = tab.length) > 0 &&
                (p = tab[index = (n - 1) & hash]) != null) {
            Node<K, V> node = null, e;
            K k;
            V v;
            if (p.hash == hash &&
                    ((k = p.key) == key || (key != null && key.equals(k))))
                node = p;
            else if ((e = p.next) != null) {
                if (p instanceof TreeNode)
                    node = ((TreeNode<K, V>) p).getTreeNode(hash, key);
                else {
                    do {
                        if (e.hash == hash &&
                                ((k = e.key) == key ||
                                        (key != null && key.equals(k)))) {
                            node = e;
                            break;
                        }
                        p = e;
                    } while ((e = e.next) != null);
                }
            }
            if (node != null && (!matchValue || (v = node.value) == value ||
                    (value != null && value.equals(v)))) {
                if (node instanceof TreeNode)
                    ((TreeNode<K, V>) node).removeTreeNode(this, tab, movable);
                else if (node == p)
                    tab[index] = node.next;
                else
                    p.next = node.next;
                ++modCount;
                --size;
                afterNodeRemoval(node);
                return node;
            }
        }
        return null;
    }

    public void clear() {
        Node<K, V>[] tab;
        modCount++;
        if ((tab = table) != null && size > 0) {
            size = 0;
            for (int i = 0; i < tab.length; ++i)
                tab[i] = null;
        }
    }


    public boolean containsValue(Object value) {
        Node<K, V>[] tab;
        V v;
        if ((tab = table) != null && size > 0) {
            for (Node<K, V> e : tab) {
                for (; e != null; e = e.next) {
                    if ((v = e.value) == value ||
                            (value != null && value.equals(v)))
                        return true;
                }
            }
        }
        return false;
    }


    public Set<K> keySet() {
        Set<K> ks = keySet;
        if (ks == null) {
            ks = new KeySet();
            keySet = ks;
        }
        return ks;
    }


    @SuppressWarnings("unchecked")
    final <T> T[] prepareArray(T[] a) {
        int size = this.size;
        if (a.length < size) {
            return (T[]) java.lang.reflect.Array
                    .newInstance(a.getClass().getComponentType(), size);
        }
        if (a.length > size) {
            a[size] = null;
        }
        return a;
    }


    <T> T[] keysToArray(T[] a) {
        Object[] r = a;
        Node<K, V>[] tab;
        int idx = 0;
        if (size > 0 && (tab = table) != null) {
            for (Node<K, V> e : tab) {
                for (; e != null; e = e.next) {
                    r[idx++] = e.key;
                }
            }
        }
        return a;
    }


    <T> T[] valuesToArray(T[] a) {
        Object[] r = a;
        Node<K, V>[] tab;
        int idx = 0;
        if (size > 0 && (tab = table) != null) {
            for (Node<K, V> e : tab) {
                for (; e != null; e = e.next) {
                    r[idx++] = e.value;
                }
            }
        }
        return a;
    }

    final class KeySet extends AbstractSet<K> {
        public final int size() {
            return size;
        }

        public final void clear() {
            HashMap.this.clear();
        }

        public final Iterator<K> iterator() {
            return new KeyIterator();
        }

        public final boolean contains(Object o) {
            return containsKey(o);
        }

        public final boolean remove(Object key) {
            return removeNode(hash(key), key, null, false, true) != null;
        }

        public final Spliterator<K> spliterator() {
            return new KeySpliterator<>(HashMap.this, 0, -1, 0, 0);
        }

        public Object[] toArray() {
            return keysToArray(new Object[size]);
        }

        public <T> T[] toArray(T[] a) {
            return keysToArray(prepareArray(a));
        }

        public final void forEach(Consumer<? super K> action) {
            Node<K, V>[] tab;
            if (action == null)
                throw new NullPointerException();
            if (size > 0 && (tab = table) != null) {
                int mc = modCount;
                for (Node<K, V> e : tab) {
                    for (; e != null; e = e.next)
                        action.accept(e.key);
                }
                if (modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }
    }


    public Collection<V> values() {
        Collection<V> vs = values;
        if (vs == null) {
            vs = new Values();
            values = vs;
        }
        return vs;
    }

    final class Values extends AbstractCollection<V> {
        public final int size() {
            return size;
        }

        public final void clear() {
            HashMap.this.clear();
        }

        public final Iterator<V> iterator() {
            return new ValueIterator();
        }

        public final boolean contains(Object o) {
            return containsValue(o);
        }

        public final Spliterator<V> spliterator() {
            return new ValueSpliterator<>(HashMap.this, 0, -1, 0, 0);
        }

        public Object[] toArray() {
            return valuesToArray(new Object[size]);
        }

        public <T> T[] toArray(T[] a) {
            return valuesToArray(prepareArray(a));
        }

        public final void forEach(Consumer<? super V> action) {
            Node<K, V>[] tab;
            if (action == null)
                throw new NullPointerException();
            if (size > 0 && (tab = table) != null) {
                int mc = modCount;
                for (Node<K, V> e : tab) {
                    for (; e != null; e = e.next)
                        action.accept(e.value);
                }
                if (modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }
    }


    public Set<Entry<K, V>> entrySet() {
        Set<Entry<K, V>> es;
        return (es = entrySet) == null ? (entrySet = new EntrySet()) : es;
    }

    final class EntrySet extends AbstractSet<Entry<K, V>> {
        public final int size() {
            return size;
        }

        public final void clear() {
            HashMap.this.clear();
        }

        public final Iterator<Entry<K, V>> iterator() {
            return new EntryIterator();
        }

        public final boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Entry<?, ?> e = (Entry<?, ?>) o;
            Object key = e.getKey();
            Node<K, V> candidate = getNode(hash(key), key);
            return candidate != null && candidate.equals(e);
        }

        public final boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Entry<?, ?> e = (Entry<?, ?>) o;
                Object key = e.getKey();
                Object value = e.getValue();
                return removeNode(hash(key), key, value, true, true) != null;
            }
            return false;
        }

        public final Spliterator<Entry<K, V>> spliterator() {
            return new EntrySpliterator<>(HashMap.this, 0, -1, 0, 0);
        }

        public final void forEach(Consumer<? super Entry<K, V>> action) {
            Node<K, V>[] tab;
            if (action == null)
                throw new NullPointerException();
            if (size > 0 && (tab = table) != null) {
                int mc = modCount;
                for (Node<K, V> e : tab) {
                    for (; e != null; e = e.next)
                        action.accept(e);
                }
                if (modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }
    }

    /**
     * 如果key所对应的value由则返回，否则返回默认值
     */
    @Override
    public V getOrDefault(Object key, V defaultValue) {
        Node<K, V> e;
        return (e = getNode(hash(key), key)) == null ? defaultValue : e.value;
    }

    /**
     * 如果key不存在才put，如果存在则不put
     */
    @Override
    public V putIfAbsent(K key, V value) {
        return putVal(hash(key), key, value, true, true);
    }

    /**
     * 从map中移除key-value
     */
    @Override
    public boolean remove(Object key, Object value) {
        return removeNode(hash(key), key, value, true, true) != null;
    }

    /**
     * 替换旧值，成功则返回true、失败返回false
     */
    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        Node<K, V> e;
        V v;
        if ((e = getNode(hash(key), key)) != null && ((v = e.value) == oldValue || (v != null && v.equals(oldValue)))) {
            e.value = newValue;// 替换成新值
            afterNodeAccess(e);
            return true;
        }
        return false;
    }

    /**
     * 替换旧的value返回旧的value
     */
    @Override
    public V replace(K key, V value) {
        Node<K, V> e;
        if ((e = getNode(hash(key), key)) != null) {
            V oldValue = e.value;
            e.value = value;
            afterNodeAccess(e);
            return oldValue;
        }
        return null;
    }

    /**
     * 当key不存在时，才根据已知的 k v 算出新的v并put。
     * 注意：如果无此key，那么oldVal为null，lambda中涉及到oldVal的计算会报空指针。
     */
    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        if (mappingFunction == null)
            throw new NullPointerException();
        int hash = hash(key);
        Node<K, V>[] tab;
        Node<K, V> first;
        int n, i;
        int binCount = 0;
        TreeNode<K, V> t = null;
        Node<K, V> old = null;
        if (size > threshold || (tab = table) == null || (n = tab.length) == 0)
            n = (tab = resize()).length;
        if ((first = tab[i = (n - 1) & hash]) != null) {
            if (first instanceof TreeNode)
                old = (t = (TreeNode<K, V>) first).getTreeNode(hash, key);
            else {
                Node<K, V> e = first;
                K k;
                do {
                    if (e.hash == hash &&
                            ((k = e.key) == key || (key != null && key.equals(k)))) {
                        old = e;
                        break;
                    }
                    ++binCount;
                } while ((e = e.next) != null);
            }
            V oldValue;
            if (old != null && (oldValue = old.value) != null) {
                afterNodeAccess(old);
                return oldValue;
            }
        }
        int mc = modCount;
        V v = mappingFunction.apply(key);
        if (mc != modCount) {
            throw new ConcurrentModificationException();
        }
        if (v == null) {
            return null;
        } else if (old != null) {
            old.value = v;
            afterNodeAccess(old);
            return v;
        } else if (t != null)
            t.putTreeVal(this, tab, hash, key, v);
        else {
            tab[i] = newNode(hash, key, v, first);
            if (binCount >= TREEIFY_THRESHOLD - 1)
                treeifyBin(tab, hash);
        }
        modCount = mc + 1;
        ++size;
        afterNodeInsertion(true);
        return v;
    }



    /**
     * compute()的补充，key存在时才compute()，避免潜在的空指针情况。其他和compute()相同。
     */
    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (remappingFunction == null)
            throw new NullPointerException();
        Node<K, V> e;
        V oldValue;
        int hash = hash(key);
        if ((e = getNode(hash, key)) != null &&
                (oldValue = e.value) != null) {
            int mc = modCount;
            V v = remappingFunction.apply(key, oldValue);
            if (mc != modCount) {
                throw new ConcurrentModificationException();
            }
            if (v != null) {
                e.value = v;
                afterNodeAccess(e);
                return v;
            } else
                removeNode(hash, key, null, false, true);
        }
        return null;
    }


    /**
     * java8特性
     * 根据已知的 k v 算出新的v并put。
     * 注意：如果无此key，那么oldVal为null，lambda中涉及到oldVal的计算会报空指针。
     */
    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (remappingFunction == null)
            throw new NullPointerException();
        int hash = hash(key);
        Node<K, V>[] tab;
        Node<K, V> first;
        int n, i;
        int binCount = 0;
        TreeNode<K, V> t = null;
        Node<K, V> old = null;
        if (size > threshold || (tab = table) == null ||
                (n = tab.length) == 0)
            n = (tab = resize()).length;
        if ((first = tab[i = (n - 1) & hash]) != null) {
            if (first instanceof TreeNode)
                old = (t = (TreeNode<K, V>) first).getTreeNode(hash, key);
            else {
                Node<K, V> e = first;
                K k;
                do {
                    if (e.hash == hash &&
                            ((k = e.key) == key || (key != null && key.equals(k)))) {
                        old = e;
                        break;
                    }
                    ++binCount;
                } while ((e = e.next) != null);
            }
        }
        V oldValue = (old == null) ? null : old.value;
        int mc = modCount;
        V v = remappingFunction.apply(key, oldValue);
        if (mc != modCount) {
            throw new ConcurrentModificationException();
        }
        if (old != null) {
            if (v != null) {
                old.value = v;
                afterNodeAccess(old);
            } else
                removeNode(hash, key, null, false, true);
        } else if (v != null) {
            if (t != null)
                t.putTreeVal(this, tab, hash, key, v);
            else {
                tab[i] = newNode(hash, key, v, first);
                if (binCount >= TREEIFY_THRESHOLD - 1)
                    treeifyBin(tab, hash);
            }
            modCount = mc + 1;
            ++size;
            afterNodeInsertion(true);
        }
        return v;
    }

    /**
     * java8特性，
     * 如果key存在，则执行lambda表达式，表达式入参为oldVal和newVal(neVal即merge()的第二个参数)。表达式返回最终put的val。
     * 如果key不存在，则直接putnewVal。
     * 会发现这个方法非常的像PutVal因此偷个懒不写注释
     */
    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (value == null || remappingFunction == null)
            throw new NullPointerException(); // 报空指针异常
        int hash = hash(key); // 得到hash值
        Node<K, V>[] tab;
        Node<K, V> first;
        int n, i;
        int binCount = 0;
        TreeNode<K, V> t = null;
        Node<K, V> old = null;
        if (size > threshold || (tab = table) == null || (n = tab.length) == 0)
            n = (tab = resize()).length;
        if ((first = tab[i = (n - 1) & hash]) != null) {
            if (first instanceof TreeNode)
                old = (t = (TreeNode<K, V>) first).getTreeNode(hash, key);
            else {
                Node<K, V> e = first;
                K k;
                do {
                    if (e.hash == hash &&
                            ((k = e.key) == key || (key != null && key.equals(k)))) {
                        old = e;
                        break;
                    }
                    ++binCount;
                } while ((e = e.next) != null);
            }
        }
        if (old != null) {
            V v;
            if (old.value != null) {
                int mc = modCount;
                v = remappingFunction.apply(old.value, value);
                if (mc != modCount) {
                    throw new ConcurrentModificationException();
                }
            } else {
                v = value;
            }
            if (v != null) {
                old.value = v;
                afterNodeAccess(old);
            } else
                removeNode(hash, key, null, false, true);
            return v;
        } else {
            if (t != null)
                t.putTreeVal(this, tab, hash, key, value);
            else {
                tab[i] = newNode(hash, key, value, first);
                if (binCount >= TREEIFY_THRESHOLD - 1)
                    treeifyBin(tab, hash);
            }
            ++modCount;
            ++size;
            afterNodeInsertion(true);
            return value;
        }
    }

    /**
     * java8的遍历，因为map是key，value组成因此使用BiConsumer接口
     */
    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        Node<K, V>[] tab;
        if (action == null)
            throw new NullPointerException();
        if (size > 0 && (tab = table) != null) {
            int mc = modCount;
            for (Node<K, V> e : tab) {
                for (; e != null; e = e.next)
                    action.accept(e.key, e.value); // 由lambda表达式来实现
            }
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
    }

    /**
     * java8处理所有value
     */
    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Node<K, V>[] tab;
        if (function == null)
            throw new NullPointerException();
        if (size > 0 && (tab = table) != null) {
            int mc = modCount;
            for (Node<K, V> e : tab) { // 增强for遍历
                for (; e != null; e = e.next) { // 链表结构的遍历处理
                    e.value = function.apply(e.key, e.value);//
                }
            }
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
    }


    /**
     * 浅拷贝
     */
    @SuppressWarnings("unchecked")
    @Override
    public Object clone() {
        HashMap<K, V> result;
        try {
            result = (HashMap<K, V>) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
        result.reinitialize();
        result.putMapEntries(this, false);
        return result;
    }

    /**
     * 返回加载因子
     */
    final float loadFactor() {
        return loadFactor;
    }

    /**
     * HashMap容量
     */
    final int capacity() {
        return (table != null) ? table.length : (threshold > 0) ? threshold : DEFAULT_INITIAL_CAPACITY;
    }

    /**
     * 返回一个节点
     */
    Node<K, V> newNode(int hash, K key, V value, Node<K, V> next) {
        return new Node<>(hash, key, value, next);
    }


    /**
     * 替换节点
     */
    Node<K, V> replacementNode(Node<K, V> p, Node<K, V> next) {
        return new Node<>(p.hash, p.key, p.value, next);
    }


    /**
     * 封装成TreeNode
     */
    TreeNode<K, V> newTreeNode(int hash, K key, V value, Node<K, V> next) {
        return new TreeNode<>(hash, key, value, next);
    }

    /**
     * Node节点转成树节点（ TreeNode ）
     */
    TreeNode<K, V> replacementTreeNode(Node<K, V> p, Node<K, V> next) {
        return new TreeNode<>(p.hash, p.key, p.value, next);
    }

    /**
     * 重新初始化
     */
    void reinitialize() {
        table = null;
        entrySet = null;
        keySet = null;
        values = null;
        modCount = 0;
        threshold = 0;
        size = 0;
    }
    /**
     * 序列化到对象输出流
     */
    @java.io.Serial
    private void writeObject(java.io.ObjectOutputStream s) throws IOException {
        int buckets = capacity();
        s.defaultWriteObject();
        s.writeInt(buckets);
        s.writeInt(size);
        internalWriteEntries(s);
    }


    /**
     * 反序列化到输入流s
     */
    @java.io.Serial
    private void readObject(java.io.ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        reinitialize();
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new InvalidObjectException("Illegal load factor: " + loadFactor);
        s.readInt();
        int mappings = s.readInt();
        if (mappings < 0)
            throw new InvalidObjectException("Illegal mappings count: " + mappings);
        else if (mappings > 0) {
            float lf = Math.min(Math.max(0.25f, loadFactor), 4.0f);
            float fc = (float) mappings / lf + 1.0f;
            int cap = ((fc < DEFAULT_INITIAL_CAPACITY) ?
                    DEFAULT_INITIAL_CAPACITY :
                    (fc >= MAXIMUM_CAPACITY) ?
                            MAXIMUM_CAPACITY :
                            tableSizeFor((int) fc));
            float ft = (float) cap * lf;
            threshold = ((cap < MAXIMUM_CAPACITY && ft < MAXIMUM_CAPACITY) ?
                    (int) ft : Integer.MAX_VALUE);
            SharedSecrets.getJavaObjectInputStreamAccess().checkArray(s, Entry[].class, cap);
            @SuppressWarnings({"rawtypes", "unchecked"})
            Node<K, V>[] tab = (Node<K, V>[]) new Node[cap];
            table = tab;

            for (int i = 0; i < mappings; i++) {
                @SuppressWarnings("unchecked")
                K key = (K) s.readObject();
                @SuppressWarnings("unchecked")
                V value = (V) s.readObject();
                putVal(hash(key), key, value, false, false);
            }
        }
    }


    abstract class HashIterator {
        Node<K, V> next;        // next entry to return
        Node<K, V> current;     // current entry
        int expectedModCount;  // for fast-fail
        int index;             // current slot

        HashIterator() {
            expectedModCount = modCount;
            Node<K, V>[] t = table;
            current = next = null;
            index = 0;
            if (t != null && size > 0) { // advance to first entry
                do {
                } while (index < t.length && (next = t[index++]) == null);
            }
        }

        public final boolean hasNext() {
            return next != null;
        }

        final Node<K, V> nextNode() {
            Node<K, V>[] t;
            Node<K, V> e = next;
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            if (e == null)
                throw new NoSuchElementException();
            if ((next = (current = e).next) == null && (t = table) != null) {
                do {
                } while (index < t.length && (next = t[index++]) == null);
            }
            return e;
        }

        public final void remove() {
            Node<K, V> p = current;
            if (p == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            current = null;
            removeNode(p.hash, p.key, null, false, false);
            expectedModCount = modCount;
        }
    }

    final class KeyIterator extends HashIterator implements Iterator<K> {
        public final K next() {
            return nextNode().key;
        }
    }

    final class ValueIterator extends HashIterator implements Iterator<V> {
        public final V next() {
            return nextNode().value;
        }
    }

    final class EntryIterator extends HashIterator implements Iterator<Entry<K, V>> {
        public final Entry<K, V> next() {
            return nextNode();
        }
    }


    static class HashMapSpliterator<K, V> {
        final HashMap<K, V> map;
        Node<K, V> current;          // current node
        int index;                  // current index, modified on advance/split
        int fence;                  // one past last index
        int est;                    // size estimate
        int expectedModCount;       // for comodification checks

        HashMapSpliterator(HashMap<K, V> m, int origin,
                           int fence, int est,
                           int expectedModCount) {
            this.map = m;
            this.index = origin;
            this.fence = fence;
            this.est = est;
            this.expectedModCount = expectedModCount;
        }

        final int getFence() { // initialize fence and size on first use
            int hi;
            if ((hi = fence) < 0) {
                HashMap<K, V> m = map;
                est = m.size;
                expectedModCount = m.modCount;
                Node<K, V>[] tab = m.table;
                hi = fence = (tab == null) ? 0 : tab.length;
            }
            return hi;
        }

        public final long estimateSize() {
            getFence(); // force init
            return (long) est;
        }
    }

    static final class KeySpliterator<K, V> extends HashMapSpliterator<K, V> implements Spliterator<K> {
        KeySpliterator(HashMap<K, V> m, int origin, int fence, int est, int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        public KeySpliterator<K, V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid || current != null) ? null :
                    new KeySpliterator<>(map, lo, index = mid, est >>>= 1,
                            expectedModCount);
        }

        public void forEachRemaining(Consumer<? super K> action) {
            int i, hi, mc;
            if (action == null)
                throw new NullPointerException();
            HashMap<K, V> m = map;
            Node<K, V>[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = (tab == null) ? 0 : tab.length;
            } else
                mc = expectedModCount;
            if (tab != null && tab.length >= hi &&
                    (i = index) >= 0 && (i < (index = hi) || current != null)) {
                Node<K, V> p = current;
                current = null;
                do {
                    if (p == null)
                        p = tab[i++];
                    else {
                        action.accept(p.key);
                        p = p.next;
                    }
                } while (p != null || i < hi);
                if (m.modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super K> action) {
            int hi;
            if (action == null)
                throw new NullPointerException();
            Node<K, V>[] tab = map.table;
            if (tab != null && tab.length >= (hi = getFence()) && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null)
                        current = tab[index++];
                    else {
                        K k = current.key;
                        current = current.next;
                        action.accept(k);
                        if (map.modCount != expectedModCount)
                            throw new ConcurrentModificationException();
                        return true;
                    }
                }
            }
            return false;
        }

        public int characteristics() {
            return (fence < 0 || est == map.size ? Spliterator.SIZED : 0) |
                    Spliterator.DISTINCT;
        }
    }

    static final class ValueSpliterator<K, V> extends HashMapSpliterator<K, V> implements Spliterator<V> {
        ValueSpliterator(HashMap<K, V> m, int origin, int fence, int est,
                         int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        public ValueSpliterator<K, V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid || current != null) ? null :
                    new ValueSpliterator<>(map, lo, index = mid, est >>>= 1,
                            expectedModCount);
        }

        public void forEachRemaining(Consumer<? super V> action) {
            int i, hi, mc;
            if (action == null)
                throw new NullPointerException();
            HashMap<K, V> m = map;
            Node<K, V>[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = (tab == null) ? 0 : tab.length;
            } else
                mc = expectedModCount;
            if (tab != null && tab.length >= hi &&
                    (i = index) >= 0 && (i < (index = hi) || current != null)) {
                Node<K, V> p = current;
                current = null;
                do {
                    if (p == null)
                        p = tab[i++];
                    else {
                        action.accept(p.value);
                        p = p.next;
                    }
                } while (p != null || i < hi);
                if (m.modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super V> action) {
            int hi;
            if (action == null)
                throw new NullPointerException();
            Node<K, V>[] tab = map.table;
            if (tab != null && tab.length >= (hi = getFence()) && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null)
                        current = tab[index++];
                    else {
                        V v = current.value;
                        current = current.next;
                        action.accept(v);
                        if (map.modCount != expectedModCount)
                            throw new ConcurrentModificationException();
                        return true;
                    }
                }
            }
            return false;
        }

        public int characteristics() {
            return (fence < 0 || est == map.size ? Spliterator.SIZED : 0);
        }
    }

    static final class EntrySpliterator<K, V> extends HashMapSpliterator<K, V> implements Spliterator<Entry<K, V>> {
        EntrySpliterator(HashMap<K, V> m, int origin, int fence, int est,
                         int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        public EntrySpliterator<K, V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid || current != null) ? null :
                    new EntrySpliterator<>(map, lo, index = mid, est >>>= 1,
                            expectedModCount);
        }

        public void forEachRemaining(Consumer<? super Entry<K, V>> action) {
            int i, hi, mc;
            if (action == null)
                throw new NullPointerException();
            HashMap<K, V> m = map;
            Node<K, V>[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = (tab == null) ? 0 : tab.length;
            } else
                mc = expectedModCount;
            if (tab != null && tab.length >= hi &&
                    (i = index) >= 0 && (i < (index = hi) || current != null)) {
                Node<K, V> p = current;
                current = null;
                do {
                    if (p == null)
                        p = tab[i++];
                    else {
                        action.accept(p);
                        p = p.next;
                    }
                } while (p != null || i < hi);
                if (m.modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super Entry<K, V>> action) {
            int hi;
            if (action == null)
                throw new NullPointerException();
            Node<K, V>[] tab = map.table;
            if (tab != null && tab.length >= (hi = getFence()) && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null)
                        current = tab[index++];
                    else {
                        Node<K, V> e = current;
                        current = current.next;
                        action.accept(e);
                        if (map.modCount != expectedModCount)
                            throw new ConcurrentModificationException();
                        return true;
                    }
                }
            }
            return false;
        }

        public int characteristics() {
            return (fence < 0 || est == map.size ? Spliterator.SIZED : 0) |
                    Spliterator.DISTINCT;
        }
    }



    /**
     * 允许LinkedHashMap post-actions回调
     */
    void afterNodeAccess(Node<K, V> p) {
    }

    void afterNodeInsertion(boolean evict) {
    }

    void afterNodeRemoval(Node<K, V> p) {
    }

    void internalWriteEntries(java.io.ObjectOutputStream s) throws IOException {
        Node<K, V>[] tab;
        if (size > 0 && (tab = table) != null) {
            for (Node<K, V> e : tab) {
                for (; e != null; e = e.next) {
                    s.writeObject(e.key);
                    s.writeObject(e.value);
                }
            }
        }
    }

    static final class TreeNode<K, V> extends LinkedHashMap.Entry<K, V> {
        TreeNode<K, V> parent;  // red-black tree links
        TreeNode<K, V> left;
        TreeNode<K, V> right;
        TreeNode<K, V> prev;    // needed to unlink next upon deletion
        boolean red;

        TreeNode(int hash, K key, V val, Node<K, V> next) {
            super(hash, key, val, next);
        }


        /**
         * 返回根节点
         */
        final TreeNode<K, V> root() {
            for (TreeNode<K, V> r = this, p; ; ) {
                // 一直向上找parent，知道找到根节点将根节点返回
                if ((p = r.parent) == null)
                    return r;
                r = p;
            }
        }


        /**
         *
         */
        static <K, V> void moveRootToFront(Node<K, V>[] tab, TreeNode<K, V> root) {
            int n;
            if (root != null && tab != null && (n = tab.length) > 0) {
                int index = (n - 1) & root.hash;
                TreeNode<K, V> first = (TreeNode<K, V>) tab[index];
                if (root != first) {
                    Node<K, V> rn;
                    tab[index] = root;
                    TreeNode<K, V> rp = root.prev;
                    if ((rn = root.next) != null)
                        ((TreeNode<K, V>) rn).prev = rp;
                    if (rp != null)
                        rp.next = rn;
                    if (first != null)
                        first.prev = root;
                    root.next = first;
                    root.prev = null;
                }
                assert checkInvariants(root);
            }
        }


        /**
         * 查找h,k所代表的Node节点，有则返回该节点，没有就返回null
         */
        final TreeNode<K, V> find(int h, Object k, java.lang.Class<?> kc) {
            TreeNode<K, V> p = this;
            do {
                int ph, dir;
                K pk;
                TreeNode<K, V> pl = p.left, pr = p.right, q;// 得到左节点pl，右节点pr
                if ((ph = p.hash) > h)  // 当前节点的hash值如果大于h则在左边找
                    p = pl;             // 将左节点设置为当前节点
                else if (ph < h)        // 说明需要向右边找
                    p = pr;             // 将右节点设置为当前节点
                else if ((pk = p.key) == k || (k != null && k.equals(pk))) // 当前节点的key和传入的k是否相等或者equals相等
                    return p;                                              // 相等说明找到了，直接返回该节点
                else if (pl == null)// 左节点等于null，则向右找（开始省略部分注释，类比前面即可）
                    p = pr;
                else if (pr == null)// 右节点为null，则向左找
                    p = pl;
                else if ((kc != null || (kc = comparableClassFor(k)) != null) && (dir = compareComparables(kc, k, pk)) != 0)
                    p = (dir < 0) ? pl : pr;
                else if ((q = pr.find(h, k, kc)) != null) // 递归查找右子树，返回查找到的结果
                    return q;
                else
                    p = pl; // 默认从左边开始遍历
            } while (p != null);
            return null;// 没找到则返回null
        }

        /**
         * 从根节点开始找，找到k所对应的节点并返回
         */
        final TreeNode<K, V> getTreeNode(int h, Object k) {
            return ((parent != null) ? root() : this).find(h, k, null);
        }


        /**
         * a的hash <= b 则返回-1，否则返回1，如果其中一个不为null则返回0
         */
        static int tieBreakOrder(Object a, Object b) {
            int d;
            if (a == null || b == null || (d = a.getClass().getName().compareTo(b.getClass().getName())) == 0) {
                d = (System.identityHashCode(a) <= System.identityHashCode(b) ? -1 : 1);
            }
            return d;
        }


        /**
         * 树化的真正方法，前面需要形成双向链表
         */
        final void treeify(Node<K, V>[] tab) {
            TreeNode<K, V> root = null; // 存储根节点用

            // 遍历双向链表这里的this就是双向链表头
            for (TreeNode<K, V> x = this, next; x != null; x = next) {
                next = (TreeNode<K, V>) x.next; // 得到下一个节点
                x.left = x.right = null;        // 默认将当前树节点的左右树节点引用置null

                if (root == null) {             // 第一次才执行，为了得到树的根节点（第一个节点）
                    x.parent = null;            // 因为根节点没有父节点，因此将根节点的父引用置null
                    x.red = false;              // 不是红树节点，应该red的true和false就是表示左右
                    root = x;                   // 存储根节点
                } else {
                    K k = x.key;                // 得到当前节点的key
                    int h = x.hash;             // 得到当前节点的hash
                    java.lang.Class<?> kc = null;         //
                    /**
                     * 将当前节点x加到根节点root上
                     */
                    for (TreeNode<K, V> p = root; ; ) {
                        int dir, ph;            // 定义路径、根节点的hash值
                        K pk = p.key;           // 得到根节点的key，后面p就是子树的根节点，也就是相对临时根节点
                        /**
                         * 根结点的hash是否大于当前节点x的hash值，
                         *      是则放在右边也就是dir=-1
                         *      不是则放在左边也就是dir=1
                         * 如果当前节点p和root节点的hash值相等（则再一次比较，要找到新的root节点）
                         *
                         * 下面这个分支只是得到当前节点x放在左子树还是右子树，后面的balanceInsertion方法才是真正将树节点挂到root上
                         */
                        if ((ph = p.hash) > h)  // h是
                            dir = -1;
                        else if (ph < h)
                            dir = 1;
                        else if ((kc == null && (kc = comparableClassFor(k)) == null) || (dir = compareComparables(kc, k, pk)) == 0) {
                            // hashMap的key是允许为null的因此这里就是k=null且kc=null这种特殊情况，
                            dir = tieBreakOrder(k, pk);// 当前节点x的hash值 k <= pk 则返回-1，否则返回1（意味着默认放在root左边的子树）
                        }

                        /**
                         * 前面得到存放当前节点x的位置，这里则是将节点x挂上根节点root，如果dir <=0放左边、dir>0则放右边）
                         */
                        TreeNode<K, V> xp = p;
                        /**
                         * 根据前面计算得到x节点要加入root树的方向，
                         * 再进一步判断是否有子树，有则不能直接添加
                         *      一、如果存在子树，p就成了新的根节点，上面的h就是x节点hash值，然后比较新的根节点p和x的hash值，
                         *            直到x的hash值h找到了一个合适位置，进入if进行连接树节点，连上了x节点就break到外层循环（进行下一个节点x的操作）
                         *      二、知道不存在子树，则说明x的位置就在当前子树的左边或者右边，将其连上即可
                         */
                        if ((p = (dir <= 0) ? p.left : p.right) == null) {
                            x.parent = xp;  // 将当前节点x的父节点引用指向根节点root
                            if (dir <= 0)
                                xp.left = x; // 将根节点的左树节点引用指向当前节点x
                            else
                                xp.right = x;// 将根节点的右树节点引用指向当前节点x
                            /**
                             * 虽然是将节点加入了，但有可能会出现极端情况，
                             * 这颗树只有左子树，或者只有右子树，这样查询时间就是O(1)，没有起到树的特性(查询快的特点)
                             * 因此下面的操作就防止极端情况，重新将树平衡化
                             */
                            root = balanceInsertion(root, x);// 进行平衡化得到新的根节点
                            break;
                        }
                    }
                }
            }
            moveRootToFront(tab, root);
        }


        final Node<K, V> untreeify(HashMap<K, V> map) {
            Node<K, V> hd = null, tl = null;
            for (Node<K, V> q = this; q != null; q = q.next) {
                Node<K, V> p = map.replacementNode(q, null);
                if (tl == null)
                    hd = p;
                else
                    tl.next = p;
                tl = p;
            }
            return hd;
        }

        /**
         * 添加树节点
         */
        final TreeNode<K, V> putTreeVal(HashMap<K, V> map, Node<K, V>[] tab, int h, K k, V v) {
            java.lang.Class<?> kc = null;
            boolean searched = false;
            // 得到根节点
            TreeNode<K, V> root = (parent != null) ? root() : this;

            for (TreeNode<K, V> p = root; ; ) {
                int dir, ph;
                K pk;
                if ((ph = p.hash) > h) // 根节点的hash值 > 新节点的hash值，则放在left子树，也就是dir=-1
                    dir = -1;
                else if (ph < h)// 根节点hash<新节点的hash值，将这个新节点放在right子树，也就是dir=1
                    dir = 1;
                else if ((pk = p.key) == k || (k != null && k.equals(pk)))// 如果key相同则直接返回根节点
                    return p;
                else if ((kc == null && (kc = comparableClassFor(k)) == null) || (dir = compareComparables(kc, k, pk)) == 0) {// 循环第一次肯定要进来

                    if (!searched) { // 如果没有搜索过，则进入代码进行搜索
                        TreeNode<K, V> q, ch;
                        searched = true;// 标识搜索过了
                        // 调用find()进行搜索节点
                        if (((ch = p.left) != null && (q = ch.find(h, k, kc)) != null) || ((ch = p.right) != null && (q = ch.find(h, k, kc)) != null))
                            return q;
                    }
                    dir = tieBreakOrder(k, pk);
                }

                TreeNode<K, V> xp = p;
                if ((p = (dir <= 0) ? p.left : p.right) == null) {
                    Node<K, V> xpn = xp.next;
                    TreeNode<K, V> x = map.newTreeNode(h, k, v, xpn);
                    if (dir <= 0)
                        xp.left = x;
                    else
                        xp.right = x;
                    xp.next = x;
                    x.parent = x.prev = xp;
                    if (xpn != null)
                        ((TreeNode<K, V>) xpn).prev = x;
                    moveRootToFront(tab, balanceInsertion(root, x));
                    return null;
                }
            }
        }


        final void removeTreeNode(HashMap<K, V> map, Node<K, V>[] tab, boolean movable) {
            int n;
            if (tab == null || (n = tab.length) == 0)
                return;
            int index = (n - 1) & hash;
            TreeNode<K, V> first = (TreeNode<K, V>) tab[index], root = first, rl;
            TreeNode<K, V> succ = (TreeNode<K, V>) next, pred = prev;
            if (pred == null)
                tab[index] = first = succ;
            else
                pred.next = succ;
            if (succ != null)
                succ.prev = pred;
            if (first == null)
                return;
            if (root.parent != null)
                root = root.root();
            if (root == null
                    || (movable
                    && (root.right == null
                    || (rl = root.left) == null
                    || rl.left == null))) {
                tab[index] = first.untreeify(map);  // too small
                return;
            }
            TreeNode<K, V> p = this, pl = left, pr = right, replacement;
            if (pl != null && pr != null) {
                TreeNode<K, V> s = pr, sl;
                while ((sl = s.left) != null) // find successor
                    s = sl;
                boolean c = s.red;
                s.red = p.red;
                p.red = c; // swap colors
                TreeNode<K, V> sr = s.right;
                TreeNode<K, V> pp = p.parent;
                if (s == pr) { // p was s's direct parent
                    p.parent = s;
                    s.right = p;
                } else {
                    TreeNode<K, V> sp = s.parent;
                    if ((p.parent = sp) != null) {
                        if (s == sp.left)
                            sp.left = p;
                        else
                            sp.right = p;
                    }
                    if ((s.right = pr) != null)
                        pr.parent = s;
                }
                p.left = null;
                if ((p.right = sr) != null)
                    sr.parent = p;
                if ((s.left = pl) != null)
                    pl.parent = s;
                if ((s.parent = pp) == null)
                    root = s;
                else if (p == pp.left)
                    pp.left = s;
                else
                    pp.right = s;
                if (sr != null)
                    replacement = sr;
                else
                    replacement = p;
            } else if (pl != null)
                replacement = pl;
            else if (pr != null)
                replacement = pr;
            else
                replacement = p;
            if (replacement != p) {
                TreeNode<K, V> pp = replacement.parent = p.parent;
                if (pp == null)
                    (root = replacement).red = false;
                else if (p == pp.left)
                    pp.left = replacement;
                else
                    pp.right = replacement;
                p.left = p.right = p.parent = null;
            }

            TreeNode<K, V> r = p.red ? root : balanceDeletion(root, replacement);

            if (replacement == p) {  // detach
                TreeNode<K, V> pp = p.parent;
                p.parent = null;
                if (pp != null) {
                    if (p == pp.left)
                        pp.left = null;
                    else if (p == pp.right)
                        pp.right = null;
                }
            }
            if (movable)
                moveRootToFront(tab, r);
        }


        /**
         * 将树箱中的节点拆分为较高和较低的树箱，如果现在太小，则取消树化。 仅从调整大小调用；
         * 取出index处的Node，相当于去除了根节点，那么需要一个新的根节点，下面的操作应该就是这么回事
         */
        final void split(HashMap<K, V> map, Node<K, V>[] tab, int index, int bit) {
            TreeNode<K, V> b = this; // 当前树节点
            TreeNode<K, V> loHead = null, loTail = null; // 低头节点和低尾节点
            TreeNode<K, V> hiHead = null, hiTail = null; // 高头节点和低尾节点
            int lc = 0, hc = 0;
            /**
             * 遍历树
             */
            for (TreeNode<K, V> e = b, next; e != null; e = next) {
                next = (TreeNode<K, V>) e.next;// 得到下一个树节点
                e.next = null;  // 将当前树节点的下一个节点置null
                if ((e.hash & bit) == 0) { // 如果旧的索引位置为0
                    if ((e.prev = loTail) == null)
                        loHead = e; // 保存当前节点，意思是上一个头节点
                    else
                        loTail.next = e;// 上一个尾节点
                    loTail = e;         // 保存尾节点
                    ++lc; // 计数+1
                } else {
                    if ((e.prev = hiTail) == null)
                        hiHead = e;
                    else
                        hiTail.next = e;
                    hiTail = e;
                    ++hc; // 计数+1
                }
            }

            if (loHead != null) {
                if (lc <= UNTREEIFY_THRESHOLD)
                    tab[index] = loHead.untreeify(map);
                else {
                    tab[index] = loHead;
                    if (hiHead != null) // (else is already treeified)
                        loHead.treeify(tab);
                }
            }
            if (hiHead != null) {
                if (hc <= UNTREEIFY_THRESHOLD)
                    tab[index + bit] = hiHead.untreeify(map);
                else {
                    tab[index + bit] = hiHead;
                    if (loHead != null)
                        hiHead.treeify(tab);
                }
            }
        }


        /**
         * 将p子树左旋
         */
        static <K, V> TreeNode<K, V> rotateLeft(TreeNode<K, V> root, TreeNode<K, V> p) {
            TreeNode<K, V> r, pp, rl;
            if (p != null && (r = p.right) != null) { // 如果p是满节点的一颗子树，左右都有节点则进入if

                if ((rl = p.right = r.left) != null)  //
                    rl.parent = p; // 将p的右节点的左节点的parent指向p，后面肯定也需要将p的引用指向这个节点
                if ((pp = r.parent = p.parent) == null)// p的父节点如果为null说明是顶级的root节点
                    (root = r).red = false; // 到了顶级的根节点，根节点不需要平衡化了
                else if (pp.left == p) //
                    pp.left = r;// 更换节点，将右节点挂在p的位置上
                else
                    pp.right = r;
                r.left = p;
                p.parent = r;
            }
            return root;
        }

        static <K, V> TreeNode<K, V> rotateRight(TreeNode<K, V> root, TreeNode<K, V> p) {
            TreeNode<K, V> l, pp, lr;
            if (p != null && (l = p.left) != null) {
                if ((lr = p.left = l.right) != null)
                    lr.parent = p;
                if ((pp = l.parent = p.parent) == null)
                    (root = l).red = false;
                else if (pp.right == p)
                    pp.right = l;
                else
                    pp.left = l;
                l.right = p;
                p.parent = l;
            }
            return root;
        }

        /**
         * 得到平衡二叉树，防止极端情况（只存在 左或右 子树），因此根节很可能被替换，形成一个新的树
         * 这个x就是从链表中取出的那个节点x，而执行这个方法则说明x已经加入树了，需要进行平衡化，也就是后面通过xp表示x的父节点
         */
        static <K, V> TreeNode<K, V> balanceInsertion(TreeNode<K, V> root, TreeNode<K, V> x) {
            x.red = true;// 当前节点x需要进行平衡化
            for (TreeNode<K, V> xp, xpp, xppl, xppr; ; ) {


                if ((xp = x.parent) == null) {
                    /**
                     * 这种情况应该不存在，因为只有添加了一节点才会执行这个平衡化方法，
                     * 也就意味着至少有两个节点，x不可能是根节点，
                     * 所以一般情况x.parent是肯定不会为null的
                     */
                    x.red = false;  // 将当前节点取消为红黑树为节点
                    return x;       // 当前节点x就是根节点直接返回
                }
                else if (!xp.red || (xpp = xp.parent) == null) {
                    /**
                     * 这种情况说明只有两层，没必要平衡化，直接返回即可
                     */
                    return root;
                }
                /**
                 *
                 * 下面的分支就是左子树和右子树两种情况（  xp是当前加入的x节点的父节点，xpp则是父父节点。  ）
                 *
                 *  一、 父父节点的左节点 如果等于 父节点，说明x节点在左子树上，如果父父节点的右子树没有满则说明没有平衡化
                 */
                if (xp == (xppl = xpp.left)) {

                    /**
                     * 父父节点的右子树节点不为null 并且 是平衡树则执行。
                     * 从这里推断出red就是标识是否是平衡树的意思（红黑有正负的意思，也就是大和小的意思，类比左右子树的含义）
                     * red为true的含义是已经平衡化过了，false则是当前节点没有平衡化过
                     */
                    if ((xppr = xpp.right) != null && xppr.red) {
                        /**
                         * xppr.red=true且xpp存在右子树说明父父节点需要进行平衡化，暂停父父节点两边子树的平衡化
                         */
                        xppr.red = false; // 父父节点的右子树标识为需要平衡化
                        xp.red = false;   // 父节点xp也标识为需要平衡化（相当于父父节点的左子树度标记为false不需要平衡化、或者说暂停平衡化）
                        xpp.red = true;   // 父父节点标识为不需要平衡化
                        x = xpp;          // 将父节点设置为当前节点x，目的就是将父父节点进行平衡化
                    } else {
                        /**
                         * 父父节点的右节点为null，父父节点失去了平衡。
                         * 红黑树的一个性质就是从根节点出发到达叶子null节点经过的黑色节点个数相同
                         * （因为加入x导致父父节点平衡因子变成了2，不满足平衡因子为-1，0，1，平衡因子就是左边经过黑数节点的个数减去右边经过黑节点的个数）
                         */
                        if (x == xp.right) {
                            root = rotateLeft(root, x = xp);// 进行左旋
                            /**
                             * 这里的x可能是一棵树，因此还需要再操作
                             */
                            xpp = (xp = x.parent) == null ? null : xp.parent;
                        }
                        if (xp != null) {
                            xp.red = false;
                            if (xpp != null) {
                                xpp.red = true;
                                root = rotateRight(root, xpp);
                            }
                        }
                    }
                } else {
                    if (xppl != null && xppl.red) {
                        xppl.red = false;
                        xp.red = false;
                        xpp.red = true;
                        x = xpp;
                    } else {
                        if (x == xp.left) {
                            root = rotateRight(root, x = xp);
                            xpp = (xp = x.parent) == null ? null : xp.parent;
                        }
                        if (xp != null) {
                            xp.red = false;
                            if (xpp != null) {
                                xpp.red = true;
                                root = rotateLeft(root, xpp);
                            }
                        }
                    }
                }
            }
        }

        /**
         *
         */
        static <K, V> TreeNode<K, V> balanceDeletion(TreeNode<K, V> root, TreeNode<K, V> x) {
            for (TreeNode<K, V> xp, xpl, xpr; ; ) {
                if (x == null || x == root)
                    return root;
                else if ((xp = x.parent) == null) {
                    x.red = false;
                    return x;
                } else if (x.red) {
                    x.red = false;
                    return root;
                } else if ((xpl = xp.left) == x) {
                    if ((xpr = xp.right) != null && xpr.red) {
                        xpr.red = false;
                        xp.red = true;
                        root = rotateLeft(root, xp);
                        xpr = (xp = x.parent) == null ? null : xp.right;
                    }
                    if (xpr == null)
                        x = xp;
                    else {
                        TreeNode<K, V> sl = xpr.left, sr = xpr.right;
                        if ((sr == null || !sr.red) &&
                                (sl == null || !sl.red)) {
                            xpr.red = true;
                            x = xp;
                        } else {
                            if (sr == null || !sr.red) {
                                if (sl != null)
                                    sl.red = false;
                                xpr.red = true;
                                root = rotateRight(root, xpr);
                                xpr = (xp = x.parent) == null ?
                                        null : xp.right;
                            }
                            if (xpr != null) {
                                xpr.red = (xp == null) ? false : xp.red;
                                if ((sr = xpr.right) != null)
                                    sr.red = false;
                            }
                            if (xp != null) {
                                xp.red = false;
                                root = rotateLeft(root, xp);
                            }
                            x = root;
                        }
                    }
                } else {
                    if (xpl != null && xpl.red) {
                        xpl.red = false;
                        xp.red = true;
                        root = rotateRight(root, xp);
                        xpl = (xp = x.parent) == null ? null : xp.left;
                    }
                    if (xpl == null)
                        x = xp;
                    else {
                        TreeNode<K, V> sl = xpl.left, sr = xpl.right;
                        if ((sl == null || !sl.red) &&
                                (sr == null || !sr.red)) {
                            xpl.red = true;
                            x = xp;
                        } else {
                            if (sl == null || !sl.red) {
                                if (sr != null)
                                    sr.red = false;
                                xpl.red = true;
                                root = rotateLeft(root, xpl);
                                xpl = (xp = x.parent) == null ?
                                        null : xp.left;
                            }
                            if (xpl != null) {
                                xpl.red = (xp == null) ? false : xp.red;
                                if ((sl = xpl.left) != null)
                                    sl.red = false;
                            }
                            if (xp != null) {
                                xp.red = false;
                                root = rotateRight(root, xp);
                            }
                            x = root;
                        }
                    }
                }
            }
        }


        static <K, V> boolean checkInvariants(TreeNode<K, V> t) {
            TreeNode<K, V> tp = t.parent, tl = t.left, tr = t.right,
                    tb = t.prev, tn = (TreeNode<K, V>) t.next;
            if (tb != null && tb.next != t)
                return false;
            if (tn != null && tn.prev != t)
                return false;
            if (tp != null && t != tp.left && t != tp.right)
                return false;
            if (tl != null && (tl.parent != t || tl.hash > t.hash))
                return false;
            if (tr != null && (tr.parent != t || tr.hash < t.hash))
                return false;
            if (t.red && tl != null && tl.red && tr != null && tr.red)
                return false;
            if (tl != null && !checkInvariants(tl))
                return false;
            if (tr != null && !checkInvariants(tr))
                return false;
            return true;
        }
    }

}
