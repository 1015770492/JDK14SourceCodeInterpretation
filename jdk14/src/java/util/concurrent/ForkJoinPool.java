package java.util.concurrent;

import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.Permission;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.concurrent.locks.LockSupport;


public class ForkJoinPool extends AbstractExecutorService {
    static final int SWIDTH = 16;            // width of short
    static final int SMASK = 0xffff;        // short bits == max index
    static final int MAX_CAP = 0x7fff;        // max #workers - 1
    static final int SQMASK = 0x007e;        // max 64 (even) slots
    static final int UNSIGNALLED = 1 << 31;       // must be negative
    static final int SS_SEQ = 1 << 16;       // version count
    static final int QLOCK = 1;             // must be 1
    static final int OWNED = 1;             // queue has owner thread
    static final int FIFO = 1 << 16;       // fifo queue or access mode
    static final int SHUTDOWN = 1 << 18;
    static final int TERMINATED = 1 << 19;
    static final int STOP = 1 << 31;       // must be negative
    static final int QUIET = 1 << 30;       // not scanning or working
    static final int DORMANT = QUIET | UNSIGNALLED;
    static final int INITIAL_QUEUE_CAPACITY = 1 << 13;
    static final int MAXIMUM_QUEUE_CAPACITY = 1 << 26; // 64M
    static final int TOP_BOUND_SHIFT = 10;
    public static final ForkJoinWorkerThreadFactory defaultForkJoinWorkerThreadFactory;
    static final RuntimePermission modifyThreadPermission;
    static final ForkJoinPool common;
    static final int COMMON_PARALLELISM;
    private static final int COMMON_MAX_SPARES;
    private static int poolNumberSequence;
    private static final long DEFAULT_KEEPALIVE = 60_000L;
    private static final long TIMEOUT_SLOP = 20L;
    private static final int DEFAULT_COMMON_MAX_SPARES = 256;
    private static final int SEED_INCREMENT = 0x9e3779b9;
    private static final long SP_MASK = 0xffffffffL;
    private static final long UC_MASK = ~SP_MASK;
    private static final int RC_SHIFT = 48;
    private static final long RC_UNIT = 0x0001L << RC_SHIFT;
    private static final long RC_MASK = 0xffffL << RC_SHIFT;
    private static final int TC_SHIFT = 32;
    private static final long TC_UNIT = 0x0001L << TC_SHIFT;
    private static final long TC_MASK = 0xffffL << TC_SHIFT;
    private static final long ADD_WORKER = 0x0001L << (TC_SHIFT + 15); // sign
    volatile long stealCount;            // collects worker nsteals
    final long keepAlive;                // milliseconds before dropping if idle
    int indexSeed;                       // next worker index
    final int bounds;                    // min, max threads packed as shorts
    volatile int mode;                   // parallelism, runstate, queue mode
    WorkQueue[] workQueues;              // main registry
    final String workerNamePrefix;       // for worker thread string; sync lock
    final ForkJoinWorkerThreadFactory factory;
    final UncaughtExceptionHandler ueh;  // per-worker UEH
    final Predicate<? super ForkJoinPool> saturate;
    @jdk.internal.vm.annotation.Contended("fjpctl") // segregate
    volatile long ctl;                   // main pool control
    private static final VarHandle CTL;
    private static final VarHandle MODE;
    static final VarHandle QA;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            CTL = l.findVarHandle(ForkJoinPool.class, "ctl", long.class);
            MODE = l.findVarHandle(ForkJoinPool.class, "mode", int.class);
            QA = MethodHandles.arrayElementVarHandle(ForkJoinTask[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
        Class<?> ensureLoaded = LockSupport.class;

        int commonMaxSpares = DEFAULT_COMMON_MAX_SPARES;
        try {
            String p = System.getProperty("java.util.concurrent.ForkJoinPool.common.maximumSpares");
            if (p != null)
                commonMaxSpares = Integer.parseInt(p);
        } catch (Exception ignore) {
        }
        COMMON_MAX_SPARES = commonMaxSpares;

        defaultForkJoinWorkerThreadFactory =
                new DefaultForkJoinWorkerThreadFactory();
        modifyThreadPermission = new RuntimePermission("modifyThread");

        common = AccessController.doPrivileged(new PrivilegedAction<>() {
            public ForkJoinPool run() {
                return new ForkJoinPool((byte) 0);
            }
        });

        COMMON_PARALLELISM = Math.max(common.mode & SMASK, 1);
    }

    public ForkJoinPool() {
        this(Math.min(MAX_CAP, Runtime.getRuntime().availableProcessors()),
                defaultForkJoinWorkerThreadFactory,
                null,
                false,
                0,
                MAX_CAP,
                1,
                null,
                DEFAULT_KEEPALIVE,
                TimeUnit.MILLISECONDS);
    }
    public ForkJoinPool(int parallelism) {
        this(parallelism, defaultForkJoinWorkerThreadFactory,
                null,
                false,
                0,
                MAX_CAP,
                1,
                null,
                DEFAULT_KEEPALIVE,
                TimeUnit.MILLISECONDS);
    }

    public ForkJoinPool(int parallelism, ForkJoinWorkerThreadFactory factory, UncaughtExceptionHandler handler, boolean asyncMode) {
        this(parallelism, factory, handler, asyncMode,
                0, MAX_CAP, 1, null, DEFAULT_KEEPALIVE, TimeUnit.MILLISECONDS);
    }

    public ForkJoinPool(int parallelism,
                        ForkJoinWorkerThreadFactory factory,
                        UncaughtExceptionHandler handler,
                        boolean asyncMode,
                        int corePoolSize,
                        int maximumPoolSize,
                        int minimumRunnable,
                        Predicate<? super ForkJoinPool> saturate,
                        long keepAliveTime,
                        TimeUnit unit) {
        if (parallelism <= 0 || parallelism > MAX_CAP ||
                maximumPoolSize < parallelism || keepAliveTime <= 0L)
            throw new IllegalArgumentException();
        if (factory == null)
            throw new NullPointerException();
        long ms = Math.max(unit.toMillis(keepAliveTime), TIMEOUT_SLOP);

        int corep = Math.min(Math.max(corePoolSize, parallelism), MAX_CAP);
        long c = ((((long) (-corep) << TC_SHIFT) & TC_MASK) |
                (((long) (-parallelism) << RC_SHIFT) & RC_MASK));
        int m = parallelism | (asyncMode ? FIFO : 0);
        int maxSpares = Math.min(maximumPoolSize, MAX_CAP) - parallelism;
        int minAvail = Math.min(Math.max(minimumRunnable, 0), MAX_CAP);
        int b = ((minAvail - parallelism) & SMASK) | (maxSpares << SWIDTH);
        int n = (parallelism > 1) ? parallelism - 1 : 1; // at least 2 slots
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        n = (n + 1) << 1;

        this.workerNamePrefix = "ForkJoinPool-" + nextPoolId() + "-worker-";
        this.workQueues = new WorkQueue[n];
        this.factory = factory;
        this.ueh = handler;
        this.saturate = saturate;
        this.keepAlive = ms;
        this.bounds = b;
        this.mode = m;
        this.ctl = c;
        checkPermission();
    }

    private ForkJoinPool(byte forCommonPoolOnly) {
        int parallelism = -1;
        ForkJoinWorkerThreadFactory fac = null;
        UncaughtExceptionHandler handler = null;
        try {
            String pp = System.getProperty
                    ("java.util.concurrent.ForkJoinPool.common.parallelism");
            if (pp != null)
                parallelism = Integer.parseInt(pp);
            fac = (ForkJoinWorkerThreadFactory) newInstanceFromSystemProperty(
                    "java.util.concurrent.ForkJoinPool.common.threadFactory");
            handler = (UncaughtExceptionHandler) newInstanceFromSystemProperty(
                    "java.util.concurrent.ForkJoinPool.common.exceptionHandler");
        } catch (Exception ignore) {
        }

        if (fac == null) {
            if (System.getSecurityManager() == null)
                fac = defaultForkJoinWorkerThreadFactory;
            else // use security-managed default
                fac = new InnocuousForkJoinWorkerThreadFactory();
        }
        if (parallelism < 0 && // default 1 less than #cores
                (parallelism = Runtime.getRuntime().availableProcessors() - 1) <= 0)
            parallelism = 1;
        if (parallelism > MAX_CAP)
            parallelism = MAX_CAP;

        long c = ((((long) (-parallelism) << TC_SHIFT) & TC_MASK) |
                (((long) (-parallelism) << RC_SHIFT) & RC_MASK));
        int b = ((1 - parallelism) & SMASK) | (COMMON_MAX_SPARES << SWIDTH);
        int n = (parallelism > 1) ? parallelism - 1 : 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        n = (n + 1) << 1;

        this.workerNamePrefix = "ForkJoinPool.commonPool-worker-";
        this.workQueues = new WorkQueue[n];
        this.factory = fac;
        this.ueh = handler;
        this.saturate = null;
        this.keepAlive = DEFAULT_KEEPALIVE;
        this.bounds = b;
        this.mode = parallelism;
        this.ctl = c;
    }

    private static void checkPermission() {
        SecurityManager security = System.getSecurityManager();
        if (security != null)
            security.checkPermission(modifyThreadPermission);
    }

    public static interface ForkJoinWorkerThreadFactory {
        public ForkJoinWorkerThread newThread(ForkJoinPool pool);
    }

    static AccessControlContext contextWithPermissions(Permission... perms) {
        Permissions permissions = new Permissions();
        for (Permission perm : perms)
            permissions.add(perm);
        return new AccessControlContext(
                new ProtectionDomain[]{new ProtectionDomain(null, permissions)});
    }

    private static final class DefaultForkJoinWorkerThreadFactory implements ForkJoinWorkerThreadFactory {
        private static final AccessControlContext ACC = contextWithPermissions(
                new RuntimePermission("getClassLoader"),
                new RuntimePermission("setContextClassLoader"));

        public final ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            return AccessController.doPrivileged(
                    new PrivilegedAction<>() {
                        public ForkJoinWorkerThread run() {
                            return new ForkJoinWorkerThread(
                                    pool, ClassLoader.getSystemClassLoader());
                        }
                    },
                    ACC);
        }
    }

