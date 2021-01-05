package java.util.concurrent.locks;
/**
 * jdk14 完结
 */
import java.io.Serializable;
import java.util.Collection;
import java.util.concurrent.TimeUnit;


public class ReentrantLock implements Lock, Serializable {
    private static final long serialVersionUID = 7373984872572414699L; // 序列化版本号
    private final Sync sync; // 同步器

    /**
     * Sync是公平锁和非公平锁的父类
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -5179523762034025860L;

        // 初始化锁,由子类实现具体功能
        abstract boolean initialTryLock();
        // 不管是公平锁还是非公平锁的lock(),最终都会执行这个final修饰的lock方法，因此是公有不可重写的方法
        @ReservedStackAccess
        final void lock() {

            //initialTryLock()本质上调用的是子类重写的方法例如：NonfairSync和FairSync内的initialTryLock()
            // 只有已经上锁了，当前线程没有获得到锁才会进入if
            if (!initialTryLock()){
                /**
                 * 如果是不同的线程则返回false,就执行if内的 acquire(1);
                 * 又去执行AQS的 public final void acquire(int arg)方法，
                 * 意思就是需要将其加入线程等待队列中（判断中initialTryLock()就已经知道这个线程没有获得到锁返回了false）
                 */
                acquire(1);// AQS的线程队列是在这个方法内部形成的，需求去父类AQS查看这个final修饰方法
            }

        }


        // 尝试加锁
        @ReservedStackAccess
        final boolean tryLock() {
            Thread current = Thread.currentThread();// 获取当前线程对象
            int c = getState();// 获取当前线程节点的state值
            if (c == 0) {       // c=0说明没有加锁,>0说明加锁了
                if (compareAndSetState(0, 1)) {// 进行加锁，通过cas操作将state更新为1（就表示加锁）
                    setExclusiveOwnerThread(current);  // 将当前线程设置为独占访问，也就是真正的加锁含义，上面的state只是一个值，而这个操作是真正的让加锁代码段只能让当前线程访问
                    return true;
                }
            } else if (getExclusiveOwnerThread() == current) {// c!=0说明之前上过锁，需要判断当前线程和上锁的线程是否是同一个线程（可重入的含义）
                if (++c < 0) // c超过int的最大值，也就是说同一个线程重入了很多次，导致c超过了最大值变成了负数。需要抛异常提示
                    throw new Error("Maximum lock count exceeded");
                setState(c);// 更新state值因为前面判断++c将c自增了，相当于前面已经c=c+1，相当于加锁次数+1
                return true;
            }
            return false;
        }
        // 尝试释放锁
        @ReservedStackAccess
        protected final boolean tryRelease(int releases) {
            int c = getState() - releases;//计算释放后的state值
            if (getExclusiveOwnerThread() != Thread.currentThread())// 如果当前线程不是和独占线程同一个线程抛异常IllegalMonitorStateException
                throw new IllegalMonitorStateException();
            boolean free = (c == 0);// 计算是否要清除独占先（计算释放后是否还有线程持有锁）
            if (free)
                setExclusiveOwnerThread(null);//清除独占线程
            setState(c);//更新state值
            return free;
        }

        @ReservedStackAccess
        final void lockInterruptibly() throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            if (!initialTryLock())
                acquireInterruptibly(1);
        }

        @ReservedStackAccess
        final boolean tryLockNanos(long nanos) throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            return initialTryLock() || tryAcquireNanos(1, nanos);
        }



        // 判断当前线程是否是独占线程，实际上就是判断当前线程和独占线程是否是同一个线程
        protected final boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }
        // 返回AQS的内部类ConditionObject实例
        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        // 获得独占线程如果没有独占线程返回null
        final Thread getOwner() {
            return getState() == 0 ? null : getExclusiveOwnerThread();
        }
        // 返回加锁次数state值，如果不是独占线程则返回0
        final int getHoldCount() {
            return isHeldExclusively() ? getState() : 0;
        }

        final boolean isLocked() {
            return getState() != 0; // 如果state=0则说明没有线程加锁 0!=0则会返回false，其它情况都是true则说明加锁了
        }

        /**
         * Reconstitutes the instance from a stream (that is, deserializes it).
         */
        private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            setState(0); // reset to unlocked state
        }
    }

    /**
     * 非公平锁
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = 7316153563782823691L;// 序列化版本号
        final boolean initialTryLock() {
            Thread current = Thread.currentThread();        // 获取当前线程
            if (compareAndSetState(0, 1)) {   // 之前没有加锁，则将state更新为1，并进行加锁 setExclusiveOwnerThread(current);
                setExclusiveOwnerThread(current);           // 将当前线程设置为独占
                return true;                                // 加锁成功
            } else if (getExclusiveOwnerThread() == current) {// 之前加过锁，相当于重入锁（也意味着当前线程本来就获得到了锁），第二次进入则进入代码块
                int c = getState() + 1;                        // 将锁计数+1，相当于多次加锁，每加一次锁就会+1
                if (c < 0) // 小于0，可能是超过int的上限导致变成负数抛异常
                    throw new Error("Maximum lock count exceeded");
                setState(c);//更新state值
                return true;
            } else //这个分支说明当前线程所操作的资源已经被加锁了，需要等待释放锁后获得锁。所以返回false
                return false;
        }

        /**
         * 尝试获取锁（加锁）acquires次
         */
        protected final boolean tryAcquire(int acquires) {
            // 如果加锁线程已经释放了锁，也就是state=0那么就将当前线程设置为独占线程并更新state值为1，表示抢占锁成功
            if (getState() == 0 && compareAndSetState(0, acquires)) {
                setExclusiveOwnerThread(Thread.currentThread());// 将当前线程设置为独占线程，表示当前线程获得到锁
                return true;
            }
            return false;//如果已经加过锁了由于state则不为0则会返回false，表示抢占锁失败
        }
    }

    /**
     * 公平锁
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -3000897897090466540L;// 序列化版本号

        /**
         * 初始化尝试加锁
         */
        final boolean initialTryLock() {
            Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                // 是否有aqs队列，如果有则不进入，最终返回false。 如果没有则进行第一次加锁更新state为1
                if (!hasQueuedThreads() && compareAndSetState(0, 1)) {
                    setExclusiveOwnerThread(current);// 将当前线程设置为独占线程
                    return true;
                }
            } else if (getExclusiveOwnerThread() == current) { // 重入锁，和前面非公平锁一样的道理
                if (++c < 0) //
                    throw new Error("Maximum lock count exceeded");
                setState(c);
                return true;
            }
            return false; // 说明当前资源被其它线程独占，加锁失败（也可以称为获得锁失败！）
        }

        /**
         * 加锁，和前面的非公平锁一样
         */
        protected final boolean tryAcquire(int acquires) {
            if (getState() == 0 && !hasQueuedPredecessors() && compareAndSetState(0, acquires)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }
    }

    /**
     * 默认创建一个非公平锁
     */
    public ReentrantLock() {
        sync = new NonfairSync();
    }

    /**
     * true则是公平锁，false则是非公平锁
     */
    public ReentrantLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
    }

    /**
     * 尝试加锁
     * 如果没有线程加锁则立即将锁计数记为1
     * 如果是同一个线程调用这个方法则计数+1
     * 如果已经被其它线程上锁了，则禁止当前线程调度。知道其它线程释放了锁
     */
    public void lock() {
        sync.lock();
    }
    /**
     * 释放锁
     */
    public void unlock() {
        sync.release(1);
    }
    /**
     * 获取锁，除非当前线程被中断
     */
    public void lockInterruptibly() throws InterruptedException {
        sync.lockInterruptibly();
    }

    /**
     * 尝试加锁
     */
    public boolean tryLock() {
        return sync.tryLock();
    }

    /**
     * 尝试在规定的实际内获得到锁，传入时间数值、以及时间单位。超时抛异常InterruptedException
     */
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryLockNanos(unit.toNanos(timeout));
    }



    /**
     * 得到AQS的内部类ConditionObject的实例
     */
    public Condition newCondition() {
        return sync.newCondition();
    }

    /**
     * 如果当前线程是独占的则返回state值，否则返回0
     */
    public int getHoldCount() {
        return sync.getHoldCount();
    }

    /**
     * 是否被当前线程获得到了锁
     */
    public boolean isHeldByCurrentThread() {
        return sync.isHeldExclusively();
    }

    /**
     * true则说明加锁了，false则说明没有加锁。
     * 底层是直接 return state != 0;
     */
    public boolean isLocked() {
        return sync.isLocked();
    }

    /**
     * 是否是公平锁
     */
    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    /**
     * 获取独占线程，如果没有独占线程则返回null
     */
    protected Thread getOwner() {
        return sync.getOwner();
    }

    /**
     * 是否有等待线程
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     *
     */
    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    /**
     *
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     *
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    /**
     *
     */
    public boolean hasWaiters(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     *
     */
    public int getWaitQueueLength(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     *
     */
    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     *
     */
    public String toString() {
        Thread o = sync.getOwner();
        return super.toString() + ((o == null) ?
                                   "[Unlocked]" :
                                   "[Locked by thread " + o.getName() + "]");
    }
}
