package java.util.concurrent;


import java.util.concurrent.locks.AbstractQueuedSynchronizer;

public class CountDownLatch {
    private final Sync sync;    // 同步器

    /**
     * 同步计数器的构造方法，传入计数总量
     */
    public CountDownLatch(int count) {
        if (count < 0) throw new IllegalArgumentException("count < 0");
        this.sync = new Sync(count);
    }

    /**
     * 等待计数等于0，如果不为0则进入中断状态
     */
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

    /**
     * 规定时间内 等待计数等于0，如果不为0则进入中断状态
     * @param timeout 最长等待时间
     * @param unit 时间单位，例如传入TimeUnit.SECONDS
     * @throws InterruptedException
     */
    public boolean await(long timeout, TimeUnit unit)
        throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    /**
     * 递减锁计数
     * 如果减到0调用则什么都不会发生
     */
    public void countDown() {
        sync.releaseShared(1);
    }

    /**
     * 返回当前计数
     */
    public long getCount() {
        return sync.getCount(); // 实际上最终调用aqs的getState方法
    }

    /**
     * 返回计数信息的字符串
     */
    public String toString() {
        return super.toString() + "[Count = " + sync.getCount() + "]";
    }

    /**
     * 同步控制计数器
     */
    private static final class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 4982264981922014374L;
        Sync(int count) {
            setState(count); // 给aqs设置state值
        }

        int getCount() {
            return getState(); // 调用aqs中的方法得到state值
        }

        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }

        protected boolean tryReleaseShared(int releases) {
            // 递减计数，直到计数等于0返回
            for (;;) {
                int c = getState();//获取state值
                if (c == 0)
                    return false;// 返回
                int nextc = c - 1;
                if (compareAndSetState(c, nextc)) // 将计数器更新
                    return nextc == 0;
            }
        }
    }
}