    @jdk.internal.vm.annotation.Contended
    static final class WorkQueue {
        volatile int source;       // source queue id, or sentinel
        int id;                    // pool index, mode, tag
        int base;                  // index of next slot for poll
        int top;                   // index of next slot for push
        volatile int phase;        // versioned, negative: queued, 1: locked
        int stackPred;             // pool stack (ctl) predecessor link
        int nsteals;               // number of steals
        ForkJoinTask<?>[] array;   // the queued tasks; power of 2 size
        final ForkJoinPool pool;   // the containing pool (may be null)
        final ForkJoinWorkerThread owner; // owning thread or null if shared

        WorkQueue(ForkJoinPool pool, ForkJoinWorkerThread owner) {
            this.pool = pool;
            this.owner = owner;
            // Place indices in the center of array (that is not yet allocated)
            base = top = INITIAL_QUEUE_CAPACITY >>> 1;
        }


        final boolean tryLockPhase() {
            return PHASE.compareAndSet(this, 0, 1);
        }

        final void releasePhaseLock() {
            PHASE.setRelease(this, 0);
        }

        final int getPoolIndex() {
            return (id & 0xffff) >>> 1; // ignore odd/even tag bit
        }

        final int queueSize() {
            int n = (int) BASE.getAcquire(this) - top;
            return (n >= 0) ? 0 : -n; // ignore transient negative
        }

        final boolean isEmpty() {
            ForkJoinTask<?>[] a;
            int n, cap, b;
            VarHandle.acquireFence(); // needed by external callers
            return ((n = (b = base) - top) >= 0 || // possibly one task
                    (n == -1 && ((a = array) == null ||
                            (cap = a.length) == 0 ||
                            a[(cap - 1) & b] == null)));
        }


        final void push(ForkJoinTask<?> task) {
            ForkJoinTask<?>[] a;
            int s = top, d = s - base, cap, m;
            ForkJoinPool p = pool;
            if ((a = array) != null && (cap = a.length) > 0) {
                QA.setRelease(a, (m = cap - 1) & s, task);
                top = s + 1;
                if (d == m)
                    growArray(false);
                else if (QA.getAcquire(a, m & (s - 1)) == null && p != null) {
                    VarHandle.fullFence();  // was empty
                    p.signalWork(null);
                }
            }
        }

        final boolean lockedPush(ForkJoinTask<?> task) {
            ForkJoinTask<?>[] a;
            boolean signal = false;
            int s = top, d = s - base, cap, m;
            if ((a = array) != null && (cap = a.length) > 0) {
                a[(m = (cap - 1)) & s] = task;
                top = s + 1;
                if (d == m)
                    growArray(true);
                else {
                    phase = 0; // full volatile unlock
                    if (((s - base) & ~1) == 0) // size 0 or 1
                        signal = true;
                }
            }
            return signal;
        }


        final void growArray(boolean locked) {
            ForkJoinTask<?>[] newA = null;
            try {
                ForkJoinTask<?>[] oldA;
                int oldSize, newSize;
                if ((oldA = array) != null && (oldSize = oldA.length) > 0 &&
                        (newSize = oldSize << 1) <= MAXIMUM_QUEUE_CAPACITY &&
                        newSize > 0) {
                    try {
                        newA = new ForkJoinTask<?>[newSize];
                    } catch (OutOfMemoryError ex) {
                    }
                    if (newA != null) { // poll from old array, push to new
                        int oldMask = oldSize - 1, newMask = newSize - 1;
                        for (int s = top - 1, k = oldMask; k >= 0; --k) {
                            ForkJoinTask<?> x = (ForkJoinTask<?>)
                                    QA.getAndSet(oldA, s & oldMask, null);
                            if (x != null)
                                newA[s-- & newMask] = x;
                            else
                                break;
                        }
                        array = newA;
                        VarHandle.releaseFence();
                    }
                }
            } finally {
                if (locked)
                    phase = 0;
            }
            if (newA == null)
                throw new RejectedExecutionException("Queue capacity exceeded");
        }

