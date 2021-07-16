package java.lang;

import jdk.internal.misc.TerminatingThreadLocal;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;


public class ThreadLocal<T> {

    private final int threadLocalHashCode = nextHashCode();         // 设计为常量，只能赋值一次
    private static AtomicInteger nextHashCode = new AtomicInteger(); // 下一个hash值的一个原子变量
    private static final int HASH_INCREMENT = 0x61c88647; // hash增量 二进制表示：0110 0001 1100 1000 1000 0110 0100 0111

    public ThreadLocal() {
    } // 构造方法


    /**
     * get方法、重点阅读
     */
    public T get() {
        Thread t = Thread.currentThread();  // 获取当前线程
        ThreadLocalMap map = getMap(t);     // 获取当前线程绑定的ThreadLocalMap
        if (map != null) {                  // 如果map不为null
            // 取出key所对应的Entry，key如果被gc了那么this就是null,会返回null
            ThreadLocalMap.Entry e = map.getEntry(this); // 如果this为所指的对象是null，那么返回null，就会执行setInitialValue()
            if (e != null) {                // 如果当前数据不为null
                T result = (T) e.value;     // 获取当前数据绑定的数据，进行了强转为泛型
                return result;              // 返回数据
            }
        }
        return setInitialValue();           // map为null返回null
    }

    /**
     * set方法、重点阅读
     */
    public void set(T value) {
        Thread t = Thread.currentThread();  // 获取当前线程
        ThreadLocalMap map = getMap(t);     // 获取当前线程绑定的 ThreadLocalMap
        if (map != null) {                  // map不为null
            map.set(this, value);           // 给当前线程绑定的map中添加数据，key是当前ThreadLocal对象、值是传入的value
        } else {
            createMap(t, value);            // 当前线程没有map，创建一个map将传入的value存入map中并绑定到当前线程对象
        }
    }

    /**
     * remove方法、
     * 移除当前线程绑定的数据，
     * 每次使用完后就需要进行remove防止内存泄漏！！！
     */
    public void remove() {
        ThreadLocalMap m = getMap(Thread.currentThread()); // 获取当前线程绑定的map
        if (m != null) {            // 存在map
            m.remove(this);    // key是当前ThreadLocal对象绑定的数据
        }
    }


    /**
     * 始终返回null
     */
    protected T initialValue() {
        return null;
    }

    /**
     * 从线程t中得到ThreadLocalMap对象
     */
    ThreadLocalMap getMap(Thread t) {
        return t.threadLocals;
    }

    /**
     * 给线程t创建一个map。并添加了一条map记录
     */
    void createMap(Thread t, T firstValue) {
        t.threadLocals = new ThreadLocalMap(this, firstValue);
    }

