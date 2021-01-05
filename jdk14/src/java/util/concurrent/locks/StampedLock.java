package java.util.concurrent.locks;


import jdk.internal.misc.Unsafe;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

public class StampedLock implements Serializable {

    private static final long serialVersionUID = -6001602636862214147L; // 序列化版本号
    private static final Unsafe U = Unsafe.getUnsafe(); //工具类，提供CAS等内存级别的直接操作，是juc中很重要的一个类之一
    private static final long STATE = U.objectFieldOffset(StampedLock.class, "state"); // 内存操作直接得到当前对象的state值，类似与反射，但更底层
    private static final long HEAD = U.objectFieldOffset(StampedLock.class, "head");// 得到head值
    private static final long TAIL = U.objectFieldOffset(StampedLock.class, "tail");// 得到tail值
    private transient volatile long state;                                          // 真正的state属性，上面通过Unsafe类内存操作直接得到该值
    private transient volatile Node head;                                           // 真正的head对象
    private transient volatile Node tail;                                           // 真正的tail对象
    private static final int LG_READERS = 7; // 127 readers
    private static final long WBIT = 1L << LG_READERS; // 2的七次方=128
    private static final long RUNIT = 1L;
    private static final long RBITS = WBIT - 1L;// 127
    private static final long RFULL = RBITS - 1L;// 127
    private static final long ABITS = RBITS | WBIT;
    private static final long SBITS = ~RBITS; // note overlap with ABITS
    private static final long RSAFE = ~(3L << (LG_READERS - 1));
    private static final long ORIGIN = WBIT << 1;
    private static final long INTERRUPTED = 1L;
    static final int WAITING = 1;
    static final int CANCELLED = 0x80000000; // must be negative
    transient ReadLockView readLockView;
    transient WriteLockView writeLockView;
    transient ReadWriteLockView readWriteLockView;
    private transient int readerOverflow;

    static {
        java.lang.Class<?> ensureLoaded = LockSupport.class; // z阻塞线程工具类，防止LockSupport没有被加载
    }

    /**
     * CLH nodes
     */
    abstract static class Node {
        volatile Node prev;       // initially attached via casTail
        volatile Node next;       // visibly nonnull when signallable
        Thread waiter;            // visibly nonnull when enqueued
        volatile int status;      // written by owner, atomic bit ops by others

        final boolean casPrev(Node c, Node v) {  // for cleanQueue
            return U.weakCompareAndSetReference(this, PREV, c, v);
        }

        final boolean casNext(Node c, Node v) {  // for cleanQueue
            return U.weakCompareAndSetReference(this, NEXT, c, v);
        }

        final int getAndUnsetStatus(int v) {     // for signalling
            return U.getAndBitwiseAndInt(this, STATUS, ~v);
        }

        final void setPrevRelaxed(Node p) {      // for off-queue assignment
            U.putReference(this, PREV, p);
        }

        final void setStatusRelaxed(int s) {     // for off-queue assignment
            U.putInt(this, STATUS, s);
        }

        final void clearStatus() {               // for reducing unneeded signals
            U.putIntOpaque(this, STATUS, 0);
        }

        private static final long STATUS = U.objectFieldOffset(Node.class, "status");
        private static final long NEXT = U.objectFieldOffset(Node.class, "next");
        private static final long PREV = U.objectFieldOffset(Node.class, "prev");
    }

    static final class WriterNode extends Node { // node for writers
    }

    static final class ReaderNode extends Node { // node for readers
        volatile ReaderNode cowaiters;           // list of linked readers

        final boolean casCowaiters(ReaderNode c, ReaderNode v) {
            return U.weakCompareAndSetReference(this, COWAITERS, c, v);
        }

        final void setCowaitersRelaxed(ReaderNode p) {
            U.putReference(this, COWAITERS, p);
        }

        private static final long COWAITERS = U.objectFieldOffset(ReaderNode.class, "cowaiters");
    }


    public StampedLock() {
        state = ORIGIN;
    }

    private boolean casState(long expect, long update) {
        return U.compareAndSetLong(this, STATE, expect, update);
    }

    @ReservedStackAccess
    private long tryAcquireWrite() {
        long s, nextState;
        if (((s = state) & ABITS) == 0L && casState(s, nextState = s | WBIT)) {
            U.storeStoreFence();
            return nextState;
        }
        return 0L;
    }