        final ForkJoinTask<?> poll() {
            int b, k, cap;
            ForkJoinTask<?>[] a;
            while ((a = array) != null && (cap = a.length) > 0 &&
                    top - (b = base) > 0) {
                ForkJoinTask<?> t = (ForkJoinTask<?>)
                        QA.getAcquire(a, k = (cap - 1) & b);
                if (base == b++) {
                    if (t == null)
                        Thread.yield(); // await index advance
                    else if (QA.compareAndSet(a, k, t, null)) {
                        BASE.setOpaque(this, b);
                        return t;
                    }
                }
            }
            return null;
        }

        final ForkJoinTask<?> nextLocalTask() {
            ForkJoinTask<?> t = null;
            int md = id, b, s, d, cap;
            ForkJoinTask<?>[] a;
            if ((a = array) != null && (cap = a.length) > 0 &&
                    (d = (s = top) - (b = base)) > 0) {
                if ((md & FIFO) == 0 || d == 1) {
                    if ((t = (ForkJoinTask<?>)
                            QA.getAndSet(a, (cap - 1) & --s, null)) != null)
                        TOP.setOpaque(this, s);
                } else if ((t = (ForkJoinTask<?>)
                        QA.getAndSet(a, (cap - 1) & b++, null)) != null) {
                    BASE.setOpaque(this, b);
                } else // on contention in FIFO mode, use regular poll
                    t = poll();
            }
            return t;
        }

        final ForkJoinTask<?> peek() {
            int cap;
            ForkJoinTask<?>[] a;
            return ((a = array) != null && (cap = a.length) > 0) ?
                    a[(cap - 1) & ((id & FIFO) != 0 ? base : top - 1)] : null;
        }

        final boolean tryUnpush(ForkJoinTask<?> task) {
            boolean popped = false;
            int s, cap;
            ForkJoinTask<?>[] a;
            if ((a = array) != null && (cap = a.length) > 0 &&
                    (s = top) != base &&
                    (popped = QA.compareAndSet(a, (cap - 1) & --s, task, null)))
                TOP.setOpaque(this, s);
            return popped;
        }

        final boolean tryLockedUnpush(ForkJoinTask<?> task) {
            boolean popped = false;
            int s = top - 1, k, cap;
            ForkJoinTask<?>[] a;
            if ((a = array) != null && (cap = a.length) > 0 &&
                    a[k = (cap - 1) & s] == task && tryLockPhase()) {
                if (top == s + 1 && array == a &&
                        (popped = QA.compareAndSet(a, k, task, null)))
                    top = s;
                releasePhaseLock();
            }
            return popped;
        }


        final void cancelAll() {
            for (ForkJoinTask<?> t; (t = poll()) != null; )
                ForkJoinTask.cancelIgnoringExceptions(t);
        }

        final void topLevelExec(ForkJoinTask<?> t, WorkQueue q, int n) {
            int nstolen = 1;
            for (int j = 0; ; ) {
                if (t != null)
                    t.doExec();
                if (j++ <= n)
                    t = nextLocalTask();
                else {
                    j = 0;
                    t = null;
                }
                if (t == null) {
                    if (q != null && (t = q.poll()) != null) {
                        ++nstolen;
                        j = 0;
                    } else if (j != 0)
                        break;
                }
            }
            ForkJoinWorkerThread thread = owner;
            nsteals += nstolen;
            source = 0;
            if (thread != null)
                thread.afterTopLevelExec();
        }


        final void tryRemoveAndExec(ForkJoinTask<?> task) {
            ForkJoinTask<?>[] a;
            int s, cap;
            if ((a = array) != null && (cap = a.length) > 0 &&
                    (s = top) - base > 0) { // traverse from top
                for (int m = cap - 1, ns = s - 1, i = ns; ; --i) {
                    int index = i & m;
                    ForkJoinTask<?> t = (ForkJoinTask<?>) QA.get(a, index);
                    if (t == null)
                        break;
                    else if (t == task) {
                        if (QA.compareAndSet(a, index, t, null)) {
                            top = ns;   // safely shift down
                            for (int j = i; j != ns; ++j) {
                                ForkJoinTask<?> f;
                                int pindex = (j + 1) & m;
                                f = (ForkJoinTask<?>) QA.get(a, pindex);
                                QA.setVolatile(a, pindex, null);
                                int jindex = j & m;
                                QA.setRelease(a, jindex, f);
                            }
                            VarHandle.releaseFence();
                            t.doExec();
                        }
                        break;
                    }
                }
            }
        }

        final int helpCC(CountedCompleter<?> task, int limit, boolean shared) {
            int status = 0;
            if (task != null && (status = task.status) >= 0) {
                int s, k, cap;
                ForkJoinTask<?>[] a;
                while ((a = array) != null && (cap = a.length) > 0 &&
                        (s = top) - base > 0) {
                    CountedCompleter<?> v = null;
                    ForkJoinTask<?> o = a[k = (cap - 1) & (s - 1)];
                    if (o instanceof CountedCompleter) {
                        CountedCompleter<?> t = (CountedCompleter<?>) o;
                        for (CountedCompleter<?> f = t; ; ) {
                            if (f != task) {
                                if ((f = f.completer) == null)
                                    break;
                            } else if (shared) {
                                if (tryLockPhase()) {
                                    if (top == s && array == a &&
                                            QA.compareAndSet(a, k, t, null)) {
                                        top = s - 1;
                                        v = t;
                                    }
                                    releasePhaseLock();
                                }
                                break;
                            } else {
                                if (QA.compareAndSet(a, k, t, null)) {
                                    top = s - 1;
                                    v = t;
                                }
                                break;
                            }
                        }
                    }
                    if (v != null)
                        v.doExec();
                    if ((status = task.status) < 0 || v == null ||
                            (limit != 0 && --limit == 0))
                        break;
                }
            }
            return status;
        }

        final void helpAsyncBlocker(ManagedBlocker blocker) {
            if (blocker != null) {
                int b, k, cap;
                ForkJoinTask<?>[] a;
                ForkJoinTask<?> t;
                while ((a = array) != null && (cap = a.length) > 0 &&
                        top - (b = base) > 0) {
                    t = (ForkJoinTask<?>) QA.getAcquire(a, k = (cap - 1) & b);
                    if (blocker.isReleasable())
                        break;
                    else if (base == b++ && t != null) {
                        if (!(t instanceof CompletableFuture.
                                AsynchronousCompletionTask))
                            break;
                        else if (QA.compareAndSet(a, k, t, null)) {
                            BASE.setOpaque(this, b);
                            t.doExec();
                        }
                    }
                }
            }
        }

        final boolean isApparentlyUnblocked() {
            Thread wt;
            Thread.State s;
            return ((wt = owner) != null &&
                    (s = wt.getState()) != Thread.State.BLOCKED &&
                    s != Thread.State.WAITING &&
                    s != Thread.State.TIMED_WAITING);
        }

