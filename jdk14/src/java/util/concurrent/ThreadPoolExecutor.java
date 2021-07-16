package java.util.concurrent;
/**
 * jdk14
 */

import java.util.*;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * int的前3位表示线程池状态,后面29位表示该池中最大的线程数量
 */
public class ThreadPoolExecutor extends AbstractExecutorService {
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));//
    private static final int COUNT_BITS = Integer.SIZE - 3;// 29
    private static final int COUNT_MASK = (1 << COUNT_BITS) - 1;// 前面3个0,后面29个1
    /**
     * 下面是关于线程池状态的几个常量，主要还是最高的3位所表示的数
     *
     * RUNNING 接受新任务，执行已入队任务
     * SHUTDOWN 不接受新任务, 但执行已入队的任务, 中断所有空闲的线程
     * TIDYING	所有线程停止, workerCount数量为0, 将执行hook方法: terminated()
     * TERMINATED terminated()方法执行完毕
     */
    private static final int RUNNING = -1 << COUNT_BITS;        // 前面3个1,后面29个0
    private static final int SHUTDOWN = 0 << COUNT_BITS;        // 关闭状态0
    private static final int STOP = 1 << COUNT_BITS;            // 停止状态1 后面29个0
    private static final int TIDYING = 2 << COUNT_BITS;         // 整理状态10 后面29个0
    private static final int TERMINATED = 3 << COUNT_BITS;      // 终止状态11 后面29个0

    private final BlockingQueue<Runnable> workQueue;            // 工作队列（阻塞队列）
    private final ReentrantLock mainLock = new ReentrantLock(); // 重入锁
    private final HashSet<Worker> workers = new HashSet<>();    // 工作队列的set集合
    private final Condition termination = mainLock.newCondition(); // condition
    private volatile int corePoolSize;                          // 核心线程数量
    private volatile int maximumPoolSize;                       // 线程池最大数量
    private volatile long keepAliveTime;                        // 存活时间
    private int largestPoolSize;                                // 池最大值
    private long completedTaskCount;                            // 已完成任务计数
    private volatile ThreadFactory threadFactory;               // 线程工程
    private volatile RejectedExecutionHandler handler;          // 拒绝策略
    private volatile boolean allowCoreThreadTimeOut;            // 核心线程超时时间
    private static final RejectedExecutionHandler defaultHandler = new AbortPolicy();// 默认的拒绝策略,抛弃新任务并且抛异常
    private static final RuntimePermission shutdownPerm = new RuntimePermission("modifyThread");//

    // 得到状态
    private static int runStateOf(int c) {
        return c & ~COUNT_MASK;
    }
    // 得到后面29位所表示的数
    private static int workerCountOf(int c) {
        return c & COUNT_MASK;
    }
    // 合并成一个数据，也就是将前3位和后面29位合并成一个新的32位的int型数据
    private static int ctlOf(int rs, int wc) {
        return rs | wc;
    }
    //
    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }
    //
    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }
    //
    private static boolean isRunning(int c) {
        return c < SHUTDOWN;
    }
    //
    private boolean compareAndIncrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect + 1);
    }
    //
    private boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }
    //
    private void decrementWorkerCount() {
        ctl.addAndGet(-1);
    }


    /**
     *
     */
    private final class Worker extends AbstractQueuedSynchronizer implements Runnable {

        private static final long serialVersionUID = 6138294804551838833L;
        final Thread thread; //
        Runnable firstTask;  //
        volatile long completedTasks;

        /**
         *
         */
        Worker(Runnable firstTask) {
            setState(-1); // 禁止中断，直到runWorker
            this.firstTask = firstTask; // 新任务
            this.thread = getThreadFactory().newThread(this);// 从工厂中获取到线程
        }

        /**
         *
         */
        public void run() {
            runWorker(this);
        }


        protected boolean isHeldExclusively() {
            return getState() != 0;
        }

        protected boolean tryAcquire(int unused) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        public void lock() {
            acquire(1);
        }

        public boolean tryLock() {
            return tryAcquire(1);
        }

        public void unlock() {
            release(1);
        }

        public boolean isLocked() {
            return isHeldExclusively();
        }

        void interruptIfStarted() {
            Thread t;
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }


    /**
     *
     */
    private void advanceRunState(int targetState) {
        // assert targetState == SHUTDOWN || targetState == STOP;
        for (; ; ) {
            int c = ctl.get();
            if (runStateAtLeast(c, targetState) ||
                    ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c))))
                break;
        }
    }

    /**
     *
     */
    final void tryTerminate() {
        for (; ; ) {
            int c = ctl.get();
            if (isRunning(c) ||
                    runStateAtLeast(c, TIDYING) ||
                    (runStateLessThan(c, STOP) && !workQueue.isEmpty()))
                return;
            if (workerCountOf(c) != 0) { // Eligible to terminate
                interruptIdleWorkers(ONLY_ONE);
                return;
            }

            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        terminated();
                    } finally {
                        ctl.set(ctlOf(TERMINATED, 0));
                        termination.signalAll();
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }
        }
    }


    /**
     *
     */
    private void checkShutdownAccess() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(shutdownPerm);
            for (Worker w : workers)
                security.checkAccess(w.thread);
        }
    }

    /**
     *
     */
    private void interruptWorkers() {
        for (Worker w : workers)
            w.interruptIfStarted();
    }

    /**
     *
     */
    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                Thread t = w.thread;
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
                if (onlyOne)
                    break;
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     *
     */
    private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }

    private static final boolean ONLY_ONE = true;


    /**
     *
     */
    final void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    /**
     *
     */
    void onShutdown() {
    }

    /**
     *
     */
    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> q = workQueue;
        ArrayList<Runnable> taskList = new ArrayList<>();
        q.drainTo(taskList);
        if (!q.isEmpty()) {
            for (Runnable r : q.toArray(new Runnable[0])) {
                if (q.remove(r))
                    taskList.add(r);
            }
        }
        return taskList;
    }


    /**
     *
     */
    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        for (int c = ctl.get(); ; ) {
            // Check if queue empty only if necessary.
            if (runStateAtLeast(c, SHUTDOWN)
                    && (runStateAtLeast(c, STOP)
                    || firstTask != null
                    || workQueue.isEmpty()))
                return false;

            for (; ; ) {
                if (workerCountOf(c)
                        >= ((core ? corePoolSize : maximumPoolSize) & COUNT_MASK))
                    return false;
                if (compareAndIncrementWorkerCount(c))
                    break retry;
                c = ctl.get();  // Re-read ctl
                if (runStateAtLeast(c, SHUTDOWN))
                    continue retry;
            }
        }

        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            w = new Worker(firstTask);
            final Thread t = w.thread;
            if (t != null) {
                final ReentrantLock mainLock = this.mainLock;
                mainLock.lock();
                try {
                    int c = ctl.get();

                    if (isRunning(c) ||
                            (runStateLessThan(c, STOP) && firstTask == null)) {
                        if (t.getState() != Thread.State.NEW)
                            throw new IllegalThreadStateException();
                        workers.add(w);
                        workerAdded = true;
                        int s = workers.size();
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                    }
                } finally {
                    mainLock.unlock();
                }
                if (workerAdded) {
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            if (!workerStarted)
                addWorkerFailed(w);
        }
        return workerStarted;
    }

    /**
     *
     */
    private void addWorkerFailed(Worker w) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (w != null)
                workers.remove(w);
            decrementWorkerCount();
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     *
     */
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        if (completedAbruptly) // If abrupt, then workerCount wasn't adjusted
            decrementWorkerCount();

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            completedTaskCount += w.completedTasks;
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }

        tryTerminate();

        int c = ctl.get();
        if (runStateLessThan(c, STOP)) {
            if (!completedAbruptly) {
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                if (min == 0 && !workQueue.isEmpty())
                    min = 1;
                if (workerCountOf(c) >= min)
                    return; // replacement not needed
            }
            addWorker(null, false);
        }
    }

    /**
     *
     */
    private Runnable getTask() {
        boolean timedOut = false;

        for (; ; ) {
            int c = ctl.get();

            if (runStateAtLeast(c, SHUTDOWN)
                    && (runStateAtLeast(c, STOP) || workQueue.isEmpty())) {
                decrementWorkerCount();
                return null;
            }

            int wc = workerCountOf(c);

            boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;

            if ((wc > maximumPoolSize || (timed && timedOut))
                    && (wc > 1 || workQueue.isEmpty())) {
                if (compareAndDecrementWorkerCount(c))
                    return null;
                continue;
            }

            try {
                Runnable r = timed ?
                        workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                        workQueue.take();
                if (r != null)
                    return r;
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }

    /**
     *
     */
    final void runWorker(Worker w) {
        Thread wt = Thread.currentThread();
        Runnable task = w.firstTask;
        w.firstTask = null;
        w.unlock(); // allow interrupts
        boolean completedAbruptly = true;
        try {
            while (task != null || (task = getTask()) != null) {
                w.lock();
                if ((runStateAtLeast(ctl.get(), STOP) ||
                        (Thread.interrupted() &&
                                runStateAtLeast(ctl.get(), STOP))) &&
                        !wt.isInterrupted())
                    wt.interrupt();
                try {
                    beforeExecute(wt, task);
                    try {
                        task.run();
                        afterExecute(task, null);
                    } catch (Throwable ex) {
                        afterExecute(task, ex);
                        throw ex;
                    }
                } finally {
                    task = null;
                    w.completedTasks++;
                    w.unlock();
                }
            }
            completedAbruptly = false;
        } finally {
            processWorkerExit(w, completedAbruptly);
        }
    }


    /**
     *
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                Executors.defaultThreadFactory(), defaultHandler);
    }

    /**
     *
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                threadFactory, defaultHandler);
    }

    /**
     *
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                Executors.defaultThreadFactory(), handler);
    }

    /**
     *
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        if (corePoolSize < 0 ||
                maximumPoolSize <= 0 ||
                maximumPoolSize < corePoolSize ||
                keepAliveTime < 0)
            throw new IllegalArgumentException();
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }

    /**
     *
     */
    public void execute(Runnable command) {
        if (command == null)
            throw new NullPointerException();
        int c = ctl.get();
        if (workerCountOf(c) < corePoolSize) {
            if (addWorker(command, true))
                return;
            c = ctl.get();
        }
        if (isRunning(c) && workQueue.offer(command)) {
            int recheck = ctl.get();
            if (!isRunning(recheck) && remove(command))
                reject(command);
            else if (workerCountOf(recheck) == 0)
                addWorker(null, false);
        } else if (!addWorker(command, false))
            reject(command);
    }

    /**
     *
     */
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            advanceRunState(SHUTDOWN);
            interruptIdleWorkers();
            onShutdown(); //
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
    }

    /**
     *
     */
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            advanceRunState(STOP);
            interruptWorkers();
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
        return tasks;
    }

    public boolean isShutdown() {
        return runStateAtLeast(ctl.get(), SHUTDOWN);
    }

    /**
     *
     */
    boolean isStopped() {
        return runStateAtLeast(ctl.get(), STOP);
    }

    /**
     *
     */
    public boolean isTerminating() {
        int c = ctl.get();
        return runStateAtLeast(c, SHUTDOWN) && runStateLessThan(c, TERMINATED);
    }

    public boolean isTerminated() {
        return runStateAtLeast(ctl.get(), TERMINATED);
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            while (runStateLessThan(ctl.get(), TERMINATED)) {
                if (nanos <= 0L)
                    return false;
                nanos = termination.awaitNanos(nanos);
            }
            return true;
        } finally {
            mainLock.unlock();
        }
    }


    /**
     *
     */
    @Deprecated(since = "9")
    protected void finalize() {
    }

    /**
     *
     */
    public void setThreadFactory(ThreadFactory threadFactory) {
        if (threadFactory == null)
            throw new NullPointerException();
        this.threadFactory = threadFactory;
    }

    /**
     *
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    /**
     *
     */
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        if (handler == null)
            throw new NullPointerException();
        this.handler = handler;
    }

    /**
     *
     */
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return handler;
    }

    /**
     *
     */
    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize < 0 || maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException();
        int delta = corePoolSize - this.corePoolSize;
        this.corePoolSize = corePoolSize;
        if (workerCountOf(ctl.get()) > corePoolSize)
            interruptIdleWorkers();
        else if (delta > 0) {
            int k = Math.min(delta, workQueue.size());
            while (k-- > 0 && addWorker(null, true)) {
                if (workQueue.isEmpty())
                    break;
            }
        }
    }

    /**
     *
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     *
     */
    public boolean prestartCoreThread() {
        return workerCountOf(ctl.get()) < corePoolSize &&
                addWorker(null, true);
    }

    /**
     *
     */
    void ensurePrestart() {
        int wc = workerCountOf(ctl.get());
        if (wc < corePoolSize)
            addWorker(null, true);
        else if (wc == 0)
            addWorker(null, false);
    }

    /**
     *
     */
    public int prestartAllCoreThreads() {
        int n = 0;
        while (addWorker(null, true))
            ++n;
        return n;
    }

    /**
     *
     */
    public boolean allowsCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    /**
     *
     */
    public void allowCoreThreadTimeOut(boolean value) {
        if (value && keepAliveTime <= 0)
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value;
            if (value)
                interruptIdleWorkers();
        }
    }

    /**
     *
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException();
        this.maximumPoolSize = maximumPoolSize;
        if (workerCountOf(ctl.get()) > maximumPoolSize)
            interruptIdleWorkers();
    }

    /**
     *
     */
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     *
     */
    public void setKeepAliveTime(long time, TimeUnit unit) {
        if (time < 0)
            throw new IllegalArgumentException();
        if (time == 0 && allowsCoreThreadTimeOut())
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        long keepAliveTime = unit.toNanos(time);
        long delta = keepAliveTime - this.keepAliveTime;
        this.keepAliveTime = keepAliveTime;
        if (delta < 0)
            interruptIdleWorkers();
    }

    /**
     *
     */
    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, TimeUnit.NANOSECONDS);
    }


    /**
     * R
     */
    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
    }

    /**
     *
     */
    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        tryTerminate(); // In case SHUTDOWN and now empty
        return removed;
    }

    /**
     *
     */
    public void purge() {
        final BlockingQueue<Runnable> q = workQueue;
        try {
            Iterator<Runnable> it = q.iterator();
            while (it.hasNext()) {
                Runnable r = it.next();
                if (r instanceof Future<?> && ((Future<?>) r).isCancelled())
                    it.remove();
            }
        } catch (ConcurrentModificationException fallThrough) {
            for (Object r : q.toArray())
                if (r instanceof Future<?> && ((Future<?>) r).isCancelled())
                    q.remove(r);
        }

        tryTerminate(); // In case SHUTDOWN and now empty
    }


    /**
     *
     */
    public int getPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            return runStateAtLeast(ctl.get(), TIDYING) ? 0
                    : workers.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     *
     */
    public int getActiveCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int n = 0;
            for (Worker w : workers)
                if (w.isLocked())
                    ++n;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     *
     */
    public int getLargestPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            return largestPoolSize;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     *
     */
    public long getTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers) {
                n += w.completedTasks;
                if (w.isLocked())
                    ++n;
            }
            return n + workQueue.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     *
     */
    public long getCompletedTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers)
                n += w.completedTasks;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     *
     */
    public String toString() {
        long ncompleted;
        int nworkers, nactive;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            ncompleted = completedTaskCount;
            nactive = 0;
            nworkers = workers.size();
            for (Worker w : workers) {
                ncompleted += w.completedTasks;
                if (w.isLocked())
                    ++nactive;
            }
        } finally {
            mainLock.unlock();
        }
        int c = ctl.get();
        String runState =
                isRunning(c) ? "Running" :
                        runStateAtLeast(c, TERMINATED) ? "Terminated" :
                                "Shutting down";
        return super.toString() +
                "[" + runState +
                ", pool size = " + nworkers +
                ", active threads = " + nactive +
                ", queued tasks = " + workQueue.size() +
                ", completed tasks = " + ncompleted +
                "]";
    }

    /* Extension hooks */

    /**
     *
     */
    protected void beforeExecute(Thread t, Runnable r) {
    }

    /**
     *
     */
    protected void afterExecute(Runnable r, Throwable t) {
    }

    /**
     *
     */
    protected void terminated() {
    }

    /* Predefined RejectedExecutionHandlers */

    /**
     *
     */
    public static class CallerRunsPolicy implements RejectedExecutionHandler {
        /**
         *
         */
        public CallerRunsPolicy() {
        }

        /**
         *
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }

    /**
     *
     */
    public static class AbortPolicy implements RejectedExecutionHandler {
        /**
         *
         */
        public AbortPolicy() {
        }

        /**
         *
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() +
                    " rejected from " +
                    e.toString());
        }
    }

    /**
     *
     */
    public static class DiscardPolicy implements RejectedExecutionHandler {
        /**
         *
         */
        public DiscardPolicy() {
        }

        /**
         *
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        }
    }

    /**
     *
     */
    public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        /**
         *
         */
        public DiscardOldestPolicy() {
        }

        /**
         *
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
}