    @ReservedStackAccess
    private long tryAcquireRead() {
        for (long s, m, nextState; ; ) {
            if ((m = (s = state) & ABITS) < RFULL) {
                if (casState(s, nextState = s + RUNIT))
                    return nextState;
            } else if (m == WBIT)
                return 0L;
            else if ((nextState = tryIncReaderOverflow(s)) != 0L)
                return nextState;
        }
    }

    /**
     *
     */
    private static long unlockWriteState(long s) {
        return ((s += WBIT) == 0L) ? ORIGIN : s;
    }

    private long releaseWrite(long s) {
        long nextState = state = unlockWriteState(s);
        signalNext(head);
        return nextState;
    }

    /**
     *
     */
    @ReservedStackAccess
    public long writeLock() {
        // try unconditional CAS confirming weak read
        long s = U.getLongOpaque(this, STATE) & ~ABITS, nextState;
        if (casState(s, nextState = s | WBIT)) {
            U.storeStoreFence();
            return nextState;
        }
        return acquireWrite(false, false, 0L);
    }

    /**
     *
     */
    public long tryWriteLock() {
        return tryAcquireWrite();
    }

    /**
     *
     */
    public long tryWriteLock(long time, TimeUnit unit)
            throws InterruptedException {
        long nanos = unit.toNanos(time);
        if (!Thread.interrupted()) {
            long nextState;
            if ((nextState = tryAcquireWrite()) != 0L)
                return nextState;
            if (nanos <= 0L)
                return 0L;
            nextState = acquireWrite(true, true, System.nanoTime() + nanos);
            if (nextState != INTERRUPTED)
                return nextState;
        }
        throw new InterruptedException();
    }

    /**
     *
     */
    public long writeLockInterruptibly() throws InterruptedException {
        long nextState;
        if (!Thread.interrupted() &&
                ((nextState = tryAcquireWrite()) != 0L ||
                        (nextState = acquireWrite(true, false, 0L)) != INTERRUPTED))
            return nextState;
        throw new InterruptedException();
    }

    /**
     *
     */
    @ReservedStackAccess
    public long readLock() {
        // unconditionally optimistically try non-overflow case once
        long s = U.getLongOpaque(this, STATE) & RSAFE, nextState;
        if (casState(s, nextState = s + RUNIT))
            return nextState;
        else
            return acquireRead(false, false, 0L);
    }

    /**
     *
     */
    public long tryReadLock() {
        return tryAcquireRead();
    }

    /**
     *
     */
    public long tryReadLock(long time, TimeUnit unit)
            throws InterruptedException {
        long nanos = unit.toNanos(time);
        if (!Thread.interrupted()) {
            long nextState;
            if (tail == head && (nextState = tryAcquireRead()) != 0L)
                return nextState;
            if (nanos <= 0L)
                return 0L;
            nextState = acquireRead(true, true, System.nanoTime() + nanos);
            if (nextState != INTERRUPTED)
                return nextState;
        }
        throw new InterruptedException();
    }

    /**
     *
     */
    public long readLockInterruptibly() throws InterruptedException {
        long nextState;
        if (!Thread.interrupted() &&
                ((nextState = tryAcquireRead()) != 0L ||
                        (nextState = acquireRead(true, false, 0L)) != INTERRUPTED))
            return nextState;
        throw new InterruptedException();
    }

    /**
     *
     */
    public long tryOptimisticRead() {
        long s;
        return (((s = state) & WBIT) == 0L) ? (s & SBITS) : 0L;
    }

    /**
     *
     */
    public boolean validate(long stamp) {
        U.loadFence();
        return (stamp & SBITS) == (state & SBITS);
    }

    /**
     *
     */
    @ReservedStackAccess
    public void unlockWrite(long stamp) {
        if (state != stamp || (stamp & WBIT) == 0L)
            throw new IllegalMonitorStateException();
        releaseWrite(stamp);
    }

