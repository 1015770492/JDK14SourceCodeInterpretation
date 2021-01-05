package java.util.concurrent.locks;

import java.util.Collection;
import java.util.concurrent.ThreadLocal;
import java.util.concurrent.TimeUnit;

public class ReentrantReadWriteLock implements ReadWriteLock, java.io.Serializable {
    private static final long serialVersionUID = -6992448646407690164L;
    private final ReadLock readerLock; // 读锁
    private final WriteLock writerLock;// 写锁
    final Sync sync;   // 同步器

    /**
     * 创建默认的非公平锁
     */
    public ReentrantReadWriteLock() {
        this(false);
    }

    /**
     * 创建公平锁/非公平锁、读锁、写锁
     */
    public ReentrantReadWriteLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync(); // true则是公平锁，false则是非公平锁
        readerLock = new ReadLock(this); // 读锁
        writerLock = new WriteLock(this);// 写锁
    }

    public WriteLock writeLock() {
        return writerLock;
    } // 返回写锁

    public ReadLock readLock() {
        return readerLock;
    } // 返回读锁

    /**
     * 读写锁的同步器
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 6317671515068378041L;//序列化版本号
        static final int SHARED_SHIFT = 16;                         // 常量16，目的是将state按位右移16位得到的值就是读锁的个数
        static final int SHARED_UNIT = (1 << SHARED_SHIFT);         // 2的16次方，实际上表示读锁加的锁次数是1
        static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;  // 2的16次方再减1，前面16位全0后面16位就是全1，目的就是通过&运算得到写锁的个数
        static final int MAX_COUNT = (1 << SHARED_SHIFT) - 1;       // 2的16次方再减1，表示加锁（读/写）最大的计数超过了则抛异常
        private transient Thread firstReader;                       // 第一个获取到读锁的线程
        private transient int firstReaderHoldCount;                 // 第一个线程重入锁的次数计数
        private transient HoldCounter cachedHoldCounter;            // 读锁计数器对象
        private transient ThreadLocalHoldCounter readHolds;         // 在构造Sync的时候就会被赋值，重入读锁的计数器保持对象（对象中存了获取读锁的次数）

        /**
         * 构造方法
         */
        Sync() {
            readHolds = new ThreadLocalHoldCounter(); //
            setState(getState()); // 确保readHolds的可见性
        }

        /**
         * 读锁计数，实际存入Thread中的ThreadLocal变量中
         */
        static final class HoldCounter {
            int count; //获取读锁的次数，相当于线程存了线程id为key，value为获得锁的计数
            final long tid = LockSupport.getThreadId(Thread.currentThread());
        }

        /**
         * 将锁计数保持对象存入当前线程的ThreadLocal变量
         */
        static final class ThreadLocalHoldCounter extends ThreadLocal<HoldCounter> {
            public HoldCounter initialValue() {
                return new HoldCounter();
            }
        }

        /**
         * 由于读写锁的设计将state前16位用于读锁的个数，后16位标识写锁的个数因此厦门两个方法目的就是得到读锁和写锁的个数
         */
        /**
         * 返回读锁的个数
         */
        static int sharedCount(int c) {
            return c >>> SHARED_SHIFT;
        }

        /**
         * 返回写锁的个数
         */
        static int exclusiveCount(int c) {
            return c & EXCLUSIVE_MASK;
        }


        /**
         * 获取读锁时阻塞当前线程，由子类公平锁/非公平锁实现
         */
        abstract boolean readerShouldBlock();

        /**
         * 获取写锁时阻塞当前线程，由子类公平锁/非公平锁实现
         */
        abstract boolean writerShouldBlock();

        /**
         * 尝试释放锁
         */
        @ReservedStackAccess
        protected final boolean tryRelease(int releases) {
            if (!isHeldExclusively())//判断当前现是否是持有锁线程如果是则不执行，如果不是则需要抛异常，因为当前线程没有持有锁
                throw new IllegalMonitorStateException();
            int nextc = getState() - releases;// 计算释放锁释放合法，不允许释放超过加锁次数
            boolean free = exclusiveCount(nextc) == 0;//
            if (free) // 判断释放锁后释放锁计数是否为0，为0则说明当前线程不再是持有锁线程将其从排他线程状态清除
                setExclusiveOwnerThread(null);
            setState(nextc);// 更新锁次数计数
            return free;//如果释放锁后计数为0则返回true，否则返回false
        }

        /**
         * 这个方法是写锁调用才会执行的
         * 不是第一次加写锁
         * 因为是写锁才调用的方法，因此只需要排除前面加的都是读锁这种情况即可，也就是 c!=0 但 w==0的情况
         * 先判断是否加了锁c！=0，如果加了锁也就是c!=0内部的这个分支，
         *
         * 是否没有加过写锁
         * 是否是重入写锁
         * 是否是第一次加写锁
         */
        @ReservedStackAccess
        protected final boolean tryAcquire(int acquires) {
            Thread current = Thread.currentThread();// 获取当前线程对象
            int c = getState(); // 获取state（前16位是读锁个数、后16位是写锁个数）
            int w = exclusiveCount(c);// 获得写锁的个数，w有write的含义。这个值就是写锁的个数，通过按位与 15 得到写锁个数
            if (c != 0) {// c！=0则说明加过锁
                // 如果写锁个数为0 （说明加的都是读锁，不需要阻塞因此抢占锁失败） 或者 当前线程不是持有写锁线程(w!=0说明加过写锁需要判断当前线程是否是持有写锁的那个线程，不是则说明抢占锁失败)
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false; // 表示抢占锁失败，这里导致了两种情况，一种是加的都是读锁，一种是加了写锁，但当前线程不是持有锁线程
                // 执行下面的判断都表示加过了写锁，相当于写锁的重入，因此需要将写锁计数相加也就是判断里的操作
                if (w + exclusiveCount(acquires) > MAX_COUNT) // 说明是重入锁，判断本次加了acquires次锁后锁计数是否超过最大值 2的16次方-1
                    throw new Error("Maximum lock count exceeded");// 超过能加写锁的最大值则抛异常
                // 写锁重入，因此保留读锁加上写锁重入的acquires次，将state更新
                setState(c + acquires);
                return true;//返回true说明加锁成功
            }
            // 前面没有加过锁，需要加写锁，尝试利用CAS操作更新state进行加锁，实际上逻辑上不需要这里的if，但是应该是由于并发问题怕中途state值被改了，因此CAS操作可能失败（所以失败则return false）
            // c==0 说明没有加过锁，尝试将state从0更新为acquires，更新成功则说明加锁成功，因此不会返回false，而是执行后面的return true
            if (writerShouldBlock() || !compareAndSetState(c, c + acquires))
                return false;
            setExclusiveOwnerThread(current);// 将当前线程设置为独占线程，表示加写锁成功！
            return true;// 加锁成功
        }

        @ReservedStackAccess
        protected final boolean tryReleaseShared(int unused) {
            Thread current = Thread.currentThread();// 获取当前线程对象
            if (firstReader == current) { // 当前线程是否是第一个持有锁线程
                if (firstReaderHoldCount == 1) // 是否是第一次上锁后就解锁了
                    firstReader = null; // 清除第一个读锁线程
                else
                    firstReaderHoldCount--;// 将读锁计数减一
            } else {
                HoldCounter rh = cachedHoldCounter; // 得到缓存的计数器对象
                if (rh == null || rh.tid != LockSupport.getThreadId(current))
                    rh = readHolds.get(); // 如果缓存的计数器对象不是当前线程的，则获取当前线程的计数器对象，重新赋值
                int count = rh.count; // 得到当前线程的读锁计数
                if (count <= 1) { // 释放锁后为0，或者过度释放，则移除计数器
                    readHolds.remove();// 移除计数器
                    if (count <= 0)
                        throw unmatchedUnlockException();
                }
                --rh.count; // 锁计数减1
            }
            for (; ; ) {
                int c = getState(); // 获得锁计数
                int nextc = c - SHARED_UNIT; // 读锁计数减一
                if (compareAndSetState(c, nextc)) // cas操作更新state值
                    // 释放读取锁定对读取器没有影响.但是,如果现在读和写锁都已释放,则可能允许等待的编写器继续进行.
                    return nextc == 0;
            }
        }

        private static IllegalMonitorStateException unmatchedUnlockException() {
            return new IllegalMonitorStateException("attempt to unlock read lock, not locked by current thread");
        }

        /**
         * 读锁才调用的方法，当前线程尝试获取读锁
         */
        @ReservedStackAccess
        protected final int tryAcquireShared(int unused) {
            Thread current = Thread.currentThread(); // 获取当前线程
            int c = getState();// 获取存有读和写锁次数的state值
            /**
             * 是写锁则进入
             */
            // 通过exclusiveCount(c)得到写锁次数，如果不为0则说明加了写锁。加了写锁需要判断当前线程是否是持有写锁的线程，是则不返回-1，不是则说明是写读状态需要进行阻塞当前线程
            if (exclusiveCount(c) != 0 && getExclusiveOwnerThread() != current)
                return -1; // 说明是写读状态、返回-1，抢占读锁失败
            // 执行到这里说明前面没有加过写锁，可能加过读锁
            int r = sharedCount(c); // 获取加的读锁次数，r就是read，实际就是将state右移16位得到
            // 到这里说明没有加过锁，到这里c是0，因此进行加锁操作将state更新为读锁的1 实际二进制是：0000 0000 0000 0001 0000 0000 0000 0000
            /**
             *  是读锁，
             *  一、读是共享的情况直接执行if内
             */
            if (!readerShouldBlock() && r < MAX_COUNT && compareAndSetState(c, c + SHARED_UNIT)) {
                if (r == 0) { // 第一次加读锁进入，因为能到达这里就说明没有写锁，有判断r==0则说明读锁也为0，则说明是第一次调用
                    firstReader = current; // 将第一个线程存起来
                    firstReaderHoldCount = 1;// 计数为1
                } else if (firstReader == current) {
                    firstReaderHoldCount++; // 读重入，读锁计数进行累加
                } else {
                    // 说明不是获得读锁的线程进来了
                    // tid 为key ，value为读锁次数
                    HoldCounter rh = cachedHoldCounter;// 将当前线程初始值是null
                    // 第一次null直接创建一个
                    if (rh == null || rh.tid != LockSupport.getThreadId(current))
                        cachedHoldCounter = rh = readHolds.get();// 通过ThreadLocal得到HoldCounter（计数保持器，内部存了加锁计数）
                    else if (rh.count == 0) // 如果锁计数为0
                        readHolds.set(rh); // 更新锁计数保持器对象
                    rh.count++; // 计数累加
                }
                return 1;// 表示抢占读锁成功
            }
            /**
             * 二、读是排他的情况，调用下面这个方法
             */
            return fullTryAcquireShared(current);
        }

        /**
         * 读是排他的情况采用自旋方式
         * 完整版本的获取读，可处理CAS错误和tryAcquireShared中未处理的可重入读。
         */
        final int fullTryAcquireShared(Thread current) {
            /**
             * 该代码与tryAcquireShared中的代码部分冗余，但由于不使tryAcquireShared与重试和延迟读取保持计数之间的交互复杂化，因此整体代码更简单。
             */
            HoldCounter rh = null;
            for (; ; ) {// 自旋
                int c = getState(); // 获取读写锁计数
                /**
                 * 如果存在写锁
                 */
                if (exclusiveCount(c) != 0) {
                    if (getExclusiveOwnerThread() != current)// 判断当前线程是否是持有同一把写锁的线程
                        return -1;// 加锁失败，当前线程不是持有写锁线程
                }
                /**
                 * 不存在写的情况
                 */
                // 1.判断读是否是排他的，如果是则进入
                else if (readerShouldBlock()) {
                    // 当前线程是不是第一个读锁线程，是则说明当前线程是重入的读锁线程
                    if (firstReader == current) {
                        // 什么也没有
                    } else {
                        // 如果当前线程不是第一个抢占到读锁的线程，如果锁计数存在
                        if (rh == null) {
                            rh = cachedHoldCounter;  // 得到锁计数保持器
                            if (rh == null || rh.tid != LockSupport.getThreadId(current)) {
                                rh = readHolds.get(); // 得到锁计数保持器
                                if (rh.count == 0) // 如果计数为0
                                    readHolds.remove(); // 清除保持器
                            }
                        }
                        // 读锁计数保持器存在，如果等于0则抢占读锁失败，因为这个计数器在tryAcquireShared方法已经被赋值了，所以不会为0，为0说明cas操作失败了
                        if (rh.count == 0)
                            return -1; // 加锁失败，当前线程
                    }
                }
                // 2.到这里说明是共享的读
                /**
                 * 注意：
                 *  如果是tryAcquireShared方法过来的其实下面不会执行到的，
                 *  因为在tryAcquireShared方法中已经走过一遍这个逻辑了，
                 *  这里加上这个逻辑只是处于对当前方法的封装，这样当前方法可以不用依赖tryAcquireShared方法
                 */
                if (sharedCount(c) == MAX_COUNT) // 判断读锁是否超过最大值
                    throw new Error("Maximum lock count exceeded");
                // 读共享，因此只需要通过cas将读锁计数累加1即可，因为CAS操作多以是单线程所以是加1
                if (compareAndSetState(c, c + SHARED_UNIT)) {// 更新state值
                    // c 一开始是0，因为上面更新的不是c而是state值，如果c是0说明是第一个线程调用了这个方法，执行到了这里
                    if (sharedCount(c) == 0) {
                        firstReader = current; // 保存当前的第一个线程
                        firstReaderHoldCount = 1;// 保存计数（因为是第一次进入所以是1）
                    } else if (firstReader == current) {
                        firstReaderHoldCount++; // 持锁的同一个线程重入读锁
                    } else {
                        if (rh == null)
                            rh = cachedHoldCounter; // 其它线程尝试获取读锁，获取第一个线程产生的HoldCounter对象
                        if (rh == null || rh.tid != LockSupport.getThreadId(current))
                            rh = readHolds.get(); // 从ThreadLocal中获取HoldCounter对象
                        else if (rh.count == 0)
                            readHolds.set(rh); // 如果锁计数为0更新锁计数保持其对象
                        rh.count++; // 读锁计数累加
                        cachedHoldCounter = rh; // 保存读锁计数器对象
                    }
                    return 1; // 读锁加锁成功
                }
            }
        }

        /**
         * 执行tryLock进行写入，从而在两种模式下都可以进行插入。 这与tryAcquire的作用相同，只是缺少对writerShouldBlock的调用。
         */
        @ReservedStackAccess
        final boolean tryWriteLock() {
            Thread current = Thread.currentThread(); // 得到当前线程
            int c = getState(); // 得到锁计数
            if (c != 0) { // 不为0说明加过锁
                int w = exclusiveCount(c); // 得到写锁次数
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false;// 写锁被其它线程占用，当前线程抢占写锁失败
                if (w == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
            }
            // 第一次就加写锁，cas更新state值
            if (!compareAndSetState(c, c + 1))
                return false;
            // 将当前线程设置为独占
            setExclusiveOwnerThread(current);
            return true;// 写锁加锁成功！
        }

        /**
         * 执行tryLock进行读取，从而在两种模式下都可以进行插入。 除了没有调用readerReaderShouldBlock以外，这与tryAcquireShared的作用相同。
         */
        @ReservedStackAccess
        final boolean tryReadLock() {
            Thread current = Thread.currentThread(); // 获取当前线程
            for (; ; ) {
                int c = getState(); // 获取锁计数
                // 存在写的情况
                if (exclusiveCount(c) != 0 && getExclusiveOwnerThread() != current)
                    return false;
                // 不存在写的情况
                int r = sharedCount(c); // 计算读锁的次数
                if (r == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded"); // 值越界
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                    if (r == 0) {
                        firstReader = current; // 第一个线程进来读计数为0，保存第一个线程
                        firstReaderHoldCount = 1; // 设置计数为1
                    } else if (firstReader == current) { // 重入读
                        firstReaderHoldCount++; // 读计数累加1
                    } else {
                        // 其它线程进入
                        HoldCounter rh = cachedHoldCounter;// 其它线程尝试获取读锁，获取第一个线程产生的HoldCounter对象
                        if (rh == null || rh.tid != LockSupport.getThreadId(current))
                            cachedHoldCounter = rh = readHolds.get();// 从ThreadLocal中获取HoldCounter对象
                        else if (rh.count == 0)
                            readHolds.set(rh); // 如果锁计数为0更新锁计数保持其对象
                        rh.count++; // 读计数累加
                    }
                    return true; // 加读锁成功
                }
            }
        }

        protected final boolean isHeldExclusively() {
            // 虽然我们必须在拥有者之前先阅读一下状态
            // 我们不需要检查当前线程是否为所有者
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        // 与外部有关的方法
        // 得到Condition对象
        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        // 得到持有锁线程
        final Thread getOwner() {
            return ((exclusiveCount(getState()) == 0) ? null : getExclusiveOwnerThread());
        }
        // 得到读锁的上锁次数
        final int getReadLockCount() {
            return sharedCount(getState());
        }
        //是否上了写锁
        final boolean isWriteLocked() {
            return exclusiveCount(getState()) != 0;
        }
        // 得到写锁上锁次数
        final int getWriteHoldCount() {
            return isHeldExclusively() ? exclusiveCount(getState()) : 0;
        }
        // 得到读锁的上锁次数
        final int getReadHoldCount() {
            if (getReadLockCount() == 0) // 读锁上锁次数是否为0
                return 0;
            Thread current = Thread.currentThread(); // 获取当前线程
            if (firstReader == current) // 当前线程是否是第一个上读的锁线程
                return firstReaderHoldCount;// 返回锁计数
            HoldCounter rh = cachedHoldCounter; // 读计数器对象
            if (rh != null && rh.tid == LockSupport.getThreadId(current))
                return rh.count; // 返回读计数器存储的锁计数
            int count = readHolds.get().count; // 得到计数
            if (count == 0) readHolds.remove();// 清除读计数器对象
            return count; // 返回计数
        }

        /**
         * 从流中重构实例（即反序列化它）。
         */
        private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            readHolds = new ThreadLocalHoldCounter();
            setState(0); // 重置为解锁状态
        }

        // 得到state值，读锁写锁都在
        final int getCount() {
            return getState();// 返回state
        }
    }

    /**
     * 非公平锁
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = -8159625535654395037L;
        // 默认返回false
        final boolean writerShouldBlock() {
            return false;
        }

        // 阻塞当前读锁线程，如果是第一次调用就会返回false因为没有阻塞线程
        final boolean readerShouldBlock() {
            return apparentlyFirstQueuedIsExclusive(); // aqs第一个节点是排他节点则返回true，否则返回false
        }
    }

    /**
     * 公平锁
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -2274990926593161451L;

        final boolean writerShouldBlock() {
            return hasQueuedPredecessors();
        }

        final boolean readerShouldBlock() {
            return hasQueuedPredecessors();
        }
    }

    /**
     * ReentrantReadWriteLock.readLock()返回读锁的实例
     */
    public static class ReadLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -5992448646407690164L;
        private final Sync sync;

        /**
         * 构造方法
         */
        protected ReadLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }

        /**
         * 尝试获取读锁
         */
        public void lock() {
            sync.acquireShared(1);
        }

        /**
         * 可以被中断的方式获取锁
         */
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireSharedInterruptibly(1);
        }

        /**
         * 尝试获取读锁
         */
        public boolean tryLock() {
            return sync.tryReadLock();
        }

        /**
         * 超过规定的时间内抢占锁，则中断获取锁操作
         */
        public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
            return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
        }

        /**
         * 释放锁
         */
        public void unlock() {
            sync.releaseShared(1);
        }

        /**
         * 不允许操作，在这里是由于实现了Lock接口不得不写这个方法，如果调用则直接抛异常
         */
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }

        /**
         * toString方法
         */
        public String toString() {
            int r = sync.getReadLockCount();
            return super.toString() +
                    "[Read locks = " + r + "]";
        }
    }

    /**
     * 通过ReentrantReadWriteLock.writeLock()返回该对象的实例
     */
    public static class WriteLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -4992448646407690164L;
        private final Sync sync;

        /**
         * 构造方法
         */
        protected WriteLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }

        /**
         * 尝试获取锁
         * 调用的是带一个参数的acquire方法，因此里面会进行一次尝试抢占锁，失败则进入aqs队列
         */
        public void lock() {
            sync.acquire(1);
        }

        /**
         * 以可以被中断的方式抢占锁
         */
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireInterruptibly(1);
        }

        /**
         * 尝试获取锁
         */
        public boolean tryLock() {
            return sync.tryWriteLock();
        }

        /**
         * 超过规定时间则中断获取锁操作
         */
        public boolean tryLock(long timeout, TimeUnit unit)
                throws InterruptedException {
            return sync.tryAcquireNanos(1, unit.toNanos(timeout));
        }

        /**
         * 释放锁
         */
        public void unlock() {
            sync.release(1);
        }

        /**
         * 由于实现Lock接口不得不实现的方法，如果直接调用则直接抛异常
         */
        public Condition newCondition() {
            return sync.newCondition();
        }

        /**
         * toString方法
         */
        public String toString() {
            Thread o = sync.getOwner();
            return super.toString() + ((o == null) ?
                    "[Unlocked]" :
                    "[Locked by thread " + o.getName() + "]");
        }

        /**
         * 判断当前线程是否持有锁
         */
        public boolean isHeldByCurrentThread() {
            return sync.isHeldExclusively();
        }

        /**
         * 如果当前线程没有持有锁则返回0，否则返回加锁计数
         */
        public int getHoldCount() {
            return sync.getWriteHoldCount();
        }
    }


    /**
     * 是否是公平锁
     */
    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    /**
     * 返回当前持有锁的线程
     */
    protected Thread getOwner() {
        return sync.getOwner();
    }

    /**
     * 获取读锁的计数
     */
    public int getReadLockCount() {
        return sync.getReadLockCount();
    }

    /**
     * 是否是写锁上的锁
     */
    public boolean isWriteLocked() {
        return sync.isWriteLocked();
    }

    /**
     * 当前线程是否是获取到写锁的线程
     */
    public boolean isWriteLockedByCurrentThread() {
        return sync.isHeldExclusively();
    }

    /**
     * 获取当前线程写锁上锁的次数
     */
    public int getWriteHoldCount() {
        return sync.getWriteHoldCount();
    }

    /**
     * 获取当前线程读锁上锁的次数
     */
    public int getReadHoldCount() {
        return sync.getReadHoldCount();
    }

    /**
     * 获取正在获取写锁的线程的集合
     */
    protected Collection<Thread> getQueuedWriterThreads() {
        return sync.getExclusiveQueuedThreads();
    }

    /**
     * 获取正在获取读锁的线程的集合
     */
    protected Collection<Thread> getQueuedReaderThreads() {
        return sync.getSharedQueuedThreads();
    }

    /**
     * 是否有线程正在等待获取 读锁或写锁
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * 判断线程是否在等待队列中
     */
    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    /**
     * 返回等待读或者等待写的线程的估计值
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     * 返回等待读或写的线程的集合
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    /**
     * 是否有等待线程，有则返回true
     */
    public boolean hasWaiters(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject) condition);
    }

    /**
     * 返回等待线程的估计数量
     */
    public int getWaitQueueLength(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject) condition);
    }

    /**
     * 返回符合Condition条件的等待线程的集合
     */
    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject) condition);
    }

    /**
     * toString方法
     */
    public String toString() {
        int c = sync.getCount();
        int w = Sync.exclusiveCount(c);
        int r = Sync.sharedCount(c);

        return super.toString() +
                "[Write locks = " + w + ", Read locks = " + r + "]";
    }

}
