package java.util.concurrent;
/**
 * jdk14 完结
 */
import java.util.Collection;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

public class Semaphore implements java.io.Serializable {
    private static final long serialVersionUID = -3222578661600680210L; // 序列化版本号
    private final Sync sync; // 抽象队列同步器的子类成员常量


    // 构造方法
    /**
     * 构造一个有permits个信号量的非公平同步器
     */
    public Semaphore(int permits) {
        sync = new NonfairSync(permits);
    }

    /**
     * 构造一个有permits个信号量
     * 如果传入的布尔值fair=true则是公平同步器，false则是非公平同步器
     */
    public Semaphore(int permits, boolean fair) {
        sync = fair ? new FairSync(permits) : new NonfairSync(permits);
    }


    /**
     * 下面是常用方法
     */
    /**
     * 获取信号量
     */
    // 获取一个信号量，如果信号量被使用完则抛异常InterruptedException
    public void acquire() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

    // 获取permits个信号量，如果不够则抛异常InterruptedException
    public void acquire(int permits) throws InterruptedException {
        if (permits < 0) throw new IllegalArgumentException();
        sync.acquireSharedInterruptibly(permits);
    }

    // 获取permits个不可中断的信号量
    public void acquireUninterruptibly(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        sync.acquireShared(permits);
    }

    // 阻塞式（就是等待）获取信号量，当其它信号量被释放，并执行当前代码则成功
    public void acquireUninterruptibly() {
        sync.acquireShared(1);
    }

    /**
     * 释放信号量方法
     */
    // 释放一个信号量
    public void release() {
        sync.releaseShared(1);
    }

    // 释放permits个信号量
    public void release(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        sync.releaseShared(permits);
    }

    /**
     * 常用信号量辅助方法
     */
    // 获取所有信号量，并将所有信号量都使用掉，返回0
    public int drainPermits() {
        return sync.drainPermits();
    }

    // 获取可用的信号量个数
    public int availablePermits() {
        return sync.getPermits();
    }

    // 获取等待线程队列的长度
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     * 判断信号量方法
     */
    // 判断是否可以获取一个信号量
    public boolean tryAcquire() {
        return sync.nonfairTryAcquireShared(1) >= 0;
    }
    // 判断是否可以获取permits个信号量
    public boolean tryAcquire(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        return sync.nonfairTryAcquireShared(permits) >= 0;
    }
    // 判断在指定时间内可否获取一个信号量,超过等待时间timeout则抛异常InterruptedException
    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }
    // 判断在timeout时间内是否可以获取permits个信号量throws InterruptedException
    public boolean tryAcquire(int permits, long timeout, TimeUnit unit) throws InterruptedException {
        if (permits < 0) throw new IllegalArgumentException();
        return sync.tryAcquireSharedNanos(permits, unit.toNanos(timeout));
    }
    // 判断是否是公平机制的信号同步器
    public boolean isFair() {
        return sync instanceof FairSync;
    }
    // 判断线程队列是否有线程,有线程在等待获取信号量则返回true，否则返回false
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * toString方法得到信号量
     */
    public String toString() {
        return super.toString() + "[Permits = " + sync.getPermits() + "]";
    }


    /**
     * 获取线程队列的集合
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }
    // 减少reduction个信号量（总量减少）
    protected void reducePermits(int reduction) {
        if (reduction < 0) throw new IllegalArgumentException();
        sync.reducePermits(reduction);
    }

    /**
     * 同步器的抽象方法
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 1192457210091910933L;  // 序列化版本号

        // 带一个参数的构造方法
        Sync(int permits) {
            setState(permits);// 调用AQS中的setState
        }
        // 获得许可数量
        final int getPermits() {
            return getState();//调用AQS的方法
        }

        // 使用acquires个信号量，失败则直接返回计算得到的剩余信号量的值
        final int nonfairTryAcquireShared(int acquires) {
            for (; ; ) {
                int available = getState();//不断自旋获取当前剩余信号量
                int remaining = available - acquires;//计算剩余信号量
                //前面如果false则需要执行后面的cas操作更新信号量，如果剩余信号量大于0则将这个负值的信号量直接返回
                if (remaining < 0 || compareAndSetState(available, remaining))
                    return remaining;
            }
        }
        // 释放releases个信号量 失败则会抛异常
        protected final boolean tryReleaseShared(int releases) {
            for (; ; ) {
                int current = getState();// 不断自旋获取当前许可值
                int next = current + releases;// 计算释放后的新许可值
                if (next < current) // 超过了最大值（构造的时候传入的值）需要抛异常
                    throw new Error("Maximum permit count exceeded");
                if (compareAndSetState(current, next))// 说明没有超过最大值，进行更新许可值，并返回true
                    return true;
            }
        }

        // 使用reductions个信号量减少的是总量，也就是最大值
        final void reducePermits(int reductions) {
            for (; ; ) {
                int current = getState(); // 当前许可
                int next = current - reductions;// 计算减少后的新的总量
                if (next > current) // 新的总量不能比之前的总量大，大则抛异常
                    throw new Error("Permit count underflow");
                if (compareAndSetState(current, next))
                    return;
            }
        }

        final int drainPermits() {
            for (; ; ) {
                int current = getState();
                if (current == 0 || compareAndSetState(current, 0)) // 将信号量更新为0，表示全部使用完了
                    return current;
            }
        }
    }

    /**
     * 实现同步器的公平同步器
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = 2014338818796000944L;

        FairSync(int permits) {
            super(permits);
        }

        //尝试获取信号量（公平锁机制）
        protected int tryAcquireShared(int acquires) {
            for (; ; ) {
                if (hasQueuedPredecessors()) //如果已经形成了AQS队列，说明信号量早用完了，直接返回-1
                    return -1;
                int available = getState();  //当前可用信号量
                int remaining = available - acquires;// 计算使用了acquires信号量的值
                if (remaining < 0 || compareAndSetState(available, remaining))// 值小于0直接返回计算后的负值remaining，不小于0则说明不会进行阻塞
                    return remaining;
            }
        }
    }

    /**
     * 实现同步器的非公平同步器
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = -2694183684443567898L;

        NonfairSync(int permits) {
            super(permits);
        }

        //尝试获取信号量（非公平锁机制）
        protected int tryAcquireShared(int acquires) {
            return nonfairTryAcquireShared(acquires); // 调用父类Sync种的final方法
        }
    }

}