    /**
     *
     */
    @ReservedStackAccess
    public void unlockRead(long stamp) {
        long s, m;
        if ((stamp & RBITS) != 0L) {
            while (((s = state) & SBITS) == (stamp & SBITS) &&
                    ((m = s & RBITS) != 0L)) {
                if (m < RFULL) {
                    if (casState(s, s - RUNIT)) {
                        if (m == RUNIT)
                            signalNext(head);
                        return;
                    }
                } else if (tryDecReaderOverflow(s) != 0L)
                    return;
            }
        }
        throw new IllegalMonitorStateException();
    }

    /**
     *
     */
    public void unlock(long stamp) {
        if ((stamp & WBIT) != 0L)
            unlockWrite(stamp);
        else
            unlockRead(stamp);
    }

    /**
     *
     */
    public long tryConvertToWriteLock(long stamp) {
        long a = stamp & ABITS, m, s, nextState;
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((m = s & ABITS) == 0L) {
                if (a != 0L)
                    break;
                if (casState(s, nextState = s | WBIT)) {
                    U.storeStoreFence();
                    return nextState;
                }
            } else if (m == WBIT) {
                if (a != m)
                    break;
                return stamp;
            } else if (m == RUNIT && a != 0L) {
                if (casState(s, nextState = s - RUNIT + WBIT))
                    return nextState;
            } else
                break;
        }
        return 0L;
    }

    /**
     *
     */
    public long tryConvertToReadLock(long stamp) {
        long a, s, nextState;
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((a = stamp & ABITS) >= WBIT) {
                if (s != stamp) // write stamp
                    break;
                nextState = state = unlockWriteState(s) + RUNIT;
                signalNext(head);
                return nextState;
            } else if (a == 0L) { // optimistic read stamp
                if ((s & ABITS) < RFULL) {
                    if (casState(s, nextState = s + RUNIT))
                        return nextState;
                } else if ((nextState = tryIncReaderOverflow(s)) != 0L)
                    return nextState;
            } else { // already a read stamp
                if ((s & ABITS) == 0L)
                    break;
                return stamp;
            }
        }
        return 0L;
    }

    /**
     *
     */
    public long tryConvertToOptimisticRead(long stamp) {
        long a, m, s, nextState;
        U.loadFence();
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((a = stamp & ABITS) >= WBIT) {
                if (s != stamp)   // write stamp
                    break;
                return releaseWrite(s);
            } else if (a == 0L) { // already an optimistic read stamp
                return stamp;
            } else if ((m = s & ABITS) == 0L) { // invalid read stamp
                break;
            } else if (m < RFULL) {
                if (casState(s, nextState = s - RUNIT)) {
                    if (m == RUNIT)
                        signalNext(head);
                    return nextState & SBITS;
                }
            } else if ((nextState = tryDecReaderOverflow(s)) != 0L)
                return nextState & SBITS;
        }
        return 0L;
    }

    /**
     *
     */
    @ReservedStackAccess
    public boolean tryUnlockWrite() {
        long s;
        if (((s = state) & WBIT) != 0L) {
            releaseWrite(s);
            return true;
        }
        return false;
    }

    /**
     *
     */
    @ReservedStackAccess
    public boolean tryUnlockRead() {
        long s, m;
        while ((m = (s = state) & ABITS) != 0L && m < WBIT) {
            if (m < RFULL) {
                if (casState(s, s - RUNIT)) {
                    if (m == RUNIT)
                        signalNext(head);
                    return true;
                }
            } else if (tryDecReaderOverflow(s) != 0L)
                return true;
        }
        return false;
    }

    // status monitoring methods

    /**
     *
     */
    private int getReadLockCount(long s) {
        long readers;
        if ((readers = s & RBITS) >= RFULL)
            readers = RFULL + readerOverflow;
        return (int) readers;
    }

    /**
     *
     */
    public boolean isWriteLocked() {
        return (state & WBIT) != 0L;
    }

    /**
     *
     */
    public boolean isReadLocked() {
        return (state & RBITS) != 0L;
    }

    /**
     *
     */
    public static boolean isWriteLockStamp(long stamp) {
        return (stamp & ABITS) == WBIT;
    }

    /**
     *
     */
    public static boolean isReadLockStamp(long stamp) {
        return (stamp & RBITS) != 0L;
    }

    /**
     *
     */
    public static boolean isLockStamp(long stamp) {
        return (stamp & ABITS) != 0L;
    }

    /**
     *
     */
    public static boolean isOptimisticReadStamp(long stamp) {
        return (stamp & ABITS) == 0L && stamp != 0L;
    }

    /**
     *
     */
    public int getReadLockCount() {
        return getReadLockCount(state);
    }

    /**
     *
     */
    public String toString() {
        long s = state;
        return super.toString() +
                ((s & ABITS) == 0L ? "[Unlocked]" :
                        (s & WBIT) != 0L ? "[Write-locked]" :
                                "[Read-locks:" + getReadLockCount(s) + "]");
    }


    /**
     *
     */
    public Lock asReadLock() {
        ReadLockView v;
        if ((v = readLockView) != null) return v;
        return readLockView = new ReadLockView();
    }

    /**
     *
     */
    public Lock asWriteLock() {
        WriteLockView v;
        if ((v = writeLockView) != null) return v;
        return writeLockView = new WriteLockView();
    }

    /**
     *
     */
    public ReadWriteLock asReadWriteLock() {
        ReadWriteLockView v;
        if ((v = readWriteLockView) != null) return v;
        return readWriteLockView = new ReadWriteLockView();
    }


    final class ReadLockView implements Lock {
        public void lock() {
            readLock();
        }

        public void lockInterruptibly() throws InterruptedException {
            readLockInterruptibly();
        }

        public boolean tryLock() {
            return tryReadLock() != 0L;
        }

        public boolean tryLock(long time, TimeUnit unit)
                throws InterruptedException {
            return tryReadLock(time, unit) != 0L;
        }

        public void unlock() {
            unstampedUnlockRead();
        }

        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    final class WriteLockView implements Lock {
        public void lock() {
            writeLock();
        }

        public void lockInterruptibly() throws InterruptedException {
            writeLockInterruptibly();
        }

        public boolean tryLock() {
            return tryWriteLock() != 0L;
        }

        public boolean tryLock(long time, TimeUnit unit)
                throws InterruptedException {
            return tryWriteLock(time, unit) != 0L;
        }

        public void unlock() {
            unstampedUnlockWrite();
        }

        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    final class ReadWriteLockView implements ReadWriteLock {
        public Lock readLock() {
            return asReadLock();
        }

        public Lock writeLock() {
            return asWriteLock();
        }
    }

    // Unlock methods without stamp argument checks for view classes.
    // Needed because view-class lock methods throw away stamps.

    final void unstampedUnlockWrite() {
        long s;
        if (((s = state) & WBIT) == 0L)
            throw new IllegalMonitorStateException();
        releaseWrite(s);
    }

    final void unstampedUnlockRead() {
        long s, m;
        while ((m = (s = state) & RBITS) > 0L) {
            if (m < RFULL) {
                if (casState(s, s - RUNIT)) {
                    if (m == RUNIT)
                        signalNext(head);
                    return;
                }
            } else if (tryDecReaderOverflow(s) != 0L)
                return;
        }
        throw new IllegalMonitorStateException();
    }

    private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        state = ORIGIN; // reset to unlocked state
    }


    /**
     *
     */
    private long tryIncReaderOverflow(long s) {
        // assert (s & ABITS) >= RFULL;
        if ((s & ABITS) != RFULL)
            Thread.onSpinWait();
        else if (casState(s, s | RBITS)) {
            ++readerOverflow;
            return state = s;
        }
        return 0L;
    }

    /**
     *
     */
    private long tryDecReaderOverflow(long s) {
        // assert (s & ABITS) >= RFULL;
        if ((s & ABITS) != RFULL)
            Thread.onSpinWait();
        else if (casState(s, s | RBITS)) {
            int r;
            long nextState;
            if ((r = readerOverflow) > 0) {
                readerOverflow = r - 1;
                nextState = s;
            } else
                nextState = s - RUNIT;
            return state = nextState;
        }
        return 0L;
    }


    /**
     *
     */
    static final void signalNext(Node h) {
        Node s;
        if (h != null && (s = h.next) != null && s.status > 0) {
            s.getAndUnsetStatus(WAITING);
            LockSupport.unpark(s.waiter);
        }
    }

    /**
     *
     */
    private static void signalCowaiters(ReaderNode node) {
        if (node != null) {
            for (ReaderNode c; (c = node.cowaiters) != null; ) {
                if (node.casCowaiters(c, c.cowaiters))
                    LockSupport.unpark(c.waiter);
            }
        }
    }

    // queue link methods
    private boolean casTail(Node c, Node v) {
        return U.compareAndSetReference(this, TAIL, c, v);
    }

    /**
     *
     */
    private void tryInitializeHead() {
        Node h = new WriterNode();
        if (U.compareAndSetReference(this, HEAD, null, h))
            tail = h;
    }

    /**
     *
     */
    private long acquireWrite(boolean interruptible, boolean timed, long time) {
        byte spins = 0, postSpins = 0;   // retries upon unpark of first thread
        boolean interrupted = false, first = false;
        WriterNode node = null;
        Node pred = null;
        for (long s, nextState; ; ) {
            if (!first && (pred = (node == null) ? null : node.prev) != null &&
                    !(first = (head == pred))) {
                if (pred.status < 0) {
                    cleanQueue();           // predecessor cancelled
                    continue;
                } else if (pred.prev == null) {
                    Thread.onSpinWait();    // ensure serialization
                    continue;
                }
            }
            if ((first || pred == null) && ((s = state) & ABITS) == 0L &&
                    casState(s, nextState = s | WBIT)) {
                U.storeStoreFence();
                if (first) {
                    node.prev = null;
                    head = node;
                    pred.next = null;
                    node.waiter = null;
                    if (interrupted)
                        Thread.currentThread().interrupt();
                }
                return nextState;
            } else if (node == null) {          // retry before enqueuing
                node = new WriterNode();
            } else if (pred == null) {          // try to enqueue
                Node t = tail;
                node.setPrevRelaxed(t);
                if (t == null)
                    tryInitializeHead();
                else if (!casTail(t, node))
                    node.setPrevRelaxed(null);  // back out
                else
                    t.next = node;
            } else if (first && spins != 0) {   // reduce unfairness
                --spins;
                Thread.onSpinWait();
            } else if (node.status == 0) {      // enable signal
                if (node.waiter == null)
                    node.waiter = Thread.currentThread();
                node.status = WAITING;
            } else {
                long nanos;
                spins = postSpins = (byte) ((postSpins << 1) | 1);
                if (!timed)
                    LockSupport.park(this);
                else if ((nanos = time - System.nanoTime()) > 0L)
                    LockSupport.parkNanos(this, nanos);
                else
                    break;
                node.clearStatus();
                if ((interrupted |= Thread.interrupted()) && interruptible)
                    break;
            }
        }
        return cancelAcquire(node, interrupted);
    }

    /**
     *
     */
    private long acquireRead(boolean interruptible, boolean timed, long time) {
        boolean interrupted = false;
        ReaderNode node = null;
        for (; ; ) {
            ReaderNode leader;
            long nextState;
            Node tailPred = null, t = tail;
            if ((t == null || (tailPred = t.prev) == null) &&
                    (nextState = tryAcquireRead()) != 0L) // try now if empty
                return nextState;
            else if (t == null)
                tryInitializeHead();
            else if (tailPred == null || !(t instanceof ReaderNode)) {
                if (node == null)
                    node = new ReaderNode();
                if (tail == t) {
                    node.setPrevRelaxed(t);
                    if (casTail(t, node)) {
                        t.next = node;
                        break; // node is leader; wait in loop below
                    }
                    node.setPrevRelaxed(null);
                }
            } else if ((leader = (ReaderNode) t) == tail) { // try to cowait
                for (boolean attached = false; ; ) {
                    if (leader.status < 0 || leader.prev == null)
                        break;
                    else if (node == null)
                        node = new ReaderNode();
                    else if (node.waiter == null)
                        node.waiter = Thread.currentThread();
                    else if (!attached) {
                        ReaderNode c = leader.cowaiters;
                        node.setCowaitersRelaxed(c);
                        attached = leader.casCowaiters(c, node);
                        if (!attached)
                            node.setCowaitersRelaxed(null);
                    } else {
                        long nanos = 0L;
                        if (!timed)
                            LockSupport.park(this);
                        else if ((nanos = time - System.nanoTime()) > 0L)
                            LockSupport.parkNanos(this, nanos);
                        interrupted |= Thread.interrupted();
                        if ((interrupted && interruptible) ||
                                (timed && nanos <= 0L))
                            return cancelCowaiter(node, leader, interrupted);
                    }
                }
                if (node != null)
                    node.waiter = null;
                long ns = tryAcquireRead();
                signalCowaiters(leader);
                if (interrupted)
                    Thread.currentThread().interrupt();
                if (ns != 0L)
                    return ns;
                else
                    node = null; // restart if stale, missed, or leader cancelled
            }
        }

        // node is leader of a cowait group; almost same as acquireWrite
        byte spins = 0, postSpins = 0;   // retries upon unpark of first thread
        boolean first = false;
        Node pred = null;
        for (long nextState; ; ) {
            if (!first && (pred = node.prev) != null &&
                    !(first = (head == pred))) {
                if (pred.status < 0) {
                    cleanQueue();           // predecessor cancelled
                    continue;
                } else if (pred.prev == null) {
                    Thread.onSpinWait();    // ensure serialization
                    continue;
                }
            }
            if ((first || pred == null) &&
                    (nextState = tryAcquireRead()) != 0L) {
                if (first) {
                    node.prev = null;
                    head = node;
                    pred.next = null;
                    node.waiter = null;
                }
                signalCowaiters(node);
                if (interrupted)
                    Thread.currentThread().interrupt();
                return nextState;
            } else if (first && spins != 0) {
                --spins;
                Thread.onSpinWait();
            } else if (node.status == 0) {
                if (node.waiter == null)
                    node.waiter = Thread.currentThread();
                node.status = WAITING;
            } else {
                long nanos;
                spins = postSpins = (byte) ((postSpins << 1) | 1);
                if (!timed)
                    LockSupport.park(this);
                else if ((nanos = time - System.nanoTime()) > 0L)
                    LockSupport.parkNanos(this, nanos);
                else
                    break;
                node.clearStatus();
                if ((interrupted |= Thread.interrupted()) && interruptible)
                    break;
            }
        }
        return cancelAcquire(node, interrupted);
    }


    /**
     *
     */
    private void cleanQueue() {
        for (; ; ) {                               // restart point
            for (Node q = tail, s = null, p, n; ; ) { // (p, q, s) triples
                if (q == null || (p = q.prev) == null)
                    return;                      // end of list
                if (s == null ? tail != q : (s.prev != q || s.status < 0))
                    break;                       // inconsistent
                if (q.status < 0) {              // cancelled
                    if ((s == null ? casTail(q, p) : s.casPrev(q, p)) &&
                            q.prev == p) {
                        p.casNext(q, s);         // OK if fails
                        if (p.prev == null)
                            signalNext(p);
                    }
                    break;
                }
                if ((n = p.next) != q) {         // help finish
                    if (n != null && q.prev == p && q.status >= 0) {
                        p.casNext(n, q);
                        if (p.prev == null)
                            signalNext(p);
                    }
                    break;
                }
                s = q;
                q = q.prev;
            }
        }
    }

    /**
     *
     */
    private void unlinkCowaiter(ReaderNode node, ReaderNode leader) {
        if (leader != null) {
            while (leader.prev != null && leader.status >= 0) {
                for (ReaderNode p = leader, q; ; p = q) {
                    if ((q = p.cowaiters) == null)
                        return;
                    if (q == node) {
                        p.casCowaiters(q, q.cowaiters);
                        break;  // recheck even if succeeded
                    }
                }
            }
        }
    }

    /**
     *
     */
    private long cancelAcquire(Node node, boolean interrupted) {
        if (node != null) {
            node.waiter = null;
            node.status = CANCELLED;
            cleanQueue();
            if (node instanceof ReaderNode)
                signalCowaiters((ReaderNode) node);
        }
        return (interrupted || Thread.interrupted()) ? INTERRUPTED : 0L;
    }

    /**
     *
     */
    private long cancelCowaiter(ReaderNode node, ReaderNode leader,
                                boolean interrupted) {
        if (node != null) {
            node.waiter = null;
            node.status = CANCELLED;
            unlinkCowaiter(node, leader);
        }
        return (interrupted || Thread.interrupted()) ? INTERRUPTED : 0L;
    }


}
