package java.lang;

import jdk.internal.misc.TerminatingThreadLocal;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class Thread implements Runnable {

    // 线程的状态枚举
    public enum State {
        NEW,            // 线程未启动状态
        RUNNABLE,       // 就绪状态
        BLOCKED,        // 阻塞状态
        WAITING,        // 等待状态
        TIMED_WAITING,  // 定时等待
        TERMINATED;     // 完成任务后的终止状态
    }
    ThreadLocal.ThreadLocalMap threadLocals = null; // ThreadLocalMap变量
    ThreadLocal.ThreadLocalMap inheritableThreadLocals = null;// 继承的ThreadLocalMap
    private int priority;                       // 优先级
    public static final int MIN_PRIORITY = 1;   // 最低优先级
    public static final int NORM_PRIORITY = 5;  // 正常优先级
    public static final int MAX_PRIORITY = 10;  // 最高优先级
    private final long tid;                     // 线程id
    private volatile String name;               // 线程的名字
    private boolean daemon = false;             // 守护进程
    volatile Object parkBlocker;                // 阻塞资源
    private volatile boolean interrupted;       // 中断
    private Runnable target;                    // 线程任务
    private ThreadGroup group;                  // 线程组
    private final long stackSize;               // 堆栈大小
    private static long threadSeqNumber;        // 生成id
    private volatile int threadStatus;          // 线程状态
    private static int threadInitNumber;        // 线程初始编号
    private ClassLoader contextClassLoader;     // 线程上下文类加载器
    private volatile Interruptible blocker;     // 阻塞器(将线程中断)
    private final Object blockerLock = new Object();            // 对象锁,用于控制线程阻塞和唤醒
    private AccessControlContext inheritedAccessControlContext; // 访问控制上下文
    private volatile UncaughtExceptionHandler uncaughtExceptionHandler;                 // 除非明确设置否则位null
    private static volatile UncaughtExceptionHandler defaultUncaughtExceptionHandler;   // 默认未捕捉的异常处理
    private static final StackTraceElement[] EMPTY_STACK_TRACE = new StackTraceElement[0];
    private long eetop;                 //
    private boolean stillborn = false;  //
    @jdk.internal.vm.annotation.Contended("tlr")
    long threadLocalRandomSeed;
    @jdk.internal.vm.annotation.Contended("tlr")
    int threadLocalRandomProbe;
    @jdk.internal.vm.annotation.Contended("tlr")
    int threadLocalRandomSecondarySeed;


    /**
     * 构造方法
     */
    public Thread() {
        this(null, null, "Thread-" + nextThreadNum(), 0);
    }
    public Thread(Runnable target) {
        this(null, target, "Thread-" + nextThreadNum(), 0);
    }
    public Thread(ThreadGroup group, Runnable target) {
        this(group, target, "Thread-" + nextThreadNum(), 0);
    }
    public Thread(String name) {
        this(null, null, name, 0);
    }
    public Thread(ThreadGroup group, String name) {
        this(group, null, name, 0);
    }
    public Thread(Runnable target, String name) {
        this(null, target, name, 0);
    }
    public Thread(ThreadGroup group, Runnable target, String name) {
        this(group, target, name, 0);
    }
    public Thread(ThreadGroup group, Runnable target, String name, long stackSize) {
        this(group, target, name, stackSize, null, true);
    }
    public Thread(ThreadGroup group, Runnable target, String name, long stackSize, boolean inheritThreadLocals) {
        this(group, target, name, stackSize, null, inheritThreadLocals);
    }
    Thread(Runnable target, AccessControlContext acc) {
        this(null, target, "Thread-" + nextThreadNum(), 0, acc, false);
    }
    private Thread(ThreadGroup g, Runnable target, String name, long stackSize, AccessControlContext acc, boolean inheritThreadLocals) {
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }

        this.name = name;

        Thread parent = currentThread();
        SecurityManager security = System.getSecurityManager();
        if (g == null) {
            if (security != null) {
                g = security.getThreadGroup();
            }
            if (g == null) {
                g = parent.getThreadGroup();
            }
        }
        g.checkAccess();

        if (security != null) {
            if (isCCLOverridden(getClass())) {
                security.checkPermission(
                        SecurityConstants.SUBCLASS_IMPLEMENTATION_PERMISSION);
            }
        }

        g.addUnstarted();

        this.group = g;
        this.daemon = parent.isDaemon();
        this.priority = parent.getPriority();
        if (security == null || isCCLOverridden(parent.getClass()))
            this.contextClassLoader = parent.getContextClassLoader();
        else
            this.contextClassLoader = parent.contextClassLoader;
        this.inheritedAccessControlContext =
                acc != null ? acc : AccessController.getContext();
        this.target = target;
        setPriority(priority);
        if (inheritThreadLocals && parent.inheritableThreadLocals != null)
            this.inheritableThreadLocals =
                    ThreadLocal.createInheritedMap(parent.inheritableThreadLocals);
        /* Stash the specified stack size in case the VM cares */
        this.stackSize = stackSize;

        /* Set thread ID */
        this.tid = nextThreadID();
    }

    /**
     * 线程状态控制方法
     */
    private native void start0();
    public synchronized void start() {
        if (threadStatus != 0) throw new IllegalThreadStateException();
        group.add(this);
        boolean started = false;
        try {
            start0();// 调用native方法启动一个线程
            started = true;
        } finally {
            try {
                if (!started) {
                    group.threadStartFailed(this);
                }
            } catch (Throwable ignore) {

            }
        }
    }
    // 休眠状态
    public static native void sleep(long millis) throws InterruptedException;
    public static void sleep(long millis, int nanos) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        if (nanos < 0 || nanos > 999999) {
            throw new IllegalArgumentException("nanosecond timeout value out of range");
        }
        if (nanos > 0 && millis < Long.MAX_VALUE) {
            millis++;
        }
        sleep(millis);
    }
    // 线程要执行的任务(实现Runnable接口)
    public void run() {
        if (target != null) {
            target.run();
        }
    }
    // 让出cpu使用权,将线程变成就绪状态
    public static native void yield();

    /**
     *
     */
    public final void join() throws InterruptedException {
        join(0);
    }
    public final synchronized void join(final long millis) throws InterruptedException {
        if (millis > 0) {
            if (isAlive()) {
                final long startTime = System.nanoTime();
                long delay = millis;
                do {
                    wait(delay);
                } while (isAlive() &&
                        (delay = millis - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)) > 0);
            }
        } else if (millis == 0) {
            while (isAlive()) {
                wait(0);
            }
        } else {
            throw new IllegalArgumentException("timeout value is negative");
        }
    }
    public final synchronized void join(long millis, int nanos) throws InterruptedException {

        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        if (nanos < 0 || nanos > 999999) {
            throw new IllegalArgumentException(
                    "nanosecond timeout value out of range");
        }

        if (nanos > 0 && millis < Long.MAX_VALUE) {
            millis++;
        }

        join(millis);
    }

    // 正常结束线程,清除一些对象,防止内存泄露
    private void exit() {
        if (threadLocals != null && TerminatingThreadLocal.REGISTRY.isPresent()) {
            TerminatingThreadLocal.threadTerminated();
        }
        if (group != null) {
            group.threadTerminated(this);
            group = null;
        }
        target = null;
        threadLocals = null;
        inheritableThreadLocals = null;
        inheritedAccessControlContext = null;
        blocker = null;
        uncaughtExceptionHandler = null;
    }
    @Deprecated(since="1.2")
    public final void stop() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            checkAccess();
            if (this != Thread.currentThread()) {
                security.checkPermission(SecurityConstants.STOP_THREAD_PERMISSION);
            }
        }

        if (threadStatus != 0) {
            resume(); // Wake up thread if it was suspended; no-op otherwise
        }

        stop0(new ThreadDeath());
    }
    public void interrupt() {
        if (this != Thread.currentThread()) {
            checkAccess();

            // thread may be blocked in an I/O operation
            synchronized (blockerLock) {
                Interruptible b = blocker;
                if (b != null) {
                    interrupted = true;
                    interrupt0();  // inform VM of interrupt
                    b.interrupt(this);
                    return;
                }
            }
        }
        interrupted = true;
        // inform VM of interrupt
        interrupt0();
    }
    public static boolean interrupted() {
        Thread t = currentThread();
        boolean interrupted = t.interrupted;
        if (interrupted) {
            t.interrupted = false;
            clearInterruptEvent();
        }
        return interrupted;
    }
    // 判断线程是否被中断
    public boolean isInterrupted() {
        return interrupted;
    }
    // 判断线程是否还存活
    public final native boolean isAlive();
    @Deprecated(since="1.2", forRemoval=true)
    public final void suspend() {
        checkAccess();
        suspend0();
    }
    @Deprecated(since="1.2", forRemoval=true)
    public final void resume() {
        checkAccess();
        resume0();
    }
    @HotSpotIntrinsicCandidate
    public static void onSpinWait() {}
    static void blockedOn(Interruptible b) {
        Thread me = Thread.currentThread();
        synchronized (me.blockerLock) {
            me.blocker = b;
        }
    }
    private static synchronized long nextThreadID() {
        return ++threadSeqNumber;
    }

    /**
     * 设置线程优先级
     */
    public final void setPriority(int newPriority) {
        ThreadGroup g;
        checkAccess();
        if (newPriority > MAX_PRIORITY || newPriority < MIN_PRIORITY) {
            throw new IllegalArgumentException();
        }
        if((g = getThreadGroup()) != null) {
            if (newPriority > g.getMaxPriority()) {
                newPriority = g.getMaxPriority();
            }
            setPriority0(priority = newPriority);
        }
    }
    /**
     * 获取线程优先级
     */
    public final int getPriority() { return priority; }
    public long getId() { return tid; }
    public State getState() { return jdk.internal.misc.VM.toThreadState(threadStatus); }
    public final synchronized void setName(String name) {
        checkAccess();
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }

        this.name = name;
        if (threadStatus != 0) {
            setNativeName(name);
        }
    }
    public final String getName() {
        return name;
    }
    private static synchronized int nextThreadNum() {
        return threadInitNumber++;
    }
    public final ThreadGroup getThreadGroup() {
        return group;
    }
    public static int activeCount() {
        return currentThread().getThreadGroup().activeCount();
    }
    public static int enumerate(Thread tarray[]) {
        return currentThread().getThreadGroup().enumerate(tarray);
    }
    @Deprecated(since="1.2", forRemoval=true)
    public int countStackFrames() {
        throw new UnsupportedOperationException();
    }
    public static void dumpStack() { new Exception("Stack trace").printStackTrace(); }
    public final void setDaemon(boolean on) {
        checkAccess();
        if (isAlive()) {
            throw new IllegalThreadStateException();
        }
        daemon = on;
    }
    public final boolean isDaemon() { return daemon; }
    public final void checkAccess() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkAccess(this);
        }
    }

    @CallerSensitive
    public ClassLoader getContextClassLoader() {
        if (contextClassLoader == null)
            return null;
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            ClassLoader.checkClassLoaderPermission(contextClassLoader,
                    Reflection.getCallerClass());
        }
        return contextClassLoader;
    }
    public void setContextClassLoader(ClassLoader cl) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission("setContextClassLoader"));
        }
        contextClassLoader = cl;
    }
    public StackTraceElement[] getStackTrace() {
        if (this != Thread.currentThread()) {
            SecurityManager security = System.getSecurityManager();
            if (security != null) {
                security.checkPermission(
                        SecurityConstants.GET_STACK_TRACE_PERMISSION);
            }
            if (!isAlive()) {
                return EMPTY_STACK_TRACE;
            }
            StackTraceElement[][] stackTraceArray = dumpThreads(new Thread[] {this});
            StackTraceElement[] stackTrace = stackTraceArray[0];
            if (stackTrace == null) {
                stackTrace = EMPTY_STACK_TRACE;
            }
            return stackTrace;
        } else {
            return (new Exception()).getStackTrace();
        }
    }
    public static Map<Thread, StackTraceElement[]> getAllStackTraces() {

        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(SecurityConstants.GET_STACK_TRACE_PERMISSION);
            security.checkPermission(SecurityConstants.MODIFY_THREADGROUP_PERMISSION);
        }

        Thread[] threads = getThreads();
        StackTraceElement[][] traces = dumpThreads(threads);
        Map<Thread, StackTraceElement[]> m = new HashMap<>(threads.length);
        for (int i = 0; i < threads.length; i++) {
            StackTraceElement[] stackTrace = traces[i];
            if (stackTrace != null) {
                m.put(threads[i], stackTrace);
            }
        }
        return m;
    }
    private static class Caches {
        static final ConcurrentMap<WeakClassKey,Boolean> subclassAudits = new ConcurrentHashMap<>();
        static final ReferenceQueue<Class<?>> subclassAuditsQueue = new ReferenceQueue<>();
    }
    private static boolean isCCLOverridden(Class<?> cl) {
        if (cl == Thread.class)
            return false;

        processQueue(Caches.subclassAuditsQueue, Caches.subclassAudits);
        WeakClassKey key = new WeakClassKey(cl, Caches.subclassAuditsQueue);
        Boolean result = Caches.subclassAudits.get(key);
        if (result == null) {
            result = Boolean.valueOf(auditSubclass(cl));
            Caches.subclassAudits.putIfAbsent(key, result);
        }

        return result.booleanValue();
    }
    private static boolean auditSubclass(final Class<?> subcl) {
        Boolean result = AccessController.doPrivileged(
                new PrivilegedAction<>() {
                    public Boolean run() {
                        for (Class<?> cl = subcl; cl != Thread.class; cl = cl.getSuperclass()) {
                            try {
                                cl.getDeclaredMethod("getContextClassLoader", new Class<?>[0]);
                                return Boolean.TRUE;
                            } catch (NoSuchMethodException ex) {
                            }
                            try {
                                Class<?>[] params = {ClassLoader.class};
                                cl.getDeclaredMethod("setContextClassLoader", params);
                                return Boolean.TRUE;
                            } catch (NoSuchMethodException ex) {
                            }
                        }
                        return Boolean.FALSE;
                    }
                }
        );
        return result.booleanValue();
    }
    public String toString() {
        ThreadGroup group = getThreadGroup();
        if (group != null) {
            return "Thread[" + getName() + "," + getPriority() + "," +
                           group.getName() + "]";
        } else {
            return "Thread[" + getName() + "," + getPriority() + "," +
                            "" + "]";
        }
    }
    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }
    @FunctionalInterface
    public interface UncaughtExceptionHandler {
        void uncaughtException(Thread t, Throwable e);
    }
    public static void setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(
                new RuntimePermission("setDefaultUncaughtExceptionHandler")
                    );
        }
         defaultUncaughtExceptionHandler = eh;
     }
    public static UncaughtExceptionHandler getDefaultUncaughtExceptionHandler(){
        return defaultUncaughtExceptionHandler;
    }
    public UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return uncaughtExceptionHandler != null ?
            uncaughtExceptionHandler : group;
    }
    public void setUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
        checkAccess();
        uncaughtExceptionHandler = eh;
    }
    private void dispatchUncaughtException(Throwable e) {
        getUncaughtExceptionHandler().uncaughtException(this, e);
    }
    static void processQueue(ReferenceQueue<Class<?>> queue, ConcurrentMap<? extends WeakReference<Class<?>>, ?> map) {
        Reference<? extends Class<?>> ref;
        while((ref = queue.poll()) != null) {
            map.remove(ref);
        }
    }
    static class WeakClassKey extends WeakReference<Class<?>> {

        private final int hash;
        WeakClassKey(Class<?> cl, ReferenceQueue<Class<?>> refQueue) {
            super(cl, refQueue);
            hash = System.identityHashCode(cl);
        }
        @Override
        public int hashCode() {
            return hash;
        }
        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;

            if (obj instanceof WeakClassKey) {
                Object referent = get();
                return (referent != null) &&
                       (referent == ((WeakClassKey) obj).get());
            } else {
                return false;
            }
        }
    }
    static {
        registerNatives();// 讲方法关系进行映射
    }

    private static native void registerNatives();
    public static native boolean holdsLock(Object obj);
    private static native StackTraceElement[][] dumpThreads(Thread[] threads);
    private static native Thread[] getThreads();
    private native void setPriority0(int newPriority);
    private native void stop0(Object o);
    private native void suspend0();
    private native void resume0();
    private native void interrupt0();
    private static native void clearInterruptEvent();
    private native void setNativeName(String name);
    @HotSpotIntrinsicCandidate
    public static native Thread currentThread();
}
