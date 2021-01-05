package java.util.concurrent.locks;
/**
 * jdk14 完结
 */
import jdk.internal.misc.Unsafe;

public class LockSupport {
    private static final Unsafe U = Unsafe.getUnsafe(); // 得到cas工具类
    private static final long PARKBLOCKER = U.objectFieldOffset(Thread.class, "parkBlocker");// 获取parkBlocker字段
    private static final long TID = U.objectFieldOffset(Thread.class, "tid"); // 内存操作获取线程的tid

    private LockSupport() {} // 构造方法私有化，不能被外部初始化

    /**
     * @param t 需要阻塞的线程
     * @param arg 引起阻塞的资源
     */
    private static void setBlocker(Thread t, Object arg) {
        U.putReferenceOpaque(t, PARKBLOCKER, arg);
    }

    /**
     * 阻塞当前线程
     */
    public static void park() {
        U.park(false, 0L);
    }
    /**
     * 阻塞线程
     * 该方法用于系统排查和监测
     */
    public static void park(Object blocker) {
        Thread t = Thread.currentThread();
        setBlocker(t, blocker);
        U.park(false, 0L);
        setBlocker(t, null);
    }

    /**
     * 解除阻塞线程
     */
    public static void unpark(Thread thread) {
        if (thread != null)
            U.unpark(thread);
    }

    /**
     * blocker是引起线程阻塞的资源
     */
    public static void setCurrentBlocker(Object blocker) {
        U.putReferenceOpaque(Thread.currentThread(), PARKBLOCKER, blocker);
    }

    /**
     * @param blocker 阻塞的资源
     * @param nanos 需要阻塞的纳秒时间
     */
    public static void parkNanos(Object blocker, long nanos) {
        if (nanos > 0) {
            Thread t = Thread.currentThread();
            setBlocker(t, blocker);
            U.park(false, nanos);
            setBlocker(t, null);
        }
    }
    /**
     * 在指定的deadline时限前禁用当前线程，除非许可可用。
     */
    public static void parkUntil(long deadline) {
        U.park(true, deadline);
    }
    /**
     * 带阻塞资源的，在指定的deadline时限前禁用当前线程，除非许可可用。
     */
    public static void parkUntil(Object blocker, long deadline) {
        Thread t = Thread.currentThread();
        setBlocker(t, blocker);
        U.park(true, deadline);
        setBlocker(t, null);
    }

    /**
     * 获取阻塞
     */
    public static Object getBlocker(Thread t) {
        if (t == null)
            throw new NullPointerException();
        return U.getReferenceOpaque(t, PARKBLOCKER);
    }

    /**
     * 阻塞 nanos 纳秒
     */
    public static void parkNanos(long nanos) {
        if (nanos > 0)
            U.park(false, nanos);
    }



    /**
     * 获取线程的tid
     */
    static final long getThreadId(Thread thread) {
        return U.getLong(thread, TID);
    }


}

