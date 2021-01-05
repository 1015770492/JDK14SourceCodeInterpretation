package java.util.concurrent.locks;
/**
 * jdk14 完结
 */

import jdk.internal.misc.Unsafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public abstract class AbstractQueuedSynchronizer extends AbstractOwnableSynchronizer implements java.io.Serializable {

    private static final long serialVersionUID = 7373984972572414691L; // 序列化版本号
    static final int WAITING = 1;          // 常量1，表示线程等待
    static final int CANCELLED = 0x80000000; // 常量负数，表示取消等待
    static final int COND = 2;          // 常量2
    private volatile int state;              // 等待线程的个数
    private transient volatile Node head;    // AQS队列的头
    private transient volatile Node tail;    // AQS队列的尾
    private static final Unsafe U = Unsafe.getUnsafe(); //用的都是最底层的操作，里面有很多关于cas操作的native方法
    private static final long HEAD = U.objectFieldOffset(AbstractQueuedSynchronizer.class, "head");     // 使用cas对内存操作head的偏移量
    private static final long TAIL = U.objectFieldOffset(AbstractQueuedSynchronizer.class, "tail");     // 使用cas对内存操作tail的偏移量
    private static final long STATE = U.objectFieldOffset(AbstractQueuedSynchronizer.class, "state");   // 使用cas对内存操作state的偏移量

    static {
        Class<?> ensureLoaded = LockSupport.class;  // LockSupport实例，确保LockSupport类被初始化
    }

    /**
     * 构造方法，初始state=0
     */
    protected AbstractQueuedSynchronizer() {
    }


    /**
     * state的get、set方法
     */
    protected final int getState() {
        return state;
    }

    protected final void setState(int newState) {
        state = newState;
    }

    // CAS操作对state设置新值，(如果state的值为expect则将state更新为update)
    protected final boolean compareAndSetState(int expect, int update) {
        return U.compareAndSetInt(this, STATE, expect, update);
    }

    // c是预期值，v是待更新后的值，如果tail的值是c则更新tail=v
    private boolean casTail(Node c, Node v) {
        return U.compareAndSetReference(this, TAIL, c, v);//cas操作
    }


    // 初始化一个队列头
    private void tryInitializeHead() {
        Node h = new ExclusiveNode(); // 创建一个ExclusiveNode节点，将它做为头
        if (U.compareAndSetReference(this, HEAD, null, h)) // 更新HEAD=h
            tail = h;// 因为当前没有真正带有线程的节点。所以HEAD=h,TAIL
    }

    /**
     * 入队一个节点
     * 队列是一个双向的链表，有prev和next
     * <p>
     * 形成ExclusiveNode或SharedNode的双向链表
     */
    final void enqueue(Node node) {
        if (node != null) { // 节点不为空
            for (; ; ) {  // 自旋
                Node t = tail;      // 尾节点t
                node.setPrevRelaxed(t);         // 将node节点的prev指向t（node会被更新尾队列末尾，目的就是将node加入队列形成双向链表）
                if (t == null)                  // 如果aqs队列为空需要创建一个头（这个头只是为了方便后面使用做的一个虚头）
                    tryInitializeHead();        // 尝试初始一个AQS头
                else if (casTail(t, node)) {    // 将node入队到末尾，更新TAIL为node(注意不是小写的tail)
                    t.next = node;              // 因为t已经不是尾节点了，所以有next=null变成了next=node（此时相当于尾节点或者说是下一个节点）
                    if (t.status < 0)           // 判断入队个数是否超过了int所能表示的最大值。
                        LockSupport.unpark(node.waiter); // 唤醒node线程
                    break; // 跳出循环
                }
            }
        }
    }

    /**
     * （从队列末尾向前找） 判断节点node是否已经入队
     */
    final boolean isEnqueued(Node node) {
        for (Node t = tail; t != null; t = t.prev) //（从队列末尾向前找）
            if (t == node)
                return true;
        return false;
    }


    /**
     * 如果是SharedNode节点则唤醒
     */
    private static void signalNextIfShared(Node h) {
        Node s;
        if (h != null && (s = h.next) != null && (s instanceof SharedNode) && s.status != 0) {
            s.getAndUnsetStatus(WAITING);
            LockSupport.unpark(s.waiter);
        }
    }


    /**
     * 尝试获得到锁，实际调用下面多个参数的acquire()方法
     */
    public final void acquire(int arg) {
        /**
         * tryAcquire(arg)调用的是子类实现的tryAcquire(int),本身在AQS则是一个抽象方法
         * 例如：ReentrantLock中的内部类NonfairSync、FairSync中的tryAcquire(int)
         * 因此需要去子类查看这个方法的实现只有失败了才会进一步调用acquire(null, 1, false, false, false, 0L);
         */
        if (!tryAcquire(arg)) { // 尝试加锁，失败则需要进入队列
            // 注意参数值除了arg的值是变量，其它都是0会false或null，一遍而言传入的是1，除非一次性加了多次锁
            acquire(null, arg, false, false, false, 0L);
        }
    }

    /**
     * 抢占锁的方法，加锁的时候除了arg=1其它都是null或false
     *
     * @param node
     * @param arg           加锁次数
     * @param shared        控制是否时共享线程队列也就是SharedNode的布尔值
     * @param interruptible 是否时可中断线程
     * @param timed         是否由最长等待时间
     * @param time          中断超时时间
     */
    final int acquire(Node node, int arg, boolean shared, boolean interruptible, boolean timed, long time) {
        Thread current = Thread.currentThread();    // 获取当前线程
        byte spins = 0, postSpins = 0;              // 自旋变量和之前的自旋变量postSpins
        boolean interrupted = false, first = false; // 中断变量值interrupted，first表示第一次进入方法
        Node pred = null;                           // 存储前置节点
        // 自旋获取锁
        for (; ; ) {
            // 循环的第一次判断为 true && false (node为null pred=null,null!=null。返回false就不执行后面的赋值和判断)
            // 自旋后的第二次由于是刚创建的则prev为null因此还是false，pred=null，还是false不执行
            // 当node!=null且node.prev!=null说明节点已经入队了，因此第二个判断返回true需要判断!(first = (head == pred))返回的是false
            if (!first && (pred = (node == null) ? null : node.prev) != null && !(first = (head == pred))) {
                // 进入这个代码块说明队列不为空且不止一个线程在等待
                if (pred.status < 0) {  // 实际就是传入的node的前一个节点的status是否<0
                    cleanQueue();           // 清空队列
                    continue;
                } else if (pred.prev == null) {
                    Thread.onSpinWait();    // 自旋等待确保序列化
                    continue;
                }
            }
            //循环第一次 false || true，需要进入代码块。pred在第一次判断就被赋值为null，循环进入前也是null
            if (first || pred == null) {
                boolean acquired;
                try {
                    if (shared) {
                        // 循环第一次false进入else分支
                        acquired = (tryAcquireShared(arg) >= 0);// 这个方法由Semaphore调用，意思判断剩余信号量是否>=0
                    } else {
                        /**
                         * 尝试抢占锁，如果没有抢到则会自旋，tryAcquire(arg);会一直重复调用，直到抢占成功
                         * 子类实现的方法。例如非公平锁的tryAcquired(1);
                         * 如果state为0，则acquired则会变成true。将当前线程设置为独占并更新state为arg
                         * 如果state不为0，则acquired=false，说明被其它线程上锁了
                         *
                         * 自旋的起点是后面
                         * if (node == null) {
                         *     if (shared)// 如果是共享队列节点则创建SharedNode，然后由于后面没有代码则会自旋for循环重新执行一遍只是这个时候node不为null
                         *        node = new SharedNode(); //这个是线程队列的头节点，用来标识这个队列是一个共享锁线程等待队列
                         *     else       // 如果是一个排他锁创建一个排他节点
                         *        node = new ExclusiveNode();// 线程队列的头节点，标识是一个排他锁线程队列
                         * }
                         * 然后到这里的tryAcquire(arg);一直原地踏步，直到抢占到锁
                         * acquired更新为true
                         * 进入catch后面的if
                         *
                         */
                        acquired = tryAcquire(arg); // 会回到子类（NonfairSync、FairSync等）的实现的方法尝试获得锁，如果失败则还是false，直到成功true
                    }
                } catch (Throwable ex) {
                    cancelAcquire(node, interrupted, false);
                    throw ex;
                }
                // true说明当前线程抢占到锁了
                if (acquired) { //信号量的使用一个信号量，如果成功也会进行判断，使用信号量成功则进入,没有成功说明信号量使用完了
                    if (first) {
                        node.prev = null;
                        head = node;
                        pred.next = null;
                        node.waiter = null;
                        if (shared)
                            signalNextIfShared(node);
                        if (interrupted)
                            current.interrupt();
                    }
                    return 1; // 返回1标签抢占到锁了这是这个方法的唯一结束点，其它分支始终都会死循环
                }
            }
            /**
             *
             * AQS队列的形成起点，头节点就是下面的SharedNode或ExclusiveNode，然后自旋
             *
             */
            // 第一次进入node传入的是null因此进入
            // 第二次由于第一次进入后node=new SharedNode();或node = new ExclusiveNode();则这个if就不会在进入，会进入第二个if因为pred=null
            if (node == null) { // 形成头节点
                if (shared)// 如果是共享队列节点则创建SharedNode，然后由于后面没有代码则会自旋for循环重新执行一遍只是这个时候node不为null
                    node = new SharedNode(); //这个是线程队列的头节点，用来标识这个队列是一个共享锁线程等待队列
                else       // 如果是一个排他锁创建一个排他节点
                    node = new ExclusiveNode(); // 线程队列的头节点，标识是一个排他锁线程队列
            } else if (pred == null) {          // 尝试将当前线程入队
                node.waiter = current;          // 将当前线程存入ExclusiveNode节点的waiter成员变量
                Node t = tail;                  // 一开始tail=null
                node.setPrevRelaxed(t);         // 将node的prev指向tail，而此时node.next还是null,相当于加入到了队列的末尾，后面需要将队列末尾指向node形成双向的队列
                if (t == null)                  // 第一次队列还没有形成因此t是null需要将头节点初始化
                    tryInitializeHead();        // 初始化头节点，并且在内部将tail也执行了这个初始的头节点
                else if (!casTail(t, node))     // casTail(t, node)会将tail变成node
                    node.setPrevRelaxed(null);  // 如果tail更新失败。则node.prev=null则又会重新进入if进行更新，直到更新成功。
                else
                    t.next = node;              // 将队列的末尾指向node形成双向队列（node.next则是null，也就是说aqs队列的末尾元素的next始终为null，prev会指向前一个节点）
            } else if (first && spins != 0) {   //
                --spins;                        // 让出cpu使用权，减少线程调度的不公平性
                Thread.onSpinWait();            // 让出cpu使用全和Thread.sleep(0)差不多的作用，但是Thread.onSpinWait();更高效，使用的是cpu指令
            } else if (node.status == 0) {      // 0是初始化赋得值，这里aqs队列得节点自然是要要让线程等待，因此更新status值为1（WAITING得值就是常量1）
                node.status = WAITING;          // 如果status为0更新status值为常量WAITING=1,1表示等待
            } else {
                long nanos;
                spins = postSpins = (byte) ((postSpins << 1) | 1);// spins!=0则会调用Thread.onSpinWait();，让当前线程让出cpu的使用权
                if (!timed)//如果没有设置阻塞时间
                    LockSupport.park(this);// 阻塞当前线程
                else if ((nanos = time - System.nanoTime()) > 0L)//如果设置了阻塞时长且时间nanos > 0
                    LockSupport.parkNanos(this, nanos);  //阻塞nanos纳秒
                else // 如果时间不合法 则break
                    break;
                node.clearStatus();// 将status重新更新为0
                if ((interrupted |= Thread.interrupted()) && interruptible)
                    break;
            }
        }
        return cancelAcquire(node, interrupted, interruptible);//返回0 或者 返回CANCELLED常量负数
    }

    /**
     * 释放锁并唤醒aqs的头节点
     */
    public final boolean release(int arg) {
        if (tryRelease(arg)) { // 释放了arg个重入锁,如果释放完了就相当于释放了所有锁
            signalNext(head);//唤醒head.next所指向的节点线程
            return true;
        }
        return false;
    }

    /**
     * 唤醒线程队列节点中的线程
     * 传入的是head，通过head.next得到等待线程的第一个节点将其唤醒
     */
    private static void signalNext(Node h) {
        Node s;
        if (h != null && (s = h.next) != null && s.status != 0) {
            s.getAndUnsetStatus(WAITING); // 更新线程状态值为常量1
            LockSupport.unpark(s.waiter); // 给当前节点的线程发放一个许可，唤醒该线程
        }
    }

    /**
     * 清空队列
     */
    private void cleanQueue() {
        for (; ; ) {                               // restart point
            for (Node q = tail, s = null, p, n; ; ) { // (p, q, s) triples

                if (q == null || (p = q.prev) == null)
                    return;  // 说明队列已经为null了，直接返回退出自旋
                /*第一次false*/
                if (s == null ? tail != q : (s.prev != q || s.status < 0))
                    break;
                /*判断*/
                if (q.status < 0) {
                    if ((s == null ? casTail(q, p) : s.casPrev(q, p)) && q.prev == p) {
                        p.casNext(q, s);
                        if (p.prev == null)
                            signalNext(p);
                    }
                    break;
                }

                if ((n = p.next) != q) {
                    if (n != null && q.prev == p) {
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
     * 取消抢占锁
     */
    private int cancelAcquire(Node node, boolean interrupted, boolean interruptible) {
        if (node != null) {
            node.waiter = null;
            node.status = CANCELLED;//
            if (node.prev != null)//如果传入的node是new SharedNode()则不会进入if内进行情况队列
                cleanQueue();
        }
        if (interrupted) {//如果线程被中断了
            if (interruptible)//取消中断
                return CANCELLED;//常量负数
            else
                Thread.currentThread().interrupt();// 将线程中断
        }
        return 0;//返回0
    }


    /**
     * 抢占锁，可以被中断的方式抢占
     */
    public final void acquireInterruptibly(int arg) throws InterruptedException {
        if (Thread.interrupted() || (!tryAcquire(arg) && acquire(null, arg, false, true, false, 0L) < 0))
            throw new InterruptedException();
    }

    /**
     * 在时间nanosTimeout内抢占锁，如果没有在规定时间内抢占到则中断线程
     */
    public final boolean tryAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (!Thread.interrupted()) {
            if (tryAcquire(arg))//尝试获得锁，true则返回true
                return true;
            if (nanosTimeout <= 0L)// 校验时间是否不合法
                return false;
            // 让线程自旋得方式抢占锁
            int stat = acquire(null, arg, false, true, true, System.nanoTime() + nanosTimeout);
            if (stat > 0)
                return true; // 线程从自旋中出来执行到了这里说明当前线程已经获得到了锁
            if (stat == 0)
                return false;// 说明自旋方式去获得锁失败
        }
        throw new InterruptedException();
    }


    /**
     * 获取共享锁（读锁的lock方法会调用这个方法）
     */
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0) // 尝试获取读锁，失败则需要将节点加入aqs队列
            acquire(null, arg, true, false, false, 0L);
    }

    /**
     * 带中断的方式获得锁
     */
    public final void acquireSharedInterruptibly(int arg) throws InterruptedException {
        if (Thread.interrupted() || (tryAcquireShared(arg) < 0 && acquire(null, arg, true, true, false, 0L) < 0))
            throw new InterruptedException();
    }

    /**
     * 在时间nanosTimeout内尝试获得共享锁
     */
    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (!Thread.interrupted()) {
            if (tryAcquireShared(arg) >= 0)
                return true;
            if (nanosTimeout <= 0L)
                return false;
            int stat = acquire(null, arg, true, true, true, System.nanoTime() + nanosTimeout);
            if (stat > 0)
                return true;
            if (stat == 0)
                return false;
        }
        throw new InterruptedException();
    }

    /**
     * 释放共享锁
     */
    public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) {
            signalNext(head);
            return true;
        }
        return false;
    }

    /**
     * 是否有等待的线程
     */
    public final boolean hasQueuedThreads() {
        // 这里的循环之所以这样做是因为head并不包含等待线程只是为了方便找到队列得头节点设置得一个节点
        for (Node p = tail, h = head; p != h && p != null; p = p.prev)
            if (p.status >= 0)
                return true;
        return false;
    }

    /**
     * 是否由aqs队列
     */
    public final boolean hasContended() {
        return head != null;
    }

    /**
     * 从末尾向前找 获得aqs队列的头节点的线程
     */
    public final Thread getFirstQueuedThread() {
        Thread first = null, w;
        Node h, s;
        if ((h = head) != null && ((s = h.next) == null || (first = s.waiter) == null || s.prev == null)) {
            // 从队列末尾向前找第一个元素，之所以这样设计的原因应该是第一个线程可能被唤醒了，这样在aqs队列中可能不存在了该节点
            // 假设直接从head那么可能head刚好被唤醒，刚好要从队列中剔除。那么节点不存在了
            for (Node p = tail, q; p != null && (q = p.prev) != null; p = q)
                if ((w = p.waiter) != null)
                    first = w; // 线程不为null得到节点的线程
        }
        return first;
    }

    /**
     * 判断线程是否在aqs队列中
     */
    public final boolean isQueued(Thread thread) {
        if (thread == null) throw new NullPointerException();
        for (Node p = tail; p != null; p = p.prev) // 从尾向前找
            if (p.waiter == thread)
                return true;
        return false;
    }

    /**
     * 判断 aqs队列的第一个线程节点是否是ExclusiveNode并且线程节点存的线程不为null
     * 读锁中用到了
     */
    final boolean apparentlyFirstQueuedIsExclusive() {
        Node h, s;
        // 头节点是排他节点并且线程不为空
        return (h = head) != null && (s = h.next) != null && !(s instanceof SharedNode) && s.waiter != null;
    }

    /**
     * 是否有阻塞线程
     */
    public final boolean hasQueuedPredecessors() {
        Thread first = null;
        Node h, s;
        if ((h = head) != null && ((s = h.next) == null || (first = s.waiter) == null || s.prev == null))
            first = getFirstQueuedThread(); // 获得队列第一个节点中的线程
        return first != null && first != Thread.currentThread(); // 第一个线程不等于当前线程则返回true
    }


    /**
     * 获得队列长度
     */
    public final int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null; p = p.prev) {
            if (p.waiter != null)
                ++n;
        }
        return n;
    }

    /**
     * 将队列中的线程封装成ArrayList集合类
     */
    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.waiter;
            if (t != null)
                list.add(t);
        }
        return list;
    }

    /**
     * 将排他锁线程队列中的线程，封装成ArrayList集合
     */
    public final Collection<Thread> getExclusiveQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<>();
        for (Node p = tail; p != null; p = p.prev) {
            if (!(p instanceof SharedNode)) {
                Thread t = p.waiter;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * 将共享锁队列中的线程，封装成ArrayList集合
     */
    public final Collection<Thread> getSharedQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p instanceof SharedNode) {
                Thread t = p.waiter;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * 得到队列加锁次数和是否是空队列的字符串
     */
    public String toString() {
        return super.toString()
                + "[State = " + getState() + ", "
                + (hasQueuedThreads() ? "non" : "") + "empty queue]";
    }


    /**
     * 通过Condition接口
     */
    public final boolean owns(ConditionObject condition) {
        return condition.isOwnedBy(this);
    }

    /**
     *
     */
    public final boolean hasWaiters(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.hasWaiters();
    }

    /**
     *
     */
    public final int getWaitQueueLength(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitQueueLength();
    }

    /**
     *
     */
    public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitingThreads();
    }

    /**
     * 尝试加锁，有子类重写
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试释放锁
     */
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试加共享锁
     */
    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试释放共享锁
     */
    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 是否是排他锁
     */
    protected boolean isHeldExclusively() {
        /**
         * 调用ReentrantLock.isHeldExclusively()
         * 底层就是 return getExclusiveOwnerThread() == Thread.currentThread();
         *
         * 在子类实现,如果是ReentrantLock则底层是判断 当前线程对象==持有锁线程对象
         */
        throw new UnsupportedOperationException();
    }

    /**
     * CLH Nodes
     */
    abstract static class Node {
        volatile Node prev;       // 指向上一个Node节点
        volatile Node next;       // 指向下一个Node节点
        Thread waiter;            // 等待线程
        volatile int status;      // 线程状态
        private static final long STATUS = U.objectFieldOffset(Node.class, "status");
        private static final long NEXT = U.objectFieldOffset(Node.class, "next");
        private static final long PREV = U.objectFieldOffset(Node.class, "prev");

        /**
         * 封装的一些cas原子操作，本质都是调用Unsafe类的api
         */
        final boolean casPrev(Node c, Node v) {  // for cleanQueue
            return U.weakCompareAndSetReference(this, PREV, c, v);
        }

        final boolean casNext(Node c, Node v) {  // for cleanQueue
            return U.weakCompareAndSetReference(this, NEXT, c, v);
        }

        /**
         * 为了Condition的signal方法使用
         */
        final int getAndUnsetStatus(int v) {
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

    }

    // 排他锁线程队列
    static final class ExclusiveNode extends Node {
    }

    // 共享锁队列线程
    static final class SharedNode extends Node {
    }

    /**
     * Condition链表的底层数据结构
     */
    static final class ConditionNode extends Node implements ForkJoinPool.ManagedBlocker {
        ConditionNode nextWaiter;            // 链接到下一个ConditionNode

        /**
         * 是否可以中断
         */
        public final boolean isReleasable() {
            return status <= 1 || Thread.currentThread().isInterrupted();
        }

        // 阻塞线程，直到判断中返回true结束循环并最终返回true。如果是直接调用该方法那么线程会阻塞
        public final boolean block() {
            while (!isReleasable()) LockSupport.park();
            return true;
        }
    }

    /**
     * 暴露给外部
     */
    public class ConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;

        // 形成的是一个ConditionNode的单向链表,下面两个分别记录头和尾节点
        private transient ConditionNode firstWaiter;//Condition队列的第一个节点
        private transient ConditionNode lastWaiter; //Condition队列的最后一个节点

        /**
         * 构造方法
         */
        public ConditionObject() {
        }

        /**
         * 真正执行唤醒线程的方法
         * 如果传入的all=true则相当于遍历链表,将所有节点都唤醒
         */
        private void doSignal(ConditionNode first, boolean all) {
            while (first != null) { // 如果有队列头
                ConditionNode next = first.nextWaiter; // 找到下一个节点
                if ((firstWaiter = next) == null) // 如果下一个节点为空,说明到了链表的末尾节点
                    lastWaiter = null; //
                // 如果first移到到了最后一个节点,也就是上面逻辑first.nextWaiter=null的节点
                if ((first.getAndUnsetStatus(COND) & COND) != 0) {
                    enqueue(first);// 入队,将节点
                    if (!all) // 是否唤醒所有节点,如果传入的是true则继续遍历链表
                        break;
                }
                first = next; // 移动到下一个节点重复上面逻辑
            }
        }

        /**
         * 唤醒一个线程（头节点）
         */
        public final void signal() {
            ConditionNode first = firstWaiter;// 链表头
            if (!isHeldExclusively()) // 如果当前线程不是持有锁的对象,也就是!false会抛异常
                throw new IllegalMonitorStateException();// 抛非法监视器状态异常
            if (first != null)
                doSignal(first, false);// 调用上面的方法进行唤醒,传入false则只随机唤醒一个
        }

        /**
         * 唤醒所有被Condition阻塞的线程
         */
        public final void signalAll() {
            ConditionNode first = firstWaiter;// 链表头
            if (!isHeldExclusively())// 当前线程对象不是持有锁线程则配移除
                throw new IllegalMonitorStateException();// 抛异常
            if (first != null)
                doSignal(first, true);// 进行唤醒
        }

        /**
         * 被await方法调用,用于判断是否可以进行阻塞
         * node是否可以等待(阻塞),不可以则抛异常
         */
        private int enableWait(ConditionNode node) {
            if (isHeldExclusively()) {                  // 如果当前线程是持有锁的线程对象
                node.waiter = Thread.currentThread();   // 获取当前线程对象
                node.setStatusRelaxed(COND | WAITING);  // 得到的是3,设置状态值
                ConditionNode last = lastWaiter;        // 得到链表末尾节点
                if (last == null)                       // 如果链表末尾节点为null,说明是空链表
                    firstWaiter = node;                 // 直接将当前node插入链表的头形成新的头节点,同时也作为链表的末尾(即是头也是尾)
                else
                    last.nextWaiter = node;             // 如果不为null直接将node加入到末尾
                lastWaiter = node;                      // 记录末尾节点是当前node,相当于尾插法队列末尾
                int savedState = getState();// 获取state值
                if (release(savedState))    // 释放锁
                    return savedState;      // 返回释放锁后的state(相当于返回上锁次数)
            }
            node.status = CANCELLED; // 如果当前线程没有抢占到锁,更新节点的状态尾取消状态
            throw new IllegalMonitorStateException(); // 因为当前线程不是持锁线程抛出非法监视器异常
        }

        /**
         * 能否可以重新获取到锁
         */
        private boolean canReacquire(ConditionNode node) {
            return node != null && node.prev != null && isEnqueued(node); // 判断当前node是否已经入队
        }

        /**
         * 从等待队列中去除当前节点
         */
        private void unlinkCancelledWaiters(ConditionNode node) {
            if (node == null || node.nextWaiter != null || node == lastWaiter) {
                ConditionNode w = firstWaiter, trail = null;
                while (w != null) {
                    ConditionNode next = w.nextWaiter;// 得到下一个节点
                    if ((w.status & COND) == 0) {// 找到了这个
                        w.nextWaiter = null; // 置null
                        if (trail == null)
                            firstWaiter = next; // 重新保存队列头节点,因为异常的节点可能就是队列头节点,所以重新得到头节点
                        else
                            trail.nextWaiter = next;// 通过trail删除node节点
                        if (next == null)
                            lastWaiter = trail; // next为null说明上一个节点就是队列尾节点(也就是trail)
                    } else
                        trail = w;// 记录一些
                    w = next;// 移到到下一个节点
                }
            }
        }

        /**
         *
         */
        public final void awaitUninterruptibly() {
            ConditionNode node = new ConditionNode();// 创建一个节点
            int savedState = enableWait(node); // 释放锁,返回锁计数
            LockSupport.setCurrentBlocker(this); // 将当前对象设置尾阻塞资源
            boolean interrupted = false; // 不可以中断
            while (!canReacquire(node)) { // 如果不能被重新得到node则进行中断操作
                if (Thread.interrupted()) // 如果已经被中断了则更新interrupted = true;
                    interrupted = true;
                else if ((node.status & COND) != 0) {
                    try {
                        ForkJoinPool.managedBlock(node); // 阻塞当前资源
                    } catch (InterruptedException ie) {
                        interrupted = true; //
                    }
                } else
                    Thread.onSpinWait();    // 入队的时候就会被唤醒
            }
            LockSupport.setCurrentBlocker(null); // 清除阻塞资源
            node.clearStatus();                  // 清除状态值
            // 重新加锁
            acquire(node, savedState, false, false, false, 0L);
            if (interrupted)
                Thread.currentThread().interrupt(); // 中断当前线程
        }

        /**
         * 进行阻塞
         */
        public final void await() throws InterruptedException {
            // 如果线程被中断了抛中断异常
            if (Thread.interrupted()) throw new InterruptedException();

            ConditionNode node = new ConditionNode();   // 创建一个节点
            int savedState = enableWait(node);          // 如果当前线程是持有锁线程,释放所有锁,并且把锁计数返回
            LockSupport.setCurrentBlocker(this);        // 将当前对象作为阻塞资源
            boolean interrupted = false, cancelled = false; // 两个布尔变量分别表示中断和取消
            while (!canReacquire(node)) {               // 自选的方式进行中断当前线程
                if (interrupted |= Thread.interrupted()) { // 或运算,因为interrupted=false所以值取决于Thread.interrupted()
                    if (cancelled = (node.getAndUnsetStatus(COND) & COND) != 0)
                        break;              // 线程被中断了并且state值发送了变化就结束循环
                } else if ((node.status & COND) != 0) {
                    try {
                        ForkJoinPool.managedBlock(node);// 阻塞node资源
                    } catch (InterruptedException ie) {
                        interrupted = true; // 中断设置为true
                    }
                } else
                    Thread.onSpinWait();    // 进行自旋等待
            }
            LockSupport.setCurrentBlocker(null);// 清除阻塞器
            node.clearStatus();// 清除线程状态值
            acquire(node, savedState, false, false, false, 0L);// 重新进行加锁,将之前释放的锁重新进行还原回去
            if (interrupted) {
                if (cancelled) {
                    unlinkCancelledWaiters(node);// 如果当前线程需要进行中断,则从等待队列中去除当前节点
                    throw new InterruptedException();
                }
                Thread.currentThread().interrupt();// 中断当前线程
            }
        }

        /**
         *
         */
        public final long awaitNanos(long nanosTimeout) throws InterruptedException {
            if (Thread.interrupted()) throw new InterruptedException();
            ConditionNode node = new ConditionNode(); // 创建一个节点
            int savedState = enableWait(node); // 释放锁,返回上锁的次数
            long nanos = (nanosTimeout < 0L) ? 0L : nanosTimeout; // 重新计算时间,<0就赋值为0,否则还是传入的值
            long deadline = System.nanoTime() + nanos; // 计算死亡的时间,纳秒(系统的时间+nanos纳秒)
            boolean cancelled = false, interrupted = false;
            while (!canReacquire(node)) { // 如果不能被中断就进行中断,自选的方式进行中断
                if ((interrupted |= Thread.interrupted()) || (nanos = deadline - System.nanoTime()) <= 0L) {
                    if (cancelled = (node.getAndUnsetStatus(COND) & COND) != 0)
                        break;// 中断完成跳出自旋
                } else
                    LockSupport.parkNanos(this, nanos);
            }
            node.clearStatus(); // 清除状态
            // 重新加锁
            acquire(node, savedState, false, false, false, 0L);
            // 如果线程已经被中断成功了
            if (cancelled) {
                unlinkCancelledWaiters(node);// 从等待队列中去除当前node节点
                if (interrupted)
                    throw new InterruptedException();
            } else if (interrupted)
                Thread.currentThread().interrupt();// 中断当前线程
            long remaining = deadline - System.nanoTime(); // 计算还有多少纳秒的等待
            return (remaining <= nanosTimeout) ? remaining : Long.MIN_VALUE;// 返回剩余时间
        }

        /**
         *
         */
        public final boolean awaitUntil(Date deadline) throws InterruptedException {
            long abstime = deadline.getTime(); // 获取阻塞的时间
            if (Thread.interrupted())
                throw new InterruptedException();
            ConditionNode node = new ConditionNode();   // 创建一个节点
            int savedState = enableWait(node);          // 释放锁并返回锁计数
            boolean cancelled = false, interrupted = false;
            while (!canReacquire(node)) { // 自旋的方式中断当前线程
                if ((interrupted |= Thread.interrupted()) || System.currentTimeMillis() >= abstime) {
                    if (cancelled = (node.getAndUnsetStatus(COND) & COND) != 0)
                        break; // 中断成功
                } else
                    LockSupport.parkUntil(this, abstime); // 阻塞当前线程,超过abstime就自动唤醒线程
            }
            node.clearStatus();// 清除状态值,将state置0,方便下面重新加savedState次锁
            acquire(node, savedState, false, false, false, 0L);

            if (cancelled) {
                unlinkCancelledWaiters(node); // 从等待队列中移除当前节点
                if (interrupted)
                    throw new InterruptedException();
            } else if (interrupted)
                Thread.currentThread().interrupt();// 中断当前线程
            return !cancelled;
        }

        /**
         * 带超时时间的阻塞,java8新的时间api方式
         */
        public final boolean await(long time, TimeUnit unit) throws InterruptedException {
            long nanosTimeout = unit.toNanos(time); // 获取阻塞的最长时间
            if (Thread.interrupted())
                throw new InterruptedException();
            ConditionNode node = new ConditionNode();// 创建一个节点
            int savedState = enableWait(node);          // 释放锁并且返回锁计数
            long nanos = (nanosTimeout < 0L) ? 0L : nanosTimeout;// 防止负数
            long deadline = System.nanoTime() + nanos;// 计算阻塞最大的时间点(在这个点以前都阻塞,过了就会自动唤醒)
            boolean cancelled = false, interrupted = false;
            while (!canReacquire(node)) { // 自选的方式中断当前线程
                if ((interrupted |= Thread.interrupted()) ||
                        (nanos = deadline - System.nanoTime()) <= 0L) {
                    if (cancelled = (node.getAndUnsetStatus(COND) & COND) != 0)
                        break;
                } else
                    LockSupport.parkNanos(this, nanos);// 带超市的方式阻塞
            }
            node.clearStatus();// 将state置0
            // 重新加锁savedState次
            acquire(node, savedState, false, false, false, 0L);
            if (cancelled) {
                unlinkCancelledWaiters(node);
                if (interrupted)
                    throw new InterruptedException();
            } else if (interrupted)
                Thread.currentThread().interrupt();
            return !cancelled;
        }


        /**
         * 是否是AQS
         */
        final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
            return sync == AbstractQueuedSynchronizer.this;
        }

        /**
         * 是否有等待线程
         */
        protected final boolean hasWaiters() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            // 遍历单向链表进行查找
            for (ConditionNode w = firstWaiter; w != null; w = w.nextWaiter) {
                if ((w.status & COND) != 0)
                    return true;
            }
            return false;
        }

        /**
         *
         */
        protected final int getWaitQueueLength() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            int n = 0;
            for (ConditionNode w = firstWaiter; w != null; w = w.nextWaiter) {
                if ((w.status & COND) != 0) // status是常量3（表示等待状态） 而COND则是2进行&运算则是2，因此2!=0返回true
                    ++n; // 如果符合线程等待状态将计数+1
            }
            return n;//返回aqs队列中状态为waiting的线程个数
        }

        /**
         * 返回等待线程的集合
         */
        protected final Collection<Thread> getWaitingThreads() {
            if (!isHeldExclusively())// 判断是否有线程独占，独占则说明有等待线程，否则说明没有等待线程返回false也就会抛出IllegalMonitorStateException
                throw new IllegalMonitorStateException();
            ArrayList<Thread> list = new ArrayList<>(); //创建一个ArrayList集合类
            for (ConditionNode w = firstWaiter; w != null; w = w.nextWaiter) { //for遍历AQS队列的节点
                if ((w.status & COND) != 0) { //只将线程状态为WAITING状态的线程存入ArrayList中
                    Thread t = w.waiter;//取出线程
                    if (t != null) // 线程!=null
                        list.add(t);// 添加进集合
                }
            }
            return list; // 返回集合
        }
    }

}