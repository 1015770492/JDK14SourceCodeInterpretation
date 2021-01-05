
package java.lang;


import jdk.internal.misc.VM;

class Shutdown {

    private static final int MAX_SYSTEM_HOOKS = 10;
    private static final Runnable[] hooks = new Runnable[MAX_SYSTEM_HOOKS];// 创建数组存放10个线程
    private static int currentRunningHook = -1;
    private static class Lock {}                    // 静态内部类Lock
    private static Object lock = new Lock();        // Lock实例对象lock
    private static Object haltLock = new Lock();    // Lock实例对象haltLock


    static void add(int slot, boolean registerShutdownInProgress, Runnable hook) {
        if (slot < 0 || slot >= MAX_SYSTEM_HOOKS) {
            throw new IllegalArgumentException("Invalid slot: " + slot);
        }
        synchronized (lock) {
            if (hooks[slot] != null)
                throw new InternalError("Shutdown hook at slot " + slot + " already registered");

            if (!registerShutdownInProgress) {
                if (currentRunningHook >= 0)
                    throw new IllegalStateException("Shutdown in progress");
            } else {
                if (VM.isShutdown() || slot <= currentRunningHook)
                    throw new IllegalStateException("Shutdown in progress");
            }

            hooks[slot] = hook;
        }
    }

    private static void runHooks() {
        synchronized (lock) {
            if (VM.isShutdown()) return;
        }

        for (int i = 0; i < MAX_SYSTEM_HOOKS; i++) {
            try {
                Runnable hook;
                synchronized (lock) {
                    currentRunningHook = i;
                    hook = hooks[i];
                }
                if (hook != null) hook.run();
            } catch (Throwable t) {
                if (t instanceof ThreadDeath) {
                    ThreadDeath td = (ThreadDeath) t;
                    throw td;
                }
            }
        }

        VM.shutdown();
    }

    static native void beforeHalt();

    static void halt(int status) {
        synchronized (haltLock) {
            halt0(status);
        }
    }
    // 本地方法
    static native void halt0(int status);

    static void exit(int status) {
        synchronized (lock) {
            if (status != 0 && VM.isShutdown()) {
                halt(status);
            }
        }
        synchronized (Shutdown.class) {
            beforeHalt();
            runHooks();
            halt(status);
        }
    }


    static void shutdown() {
        synchronized (Shutdown.class) {
            runHooks();
        }
    }

}
