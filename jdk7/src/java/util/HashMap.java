package java.util;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.Serializable;
import java.util.*;

public class HashMap<K, V> extends AbstractMap<K, V> implements Map<K, V>, Cloneable, Serializable {
    private static final long serialVersionUID = 362498820763181265L;
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // 默认容量
    static final int MAXIMUM_CAPACITY = 1 << 30;        // 最大容量
    static final float DEFAULT_LOAD_FACTOR = 0.75f;     // 默认加载因子
    static final Entry<?, ?>[] EMPTY_TABLE = {};        // 空数组常量
    transient int size;                                 // 容器大小
    int threshold;                                      // 扩容阈值
    final float loadFactor;                             // 加载因子
    transient int modCount;                             // 并发修改计数
    static final int ALTERNATIVE_HASHING_THRESHOLD_DEFAULT = Integer.MAX_VALUE;// 默认的最大扩容阈值2的31次方
    transient int hashSeed = 0;// 为0则禁用hash
    transient Entry<K, V>[] table = (Entry<K, V>[]) EMPTY_TABLE;// 空数组
    private transient Set<Map.Entry<K, V>> entrySet = null; // entry集合
    transient volatile Set<K>        keySet = null;         // key的集合
    transient volatile Collection<V> values = null;         // value的集合


    /**
     * 用于获取命令行参数设定的扩容阈值
     */
    private static class Holder {
        static final int ALTERNATIVE_HASHING_THRESHOLD;

        static {
            String altThreshold = java.security.AccessController.doPrivileged(new sun.security.action.GetPropertyAction("jdk.map.althashing.threshold"));
            int threshold;
            try {
                threshold = (null != altThreshold) ? Integer.parseInt(altThreshold) : ALTERNATIVE_HASHING_THRESHOLD_DEFAULT;
                if (threshold == -1) {
                    threshold = Integer.MAX_VALUE;
                }
                if (threshold < 0) {
                    throw new IllegalArgumentException("value must be positive integer.");
                }
            } catch (IllegalArgumentException failed) {
                throw new Error("Illegal value for 'jdk.map.althashing.threshold'", failed);
            }

            ALTERNATIVE_HASHING_THRESHOLD = threshold;
        }
    }