        static final VarHandle PHASE;
        static final VarHandle BASE;
        static final VarHandle TOP;

        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                PHASE = l.findVarHandle(WorkQueue.class, "phase", int.class);
                BASE = l.findVarHandle(WorkQueue.class, "base", int.class);
                TOP = l.findVarHandle(WorkQueue.class, "top", int.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    private static final synchronized int nextPoolId() {
        return ++poolNumberSequence;
    }

    private boolean createWorker() {
        ForkJoinWorkerThreadFactory fac = factory;
        Throwable ex = null;
        ForkJoinWorkerThread wt = null;
        try {
            if (fac != null && (wt = fac.newThread(this)) != null) {
                wt.start();
                return true;
            }
        } catch (Throwable rex) {
            ex = rex;
        }
        deregisterWorker(wt, ex);
        return false;
    }

    private void tryAddWorker(long c) {
        do {
            long nc = ((RC_MASK & (c + RC_UNIT)) |
                    (TC_MASK & (c + TC_UNIT)));
            if (ctl == c && CTL.compareAndSet(this, c, nc)) {
                createWorker();
                break;
            }
        } while (((c = ctl) & ADD_WORKER) != 0L && (int) c == 0);
    }


    final WorkQueue registerWorker(ForkJoinWorkerThread wt) {
        UncaughtExceptionHandler handler;
        wt.setDaemon(true);                             // configure thread
        if ((handler = ueh) != null)
            wt.setUncaughtExceptionHandler(handler);
        int tid = 0;                                    // for thread name
        int idbits = mode & FIFO;
        String prefix = workerNamePrefix;
        WorkQueue w = new WorkQueue(this, wt);
        if (prefix != null) {
            synchronized (prefix) {
                WorkQueue[] ws = workQueues;
                int n;
                int s = indexSeed += SEED_INCREMENT;
                idbits |= (s & ~(SMASK | FIFO | DORMANT));
                if (ws != null && (n = ws.length) > 1) {
                    int m = n - 1;
                    tid = m & ((s << 1) | 1);           // odd-numbered indices
                    for (int probes = n >>> 1; ; ) {      // find empty slot
                        WorkQueue q;
                        if ((q = ws[tid]) == null || q.phase == QUIET)
                            break;
                        else if (--probes == 0) {
                            tid = n | 1;                // resize below
                            break;
                        } else
                            tid = (tid + 2) & m;
                    }
                    w.phase = w.id = tid | idbits;      // now publishable

                    if (tid < n)
                        ws[tid] = w;
                    else {                              // expand array
                        int an = n << 1;
                        WorkQueue[] as = new WorkQueue[an];
                        as[tid] = w;
                        int am = an - 1;
                        for (int j = 0; j < n; ++j) {
                            WorkQueue v;                // copy external queue
                            if ((v = ws[j]) != null)    // position may change
                                as[v.id & am & SQMASK] = v;
                            if (++j >= n)
                                break;
                            as[j] = ws[j];              // copy worker
                        }
                        workQueues = as;
                    }
                }
            }
            wt.setName(prefix.concat(Integer.toString(tid)));
        }
        return w;
    }

    final void deregisterWorker(ForkJoinWorkerThread wt, Throwable ex) {
        WorkQueue w = null;
        int phase = 0;
        if (wt != null && (w = wt.workQueue) != null) {
            Object lock = workerNamePrefix;
            int wid = w.id;
            long ns = (long) w.nsteals & 0xffffffffL;
            if (lock != null) {
                synchronized (lock) {
                    WorkQueue[] ws;
                    int n, i;         // remove index from array
                    if ((ws = workQueues) != null && (n = ws.length) > 0 &&
                            ws[i = wid & (n - 1)] == w)
                        ws[i] = null;
                    stealCount += ns;
                }
            }
            phase = w.phase;
        }
        if (phase != QUIET) {                         // else pre-adjusted
            long c;                                   // decrement counts
            do {
            } while (!CTL.weakCompareAndSet
                    (this, c = ctl, ((RC_MASK & (c - RC_UNIT)) |
                            (TC_MASK & (c - TC_UNIT)) |
                            (SP_MASK & c))));
        }
        if (w != null)
            w.cancelAll();                            // cancel remaining tasks

        if (!tryTerminate(false, false) &&            // possibly replace worker
                w != null && w.array != null)             // avoid repeated failures
            signalWork(null);

        if (ex == null)                               // help clean on way out
            ForkJoinTask.helpExpungeStaleExceptions();
        else                                          // rethrow
            ForkJoinTask.rethrow(ex);
    }

    final void signalWork(WorkQueue q) {
        for (; ; ) {
            long c;
            int sp;
            WorkQueue[] ws;
            int i;
            WorkQueue v;
            if ((c = ctl) >= 0L)                      // enough workers
                break;
            else if ((sp = (int) c) == 0) {            // no idle workers
                if ((c & ADD_WORKER) != 0L)           // too few workers
                    tryAddWorker(c);
                break;
            } else if ((ws = workQueues) == null)
                break;                                // unstarted/terminated
            else if (ws.length <= (i = sp & SMASK))
                break;                                // terminated
            else if ((v = ws[i]) == null)
                break;                                // terminating
            else {
                int np = sp & ~UNSIGNALLED;
                int vp = v.phase;
                long nc = (v.stackPred & SP_MASK) | (UC_MASK & (c + RC_UNIT));
                Thread vt = v.owner;
                if (sp == vp && CTL.compareAndSet(this, c, nc)) {
                    v.phase = np;
                    if (vt != null && v.source < 0)
                        LockSupport.unpark(vt);
                    break;
                } else if (q != null && q.isEmpty())     // no need to retry
                    break;
            }
        }
    }

    private int tryCompensate(WorkQueue w) {
        int t, n, sp;
        long c = ctl;
        WorkQueue[] ws = workQueues;
        if ((t = (short) (c >>> TC_SHIFT)) >= 0) {
            if (ws == null || (n = ws.length) <= 0 || w == null)
                return 0;                        // disabled
            else if ((sp = (int) c) != 0) {       // replace or release
                WorkQueue v = ws[sp & (n - 1)];
                int wp = w.phase;
                long uc = UC_MASK & ((wp < 0) ? c + RC_UNIT : c);
                int np = sp & ~UNSIGNALLED;
                if (v != null) {
                    int vp = v.phase;
                    Thread vt = v.owner;
                    long nc = ((long) v.stackPred & SP_MASK) | uc;
                    if (vp == sp && CTL.compareAndSet(this, c, nc)) {
                        v.phase = np;
                        if (vt != null && v.source < 0)
                            LockSupport.unpark(vt);
                        return (wp < 0) ? -1 : 1;
                    }
                }
                return 0;
            } else if ((int) (c >> RC_SHIFT) -      // reduce parallelism
                    (short) (bounds & SMASK) > 0) {
                long nc = ((RC_MASK & (c - RC_UNIT)) | (~RC_MASK & c));
                return CTL.compareAndSet(this, c, nc) ? 1 : 0;
            } else {                               // validate
                int md = mode, pc = md & SMASK, tc = pc + t, bc = 0;
                boolean unstable = false;
                for (int i = 1; i < n; i += 2) {
                    WorkQueue q;
                    Thread wt;
                    Thread.State ts;
                    if ((q = ws[i]) != null) {
                        if (q.source == 0) {
                            unstable = true;
                            break;
                        } else {
                            --tc;
                            if ((wt = q.owner) != null &&
                                    ((ts = wt.getState()) == Thread.State.BLOCKED ||
                                            ts == Thread.State.WAITING))
                                ++bc;            // worker is blocking
                        }
                    }
                }
                if (unstable || tc != 0 || ctl != c)
                    return 0;                    // inconsistent
                else if (t + pc >= MAX_CAP || t >= (bounds >>> SWIDTH)) {
                    Predicate<? super ForkJoinPool> sat;
                    if ((sat = saturate) != null && sat.test(this))
                        return -1;
                    else if (bc < pc) {          // lagging
                        Thread.yield();          // for retry spins
                        return 0;
                    } else
                        throw new RejectedExecutionException(
                                "Thread limit exceeded replacing blocked worker");
                }
            }
        }

        long nc = ((c + TC_UNIT) & TC_MASK) | (c & ~TC_MASK); // expand pool
        return CTL.compareAndSet(this, c, nc) && createWorker() ? 1 : 0;
    }

    final void runWorker(WorkQueue w) {
        int r = (w.id ^ ThreadLocalRandom.nextSecondarySeed()) | FIFO; // rng
        w.array = new ForkJoinTask<?>[INITIAL_QUEUE_CAPACITY]; // initialize
        for (; ; ) {
            int phase;
            if (scan(w, r)) {                     // scan until apparently empty
                r ^= r << 13;
                r ^= r >>> 17;
                r ^= r << 5; // move (xorshift)
            } else if ((phase = w.phase) >= 0) {    // enqueue, then rescan
                long np = (w.phase = (phase + SS_SEQ) | UNSIGNALLED) & SP_MASK;
                long c, nc;
                do {
                    w.stackPred = (int) (c = ctl);
                    nc = ((c - RC_UNIT) & UC_MASK) | np;
                } while (!CTL.weakCompareAndSet(this, c, nc));
            } else {                                // already queued
                int pred = w.stackPred;
                Thread.interrupted();             // clear before park
                w.source = DORMANT;               // enable signal
                long c = ctl;
                int md = mode, rc = (md & SMASK) + (int) (c >> RC_SHIFT);
                if (md < 0)                       // terminating
                    break;
                else if (rc <= 0 && (md & SHUTDOWN) != 0 &&
                        tryTerminate(false, false))
                    break;                        // quiescent shutdown
                else if (w.phase < 0) {
                    if (rc <= 0 && pred != 0 && phase == (int) c) {
                        long nc = (UC_MASK & (c - TC_UNIT)) | (SP_MASK & pred);
                        long d = keepAlive + System.currentTimeMillis();
                        LockSupport.parkUntil(this, d);
                        if (ctl == c &&           // drop on timeout if all idle
                                d - System.currentTimeMillis() <= TIMEOUT_SLOP &&
                                CTL.compareAndSet(this, c, nc)) {
                            w.phase = QUIET;
                            break;
                        }
                    } else {
                        LockSupport.park(this);
                        if (w.phase < 0)          // one spurious wakeup check
                            LockSupport.park(this);
                    }
                }
                w.source = 0;                     // disable signal
            }
        }
    }

    private boolean scan(WorkQueue w, int r) {
        WorkQueue[] ws;
        int n;
        if ((ws = workQueues) != null && (n = ws.length) > 0 && w != null) {
            for (int m = n - 1, j = r & m; ; ) {
                WorkQueue q;
                int b;
                if ((q = ws[j]) != null && q.top != (b = q.base)) {
                    int qid = q.id;
                    ForkJoinTask<?>[] a;
                    int cap, k;
                    ForkJoinTask<?> t;
                    if ((a = q.array) != null && (cap = a.length) > 0) {
                        t = (ForkJoinTask<?>) QA.getAcquire(a, k = (cap - 1) & b);
                        if (q.base == b++ && t != null &&
                                QA.compareAndSet(a, k, t, null)) {
                            q.base = b;
                            w.source = qid;
                            if (a[(cap - 1) & b] != null)
                                signalWork(q);    // help signal if more tasks
                            w.topLevelExec(t, q,  // random fairness bound
                                    (r | (1 << TOP_BOUND_SHIFT)) & SMASK);
                        }
                    }
                    return true;
                } else if (--n > 0)
                    j = (j + 1) & m;
                else
                    break;
            }
        }
        return false;
    }

    final int awaitJoin(WorkQueue w, ForkJoinTask<?> task, long deadline) {
        int s = 0;
        int seed = ThreadLocalRandom.nextSecondarySeed();
        if (w != null && task != null &&
                (!(task instanceof CountedCompleter) ||
                        (s = w.helpCC((CountedCompleter<?>) task, 0, false)) >= 0)) {
            w.tryRemoveAndExec(task);
            int src = w.source, id = w.id;
            int r = (seed >>> 16) | 1, step = (seed & ~1) | 2;
            s = task.status;
            while (s >= 0) {
                WorkQueue[] ws;
                int n = (ws = workQueues) == null ? 0 : ws.length, m = n - 1;
                while (n > 0) {
                    WorkQueue q;
                    int b;
                    if ((q = ws[r & m]) != null && q.source == id &&
                            q.top != (b = q.base)) {
                        ForkJoinTask<?>[] a;
                        int cap, k;
                        int qid = q.id;
                        if ((a = q.array) != null && (cap = a.length) > 0) {
                            ForkJoinTask<?> t = (ForkJoinTask<?>)
                                    QA.getAcquire(a, k = (cap - 1) & b);
                            if (q.source == id && q.base == b++ &&
                                    t != null && QA.compareAndSet(a, k, t, null)) {
                                q.base = b;
                                w.source = qid;
                                t.doExec();
                                w.source = src;
                            }
                        }
                        break;
                    } else {
                        r += step;
                        --n;
                    }
                }
                if ((s = task.status) < 0)
                    break;
                else if (n == 0) { // empty scan
                    long ms, ns;
                    int block;
                    if (deadline == 0L)
                        ms = 0L;                       // untimed
                    else if ((ns = deadline - System.nanoTime()) <= 0L)
                        break;                         // timeout
                    else if ((ms = TimeUnit.NANOSECONDS.toMillis(ns)) <= 0L)
                        ms = 1L;                       // avoid 0 for timed wait
                    if ((block = tryCompensate(w)) != 0) {
                        task.internalWait(ms);
                        CTL.getAndAdd(this, (block > 0) ? RC_UNIT : 0L);
                    }
                    s = task.status;
                }
            }
        }
        return s;
    }

    final void helpQuiescePool(WorkQueue w) {
        int prevSrc = w.source;
        int seed = ThreadLocalRandom.nextSecondarySeed();
        int r = seed >>> 16, step = r | 1;
        for (int source = prevSrc, released = -1; ; ) { // -1 until known
            ForkJoinTask<?> localTask;
            WorkQueue[] ws;
            while ((localTask = w.nextLocalTask()) != null)
                localTask.doExec();
            if (w.phase >= 0 && released == -1)
                released = 1;
            boolean quiet = true, empty = true;
            int n = (ws = workQueues) == null ? 0 : ws.length;
            for (int m = n - 1; n > 0; r += step, --n) {
                WorkQueue q;
                int b;
                if ((q = ws[r & m]) != null) {
                    int qs = q.source;
                    if (q.top != (b = q.base)) {
                        quiet = empty = false;
                        ForkJoinTask<?>[] a;
                        int cap, k;
                        int qid = q.id;
                        if ((a = q.array) != null && (cap = a.length) > 0) {
                            if (released == 0) {    // increment
                                released = 1;
                                CTL.getAndAdd(this, RC_UNIT);
                            }
                            ForkJoinTask<?> t = (ForkJoinTask<?>)
                                    QA.getAcquire(a, k = (cap - 1) & b);
                            if (q.base == b++ && t != null &&
                                    QA.compareAndSet(a, k, t, null)) {
                                q.base = b;
                                w.source = qid;
                                t.doExec();
                                w.source = source = prevSrc;
                            }
                        }
                        break;
                    } else if ((qs & QUIET) == 0)
                        quiet = false;
                }
            }
            if (quiet) {
                if (released == 0)
                    CTL.getAndAdd(this, RC_UNIT);
                w.source = prevSrc;
                break;
            } else if (empty) {
                if (source != QUIET)
                    w.source = source = QUIET;
                if (released == 1) {                 // decrement
                    released = 0;
                    CTL.getAndAdd(this, RC_MASK & -RC_UNIT);
                }
            }
        }
    }

    private ForkJoinTask<?> pollScan(boolean submissionsOnly) {
        WorkQueue[] ws;
        int n;
        rescan:
        while ((mode & STOP) == 0 && (ws = workQueues) != null &&
                (n = ws.length) > 0) {
            int m = n - 1;
            int r = ThreadLocalRandom.nextSecondarySeed();
            int h = r >>> 16;
            int origin, step;
            if (submissionsOnly) {
                origin = (r & ~1) & m;         // even indices and steps
                step = (h & ~1) | 2;
            } else {
                origin = r & m;
                step = h | 1;
            }
            boolean nonempty = false;
            for (int i = origin, oldSum = 0, checkSum = 0; ; ) {
                WorkQueue q;
                if ((q = ws[i]) != null) {
                    int b;
                    ForkJoinTask<?> t;
                    if (q.top - (b = q.base) > 0) {
                        nonempty = true;
                        if ((t = q.poll()) != null)
                            return t;
                    } else
                        checkSum += b + q.id;
                }
                if ((i = (i + step) & m) == origin) {
                    if (!nonempty && oldSum == (oldSum = checkSum))
                        break rescan;
                    checkSum = 0;
                    nonempty = false;
                }
            }
        }
        return null;
    }

    final ForkJoinTask<?> nextTaskFor(WorkQueue w) {
        ForkJoinTask<?> t;
        if (w == null || (t = w.nextLocalTask()) == null)
            t = pollScan(false);
        return t;
    }

    final void externalPush(ForkJoinTask<?> task) {
        int r;                                // initialize caller's probe
        if ((r = ThreadLocalRandom.getProbe()) == 0) {
            ThreadLocalRandom.localInit();
            r = ThreadLocalRandom.getProbe();
        }
        for (; ; ) {
            WorkQueue q;
            int md = mode, n;
            WorkQueue[] ws = workQueues;
            if ((md & SHUTDOWN) != 0 || ws == null || (n = ws.length) <= 0)
                throw new RejectedExecutionException();
            else if ((q = ws[(n - 1) & r & SQMASK]) == null) { // add queue
                int qid = (r | QUIET) & ~(FIFO | OWNED);
                Object lock = workerNamePrefix;
                ForkJoinTask<?>[] qa =
                        new ForkJoinTask<?>[INITIAL_QUEUE_CAPACITY];
                q = new WorkQueue(this, null);
                q.array = qa;
                q.id = qid;
                q.source = QUIET;
                if (lock != null) {     // unless disabled, lock pool to install
                    synchronized (lock) {
                        WorkQueue[] vs;
                        int i, vn;
                        if ((vs = workQueues) != null && (vn = vs.length) > 0 &&
                                vs[i = qid & (vn - 1) & SQMASK] == null)
                            vs[i] = q;  // else another thread already installed
                    }
                }
            } else if (!q.tryLockPhase()) // move if busy
                r = ThreadLocalRandom.advanceProbe(r);
            else {
                if (q.lockedPush(task))
                    signalWork(null);
                return;
            }
        }
    }

    private <T> ForkJoinTask<T> externalSubmit(ForkJoinTask<T> task) {
        Thread t;
        ForkJoinWorkerThread w;
        WorkQueue q;
        if (task == null)
            throw new NullPointerException();
        if (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) &&
                (w = (ForkJoinWorkerThread) t).pool == this &&
                (q = w.workQueue) != null)
            q.push(task);
        else
            externalPush(task);
        return task;
    }

