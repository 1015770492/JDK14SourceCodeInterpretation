package java.util.concurrent;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.List;
import java.util.RandomAccess;
import java.util.concurrent.locks.ReentrantLock;


public abstract class ForkJoinTask<V> implements Future<V>, Serializable {
    private static final long serialVersionUID = -7721805057305804111L;
    volatile int status; // 任务状态

    private static final int DONE = 1 << 31; // 必须是正数
    private static final int ABNORMAL = 1 << 18; // 设置正常的个数
    private static final int THROWN = 1 << 17; // set atomically with ABNORMAL
    private static final int SIGNAL = 1 << 16; // 如果任务已满则返回true
    private static final int SMASK = 0xffff;  //short的掩码，为了得到short

    private static final VarHandle STATUS;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            STATUS = l.findVarHandle(ForkJoinTask.class, "status", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static final class ExceptionNode extends WeakReference<ForkJoinTask<?>> {
        final Throwable ex;
        ExceptionNode next;
        final long thrower;  // use id not ref to avoid weak cycles
        final int hashCode;  // store task hashCode before weak ref disappears

        ExceptionNode(ForkJoinTask<?> task, Throwable ex, ExceptionNode next, ReferenceQueue<ForkJoinTask<?>> exceptionTableRefQueue) {
            super(task, exceptionTableRefQueue);
            this.ex = ex;
            this.next = next;
            this.thrower = Thread.currentThread().getId();
            this.hashCode = System.identityHashCode(task);
        }
    }

    static final class AdaptedRunnable<T> extends ForkJoinTask<T> implements RunnableFuture<T> {
        @SuppressWarnings("serial")
        final Runnable runnable;
        @SuppressWarnings("serial")
        T result;

        AdaptedRunnable(Runnable runnable, T result) {
            if (runnable == null) throw new NullPointerException();
            this.runnable = runnable;
            this.result = result; // OK to set this even before completion
        }

        public final T getRawResult() {
            return result;
        }

        public final void setRawResult(T v) {
            result = v;
        }

        public final boolean exec() {
            runnable.run();
            return true;
        }

        public final void run() {
            invoke();
        }

        public String toString() {
            return super.toString() + "[Wrapped task = " + runnable + "]";
        }

        private static final long serialVersionUID = 5232453952276885070L;
    }


    static final class AdaptedRunnableAction extends ForkJoinTask<Void> implements RunnableFuture<Void> {
        @SuppressWarnings("serial") // Conditionally serializable
        final Runnable runnable;

        AdaptedRunnableAction(Runnable runnable) {
            if (runnable == null) throw new NullPointerException();
            this.runnable = runnable;
        }

        public final Void getRawResult() {
            return null;
        }

        public final void setRawResult(Void v) {
        }

        public final boolean exec() {
            runnable.run();
            return true;
        }

        public final void run() {
            invoke();
        }

        public String toString() {
            return super.toString() + "[Wrapped task = " + runnable + "]";
        }

        private static final long serialVersionUID = 5232453952276885070L;
    }


    static final class RunnableExecuteAction extends ForkJoinTask<Void> {
        @SuppressWarnings("serial") // Conditionally serializable
        final Runnable runnable;

        RunnableExecuteAction(Runnable runnable) {
            if (runnable == null) throw new NullPointerException();
            this.runnable = runnable;
        }

        public final Void getRawResult() {
            return null;
        }

        public final void setRawResult(Void v) {
        }

        public final boolean exec() {
            runnable.run();
            return true;
        }

        void internalPropagateException(Throwable ex) {
            rethrow(ex); // rethrow outside exec() catches.
        }

        private static final long serialVersionUID = 5232453952276885070L;
    }


    static final class AdaptedCallable<T> extends ForkJoinTask<T> implements RunnableFuture<T> {
        private static final long serialVersionUID = 2838392045355241008L;
        @SuppressWarnings("serial")
        final Callable<? extends T> callable;
        @SuppressWarnings("serial")
        T result;

        // 构造传入callable接口实现类
        AdaptedCallable(Callable<? extends T> callable) {
            if (callable == null) throw new NullPointerException();
            this.callable = callable;
        }
        // 得到结果值
        public final T getRawResult() {
            return result;
        }
        // 设置结果值
        public final void setRawResult(T v) {
            result = v;
        }
        // 执行任务
        public final boolean exec() {
            try {
                result = callable.call();
                return true;
            } catch (RuntimeException rex) {
                throw rex;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        public final void run() {
            invoke();
        }

        public String toString() {
            return super.toString() + "[Wrapped task = " + callable + "]";
        }

    }


    static boolean isExceptionalStatus(int s) {  // needed by subclasses
        return (s & THROWN) != 0;
    }

    private int setDone() {
        int s;
        if (((s = (int) STATUS.getAndBitwiseOr(this, DONE)) & SIGNAL) != 0)
            synchronized (this) {
                notifyAll();
            }
        return s | DONE;
    }

    private int abnormalCompletion(int completion) {
        for (int s, ns; ; ) {
            if ((s = status) < 0)
                return s;
            else if (STATUS.weakCompareAndSet(this, s, ns = s | completion)) {
                if ((s & SIGNAL) != 0)
                    synchronized (this) {
                        notifyAll();
                    }
                return ns;
            }
        }
    }


    final int doExec() {
        int s;
        boolean completed;
        if ((s = status) >= 0) {
            try {
                completed = exec();
            } catch (Throwable rex) {
                completed = false;
                s = setExceptionalCompletion(rex);
            }
            if (completed)
                s = setDone();
        }
        return s;
    }


    final void internalWait(long timeout) {
        if ((int) STATUS.getAndBitwiseOr(this, SIGNAL) >= 0) {
            synchronized (this) {
                if (status >= 0)
                    try {
                        wait(timeout);
                    } catch (InterruptedException ie) {
                    }
                else
                    notifyAll();
            }
        }
    }


    private int externalAwaitDone() {
        int s = tryExternalHelp();
        if (s >= 0 && (s = (int) STATUS.getAndBitwiseOr(this, SIGNAL)) >= 0) {
            boolean interrupted = false;
            synchronized (this) {
                for (; ; ) {
                    if ((s = status) >= 0) {
                        try {
                            wait(0L);
                        } catch (InterruptedException ie) {
                            interrupted = true;
                        }
                    } else {
                        notifyAll();
                        break;
                    }
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();
        }
        return s;
    }


    private int externalInterruptibleAwaitDone() throws InterruptedException {
        int s = tryExternalHelp();
        if (s >= 0 && (s = (int) STATUS.getAndBitwiseOr(this, SIGNAL)) >= 0) {
            synchronized (this) {
                for (; ; ) {
                    if ((s = status) >= 0)
                        wait(0L);
                    else {
                        notifyAll();
                        break;
                    }
                }
            }
        } else if (Thread.interrupted())
            throw new InterruptedException();
        return s;
    }


    private int tryExternalHelp() {
        int s;
        return ((s = status) < 0 ? s :
                (this instanceof CountedCompleter) ?
                        ForkJoinPool.common.externalHelpComplete(
                                (CountedCompleter<?>) this, 0) :
                        ForkJoinPool.common.tryExternalUnpush(this) ?
                                doExec() : 0);
    }


    private int doJoin() {
        int s;
        Thread t;
        ForkJoinWorkerThread wt;
        ForkJoinPool.WorkQueue w;
        return (s = status) < 0 ? s :
                ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
                        (w = (wt = (ForkJoinWorkerThread) t).workQueue).
                                tryUnpush(this) && (s = doExec()) < 0 ? s :
                                wt.pool.awaitJoin(w, this, 0L) :
                        externalAwaitDone();
    }


    private int doInvoke() {
        int s;
        Thread t;
        ForkJoinWorkerThread wt;
        return (s = doExec()) < 0 ? s :
                ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
                        (wt = (ForkJoinWorkerThread) t).pool.
                                awaitJoin(wt.workQueue, this, 0L) :
                        externalAwaitDone();
    }


    private static final ExceptionNode[] exceptionTable = new ExceptionNode[32];

    private static final ReentrantLock exceptionTableLock = new ReentrantLock();


    private static final ReferenceQueue<ForkJoinTask<?>> exceptionTableRefQueue = new ReferenceQueue<>();


    final int recordExceptionalCompletion(Throwable ex) {
        int s;
        if ((s = status) >= 0) {
            int h = System.identityHashCode(this);
            final ReentrantLock lock = exceptionTableLock;
            lock.lock();
            try {
                expungeStaleExceptions();
                ExceptionNode[] t = exceptionTable;
                int i = h & (t.length - 1);
                for (ExceptionNode e = t[i]; ; e = e.next) {
                    if (e == null) {
                        t[i] = new ExceptionNode(this, ex, t[i],
                                exceptionTableRefQueue);
                        break;
                    }
                    if (e.get() == this) // already present
                        break;
                }
            } finally {
                lock.unlock();
            }
            s = abnormalCompletion(DONE | ABNORMAL | THROWN);
        }
        return s;
    }


    private int setExceptionalCompletion(Throwable ex) {
        int s = recordExceptionalCompletion(ex);
        if ((s & THROWN) != 0)
            internalPropagateException(ex);
        return s;
    }


    void internalPropagateException(Throwable ex) {
    }


    static final void cancelIgnoringExceptions(ForkJoinTask<?> t) {
        if (t != null && t.status >= 0) {
            try {
                t.cancel(false);
            } catch (Throwable ignore) {
            }
        }
    }


    private void clearExceptionalCompletion() {
        int h = System.identityHashCode(this);
        final ReentrantLock lock = exceptionTableLock;
        lock.lock();
        try {
            ExceptionNode[] t = exceptionTable;
            int i = h & (t.length - 1);
            ExceptionNode e = t[i];
            ExceptionNode pred = null;
            while (e != null) {
                ExceptionNode next = e.next;
                if (e.get() == this) {
                    if (pred == null)
                        t[i] = next;
                    else
                        pred.next = next;
                    break;
                }
                pred = e;
                e = next;
            }
            expungeStaleExceptions();
            status = 0;
        } finally {
            lock.unlock();
        }
    }


    private Throwable getThrowableException() {
        int h = System.identityHashCode(this);
        ExceptionNode e;
        final ReentrantLock lock = exceptionTableLock;
        lock.lock();
        try {
            expungeStaleExceptions();
            ExceptionNode[] t = exceptionTable;
            e = t[h & (t.length - 1)];
            while (e != null && e.get() != this)
                e = e.next;
        } finally {
            lock.unlock();
        }
        Throwable ex;
        if (e == null || (ex = e.ex) == null)
            return null;
        if (e.thrower != Thread.currentThread().getId()) {
            try {
                Constructor<?> noArgCtor = null;
                for (Constructor<?> c : ex.getClass().getConstructors()) {
                    Class<?>[] ps = c.getParameterTypes();
                    if (ps.length == 0)
                        noArgCtor = c;
                    else if (ps.length == 1 && ps[0] == Throwable.class)
                        return (Throwable) c.newInstance(ex);
                }
                if (noArgCtor != null) {
                    Throwable wx = (Throwable) noArgCtor.newInstance();
                    wx.initCause(ex);
                    return wx;
                }
            } catch (Exception ignore) {
            }
        }
        return ex;
    }

    private static void expungeStaleExceptions() {
        for (Object x; (x = exceptionTableRefQueue.poll()) != null; ) {
            if (x instanceof ExceptionNode) {
                ExceptionNode[] t = exceptionTable;
                int i = ((ExceptionNode) x).hashCode & (t.length - 1);
                ExceptionNode e = t[i];
                ExceptionNode pred = null;
                while (e != null) {
                    ExceptionNode next = e.next;
                    if (e == x) {
                        if (pred == null)
                            t[i] = next;
                        else
                            pred.next = next;
                        break;
                    }
                    pred = e;
                    e = next;
                }
            }
        }
    }


    static final void helpExpungeStaleExceptions() {
        final ReentrantLock lock = exceptionTableLock;
        if (lock.tryLock()) {
            try {
                expungeStaleExceptions();
            } finally {
                lock.unlock();
            }
        }
    }

    static void rethrow(Throwable ex) {
        ForkJoinTask.<RuntimeException>uncheckedThrow(ex);
    }


    @SuppressWarnings("unchecked")
    static <T extends Throwable> void uncheckedThrow(Throwable t) throws T {
        if (t != null)
            throw (T) t; // rely on vacuous cast
        else
            throw new Error("Unknown Exception");
    }


    private void reportException(int s) {
        rethrow((s & THROWN) != 0 ? getThrowableException() :
                new CancellationException());
    }


    public final ForkJoinTask<V> fork() {
        Thread t;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread)
            ((ForkJoinWorkerThread) t).workQueue.push(this);
        else
            ForkJoinPool.common.externalPush(this);
        return this;
    }


    public final V join() {
        int s;
        if (((s = doJoin()) & ABNORMAL) != 0)
            reportException(s);
        return getRawResult();
    }


    public final V invoke() {
        int s;
        if (((s = doInvoke()) & ABNORMAL) != 0)
            reportException(s);
        return getRawResult();
    }


    public static void invokeAll(ForkJoinTask<?> t1, ForkJoinTask<?> t2) {
        int s1, s2;
        t2.fork();
        if (((s1 = t1.doInvoke()) & ABNORMAL) != 0)
            t1.reportException(s1);
        if (((s2 = t2.doJoin()) & ABNORMAL) != 0)
            t2.reportException(s2);
    }

    public static void invokeAll(ForkJoinTask<?>... tasks) {
        Throwable ex = null;
        int last = tasks.length - 1;
        for (int i = last; i >= 0; --i) {
            ForkJoinTask<?> t = tasks[i];
            if (t == null) {
                if (ex == null)
                    ex = new NullPointerException();
            } else if (i != 0)
                t.fork();
            else if ((t.doInvoke() & ABNORMAL) != 0 && ex == null)
                ex = t.getException();
        }
        for (int i = 1; i <= last; ++i) {
            ForkJoinTask<?> t = tasks[i];
            if (t != null) {
                if (ex != null)
                    t.cancel(false);
                else if ((t.doJoin() & ABNORMAL) != 0)
                    ex = t.getException();
            }
        }
        if (ex != null)
            rethrow(ex);
    }

    public static <T extends ForkJoinTask<?>> Collection<T> invokeAll(Collection<T> tasks) {
        if (!(tasks instanceof RandomAccess) || !(tasks instanceof List<?>)) {
            invokeAll(tasks.toArray(new ForkJoinTask<?>[0]));
            return tasks;
        }
        @SuppressWarnings("unchecked")
        List<? extends ForkJoinTask<?>> ts =
                (List<? extends ForkJoinTask<?>>) tasks;
        Throwable ex = null;
        int last = ts.size() - 1;
        for (int i = last; i >= 0; --i) {
            ForkJoinTask<?> t = ts.get(i);
            if (t == null) {
                if (ex == null)
                    ex = new NullPointerException();
            } else if (i != 0)
                t.fork();
            else if ((t.doInvoke() & ABNORMAL) != 0 && ex == null)
                ex = t.getException();
        }
        for (int i = 1; i <= last; ++i) {
            ForkJoinTask<?> t = ts.get(i);
            if (t != null) {
                if (ex != null)
                    t.cancel(false);
                else if ((t.doJoin() & ABNORMAL) != 0)
                    ex = t.getException();
            }
        }
        if (ex != null)
            rethrow(ex);
        return tasks;
    }


    public boolean cancel(boolean mayInterruptIfRunning) {
        int s = abnormalCompletion(DONE | ABNORMAL);
        return (s & (ABNORMAL | THROWN)) == ABNORMAL;
    }

    public final boolean isDone() {
        return status < 0;
    }

    public final boolean isCancelled() {
        return (status & (ABNORMAL | THROWN)) == ABNORMAL;
    }


    public final boolean isCompletedAbnormally() {
        return (status & ABNORMAL) != 0;
    }


    public final boolean isCompletedNormally() {
        return (status & (DONE | ABNORMAL)) == DONE;
    }


    public final Throwable getException() {
        int s = status;
        return ((s & ABNORMAL) == 0 ? null :
                (s & THROWN) == 0 ? new CancellationException() :
                        getThrowableException());
    }


    public void completeExceptionally(Throwable ex) {
        setExceptionalCompletion((ex instanceof RuntimeException) ||
                (ex instanceof Error) ? ex :
                new RuntimeException(ex));
    }


    public void complete(V value) {
        try {
            setRawResult(value);
        } catch (Throwable rex) {
            setExceptionalCompletion(rex);
            return;
        }
        setDone();
    }

    public final void quietlyComplete() {
        setDone();
    }


    public final V get() throws InterruptedException, ExecutionException {
        int s = (Thread.currentThread() instanceof ForkJoinWorkerThread) ?
                doJoin() : externalInterruptibleAwaitDone();
        if ((s & THROWN) != 0)
            throw new ExecutionException(getThrowableException());
        else if ((s & ABNORMAL) != 0)
            throw new CancellationException();
        else
            return getRawResult();
    }


    public final V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        int s;
        long nanos = unit.toNanos(timeout);
        if (Thread.interrupted())
            throw new InterruptedException();
        if ((s = status) >= 0 && nanos > 0L) {
            long d = System.nanoTime() + nanos;
            long deadline = (d == 0L) ? 1L : d; // avoid 0
            Thread t = Thread.currentThread();
            if (t instanceof ForkJoinWorkerThread) {
                ForkJoinWorkerThread wt = (ForkJoinWorkerThread) t;
                s = wt.pool.awaitJoin(wt.workQueue, this, deadline);
            } else if ((s = ((this instanceof CountedCompleter) ?
                    ForkJoinPool.common.externalHelpComplete(
                            (CountedCompleter<?>) this, 0) :
                    ForkJoinPool.common.tryExternalUnpush(this) ?
                            doExec() : 0)) >= 0) {
                long ns, ms; // measure in nanosecs, but wait in millisecs
                while ((s = status) >= 0 &&
                        (ns = deadline - System.nanoTime()) > 0L) {
                    if ((ms = TimeUnit.NANOSECONDS.toMillis(ns)) > 0L &&
                            (s = (int) STATUS.getAndBitwiseOr(this, SIGNAL)) >= 0) {
                        synchronized (this) {
                            if (status >= 0)
                                wait(ms); // OK to throw InterruptedException
                            else
                                notifyAll();
                        }
                    }
                }
            }
        }
        if (s >= 0)
            throw new TimeoutException();
        else if ((s & THROWN) != 0)
            throw new ExecutionException(getThrowableException());
        else if ((s & ABNORMAL) != 0)
            throw new CancellationException();
        else
            return getRawResult();
    }


    public final void quietlyJoin() {
        doJoin();
    }


    public final void quietlyInvoke() {
        doInvoke();
    }


    public static void helpQuiesce() {
        Thread t;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) {
            ForkJoinWorkerThread wt = (ForkJoinWorkerThread) t;
            wt.pool.helpQuiescePool(wt.workQueue);
        } else
            ForkJoinPool.quiesceCommonPool();
    }


    public void reinitialize() {
        if ((status & THROWN) != 0)
            clearExceptionalCompletion();
        else
            status = 0;
    }


    public static ForkJoinPool getPool() {
        Thread t = Thread.currentThread();
        return (t instanceof ForkJoinWorkerThread) ?
                ((ForkJoinWorkerThread) t).pool : null;
    }


    public static boolean inForkJoinPool() {
        return Thread.currentThread() instanceof ForkJoinWorkerThread;
    }


    public boolean tryUnfork() {
        Thread t;
        return (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
                ((ForkJoinWorkerThread) t).workQueue.tryUnpush(this) :
                ForkJoinPool.common.tryExternalUnpush(this));
    }


    public static int getQueuedTaskCount() {
        Thread t;
        ForkJoinPool.WorkQueue q;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread)
            q = ((ForkJoinWorkerThread) t).workQueue;
        else
            q = ForkJoinPool.commonSubmitterQueue();
        return (q == null) ? 0 : q.queueSize();
    }


    public static int getSurplusQueuedTaskCount() {
        return ForkJoinPool.getSurplusQueuedTaskCount();
    }


    public abstract V getRawResult();


    protected abstract void setRawResult(V value);


    protected abstract boolean exec();


    protected static ForkJoinTask<?> peekNextLocalTask() {
        Thread t;
        ForkJoinPool.WorkQueue q;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread)
            q = ((ForkJoinWorkerThread) t).workQueue;
        else
            q = ForkJoinPool.commonSubmitterQueue();
        return (q == null) ? null : q.peek();
    }