    /**
     * 带容量和加载因子的构造方法
     */
    public HashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        }
        if (initialCapacity > MAXIMUM_CAPACITY) {
            initialCapacity = MAXIMUM_CAPACITY;
        }
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new IllegalArgumentException("Illegal load factor: " + loadFactor);
        }

        this.loadFactor = loadFactor;// 设置加载因子
        threshold = initialCapacity;// 设置初始容量
        init(); // 初始化，里面没有代码
    }

    /**
     * 带初始容量的构造，加载因子则是默认的0.75
     */
    public HashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * 空参构造方法
     */
    public HashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    /**
     * 带集合的构造方法
     */
    public HashMap(Map<? extends K, ? extends V> m) {
        this(Math.max((int) (m.size() / DEFAULT_LOAD_FACTOR) + 1, DEFAULT_INITIAL_CAPACITY), DEFAULT_LOAD_FACTOR);
        inflateTable(threshold);
        putAllForCreate(m);
    }

    /**
     * 确保num是2的倍数
     */
    private static int roundUpToPowerOf2(int number) {
        return number >= MAXIMUM_CAPACITY ? MAXIMUM_CAPACITY : (number > 1) ? Integer.highestOneBit((number - 1) << 1) : 1;
    }

    /**
     * 初始化底层的数组容器
     */
    private void inflateTable(int toSize) {
        int capacity = roundUpToPowerOf2(toSize); // 容量设置为（toSize-1）*2
        threshold = (int) Math.min(capacity * loadFactor, MAXIMUM_CAPACITY + 1);// 看下那个扩容阈值更小
        table = new Entry[capacity];// 得到一个Entry数组
        initHashSeedAsNeeded(capacity); // 初始化一个hash随机值
    }

    void init() {
    }


    /**
     * 初始化hash码的起始种子值，是一个随机值
     */
    final boolean initHashSeedAsNeeded(int capacity) {
        boolean currentAltHashing = hashSeed != 0;// 如果为0表示不需要hash
        boolean useAltHashing = sun.misc.VM.isBooted() && (capacity >= Holder.ALTERNATIVE_HASHING_THRESHOLD);// 是否使用了自定义的扩容阈值
        boolean switching = currentAltHashing ^ useAltHashing;//
        if (switching) {
            hashSeed = useAltHashing ? sun.misc.Hashing.randomHashSeed(this) : 0;
        }
        return switching;
    }

    /**
     * 计算k的hash值
     */
    final int hash(Object k) {
        int h = hashSeed;
        if (0 != h && k instanceof String) {
            return sun.misc.Hashing.stringHash32((String) k);
        }

        h ^= k.hashCode();// 得到对象的hash值

        h ^= (h >>> 20) ^ (h >>> 12);// h的前12位
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    /**
     * key的set集合（key不能重复）
     */
    public Set<K> keySet() {
        Set<K> ks = keySet;
        return (ks != null ? ks : (keySet = new KeySet()));
    }

    /**
     * value的Collection集合
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        return (vs != null ? vs : (values = new Values()));
    }
    /**
     * 得到对象的索引值
     */
    static int indexFor(int h, int length) {
        return h & (length - 1);
    }

    /**
     * 返回容器中元素个数
     */
    public int size() {
        return size;
    }

    /**
     * 容器容量
     */
    int capacity() {
        return table.length;
    }

    /**
     * 容器加载因子
     */
    float loadFactor() {
        return loadFactor;
    }

    /**
     * 判断容器是否没有元素
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 得到key所对应的Value
     */
    public V get(Object key) {
        if (key == null) {
            return getForNullKey(); // 返回key未null的Value
        }
        Entry<K, V> entry = getEntry(key);// 获取key对应得Entry元素
        return null == entry ? null : entry.getValue();// 得到entry中存入得value
    }

    /**
     * hasmMap允许key为null得数据，获取这条key为null对应的value数据
     */
    private V getForNullKey() {
        if (size == 0) {
            return null;
        }
        for (Entry<K, V> e = table[0]; e != null; e = e.next) {
            if (e.key == null) {
                return e.value; // 返回key这个key为null的value数据
            }
        }
        return null;// 没有找到就返回null
    }

    /**
     * 判断是否存在key这条记录
     */
    public boolean containsKey(Object key) {
        return getEntry(key) != null;
    }

    /**
     * 得到key对应的底层entry对象
     */
    final Entry<K, V> getEntry(Object key) {
        if (size == 0) {
            return null;// 没有元素直接返回null
        }
        // 得到对象的hash值
        int hash = (key == null) ? 0 : hash(key);
        /**
         * 找到索引所对应的entry，也是链表的头
         * 如果还有链表结构则进行遍历查找这条记录
         */
        for (Entry<K, V> e = table[indexFor(hash, table.length)]; e != null; e = e.next) {
            Object k;
            if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k)))) {
                return e; // 找到了这条记录进行返回
            }
        }
        return null;// 没有找到就返回null
    }

    /**
     * 添加一条key-value记录
     */
    public V put(K key, V value) {
        if (table == EMPTY_TABLE) {
            inflateTable(threshold); // 如果是空数组就进行初始化底层的数组结构，就是new Entry[16]
        }
        if (key == null) {
            return putForNullKey(value);// 如果key为null则添加一条null-value的记录
        }
        int hash = hash(key);// 计算hash值
        int i = indexFor(hash, table.length);// 根据hash值得到索引，就是 &(length-1)
        /**
         * 遍历i节点上的链表，如果已有记录则替换旧的value
         * 没有旧执行后面的addEntry方法将节点添加进Entry数组中
         */
        for (Entry<K, V> e = table[i]; e != null; e = e.next) {
            Object k;
            if (e.hash == hash && ((k = e.key) == key || key.equals(k))) {
                V oldValue = e.value;// 旧的值
                e.value = value;// 替换成新的value
                e.recordAccess(this);// 空方法什么也没有做
                return oldValue;// 返回旧的value
            }
        }

        modCount++;
        addEntry(hash, key, value, i);// 添加这条记录
        return null;
    }


    /**
     * key为null的记录
     * 默认添加到数组的头部，如果0所对应的元素存在则添加到末尾
     */
    private V putForNullKey(V value) {
        for (Entry<K, V> e = table[0]; e != null; e = e.next) {
            if (e.key == null) {
                V oldValue = e.value;
                e.value = value;
                e.recordAccess(this);
                return oldValue;
            }
        }
        modCount++;// 并发修改计数加一
        addEntry(0, null, value, 0);
        return null;
    }

    /**
     * 添加元素
     */
    void addEntry(int hash, K key, V value, int bucketIndex) {
        // 判断是否超过了阈值，并且插入的这个位置有元素，需要进行扩容。也就是说超过阈值也不一定会扩容！
        if ((size >= threshold) && (null != table[bucketIndex])) {
            resize(2 * table.length);// 扩容至原来的2倍
            hash = (null != key) ? hash(key) : 0;// 得到hash值
            bucketIndex = indexFor(hash, table.length);// 计算出存放的索引值
        }

        createEntry(hash, key, value, bucketIndex);// 创建Entry数组
    }

    /**
     * 创建并添加元素
     * 通过构造方法直接插入链表头部
     */
    void createEntry(int hash, K key, V value, int bucketIndex) {
        Entry<K, V> e = table[bucketIndex];// 得到原先的元素
        table[bucketIndex] = new Entry<>(hash, key, value, e);// 直接添加到链表头
        size++;// 元素个数+1
    }

    /**
     * 添加并创建该节点
     */
    private void putForCreate(K key, V value) {
        int hash = null == key ? 0 : hash(key);// 得到key的hash值
        int i = indexFor(hash, table.length);// 计算索引

        /**
         * 遍历索引所对应的链表
         * 如果以存在key则新值替换旧值
         */
        for (Entry<K, V> e = table[i]; e != null; e = e.next) {
            Object k;
            if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k)))) {
                e.value = value;
                return;
            }
        }

        // 如果不存在该key则添加到链表头
        createEntry(hash, key, value, i);
    }

    /**
     * 添加所有
     */
    private void putAllForCreate(Map<? extends K, ? extends V> m) {
        // 遍历map中的所有元素
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            putForCreate(e.getKey(), e.getValue());// 添加并创建节点
        }
    }

    /**
     * 扩容
     */
    void resize(int newCapacity) {
        Entry[] oldTable = table; // 得到旧数组
        int oldCapacity = oldTable.length;// 得到旧容量
        if (oldCapacity == MAXIMUM_CAPACITY) {//是否达到了最大容量2的30次方
            threshold = Integer.MAX_VALUE;// 扩容至2的31次方
            return;
        }

        Entry[] newTable = new Entry[newCapacity];// 创建新的容量
        transfer(newTable, initHashSeedAsNeeded(newCapacity));
        table = newTable;
        threshold = (int) Math.min(newCapacity * loadFactor, MAXIMUM_CAPACITY + 1);
    }

    /**
     * 将旧数组的内容转移到新数组中
     * 会发现并没有使用复制原理，而是进行遍历添加
     */
    void transfer(Entry[] newTable, boolean rehash) {
        int newCapacity = newTable.length;
        for (Entry<K, V> e : table) {
            /**
             * 如果是链表结构，也就是hash冲撞产生的链表
             * 遍历链表添加到新的数组中
             */
            while (null != e) {
                Entry<K, V> next = e.next;// 将链表后面部分保存下来
                if (rehash) { // 是否需要再hash
                    e.hash = null == e.key ? 0 : hash(e.key);// 重新计算hash
                }
                int i = indexFor(e.hash, newCapacity);// 重新计算索引
                e.next = newTable[i];// 保存旧的结构（可能也是链表，因此这里使用next直接链上去）
                newTable[i] = e;// 将链上的数据结构替换索引i这个位置的元素，也就是相当于插入到了链表头部
                e = next;// 移到到下一个节点
            }
        }
    }

    /**
     * 添加所有
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        int numKeysToBeAdded = m.size();// 得到map中元素的个数
        if (numKeysToBeAdded == 0) {
            return;// 如果为0则直接返回
        }

        if (table == EMPTY_TABLE) {
            /**
             * 原容器为空进行创建和初始化扩容
             * 这一步如果执行了就创建好了容器数组
             */
            inflateTable((int) Math.max(numKeysToBeAdded * loadFactor, threshold));
        }

        if (numKeysToBeAdded > threshold) {
            // 超过阈值需要扩容
            int targetCapacity = (int) (numKeysToBeAdded / loadFactor + 1);// 计算扩容后的阈值
            if (targetCapacity > MAXIMUM_CAPACITY) {
                targetCapacity = MAXIMUM_CAPACITY;// 超过最大容量就直接设置为最大容量
            }
            int newCapacity = table.length;// 新容量
            // 进行循环的*2，直到容量超过了上面计算的容量
            while (newCapacity < targetCapacity) {
                newCapacity <<= 1; // 如果新容量小于目标容量，就将新容量
            }
            // 新容量大于数组长度进行扩容
            if (newCapacity > table.length) {
                resize(newCapacity);// 进行扩容
            }
        }

        /**
         * 前面是扩容，这里的循环是将map中的数据添加到新容器中
         */
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    /**
     * 移除这条数据
     */
    public V remove(Object key) {
        Entry<K, V> e = removeEntryForKey(key);
        return (e == null ? null : e.value);
    }


    /**
     * 根据key移除entry
     */
    final Entry<K, V> removeEntryForKey(Object key) {
        if (size == 0) {
            return null; // 容器没有元素直接返回null
        }
        int hash = (key == null) ? 0 : hash(key);// 重新计算key的hash
        int i = indexFor(hash, table.length);// 计算索引
        Entry<K, V> prev = table[i];// 保存索引i代表的entry以及链表结构
        Entry<K, V> e = prev;// 另外存一份

        // 遍历链表结构
        while (e != null) {
            Entry<K, V> next = e.next;
            Object k;
            // e就是要移除的元素
            if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k)))) {
                modCount++; // 并发修改计数
                size--; // 找到了需要将其移除，元素个数-1
                if (prev == e) { // 要移除的元素就是链表的表头，直接将下一个元素覆盖i这个位置即可
                    table[i] = next;
                } else {
                    prev.next = next;// 要移除的元素不是链表头就直接将e前一个节点之间指向e后面一个节点即可
                }
                e.recordRemoval(this);// 空实现
                return e;// 返回移除的元素
            }
            prev = e;// 下一个元素的前一个元素
            e = next;// e变成了下一个元素，而prev则是上一个元素
        }

        return e;// e为null直接返回null，可以用null代替这个e
    }


    final Entry<K, V> removeMapping(Object o) {
        if (size == 0 || !(o instanceof Map.Entry)) {
            return null;
        }

        Map.Entry<K, V> entry = (Map.Entry<K, V>) o;
        Object key = entry.getKey();
        int hash = (key == null) ? 0 : hash(key);// 计算hash
        int i = indexFor(hash, table.length);   // 计算索引
        Entry<K, V> prev = table[i];            // 得到该元素
        Entry<K, V> e = prev;                   // 该元素临时存入e

        // 遍历链表
        while (e != null) {
            Entry<K, V> next = e.next; // 得到下一个节点
            if (e.hash == hash && e.equals(entry)) {
                // 如果hash值相等且对象也相等，则说明该节点e就是我们要移除的节点
                modCount++;// 并发修改计数+1
                size--;// 元素个数-1
                if (prev == e) {
                    table[i] = next;// 要移除的元素就是链表的表头，直接将下一个元素覆盖i这个位置即可
                } else {
                    prev.next = next;// 要移除的元素不是链表头就直接将e前一个节点之间指向e后面一个节点即可
                }
                e.recordRemoval(this); // 空实现
                return e;// 返回移除的元素
            }
            prev = e;// 保存上一个节点
            e = next;// 进行移到，移到到下一个节点
        }

        return e;// 可以用null代替
    }


    /**
     * 清空数组
     */
    public void clear() {
        modCount++;
        Arrays.fill(table, null);// 将数组内容置null
        size = 0;
    }


    /**
     * 判断是否包含了value
     */
    public boolean containsValue(Object value) {
        if (value == null) {
            return containsNullValue();
        }

        Entry[] tab = table;
        // 遍历数组结构
        for (int i = 0; i < tab.length; i++) {
            // 遍历链表结构
            for (Entry e = tab[i]; e != null; e = e.next) {
                if (value.equals(e.value)) {
                    return true;// 找到了则直接返回
                }
            }
        }
        return false;// 没有找到value
    }

    /**
     * 判断是否包含null的值
     */
    private boolean containsNullValue() {
        Entry[] tab = table;
        // 遍历数组结构
        for (int i = 0; i < tab.length; i++) {
            // 遍历链表结构
            for (Entry e = tab[i]; e != null; e = e.next) {
                if (e.value == null) {
                    return true; // 找到了
                }
            }
        }
        return false;
    }


    public Object clone() {
        HashMap<K, V> result = null;
        try {
            result = (HashMap<K, V>) super.clone();// 进行拷贝
        } catch (CloneNotSupportedException e) {
            // assert false;
        }
        if (result.table != EMPTY_TABLE) {
            //
            result.inflateTable(Math.min((int) Math.min(size * Math.min(1 / loadFactor, 4.0f), HashMap.MAXIMUM_CAPACITY), table.length));
        }
        result.entrySet = null;// 
        result.modCount = 0;// 并发修改异常置0
        result.size = 0;// 没有元素
        result.init();// 空实现
        result.putAllForCreate(this); // 添加所有节点

        return result;
    }

    /**
     * 存储数据的结构
     */
    static class Entry<K, V> implements Map.Entry<K, V> {
        final K key;    // key
        V value;        // value
        Entry<K, V> next;// 链表结构的支持
        int hash;        // 当前节点的hash值

        /**
         * 构造方法
         * 当前节点的内容 +下一个节点对象，采用头插法
         */
        Entry(int h, K k, V v, Entry<K, V> n) {
            next = n;// 创建节点的同时将下一个节点链接上
            value = v; // 存储value
            key = k;  // 存储key
            hash = h; // 存储hash值
        }

        public final K getKey() {
            return key;// 返回hash值
        }

        public final V getValue() {
            return value;// 返回value
        }

        public final V setValue(V newValue) {
            V oldValue = value; // 保存旧值
            value = newValue;   // 更新值
            return oldValue;    // 返回旧值
        }

        /**
         * 用于节点之间的比较大小
         */
        public final boolean equals(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;// 如果不是 Map.Entry类型就没法比较，之间返回false
            }
            Map.Entry e = (Map.Entry) o;
            Object k1 = getKey(); // 得到当前节点的key
            Object k2 = e.getKey();// 得到 o 的key
            if (k1 == k2 || (k1 != null && k1.equals(k2))) { // 先判断key是否相等
                Object v1 = getValue();// 得到当前value
                Object v2 = e.getValue();// 得到o的value
                if (v1 == v2 || (v1 != null && v1.equals(v2))) {// 判断两个value是否相等
                    return true;// 相等就返回true
                }
            }
            return false;// 返回false
        }

        /**
         * 返回hash码
         */
        public final int hashCode() {
            return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue());
        }

        /**
         * toString方法
         */
        public final String toString() {
            return getKey() + "=" + getValue();
        }

        void recordAccess(HashMap<K, V> m) {
        }

        void recordRemoval(HashMap<K, V> m) {
        }
    }


    /**
     * Hash迭代器
     */
    private abstract class HashIterator<E> implements Iterator<E> {
        Entry<K, V> next;        // next entry to return
        int expectedModCount;   // For fast-fail
        int index;              // current slot
        Entry<K, V> current;     // current entry

        HashIterator() {
            expectedModCount = modCount;
            if (size > 0) { // advance to first entry
                Entry[] t = table;
                while (index < t.length && (next = t[index++]) == null) {
                    ;
                }
            }
        }

        public final boolean hasNext() {
            return next != null;
        }

        final Entry<K, V> nextEntry() {
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            Entry<K, V> e = next;
            if (e == null) {
                throw new NoSuchElementException();
            }

            if ((next = e.next) == null) {
                Entry[] t = table;
                while (index < t.length && (next = t[index++]) == null)
                    ;
            }
            current = e;
            return e;
        }

        public void remove() {
            if (current == null) {
                throw new IllegalStateException();
            }
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            Object k = current.key;
            current = null;
            HashMap.this.removeEntryForKey(k);
            expectedModCount = modCount;
        }
    }

    /**
     * 值得迭代器
     */
    private final class ValueIterator extends HashIterator<V> {
        public V next() {
            return nextEntry().value;
        }
    }

    /**
     * key得迭代器
     */
    private final class KeyIterator extends HashIterator<K> {
        public K next() {
            return nextEntry().getKey();
        }
    }

    /**
     * entry迭代器
     */
    private final class EntryIterator extends HashIterator<Map.Entry<K, V>> {
        public Map.Entry<K, V> next() {
            return nextEntry();
        }
    }

    Iterator<K> newKeyIterator() {
        return new KeyIterator();
    }

    Iterator<V> newValueIterator() {
        return new ValueIterator();
    }

    Iterator<Map.Entry<K, V>> newEntryIterator() {
        return new EntryIterator();
    }

    /**
     * 返回key集合的迭代器
     */
    private final class KeySet extends AbstractSet<K> {
        public Iterator<K> iterator() {
            return newKeyIterator();
        }

        public int size() {
            return size;
        }

        public boolean contains(Object o) {
            return containsKey(o);
        }

        public boolean remove(Object o) {
            return HashMap.this.removeEntryForKey(o) != null;
        }

        public void clear() {
            HashMap.this.clear();
        }
    }


    /**
     * 返回value的迭代器
     */
    private final class Values extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return newValueIterator();
        }

        public int size() {
            return size;
        }

        public boolean contains(Object o) {
            return containsValue(o);
        }

        public void clear() {
            HashMap.this.clear();
        }
    }


    /**
     * 下面的代码都是为了得到key的集合，没啥内容就不写注释了
     */
    public Set<Map.Entry<K, V>> entrySet() {
        return entrySet0();
    }

    private Set<Map.Entry<K, V>> entrySet0() {
        Set<Map.Entry<K, V>> es = entrySet;
        return es != null ? es : (entrySet = new EntrySet());
    }

    private final class EntrySet extends AbstractSet<Map.Entry<K, V>> {
        public Iterator<Map.Entry<K, V>> iterator() {
            return newEntryIterator();
        }
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<K, V> e = (Map.Entry<K, V>) o;
            Entry<K, V> candidate = getEntry(e.getKey());
            return candidate != null && candidate.equals(e);
        }

        public boolean remove(Object o) {
            return removeMapping(o) != null;
        }

        public int size() {
            return size;
        }

        public void clear() {
            HashMap.this.clear();
        }
    }


    /**
     * 序列化
     */
    private void writeObject(java.io.ObjectOutputStream s) throws IOException {
        s.defaultWriteObject();

        if (table == EMPTY_TABLE) {
            s.writeInt(roundUpToPowerOf2(threshold));
        } else {
            s.writeInt(table.length);
        }

        s.writeInt(size);

        if (size > 0) {
            for (Map.Entry<K, V> e : entrySet0()) {
                s.writeObject(e.getKey());
                s.writeObject(e.getValue());
            }
        }
    }

    /**
     * 反序列化
     */
    private void readObject(java.io.ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new InvalidObjectException("Illegal load factor: " + loadFactor);
        }

        table = (Entry<K, V>[]) EMPTY_TABLE;
        s.readInt(); // ignored.
        int mappings = s.readInt();
        if (mappings < 0) {
            throw new InvalidObjectException("Illegal mappings count: " + mappings);
        }

        int capacity = (int) Math.min(mappings * Math.min(1 / loadFactor, 4.0f), HashMap.MAXIMUM_CAPACITY);

        if (mappings > 0) {
            inflateTable(capacity);
        } else {
            threshold = capacity;
        }

        init();

        for (int i = 0; i < mappings; i++) {
            K key = (K) s.readObject();
            V value = (V) s.readObject();
            putForCreate(key, value);
        }
    }


}