    static WorkQueue commonSubmitterQueue() {
        ForkJoinPool p = common;
        int r = ThreadLocalRandom.getProbe();
        WorkQueue[] ws;
        int n;
        return (p != null && (ws = p.workQueues) != null &&
                (n = ws.length) > 0) ?
                ws[(n - 1) & r & SQMASK] : null;
    }

    final boolean tryExternalUnpush(ForkJoinTask<?> task) {
        int r = ThreadLocalRandom.getProbe();
        WorkQueue[] ws;
        WorkQueue w;
        int n;
        return ((ws = workQueues) != null &&
                (n = ws.length) > 0 &&
                (w = ws[(n - 1) & r & SQMASK]) != null &&
                w.tryLockedUnpush(task));
    }

    final int externalHelpComplete(CountedCompleter<?> task, int maxTasks) {
        int r = ThreadLocalRandom.getProbe();
        WorkQueue[] ws;
        WorkQueue w;
        int n;
        return ((ws = workQueues) != null && (n = ws.length) > 0 &&
                (w = ws[(n - 1) & r & SQMASK]) != null) ?
                w.helpCC(task, maxTasks, true) : 0;
    }

    final int helpComplete(WorkQueue w, CountedCompleter<?> task, int maxTasks) {
        return (w == null) ? 0 : w.helpCC(task, maxTasks, false);
    }