    protected static ForkJoinTask<?> pollNextLocalTask() {
        Thread t;
        return ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
                ((ForkJoinWorkerThread) t).workQueue.nextLocalTask() :
                null;
    }


    protected static ForkJoinTask<?> pollTask() {
        Thread t;
        ForkJoinWorkerThread wt;
        return ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
                (wt = (ForkJoinWorkerThread) t).pool.nextTaskFor(wt.workQueue) :
                null;
    }


    protected static ForkJoinTask<?> pollSubmission() {
        Thread t;
        return ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
                ((ForkJoinWorkerThread) t).pool.pollSubmission() : null;
    }


    public final short getForkJoinTaskTag() {
        return (short) status;
    }


    public final short setForkJoinTaskTag(short newValue) {
        for (int s; ; ) {
            if (STATUS.weakCompareAndSet(this, s = status,
                    (s & ~SMASK) | (newValue & SMASK)))
                return (short) s;
        }
    }


    public final boolean compareAndSetForkJoinTaskTag(short expect, short update) {
        for (int s; ; ) {
            if ((short) (s = status) != expect)
                return false;
            if (STATUS.weakCompareAndSet(this, s,
                    (s & ~SMASK) | (update & SMASK)))
                return true;
        }
    }


    public static ForkJoinTask<?> adapt(Runnable runnable) {
        return new AdaptedRunnableAction(runnable);
    }


    public static <T> ForkJoinTask<T> adapt(Runnable runnable, T result) {
        return new AdaptedRunnable<T>(runnable, result);
    }


    public static <T> ForkJoinTask<T> adapt(Callable<? extends T> callable) {
        return new AdaptedCallable<T>(callable);
    }


    private void writeObject(java.io.ObjectOutputStream s)
            throws java.io.IOException {
        s.defaultWriteObject();
        s.writeObject(getException());
    }


    private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        Object ex = s.readObject();
        if (ex != null)
            setExceptionalCompletion((Throwable) ex);
    }


}
