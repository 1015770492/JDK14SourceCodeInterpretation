package java.util.concurrent;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


public class CyclicBarrier {

    private final ReentrantLock lock = new ReentrantLock(); // 底层是可重入的非公平锁
    private final Condition trip = lock.newCondition(); // condition接口
    private final int parties; // 总数，构造时传入，目的时做屏障器的计数（被设计成常量也是出于此，目的就是为了重复利用屏障器）
    private int count; // 计数（count则会根据调用await减一，为0后会被重新赋值为parties）
    private final Runnable barrierCommand; // runable的线程任务
    private Generation generation = new Generation(); // 存在目的是循环使用CyclicBarrier，相当于每一次循环这个对象就会变具体看 nextGeneration()

    /**
     * 构造方法，传入屏障器计数和Runnable接口的任务
     */
    public CyclicBarrier(int parties, Runnable barrierAction) {
        if (parties <= 0) throw new IllegalArgumentException(); // 如果屏障器的计数非正数则不合法，抛异常
        this.parties = parties; // 总数
        this.count = parties;   // 相当于信号量，每调用一次await就会被减1，为0重新赋值为parties
        this.barrierCommand = barrierAction;// 当计数为0时会执行这个Runnable任务
    }

    /**
     * 构造方法
     */
    public CyclicBarrier(int parties) {
        this(parties, null);
    }

    /*静态内部类*/
    private static class Generation {
        Generation() {}                 // 构造方法
        boolean broken;                 // 初始化会是false，如果为true表示CyclicBarrier不再进行循环
    }


    /**
     * 唤醒阻塞线程，进行下一个拦截循环
     */
    private void nextGeneration() {
        trip.signalAll(); // trip时可重入锁得到的condition接口，调用这个方法会唤醒所有正在等待的线程
        count = parties;  // 唤醒了所有的线程，因此count计数会重新赋值为parties，相当于重用屏障器
        generation = new Generation(); // 生成新的
    }

    /**
     * 暂停拦截器作用
     */
    private void breakBarrier() {
        generation.broken = true;   // 设置为false
        count = parties;            // count重新赋值为parties，再次使用时就是新的循环
        trip.signalAll();           // 唤醒所有被阻塞的线程
    }

    /**
     * 拦截线程的方法，被await调用传入的是false，0
     */
    private int dowait(boolean timed, long nanos) throws InterruptedException, BrokenBarrierException, TimeoutException {
        final ReentrantLock lock = this.lock; //可重入锁
        lock.lock(); // 加锁
        try {
            final Generation g = generation; // 得到当前拦截器的用于标识的Generation

            if (g.broken) // 如果为true，说明暂停了拦截（调用了breakBarrier方法）导致
                throw new BrokenBarrierException();//因为已经被叫停拦截器了所以抛异常

            if (Thread.interrupted()) { // 当前线程是否被中断
                breakBarrier(); // 暂停拦截器
                throw new InterruptedException(); // 抛出被中断异常
            }
            // 计算剩余值
            int index = --count; // 如果执行到这里，说明拦截器正常使用，因此计数减一，相当于使用了一个信号一个意思，而parties则是总信号量
            // 如果剩余值为0执行任务
            if (index == 0) {  // 当前线程使用了一个计数后（相当于使用了一个信号量，判断信号量是否被使用完了）如果为0则进入
                Runnable command = barrierCommand; // 任务
                if (command != null) { // 如果任务不为空
                    try {
                        command.run(); // 执行任务
                    } catch (Throwable ex) {
                        breakBarrier(); //异常则暂停拦截器并且下面throw异常
                        throw ex;
                    }
                }
                nextGeneration(); // 生成下一个循环拦截
                return 0;//
            }

            // 如果剩余值不为0，则将当前线程阻塞
            /**
             * 自旋方式降低cpu上下文切换的可能
             */
            for (;;) {
                //下面的try似乎不满足条件所以看后面
                try {
                    if (!timed) // 默认传入的是false，!false=true，所以会让线程阻塞
                        trip.await(); // 通过condition接口阻塞当前线程
                    else if (nanos > 0L)// 默认是0所以不大于
                        nanos = trip.awaitNanos(nanos); // 阻塞nanos纳秒，实际默认值是0纳秒
                } catch (InterruptedException ie) {
                    if (g == generation && ! g.broken) { // 如果等待过程中被修改了则需要抛异常
                        breakBarrier(); // 赞赏他使用拦截器并抛异常
                        throw ie;
                    } else {
                        Thread.currentThread().interrupt(); // 中断当前线程
                    }
                }

                if (g.broken)  // 不执行
                    throw new BrokenBarrierException();

                if (g != generation) // 不会执行
                    return index;

                if (timed && nanos <= 0L) { // 不会执行
                    breakBarrier();
                    throw new TimeoutException();
                }
            }
        } finally {
            lock.unlock();//解锁
        }
    }

    // 获得parties的值（拦截器能够拦截线程的总数）
    public int getParties() {
        return parties;
    }

    /**
     * 拦截线程，等待通知.最常用方法，本质调用dowait方法
     */
    public int await() throws InterruptedException, BrokenBarrierException {
        try {
            return dowait(false, 0L);
        } catch (TimeoutException toe) {
            throw new Error(toe); // cannot happen
        }
    }

    /**
     * 拦截线程等待通知
     */
    public int await(long timeout, TimeUnit unit) throws InterruptedException, BrokenBarrierException, TimeoutException {
        return dowait(true, unit.toNanos(timeout));
    }

    /**
     * 返回generation.broken的值，目的是根据true/false判断是否拦截器是否继续使用，false则暂停使用
     */
    public boolean isBroken() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return generation.broken;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 重置拦截器
     */
    public void reset() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            breakBarrier();   // 暂停拦截器
            nextGeneration(); // 生成下一个循环
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取正在等待的线程数
     */
    public int getNumberWaiting() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return parties - count; // 相当于总信号量-剩余信号量=正在执行的线程数（在CyclicBarrier则是自旋的线程数）
        } finally {
            lock.unlock();
        }
    }
}