    static int getSurplusQueuedTaskCount() {
        Thread t;
        ForkJoinWorkerThread wt;
        ForkJoinPool pool;
        WorkQueue q;
        if (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) &&
                (pool = (wt = (ForkJoinWorkerThread) t).pool) != null &&
                (q = wt.workQueue) != null) {
            int p = pool.mode & SMASK;
            int a = p + (int) (pool.ctl >> RC_SHIFT);
            int n = q.top - q.base;
            return n - (a > (p >>>= 1) ? 0 :
                    a > (p >>>= 1) ? 1 :
                            a > (p >>>= 1) ? 2 :
                                    a > (p >>>= 1) ? 4 :
                                            8);
        }
        return 0;
    }

    private boolean tryTerminate(boolean now, boolean enable) {
        int md; // 3 phases: try to set SHUTDOWN, then STOP, then TERMINATED

        while (((md = mode) & SHUTDOWN) == 0) {
            if (!enable || this == common)        // cannot shutdown
                return false;
            else
                MODE.compareAndSet(this, md, md | SHUTDOWN);
        }

        while (((md = mode) & STOP) == 0) {       // try to initiate termination
            if (!now) {                           // check if quiescent & empty
                for (long oldSum = 0L; ; ) {        // repeat until stable
                    boolean running = false;
                    long checkSum = ctl;
                    WorkQueue[] ws = workQueues;
                    if ((md & SMASK) + (int) (checkSum >> RC_SHIFT) > 0)
                        running = true;
                    else if (ws != null) {
                        WorkQueue w;
                        for (int i = 0; i < ws.length; ++i) {
                            if ((w = ws[i]) != null) {
                                int s = w.source, p = w.phase;
                                int d = w.id, b = w.base;
                                if (b != w.top ||
                                        ((d & 1) == 1 && (s >= 0 || p >= 0))) {
                                    running = true;
                                    break;     // working, scanning, or have work
                                }
                                checkSum += (((long) s << 48) + ((long) p << 32) +
                                        ((long) b << 16) + (long) d);
                            }
                        }
                    }
                    if (((md = mode) & STOP) != 0)
                        break;                 // already triggered
                    else if (running)
                        return false;
                    else if (workQueues == ws && oldSum == (oldSum = checkSum))
                        break;
                }
            }
            if ((md & STOP) == 0)
                MODE.compareAndSet(this, md, md | STOP);
        }

        while (((md = mode) & TERMINATED) == 0) { // help terminate others
            for (long oldSum = 0L; ; ) {            // repeat until stable
                WorkQueue[] ws;
                WorkQueue w;
                long checkSum = ctl;
                if ((ws = workQueues) != null) {
                    for (int i = 0; i < ws.length; ++i) {
                        if ((w = ws[i]) != null) {
                            ForkJoinWorkerThread wt = w.owner;
                            w.cancelAll();        // clear queues
                            if (wt != null) {
                                try {             // unblock join or park
                                    wt.interrupt();
                                } catch (Throwable ignore) {
                                }
                            }
                            checkSum += ((long) w.phase << 32) + w.base;
                        }
                    }
                }
                if (((md = mode) & TERMINATED) != 0 ||
                        (workQueues == ws && oldSum == (oldSum = checkSum)))
                    break;
            }
            if ((md & TERMINATED) != 0)
                break;
            else if ((md & SMASK) + (short) (ctl >>> TC_SHIFT) > 0)
                break;
            else if (MODE.compareAndSet(this, md, md | TERMINATED)) {
                synchronized (this) {
                    notifyAll();                  // for awaitTermination
                }
                break;
            }
        }
        return true;
    }