    /**
     * 如果当前线程没有map则会创建一个map
     * 如果当前线程有map则添加一条value为null的记录
     * 最终return null
     */
    private T setInitialValue() {
        T value = initialValue();   // 内部写死了，始终返回null
        Thread t = Thread.currentThread(); // 得到当线程对象
        ThreadLocalMap map = getMap(t);// 得到当前线程绑定的ThreadLocalMap，原先有则直接用，没有则会返回null
        if (map != null) {          // 当前map不为null，直接用。
            map.set(this, value);   // 将当前key=ThreadLocal，value置为null，原先value强引用所指向的对象会被回收。没有引用指向它所以会被回收
        } else { // 为空则需要创建一个
            createMap(t, value);    // map为空则说明当前线程没有绑定map
        }
        if (this instanceof TerminatingThreadLocal) {
            TerminatingThreadLocal.register((TerminatingThreadLocal<?>) this);
        }
        return value; // 返回初始的null，这里始终为null
    }


    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);// CAS操作得到下一个hash值
    }

    public static <S> ThreadLocal<S> withInitial(Supplier<? extends S> supplier) {
        return new SuppliedThreadLocal<>(supplier);
    }


    boolean isPresent() {
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        return map != null && map.getEntry(this) != null;
    }


    static ThreadLocalMap createInheritedMap(ThreadLocalMap parentMap) {
        return new ThreadLocalMap(parentMap);
    }


    T childValue(T parentValue) {
        throw new UnsupportedOperationException();
    }


    static final class SuppliedThreadLocal<T> extends ThreadLocal<T> {

        private final Supplier<? extends T> supplier;

        SuppliedThreadLocal(Supplier<? extends T> supplier) {
            this.supplier = Objects.requireNonNull(supplier);
        }

        @Override
        protected T initialValue() {
            return supplier.get();
        }
    }


    /**
     * Thread对象保存共享变量的数据结构
     */
    static class ThreadLocalMap {

        /**
         * value则是真正存set方法传入的对象的变量
         */
        static class Entry extends WeakReference<ThreadLocal<?>> {
            Object value;

            Entry(ThreadLocal<?> k, Object v) {
                super(k);
                value = v;
            }
        }

        private static final int INITIAL_CAPACITY = 16;// map初始容量16
        private Entry[] table;  // map底层是一个Entry数组
        private int size = 0;   // 初始没有元素是 0
        private int threshold;  // 扩容条件默认值是 0

        /**
         * 传入key，value创建一个map
         */
        ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
            table = new Entry[INITIAL_CAPACITY];
            int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);
            table[i] = new Entry(firstKey, firstValue);
            size = 1;
            setThreshold(INITIAL_CAPACITY);
        }

        /**
         * 带map的构造方法
         */
        private ThreadLocalMap(ThreadLocalMap parentMap) {
            Entry[] parentTable = parentMap.table;
            int len = parentTable.length;
            setThreshold(len);
            table = new Entry[len];

            for (Entry e : parentTable) {
                if (e != null) {
                    @SuppressWarnings("unchecked")
                    ThreadLocal<Object> key = (ThreadLocal<Object>) e.get();
                    if (key != null) {
                        Object value = key.childValue(e.value);
                        Entry c = new Entry(key, value);
                        int h = key.threadLocalHashCode & (len - 1);
                        while (table[h] != null)
                            h = nextIndex(h, len);
                        table[h] = c;
                        size++;
                    }
                }
            }
        }

        /**
         * 设置扩容值
         */
        private void setThreshold(int len) {
            threshold = len * 2 / 3;
        }

        /**
         * 下一个索引值
         */
        private static int nextIndex(int i, int len) {
            return ((i + 1 < len) ? i + 1 : 0);
        }

        /**
         * 得到索引 i的前一个索引，主要目的是包括让i=0的情况，不然直接i-1就行了
         */
        private static int prevIndex(int i, int len) {
            return ((i - 1 >= 0) ? i - 1 : len - 1);
        }


        /**
         * 返回key所在的节点e，如果没有则返回null
         */
        private Entry getEntry(ThreadLocal<?> key) {
            int i = key.threadLocalHashCode & (table.length - 1); // 得到entry的索引
            Entry e = table[i];// 取出索引元素
            if (e != null && e.get() == key) // 判断取出来的e中的key是否和传入的key，主要目的判断是否时同一个ThreadLocal对象
                return e;
            else // 不是同一个ThreadLocal对象 或者 e不存在
                return getEntryAfterMiss(key, i, e); // e为null的情况
        }

        /**
         * 在内存被回收的情况下，获取entry
         */
        private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
            Entry[] tab = table;
            int len = tab.length;

            while (e != null) {
                ThreadLocal<?> k = e.get();
                if (k == key)
                    return e;
                if (k == null)
                    expungeStaleEntry(i);
                else
                    i = nextIndex(i, len);
                e = tab[i];
            }
            return null;
        }


        /**
         * ThreadLocalMap的set方法
         */
        private void set(ThreadLocal<?> key, Object value) {
            Entry[] tab = table;
            int len = tab.length;
            /**
             * 看起来这里是运算得到的，实际上key.threadLocalHashCode这个值是固定的
             * 因为是通过常量 HASH_INCREMENT 进行原子操作，所以值是固定规律的
             * 如果是第一次进来始终都会得到同一个i的值，因此entry是同一个
             */
            int i = key.threadLocalHashCode & (len - 1);

            // 遍历数组，相当于遍历map，得到entry
            // 关于内存泄漏，我感觉作者会把泄漏的内存在这次中通过重新赋值的操作去除泄漏的内存
            // 是不是呢，我们看下这个for循环做了哪些事情
            for (Entry e = tab[i]; e != null; e = tab[i = nextIndex(i, len)]) {
                ThreadLocal<?> k = e.get(); // 弱引用的方式得到ThreadLocal对象
                /**
                 * k == key 判断弱引用对象是否和存储的那个对象相同
                 * 如果是内存泄漏，而且是我博客中讲的这种重复利用线程的情况
                 * 那么k 是不等于 key的，也就是false
                 */
                if (k == key) { // 如果想到，说明是同一个ThreadLocal对象进行的set，需要保留后一次更新的值value
                    e.value = value; // 将更新的value赋值进去
                    return;          // 这种情况就是我博客开头讲到的两次set操作只保留后面的一次，相当一只能存一个值
                }
                /**
                 * 到这里，说明进行了gc，原先的threadLocal被垃圾回收了，也是我前面流程图中的那种情况
                 * key则是当前threadLocal对象
                 */
                if (k == null) {  // 因为gc导致弱引用的ThreadLocal对象被回收
                    // 将i这个位置的entry替换成新的key和value。这里entry中旧值的value强引用就会被去除，就可以进行回收了
                    replaceStaleEntry(key, value, i); // 翻译过来的意思：取代陈旧Entry
                    return;
                }
            }

            // 到这里则说明是第一次进入e==null，直接添加entry进去就可以了，简单
            tab[i] = new Entry(key, value); // 创建了一个Entry对象，将value存入
            int sz = ++size;
            if (!cleanSomeSlots(i, sz) && sz >= threshold)
                rehash();
        }

        /**
         * 移除弱引用的ThreadLocal
         */
        private void remove(ThreadLocal<?> key) {
            Entry[] tab = table; // 得到entry数组
            int len = tab.length;// 数组长度
            int i = key.threadLocalHashCode & (len - 1);// 得到threadLocal对象key所对应的索引值i
            // 遍历i，应该和set方法有类似的做法，只不过set是进行复写将原引用去除，这里应该是通过null去除
            for (Entry e = tab[i]; e != null; e = tab[i = nextIndex(i, len)]) {
                if (e.get() == key) {
                    e.clear(); // 清除弱引用
                    expungeStaleEntry(i);// 将索引i的entry的value通过null去除引用
                    return;
                }
            }
        }


        /**
         * 替换索引为staleSlot的Entry，传入新的key，value
         */
        private void replaceStaleEntry(ThreadLocal<?> key, Object value, int staleSlot) {
            Entry[] tab = table;
            int len = tab.length;
            Entry e;
            int slotToExpunge = staleSlot;
            //
            for (int i = prevIndex(staleSlot, len); (e = tab[i]) != null; i = prevIndex(i, len))
                if (e.get() == null)
                    slotToExpunge = i;
            for (int i = nextIndex(staleSlot, len); (e = tab[i]) != null; i = nextIndex(i, len)) {
                ThreadLocal<?> k = e.get();
                if (k == key) {
                    e.value = value;

                    tab[i] = tab[staleSlot];
                    tab[staleSlot] = e;

                    if (slotToExpunge == staleSlot)
                        slotToExpunge = i;
                    cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
                    return;
                }
                if (k == null && slotToExpunge == staleSlot)
                    slotToExpunge = i;
            }

            tab[staleSlot].value = null;
            tab[staleSlot] = new Entry(key, value);

            if (slotToExpunge != staleSlot)
                cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
        }


        /**
         * 清除索引为staleSlot的entry，实际就是置null
         */
        private int expungeStaleEntry(int staleSlot) {
            Entry[] tab = table;    // 得到entry
            int len = tab.length;   // entry长度
            tab[staleSlot].value = null;// 直接将这个插槽的entry引用的value置null
            tab[staleSlot] = null;      // 将entry也置null
            size--;                     // map去除了一个元素
            Entry e;                    // 一个entry变量
            int i;                      // 一个int变量
            // 上面操作已经将staleSlot所对应的entry的引用清除了
            // 下面这个循环应该是,清除立即回收entry不可用的元素，因为key的hash值设定为final常量（通过这个常量我们得到的索引始终是一个位置）
            // 所以不可能将后面的entry往前移动，下面的for循环功能应该是清除gc导致key为null的所有entry元素
            for (i = nextIndex(staleSlot, len); (e = tab[i]) != null; i = nextIndex(i, len)) {
                // 根据staleSlot索引所对应的entry中的hash值，再通过HASH_INCREMENT进行&运算得到下一个entry索引i，（就是得到staleSlot的下一个元素位置）
                // 判断i这个位置entry是否为null，如果是则说明后面没有元素了就结束循环，循环增量则是再一次调用nextIndex到下一个索引
                // e!=null这样判断后面有没有元素是有缺陷的，因为可能后面还有元素，因为gc导致为中间这个e被清除为null

                /**
                 * 得到key
                 */
                ThreadLocal<?> k = e.get(); // i所对应的entry元素e不为null则尝试得到key，如果gc回收了，那么就是null，如果没有回收则说明需要后移
                // 如果k为null则需要清除这个entry
                if (k == null) {
                    // 说明 i 这个位置的key被回收，那么这个位置是一个垃圾，需要清除引用
                    e.value = null; // 进行清除引用
                    tab[i] = null;  // 清除数组引用entry
                    size--;         // 相当于map个数减一
                } else {
                    // 到这里则说明，当前entry可能还用的到，需要将这个e移动一下
                    int h = k.threadLocalHashCode & (len - 1); // 当前元素的索引
                    if (h != i) {       // 计算出的这个索引和当前索引i不等，说明这个数据是随机产生的，因为某些原因线程启动的时候这里会带上entry
                        tab[i] = null;  // 将当前非法的e清除
                        while (tab[h] != null) // 找到下一个为null的位置
                            h = nextIndex(h, len);// 循环找下一个元素索引，实际上就像是一个圆形的循环，因为h是通过Hash码按位&得到的索引
                        tab[h] = e; // 将e这个元素往后移动到这个空位置。
                    }
                }
            }
            return i;
        }

        /**
         * 清理
         */
        private boolean cleanSomeSlots(int i, int n) {
            boolean removed = false;    // 默认没有被移动
            Entry[] tab = table;        //
            int len = tab.length;       //
            do {
                i = nextIndex(i, len);  // 得到下一个索引
                Entry e = tab[i];       // 户去下一个元素
                if (e != null && e.get() == null) { // 如哦下一个元素不为null 且 threadLocal=null
                    n = len; //
                    removed = true;//
                    i = expungeStaleEntry(i);//
                }
            } while ((n >>>= 1) != 0);
            return removed;
        }

        private void rehash() {
            expungeStaleEntries();
            if (size >= threshold - threshold / 4)
                resize();
        }

        private void resize() {
            Entry[] oldTab = table;
            int oldLen = oldTab.length;
            int newLen = oldLen * 2;
            Entry[] newTab = new Entry[newLen];
            int count = 0;
            for (Entry e : oldTab) {
                if (e != null) {
                    ThreadLocal<?> k = e.get();
                    if (k == null) {
                        e.value = null; // Help the GC
                    } else {
                        int h = k.threadLocalHashCode & (newLen - 1);
                        while (newTab[h] != null)
                            h = nextIndex(h, newLen);
                        newTab[h] = e;
                        count++;
                    }
                }
            }

            setThreshold(newLen);
            size = count;
            table = newTab;
        }

        private void expungeStaleEntries() {
            Entry[] tab = table;
            int len = tab.length;
            for (int j = 0; j < len; j++) {
                Entry e = tab[j];
                if (e != null && e.get() == null)
                    expungeStaleEntry(j);
            }
        }
    }
}