    private static Object newInstanceFromSystemProperty(String property) throws ReflectiveOperationException {
        String className = System.getProperty(property);
        return (className == null)
                ? null
                : ClassLoader.getSystemClassLoader().loadClass(className)
                .getConstructor().newInstance();
    }


    public static ForkJoinPool commonPool() {
        return common;
    }


    public <T> T invoke(ForkJoinTask<T> task) {
        if (task == null)
            throw new NullPointerException();
        externalSubmit(task);
        return task.join();
    }

    public void execute(ForkJoinTask<?> task) {
        externalSubmit(task);
    }

    public void execute(Runnable task) {
        if (task == null)
            throw new NullPointerException();
        ForkJoinTask<?> job;
        if (task instanceof ForkJoinTask<?>) // avoid re-wrap
            job = (ForkJoinTask<?>) task;
        else
            job = new ForkJoinTask.RunnableExecuteAction(task);
        externalSubmit(job);
    }

    public <T> ForkJoinTask<T> submit(ForkJoinTask<T> task) {
        return externalSubmit(task);
    }

    public <T> ForkJoinTask<T> submit(Callable<T> task) {
        return externalSubmit(new ForkJoinTask.AdaptedCallable<T>(task));
    }

    public <T> ForkJoinTask<T> submit(Runnable task, T result) {
        return externalSubmit(new ForkJoinTask.AdaptedRunnable<T>(task, result));
    }

    @SuppressWarnings("unchecked")
    public ForkJoinTask<?> submit(Runnable task) {
        if (task == null)
            throw new NullPointerException();
        return externalSubmit((task instanceof ForkJoinTask<?>)
                ? (ForkJoinTask<Void>) task
                : new ForkJoinTask.AdaptedRunnableAction(task));
    }

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        ArrayList<Future<T>> futures = new ArrayList<>(tasks.size());

        try {
            for (Callable<T> t : tasks) {
                ForkJoinTask<T> f = new ForkJoinTask.AdaptedCallable<T>(t);
                futures.add(f);
                externalSubmit(f);
            }
            for (int i = 0, size = futures.size(); i < size; i++)
                ((ForkJoinTask<?>) futures.get(i)).quietlyJoin();
            return futures;
        } catch (Throwable t) {
            for (int i = 0, size = futures.size(); i < size; i++)
                futures.get(i).cancel(false);
            throw t;
        }
    }

    public ForkJoinWorkerThreadFactory getFactory() {
        return factory;
    }

    public UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return ueh;
    }

    public int getParallelism() {
        int par = mode & SMASK;
        return (par > 0) ? par : 1;
    }

    public static int getCommonPoolParallelism() {
        return COMMON_PARALLELISM;
    }

    public int getPoolSize() {
        return ((mode & SMASK) + (short) (ctl >>> TC_SHIFT));
    }

    public boolean getAsyncMode() {
        return (mode & FIFO) != 0;
    }

    public int getRunningThreadCount() {
        WorkQueue[] ws;
        WorkQueue w;
        VarHandle.acquireFence();
        int rc = 0;
        if ((ws = workQueues) != null) {
            for (int i = 1; i < ws.length; i += 2) {
                if ((w = ws[i]) != null && w.isApparentlyUnblocked())
                    ++rc;
            }
        }
        return rc;
    }

    public int getActiveThreadCount() {
        int r = (mode & SMASK) + (int) (ctl >> RC_SHIFT);
        return (r <= 0) ? 0 : r; // suppress momentarily negative values
    }

    public boolean isQuiescent() {
        for (; ; ) {
            long c = ctl;
            int md = mode, pc = md & SMASK;
            int tc = pc + (short) (c >>> TC_SHIFT);
            int rc = pc + (int) (c >> RC_SHIFT);
            if ((md & (STOP | TERMINATED)) != 0)
                return true;
            else if (rc > 0)
                return false;
            else {
                WorkQueue[] ws;
                WorkQueue v;
                if ((ws = workQueues) != null) {
                    for (int i = 1; i < ws.length; i += 2) {
                        if ((v = ws[i]) != null) {
                            if (v.source > 0)
                                return false;
                            --tc;
                        }
                    }
                }
                if (tc == 0 && ctl == c)
                    return true;
            }
        }
    }

    public long getStealCount() {
        long count = stealCount;
        WorkQueue[] ws;
        WorkQueue w;
        if ((ws = workQueues) != null) {
            for (int i = 1; i < ws.length; i += 2) {
                if ((w = ws[i]) != null)
                    count += (long) w.nsteals & 0xffffffffL;
            }
        }
        return count;
    }

    public long getQueuedTaskCount() {
        WorkQueue[] ws;
        WorkQueue w;
        VarHandle.acquireFence();
        int count = 0;
        if ((ws = workQueues) != null) {
            for (int i = 1; i < ws.length; i += 2) {
                if ((w = ws[i]) != null)
                    count += w.queueSize();
            }
        }
        return count;
    }

    public int getQueuedSubmissionCount() {
        WorkQueue[] ws;
        WorkQueue w;
        VarHandle.acquireFence();
        int count = 0;
        if ((ws = workQueues) != null) {
            for (int i = 0; i < ws.length; i += 2) {
                if ((w = ws[i]) != null)
                    count += w.queueSize();
            }
        }
        return count;
    }

    public boolean hasQueuedSubmissions() {
        WorkQueue[] ws;
        WorkQueue w;
        VarHandle.acquireFence();
        if ((ws = workQueues) != null) {
            for (int i = 0; i < ws.length; i += 2) {
                if ((w = ws[i]) != null && !w.isEmpty())
                    return true;
            }
        }
        return false;
    }

    protected ForkJoinTask<?> pollSubmission() {
        return pollScan(true);
    }

    protected int drainTasksTo(Collection<? super ForkJoinTask<?>> c) {
        WorkQueue[] ws;
        WorkQueue w;
        ForkJoinTask<?> t;
        VarHandle.acquireFence();
        int count = 0;
        if ((ws = workQueues) != null) {
            for (int i = 0; i < ws.length; ++i) {
                if ((w = ws[i]) != null) {
                    while ((t = w.poll()) != null) {
                        c.add(t);
                        ++count;
                    }
                }
            }
        }
        return count;
    }

    public String toString() {
        // Use a single pass through workQueues to collect counts
        int md = mode; // read volatile fields first
        long c = ctl;
        long st = stealCount;
        long qt = 0L, qs = 0L;
        int rc = 0;
        WorkQueue[] ws;
        WorkQueue w;
        if ((ws = workQueues) != null) {
            for (int i = 0; i < ws.length; ++i) {
                if ((w = ws[i]) != null) {
                    int size = w.queueSize();
                    if ((i & 1) == 0)
                        qs += size;
                    else {
                        qt += size;
                        st += (long) w.nsteals & 0xffffffffL;
                        if (w.isApparentlyUnblocked())
                            ++rc;
                    }
                }
            }
        }

        int pc = (md & SMASK);
        int tc = pc + (short) (c >>> TC_SHIFT);
        int ac = pc + (int) (c >> RC_SHIFT);
        if (ac < 0) // ignore transient negative
            ac = 0;
        String level = ((md & TERMINATED) != 0 ? "Terminated" :
                (md & STOP) != 0 ? "Terminating" :
                        (md & SHUTDOWN) != 0 ? "Shutting down" :
                                "Running");
        return super.toString() +
                "[" + level +
                ", parallelism = " + pc +
                ", size = " + tc +
                ", active = " + ac +
                ", running = " + rc +
                ", steals = " + st +
                ", tasks = " + qt +
                ", submissions = " + qs +
                "]";
    }

    public void shutdown() {
        checkPermission();
        tryTerminate(false, true);
    }

    public List<Runnable> shutdownNow() {
        checkPermission();
        tryTerminate(true, true);
        return Collections.emptyList();
    }

    public boolean isTerminated() {
        return (mode & TERMINATED) != 0;
    }

    public boolean isTerminating() {
        int md = mode;
        return (md & STOP) != 0 && (md & TERMINATED) == 0;
    }

    public boolean isShutdown() {
        return (mode & SHUTDOWN) != 0;
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        if (this == common) {
            awaitQuiescence(timeout, unit);
            return false;
        }
        long nanos = unit.toNanos(timeout);
        if (isTerminated())
            return true;
        if (nanos <= 0L)
            return false;
        long deadline = System.nanoTime() + nanos;
        synchronized (this) {
            for (; ; ) {
                if (isTerminated())
                    return true;
                if (nanos <= 0L)
                    return false;
                long millis = TimeUnit.NANOSECONDS.toMillis(nanos);
                wait(millis > 0L ? millis : 1L);
                nanos = deadline - System.nanoTime();
            }
        }
    }

    public boolean awaitQuiescence(long timeout, TimeUnit unit) {
        long nanos = unit.toNanos(timeout);
        ForkJoinWorkerThread wt;
        Thread thread = Thread.currentThread();
        if ((thread instanceof ForkJoinWorkerThread) &&
                (wt = (ForkJoinWorkerThread) thread).pool == this) {
            helpQuiescePool(wt.workQueue);
            return true;
        } else {
            for (long startTime = System.nanoTime(); ; ) {
                ForkJoinTask<?> t;
                if ((t = pollScan(false)) != null)
                    t.doExec();
                else if (isQuiescent())
                    return true;
                else if ((System.nanoTime() - startTime) > nanos)
                    return false;
                else
                    Thread.yield(); // cannot block
            }
        }
    }

    static void quiesceCommonPool() {
        common.awaitQuiescence(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    }

    public static interface ManagedBlocker {
        boolean block() throws InterruptedException;
        boolean isReleasable();
    }

    public static void managedBlock(ManagedBlocker blocker) throws InterruptedException {
        if (blocker == null) throw new NullPointerException();
        ForkJoinPool p;
        ForkJoinWorkerThread wt;
        WorkQueue w;
        Thread t = Thread.currentThread();
        if ((t instanceof ForkJoinWorkerThread) &&
                (p = (wt = (ForkJoinWorkerThread) t).pool) != null &&
                (w = wt.workQueue) != null) {
            int block;
            while (!blocker.isReleasable()) {
                if ((block = p.tryCompensate(w)) != 0) {
                    try {
                        do {
                        } while (!blocker.isReleasable() &&
                                !blocker.block());
                    } finally {
                        CTL.getAndAdd(p, (block > 0) ? RC_UNIT : 0L);
                    }
                    break;
                }
            }
        } else {
            do {
            } while (!blocker.isReleasable() &&
                    !blocker.block());
        }
    }

    static void helpAsyncBlocker(Executor e, ManagedBlocker blocker) {
        if (e instanceof ForkJoinPool) {
            WorkQueue w;
            ForkJoinWorkerThread wt;
            WorkQueue[] ws;
            int r, n;
            ForkJoinPool p = (ForkJoinPool) e;
            Thread thread = Thread.currentThread();
            if (thread instanceof ForkJoinWorkerThread &&
                    (wt = (ForkJoinWorkerThread) thread).pool == p)
                w = wt.workQueue;
            else if ((r = ThreadLocalRandom.getProbe()) != 0 &&
                    (ws = p.workQueues) != null && (n = ws.length) > 0)
                w = ws[(n - 1) & r & SQMASK];
            else
                w = null;
            if (w != null)
                w.helpAsyncBlocker(blocker);
        }
    }


    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new ForkJoinTask.AdaptedRunnable<T>(runnable, value);
    }

    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new ForkJoinTask.AdaptedCallable<T>(callable);
    }


    private static final class InnocuousForkJoinWorkerThreadFactory implements ForkJoinWorkerThreadFactory {


        private static final AccessControlContext ACC = contextWithPermissions(
                modifyThreadPermission,
                new RuntimePermission("enableContextClassLoaderOverride"),
                new RuntimePermission("modifyThreadGroup"),
                new RuntimePermission("getClassLoader"),
                new RuntimePermission("setContextClassLoader"));

        public final ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            return AccessController.doPrivileged(
                    new PrivilegedAction<>() {
                        public ForkJoinWorkerThread run() {
                            return new ForkJoinWorkerThread.
                                    InnocuousForkJoinWorkerThread(pool);
                        }
                    },
                    ACC);
        }
    }
}
