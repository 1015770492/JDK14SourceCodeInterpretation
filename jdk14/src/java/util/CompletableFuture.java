package java.util;

import javax.annotation.processing.Completion;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;


public class CompletableFuture<T> implements Future<T>, CompletionStage<T> {

    volatile Object result;       // 返回的结果
    volatile Completion stack;    // Top of Treiber stack of dependent actions
    // 是否使用默认ForkJoinPool池
    private static final boolean USE_COMMON_POOL = (ForkJoinPool.getCommonPoolParallelism() > 1);
    // 异步执行的线程，默认为ForkJoinPool,或者是new Thread().start();cpu核心数超过2个以上，即>=3个cpu核心时才会默认使用forkJoinPool，否则是使用new Thread的方式完成任务
    private static final Executor ASYNC_POOL = USE_COMMON_POOL ? ForkJoinPool.commonPool() : new ThreadPerTaskExecutor();
    static final int SYNC = 0;// 同步信号
    static final int ASYNC = 1;// 异步信号
    static final int NESTED = -1;// 嵌套
    static final AltResult NIL = new AltResult(null);// 异常
    private static final VarHandle RESULT;
    private static final VarHandle STACK;
    private static final VarHandle NEXT;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            RESULT = l.findVarHandle(CompletableFuture.class, "result", Object.class);
            STACK = l.findVarHandle(CompletableFuture.class, "stack", Completion.class);
            NEXT = l.findVarHandle(Completion.class, "next", Completion.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
        Class<?> ensureLoaded = LockSupport.class;
    }
    /**
     * 构造方法
     */
    public CompletableFuture() { }
    CompletableFuture(Object r) {
        RESULT.setRelease(this, r);
    }

    final boolean internalComplete(Object r) {
        return RESULT.compareAndSet(this, null, r);
    }
    final boolean tryPushStack(Completion c) {
        Completion h = stack;
        NEXT.set(c, h);
        return STACK.compareAndSet(this, h, c);
    }
    final void pushStack(Completion c) {
        do {
        } while (!tryPushStack(c));
    }
    final boolean completeNull() {
        return RESULT.compareAndSet(this, null, NIL);
    }
    final Object encodeValue(T t) {
        return (t == null) ? NIL : t;
    }
    final boolean completeValue(T t) {
        return RESULT.compareAndSet(this, null, (t == null) ? NIL : t);
    }
    static AltResult encodeThrowable(Throwable x) {
        return new AltResult((x instanceof CompletionException) ? x : new CompletionException(x));
    }
    final boolean completeThrowable(Throwable x) {
        return RESULT.compareAndSet(this, null, encodeThrowable(x));
    }
    static Object encodeThrowable(Throwable x, Object r) {
        if (!(x instanceof CompletionException))
            x = new CompletionException(x);
        else if (r instanceof AltResult && x == ((AltResult) r).ex)
            return r;
        return new AltResult(x);
    }
    final boolean completeThrowable(Throwable x, Object r) {
        return RESULT.compareAndSet(this, null, encodeThrowable(x, r));
    }
    Object encodeOutcome(T t, Throwable x) {
        return (x == null) ? (t == null) ? NIL : t : encodeThrowable(x);
    }
    static Object encodeRelay(Object r) {
        Throwable x;
        if (r instanceof AltResult
                && (x = ((AltResult) r).ex) != null
                && !(x instanceof CompletionException))
            r = new AltResult(new CompletionException(x));
        return r;
    }
    final boolean completeRelay(Object r) {
        return RESULT.compareAndSet(this, null, encodeRelay(r));
    }
    private static Object reportGet(Object r) throws InterruptedException, ExecutionException {
        if (r == null) // by convention below, null means interrupted
            throw new InterruptedException();
        if (r instanceof AltResult) {
            Throwable x, cause;
            if ((x = ((AltResult) r).ex) == null)
                return null;
            if (x instanceof CancellationException)
                throw (CancellationException) x;
            if ((x instanceof CompletionException) &&
                    (cause = x.getCause()) != null)
                x = cause;
            throw new ExecutionException(x);
        }
        return r;
    }
    private static Object reportJoin(Object r) {
        if (r instanceof AltResult) {
            Throwable x;
            if ((x = ((AltResult) r).ex) == null)
                return null;
            if (x instanceof CancellationException)
                throw (CancellationException) x;
            if (x instanceof CompletionException)
                throw (CompletionException) x;
            throw new CompletionException(x);
        }
        return r;
    }
    static Executor screenExecutor(Executor e) {
        if (!USE_COMMON_POOL && e == ForkJoinPool.commonPool())
            return ASYNC_POOL;
        if (e == null) throw new NullPointerException();
        return e;
    }
    final void postComplete() {
        CompletableFuture<?> f = this;
        Completion h;
        while ((h = f.stack) != null ||
                (f != this && (h = (f = this).stack) != null)) {
            CompletableFuture<?> d;
            Completion t;
            if (STACK.compareAndSet(f, h, t = h.next)) {
                if (t != null) {
                    if (f != this) {
                        pushStack(h);
                        continue;
                    }
                    NEXT.compareAndSet(h, t, null); // try to detach
                }
                f = (d = h.tryFire(NESTED)) == null ? this : d;
            }
        }
    }
    final void cleanStack() {
        Completion p = stack;
        for (boolean unlinked = false; ; ) {
            if (p == null)
                return;
            else if (p.isLive()) {
                if (unlinked)
                    return;
                else
                    break;
            } else if (STACK.weakCompareAndSet(this, p, (p = p.next)))
                unlinked = true;
            else
                p = stack;
        }
        for (Completion q = p.next; q != null; ) {
            Completion s = q.next;
            if (q.isLive()) {
                p = q;
                q = s;
            } else if (NEXT.weakCompareAndSet(p, q, s))
                break;
            else
                q = p.next;
        }
    }
    final void unipush(Completion c) {
        if (c != null) {
            while (!tryPushStack(c)) {
                if (result != null) {
                    NEXT.set(c, null);
                    break;
                }
            }
            if (result != null)
                c.tryFire(SYNC);
        }
    }
    final CompletableFuture<T> postFire(CompletableFuture<?> a, int mode) {
        if (a != null && a.stack != null) {
            Object r;
            if ((r = a.result) == null)
                a.cleanStack();
            if (mode >= 0 && (r != null || a.result != null))
                a.postComplete();
        }
        if (result != null && stack != null) {
            if (mode < 0)
                return this;
            else
                postComplete();
        }
        return null;
    }
    private <V> CompletableFuture<V> uniApplyStage(Executor e, Function<? super T, ? extends V> f) {
        if (f == null) throw new NullPointerException();
        Object r;
        if ((r = result) != null)
            return uniApplyNow(r, e, f);
        CompletableFuture<V> d = newIncompleteFuture();
        unipush(new UniApply<T, V>(e, d, this, f));
        return d;
    }
    private <V> CompletableFuture<V> uniApplyNow(Object r, Executor e, Function<? super T, ? extends V> f) {
        Throwable x;
        CompletableFuture<V> d = newIncompleteFuture();
        if (r instanceof AltResult) {
            if ((x = ((AltResult) r).ex) != null) {
                d.result = encodeThrowable(x, r);
                return d;
            }
            r = null;
        }
        try {
            if (e != null) {
                e.execute(new UniApply<T, V>(null, d, this, f));
            } else {
                @SuppressWarnings("unchecked") T t = (T) r;
                d.result = d.encodeValue(f.apply(t));
            }
        } catch (Throwable ex) {
            d.result = encodeThrowable(ex);
        }
        return d;
    }
    private CompletableFuture<Void> uniAcceptStage(Executor e, Consumer<? super T> f) {
        if (f == null) throw new NullPointerException();
        Object r;
        if ((r = result) != null)
            return uniAcceptNow(r, e, f);
        CompletableFuture<Void> d = newIncompleteFuture();
        unipush(new UniAccept<T>(e, d, this, f));
        return d;
    }
    private CompletableFuture<Void> uniAcceptNow(Object r, Executor e, Consumer<? super T> f) {
        Throwable x;
        CompletableFuture<Void> d = newIncompleteFuture();
        if (r instanceof AltResult) {
            if ((x = ((AltResult) r).ex) != null) {
                d.result = encodeThrowable(x, r);
                return d;
            }
            r = null;
        }
        try {
            if (e != null) {
                e.execute(new UniAccept<T>(null, d, this, f));
            } else {
                @SuppressWarnings("unchecked") T t = (T) r;
                f.accept(t);
                d.result = NIL;
            }
        } catch (Throwable ex) {
            d.result = encodeThrowable(ex);
        }
        return d;
    }
    private CompletableFuture<Void> uniRunStage(Executor e, Runnable f) {
        if (f == null) throw new NullPointerException();
        Object r;
        if ((r = result) != null)
            return uniRunNow(r, e, f);
        CompletableFuture<Void> d = newIncompleteFuture();
        unipush(new UniRun<T>(e, d, this, f));
        return d;
    }
    private CompletableFuture<Void> uniRunNow(Object r, Executor e, Runnable f) {
        Throwable x;
        CompletableFuture<Void> d = newIncompleteFuture();
        if (r instanceof AltResult && (x = ((AltResult) r).ex) != null)
            d.result = encodeThrowable(x, r);
        else
            try {
                if (e != null) {
                    e.execute(new UniRun<T>(null, d, this, f));
                } else {
                    f.run();
                    d.result = NIL;
                }
            } catch (Throwable ex) {
                d.result = encodeThrowable(ex);
            }
        return d;
    }
    final boolean uniWhenComplete(Object r, BiConsumer<? super T, ? super Throwable> f, UniWhenComplete<T> c) {
        T t;
        Throwable x = null;
        if (result == null) {
            try {
                if (c != null && !c.claim())
                    return false;
                if (r instanceof AltResult) {
                    x = ((AltResult) r).ex;
                    t = null;
                } else {
                    @SuppressWarnings("unchecked") T tr = (T) r;
                    t = tr;
                }
                f.accept(t, x);
                if (x == null) {
                    internalComplete(r);
                    return true;
                }
            } catch (Throwable ex) {
                if (x == null)
                    x = ex;
                else if (x != ex)
                    x.addSuppressed(ex);
            }
            completeThrowable(x, r);
        }
        return true;
    }
    private CompletableFuture<T> uniWhenCompleteStage(Executor e, BiConsumer<? super T, ? super Throwable> f) {
        if (f == null) throw new NullPointerException();
        CompletableFuture<T> d = newIncompleteFuture();
        Object r;
        if ((r = result) == null)
            unipush(new UniWhenComplete<T>(e, d, this, f));
        else if (e == null)
            d.uniWhenComplete(r, f, null);
        else {
            try {
                e.execute(new UniWhenComplete<T>(null, d, this, f));
            } catch (Throwable ex) {
                d.result = encodeThrowable(ex);
            }
        }
        return d;
    }
    final <S> boolean uniHandle(Object r, BiFunction<? super S, Throwable, ? extends T> f, UniHandle<S, T> c) {
        S s;
        Throwable x;
        if (result == null) {
            try {
                if (c != null && !c.claim())
                    return false;
                if (r instanceof AltResult) {
                    x = ((AltResult) r).ex;
                    s = null;
                } else {
                    x = null;
                    @SuppressWarnings("unchecked") S ss = (S) r;
                    s = ss;
                }
                completeValue(f.apply(s, x));
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }
    private <V> CompletableFuture<V> uniHandleStage(Executor e, BiFunction<? super T, Throwable, ? extends V> f) {
        if (f == null) throw new NullPointerException();
        CompletableFuture<V> d = newIncompleteFuture();
        Object r;
        if ((r = result) == null)
            unipush(new UniHandle<T, V>(e, d, this, f));
        else if (e == null)
            d.uniHandle(r, f, null);
        else {
            try {
                e.execute(new UniHandle<T, V>(null, d, this, f));
            } catch (Throwable ex) {
                d.result = encodeThrowable(ex);
            }
        }
        return d;
    }
    final boolean uniExceptionally(Object r, Function<? super Throwable, ? extends T> f, UniExceptionally<T> c) {
        Throwable x;
        if (result == null) {
            try {
                if (c != null && !c.claim())
                    return false;
                if (r instanceof AltResult && (x = ((AltResult) r).ex) != null)
                    completeValue(f.apply(x));
                else
                    internalComplete(r);
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }
    private CompletableFuture<T> uniExceptionallyStage(Executor e, Function<Throwable, ? extends T> f) {
        if (f == null) throw new NullPointerException();
        CompletableFuture<T> d = newIncompleteFuture();
        Object r;
        if ((r = result) == null)
            unipush(new UniExceptionally<T>(e, d, this, f));
        else if (e == null)
            d.uniExceptionally(r, f, null);
        else {
            try {
                e.execute(new UniExceptionally<T>(null, d, this, f));
            } catch (Throwable ex) {
                d.result = encodeThrowable(ex);
            }
        }
        return d;
    }
    private CompletableFuture<T> uniComposeExceptionallyStage(Executor e, Function<Throwable, ? extends CompletionStage<T>> f) {
        if (f == null) throw new NullPointerException();
        CompletableFuture<T> d = newIncompleteFuture();
        Object r, s;
        Throwable x;
        if ((r = result) == null)
            unipush(new UniComposeExceptionally<T>(e, d, this, f));
        else if (!(r instanceof AltResult) || (x = ((AltResult) r).ex) == null)
            d.internalComplete(r);
        else
            try {
                if (e != null)
                    e.execute(new UniComposeExceptionally<T>(null, d, this, f));
                else {
                    CompletableFuture<T> g = f.apply(x).toCompletableFuture();
                    if ((s = g.result) != null)
                        d.result = encodeRelay(s);
                    else
                        g.unipush(new UniRelay<T, T>(d, g));
                }
            } catch (Throwable ex) {
                d.result = encodeThrowable(ex);
            }
        return d;
    }
    private static <U, T extends U> CompletableFuture<U> uniCopyStage(CompletableFuture<T> src) {
        Object r;
        CompletableFuture<U> d = src.newIncompleteFuture();
        if ((r = src.result) != null)
            d.result = encodeRelay(r);
        else
            src.unipush(new UniRelay<U, T>(d, src));
        return d;
    }
    private MinimalStage<T> uniAsMinimalStage() {
        Object r;
        if ((r = result) != null)
            return new MinimalStage<T>(encodeRelay(r));
        MinimalStage<T> d = new MinimalStage<T>();
        unipush(new UniRelay<T, T>(d, this));
        return d;
    }
    private <V> CompletableFuture<V> uniComposeStage(Executor e, Function<? super T, ? extends CompletionStage<V>> f) {
        if (f == null) throw new NullPointerException();
        CompletableFuture<V> d = newIncompleteFuture();
        Object r, s;
        Throwable x;
        if ((r = result) == null)
            unipush(new UniCompose<T, V>(e, d, this, f));
        else {
            if (r instanceof AltResult) {
                if ((x = ((AltResult) r).ex) != null) {
                    d.result = encodeThrowable(x, r);
                    return d;
                }
                r = null;
            }
            try {
                if (e != null)
                    e.execute(new UniCompose<T, V>(null, d, this, f));
                else {
                    @SuppressWarnings("unchecked") T t = (T) r;
                    CompletableFuture<V> g = f.apply(t).toCompletableFuture();
                    if ((s = g.result) != null)
                        d.result = encodeRelay(s);
                    else
                        g.unipush(new UniRelay<V, V>(d, g));
                }
            } catch (Throwable ex) {
                d.result = encodeThrowable(ex);
            }
        }
        return d;
    }
    final void bipush(CompletableFuture<?> b, BiCompletion<?, ?, ?> c) {
        if (c != null) {
            while (result == null) {
                if (tryPushStack(c)) {
                    if (b.result == null)
                        b.unipush(new CoCompletion(c));
                    else if (result != null)
                        c.tryFire(SYNC);
                    return;
                }
            }
            b.unipush(c);
        }
    }
    final CompletableFuture<T> postFire(CompletableFuture<?> a, CompletableFuture<?> b, int mode) {
        if (b != null && b.stack != null) { // clean second source
            Object r;
            if ((r = b.result) == null)
                b.cleanStack();
            if (mode >= 0 && (r != null || b.result != null))
                b.postComplete();
        }
        return postFire(a, mode);
    }
    final <R, S> boolean biApply(Object r, Object s, BiFunction<? super R, ? super S, ? extends T> f, BiApply<R, S, T> c) {
        Throwable x;
        tryComplete:
        if (result == null) {
            if (r instanceof AltResult) {
                if ((x = ((AltResult) r).ex) != null) {
                    completeThrowable(x, r);
                    break tryComplete;
                }
                r = null;
            }
            if (s instanceof AltResult) {
                if ((x = ((AltResult) s).ex) != null) {
                    completeThrowable(x, s);
                    break tryComplete;
                }
                s = null;
            }
            try {
                if (c != null && !c.claim())
                    return false;
                @SuppressWarnings("unchecked") R rr = (R) r;
                @SuppressWarnings("unchecked") S ss = (S) s;
                completeValue(f.apply(rr, ss));
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }
    private <U, V> CompletableFuture<V> biApplyStage(Executor e, CompletionStage<U> o, BiFunction<? super T, ? super U, ? extends V> f) {
        CompletableFuture<U> b;
        Object r, s;
        if (f == null || (b = o.toCompletableFuture()) == null)
            throw new NullPointerException();
        CompletableFuture<V> d = newIncompleteFuture();
        if ((r = result) == null || (s = b.result) == null)
            bipush(b, new BiApply<T, U, V>(e, d, this, b, f));
        else if (e == null)
            d.biApply(r, s, f, null);
        else
            try {
                e.execute(new BiApply<T, U, V>(null, d, this, b, f));
            } catch (Throwable ex) {
                d.result = encodeThrowable(ex);
            }
        return d;
    }
    final <R, S> boolean biAccept(Object r, Object s, BiConsumer<? super R, ? super S> f, BiAccept<R, S> c) {
        Throwable x;
        tryComplete:
        if (result == null) {
            if (r instanceof AltResult) {
                if ((x = ((AltResult) r).ex) != null) {
                    completeThrowable(x, r);
                    break tryComplete;
                }
                r = null;
            }
            if (s instanceof AltResult) {
                if ((x = ((AltResult) s).ex) != null) {
                    completeThrowable(x, s);
                    break tryComplete;
                }
                s = null;
            }
            try {
                if (c != null && !c.claim())
                    return false;
                @SuppressWarnings("unchecked") R rr = (R) r;
                @SuppressWarnings("unchecked") S ss = (S) s;
                f.accept(rr, ss);
                completeNull();
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }
    private <U> CompletableFuture<Void> biAcceptStage(Executor e, CompletionStage<U> o, BiConsumer<? super T, ? super U> f) {
        CompletableFuture<U> b;
        Object r, s;
        if (f == null || (b = o.toCompletableFuture()) == null)
            throw new NullPointerException();
        CompletableFuture<Void> d = newIncompleteFuture();
        if ((r = result) == null || (s = b.result) == null)
            bipush(b, new BiAccept<T, U>(e, d, this, b, f));
        else if (e == null)
            d.biAccept(r, s, f, null);
        else
            try {
                e.execute(new BiAccept<T, U>(null, d, this, b, f));
            } catch (Throwable ex) {
                d.result = encodeThrowable(ex);
            }
        return d;
    }
    final boolean biRun(Object r, Object s, Runnable f, BiRun<?, ?> c) {
        Throwable x;
        Object z;
        if (result == null) {
            if ((r instanceof AltResult
                    && (x = ((AltResult) (z = r)).ex) != null) ||
                    (s instanceof AltResult
                            && (x = ((AltResult) (z = s)).ex) != null))
                completeThrowable(x, z);
            else
                try {
                    if (c != null && !c.claim())
                        return false;
                    f.run();
                    completeNull();
                } catch (Throwable ex) {
                    completeThrowable(ex);
                }
        }
        return true;
    }
    private CompletableFuture<Void> biRunStage(Executor e, CompletionStage<?> o, Runnable f) {
        CompletableFuture<?> b;
        Object r, s;
        if (f == null || (b = o.toCompletableFuture()) == null)
            throw new NullPointerException();
        CompletableFuture<Void> d = newIncompleteFuture();
        if ((r = result) == null || (s = b.result) == null)
            bipush(b, new BiRun<>(e, d, this, b, f));
        else if (e == null)
            d.biRun(r, s, f, null);
        else
            try {
                e.execute(new BiRun<>(null, d, this, b, f));
            } catch (Throwable ex) {
                d.result = encodeThrowable(ex);
            }
        return d;
    }
    static CompletableFuture<Void> andTree(CompletableFuture<?>[] cfs, int lo, int hi) {
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        if (lo > hi) // empty
            d.result = NIL;
        else {
            CompletableFuture<?> a, b;
            Object r, s, z;
            Throwable x;
            int mid = (lo + hi) >>> 1;
            if ((a = (lo == mid ? cfs[lo] :
                    andTree(cfs, lo, mid))) == null ||
                    (b = (lo == hi ? a : (hi == mid + 1) ? cfs[hi] :
                            andTree(cfs, mid + 1, hi))) == null)
                throw new NullPointerException();
            if ((r = a.result) == null || (s = b.result) == null)
                a.bipush(b, new BiRelay<>(d, a, b));
            else if ((r instanceof AltResult
                    && (x = ((AltResult) (z = r)).ex) != null) ||
                    (s instanceof AltResult
                            && (x = ((AltResult) (z = s)).ex) != null))
                d.result = encodeThrowable(x, z);
            else
                d.result = NIL;
        }
        return d;
    }
    final void orpush(CompletableFuture<?> b, BiCompletion<?, ?, ?> c) {
        if (c != null) {
            while (!tryPushStack(c)) {
                if (result != null) {
                    NEXT.set(c, null);
                    break;
                }
            }
            if (result != null)
                c.tryFire(SYNC);
            else
                b.unipush(new CoCompletion(c));
        }
    }
    private <U extends T, V> CompletableFuture<V> orApplyStage(Executor e, CompletionStage<U> o, Function<? super T, ? extends V> f) {
        CompletableFuture<U> b;
        if (f == null || (b = o.toCompletableFuture()) == null)
            throw new NullPointerException();

        Object r;
        CompletableFuture<? extends T> z;
        if ((r = (z = this).result) != null ||
                (r = (z = b).result) != null)
            return z.uniApplyNow(r, e, f);

        CompletableFuture<V> d = newIncompleteFuture();
        orpush(b, new OrApply<T, U, V>(e, d, this, b, f));
        return d;
    }
    private <U extends T> CompletableFuture<Void> orAcceptStage(Executor e, CompletionStage<U> o, Consumer<? super T> f) {
        CompletableFuture<U> b;
        if (f == null || (b = o.toCompletableFuture()) == null)
            throw new NullPointerException();

        Object r;
        CompletableFuture<? extends T> z;
        if ((r = (z = this).result) != null ||
                (r = (z = b).result) != null)
            return z.uniAcceptNow(r, e, f);

        CompletableFuture<Void> d = newIncompleteFuture();
        orpush(b, new OrAccept<T, U>(e, d, this, b, f));
        return d;
    }
    private CompletableFuture<Void> orRunStage(Executor e, CompletionStage<?> o, Runnable f) {
        CompletableFuture<?> b;
        if (f == null || (b = o.toCompletableFuture()) == null)
            throw new NullPointerException();

        Object r;
        CompletableFuture<?> z;
        if ((r = (z = this).result) != null ||
                (r = (z = b).result) != null)
            return z.uniRunNow(r, e, f);

        CompletableFuture<Void> d = newIncompleteFuture();
        orpush(b, new OrRun<>(e, d, this, b, f));
        return d;
    }
    static <U> CompletableFuture<U> asyncSupplyStage(Executor e, Supplier<U> f) {
        if (f == null) throw new NullPointerException();
        CompletableFuture<U> d = new CompletableFuture<U>();
        e.execute(new AsyncSupply<U>(d, f));
        return d;
    }
    static CompletableFuture<Void> asyncRunStage(Executor e, Runnable f) {
        if (f == null) throw new NullPointerException();
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        e.execute(new AsyncRun(d, f));
        return d;
    }
    private Object waitingGet(boolean interruptible) {
        Signaller q = null;
        boolean queued = false;
        Object r;
        while ((r = result) == null) {
            if (q == null) {
                q = new Signaller(interruptible, 0L, 0L);
                if (Thread.currentThread() instanceof ForkJoinWorkerThread)
                    ForkJoinPool.helpAsyncBlocker(defaultExecutor(), q);
            } else if (!queued)
                queued = tryPushStack(q);
            else {
                try {
                    ForkJoinPool.managedBlock(q);
                } catch (InterruptedException ie) { // currently cannot happen
                    q.interrupted = true;
                }
                if (q.interrupted && interruptible)
                    break;
            }
        }
        if (q != null && queued) {
            q.thread = null;
            if (!interruptible && q.interrupted)
                Thread.currentThread().interrupt();
            if (r == null)
                cleanStack();
        }
        if (r != null || (r = result) != null)
            postComplete();
        return r;
    }
    private Object timedGet(long nanos) throws TimeoutException {
        if (Thread.interrupted())
            return null;
        if (nanos > 0L) {
            long d = System.nanoTime() + nanos;
            long deadline = (d == 0L) ? 1L : d; // avoid 0
            Signaller q = null;
            boolean queued = false;
            Object r;
            while ((r = result) == null) { // similar to untimed
                if (q == null) {
                    q = new Signaller(true, nanos, deadline);
                    if (Thread.currentThread() instanceof ForkJoinWorkerThread)
                        ForkJoinPool.helpAsyncBlocker(defaultExecutor(), q);
                } else if (!queued)
                    queued = tryPushStack(q);
                else if (q.nanos <= 0L)
                    break;
                else {
                    try {
                        ForkJoinPool.managedBlock(q);
                    } catch (InterruptedException ie) {
                        q.interrupted = true;
                    }
                    if (q.interrupted)
                        break;
                }
            }
            if (q != null && queued) {
                q.thread = null;
                if (r == null)
                    cleanStack();
            }
            if (r != null || (r = result) != null)
                postComplete();
            if (r != null || (q != null && q.interrupted))
                return r;
        }
        throw new TimeoutException();
    }



    /**
     * 起点，异步调用的起点，一种方式是supply类型的，一种是Runnable(Consumer类型的)
     */
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        return asyncSupplyStage(ASYNC_POOL, supplier);
    } // 供给型
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier, Executor executor) {
        return asyncSupplyStage(screenExecutor(executor), supplier);
    } // 供给型,自定义执行器
    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        return asyncRunStage(ASYNC_POOL, runnable);
    } // 消费型
    public static CompletableFuture<Void> runAsync(Runnable runnable, Executor executor) {
        return asyncRunStage(screenExecutor(executor), runnable);
    }  // 消费型 自定义执行器
    public static Executor delayedExecutor(long delay, TimeUnit unit) {
        if (unit == null)
            throw new NullPointerException();
        return new DelayedExecutor(delay, unit, ASYNC_POOL);
    } // 延迟执行
    public static Executor delayedExecutor(long delay, TimeUnit unit, Executor executor) {
        if (unit == null || executor == null)
            throw new NullPointerException();
        return new DelayedExecutor(delay, unit, executor);
    }  // 延迟执行 自定义执行器
    public static <U> CompletableFuture<U> completedFuture(U value) {
        return new CompletableFuture<U>((value == null) ? NIL : value);
    }  // 完成Future
    public static <U> CompletableFuture<U> failedFuture(Throwable ex) {
        if (ex == null) throw new NullPointerException();
        return new CompletableFuture<U>(new AltResult(ex));
    }// 失败Future
    public static <U> CompletionStage<U> completedStage(U value) {
        return new MinimalStage<U>((value == null) ? NIL : value);
    }     // 完成Stage
    public static <U> CompletionStage<U> failedStage(Throwable ex) {
        if (ex == null) throw new NullPointerException();
        return new MinimalStage<U>(new AltResult(ex));
    }   // 失败Stage

    public T getNow(T valueIfAbsent) {
        Object r;
        return ((r = result) == null) ? valueIfAbsent : (T) reportJoin(r);
    }
    public T get() throws InterruptedException, ExecutionException {
        Object r;
        if ((r = result) == null)
            r = waitingGet(true);
        return (T) reportGet(r);
    }
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long nanos = unit.toNanos(timeout);
        Object r;
        if ((r = result) == null)
            r = timedGet(nanos);
        return (T) reportGet(r);
    }
    public T join() {
        Object r;
        if ((r = result) == null)
            r = waitingGet(false);
        return (T) reportJoin(r);
    }
    public boolean complete(T value) {
        boolean triggered = completeValue(value);
        postComplete();
        return triggered;
    }
    public boolean completeExceptionally(Throwable ex) {
        if (ex == null) throw new NullPointerException();
        boolean triggered = internalComplete(new AltResult(ex));
        postComplete();
        return triggered;
    }
    public boolean isDone() {
        return result != null;
    }
    public <U> CompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
        return uniApplyStage(null, fn);
    }
    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return uniApplyStage(defaultExecutor(), fn);
    }
    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return uniApplyStage(screenExecutor(executor), fn);
    }
    public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        return uniAcceptStage(null, action);
    }
    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action) {
        return uniAcceptStage(defaultExecutor(), action);
    }
    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return uniAcceptStage(screenExecutor(executor), action);
    }
    public CompletableFuture<Void> thenRun(Runnable action) {
        return uniRunStage(null, action);
    }
    public CompletableFuture<Void> thenRunAsync(Runnable action) {
        return uniRunStage(defaultExecutor(), action);
    }
    public CompletableFuture<Void> thenRunAsync(Runnable action, Executor executor) {
        return uniRunStage(screenExecutor(executor), action);
    }
    public <U, V> CompletableFuture<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return biApplyStage(null, other, fn);
    }
    public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return biApplyStage(defaultExecutor(), other, fn);
    }
    public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
        return biApplyStage(screenExecutor(executor), other, fn);
    }
    public <U> CompletableFuture<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return biAcceptStage(null, other, action);
    }
    public <U> CompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return biAcceptStage(defaultExecutor(), other, action);
    }
    public <U> CompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action, Executor executor) {
        return biAcceptStage(screenExecutor(executor), other, action);
    }
    public CompletableFuture<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return biRunStage(null, other, action);
    }
    public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return biRunStage(defaultExecutor(), other, action);
    }
    public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return biRunStage(screenExecutor(executor), other, action);
    }
    public <U> CompletableFuture<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return orApplyStage(null, other, fn);
    }
    public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return orApplyStage(defaultExecutor(), other, fn);
    }
    public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn, Executor executor) {
        return orApplyStage(screenExecutor(executor), other, fn);
    }
    public CompletableFuture<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return orAcceptStage(null, other, action);
    }
    public CompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return orAcceptStage(defaultExecutor(), other, action);
    }
    public CompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action, Executor executor) {
        return orAcceptStage(screenExecutor(executor), other, action);
    }
    public CompletableFuture<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return orRunStage(null, other, action);
    }
    public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return orRunStage(defaultExecutor(), other, action);
    }
    public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return orRunStage(screenExecutor(executor), other, action);
    }
    public <U> CompletableFuture<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return uniComposeStage(null, fn);
    }
    public <U> CompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return uniComposeStage(defaultExecutor(), fn);
    }
    public <U> CompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return uniComposeStage(screenExecutor(executor), fn);
    }
    public CompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return uniWhenCompleteStage(null, action);
    }
    public CompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return uniWhenCompleteStage(defaultExecutor(), action);
    }
    public CompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return uniWhenCompleteStage(screenExecutor(executor), action);
    }
    public <U> CompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return uniHandleStage(null, fn);
    }
    public <U> CompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return uniHandleStage(defaultExecutor(), fn);
    }
    public <U> CompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return uniHandleStage(screenExecutor(executor), fn);
    }
    public CompletableFuture<T> toCompletableFuture() {
        return this;
    }
    public CompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return uniExceptionallyStage(null, fn);
    }
    public CompletableFuture<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return uniExceptionallyStage(defaultExecutor(), fn);
    }
    public CompletableFuture<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        return uniExceptionallyStage(screenExecutor(executor), fn);
    }
    public CompletableFuture<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return uniComposeExceptionallyStage(null, fn);
    }
    public CompletableFuture<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return uniComposeExceptionallyStage(defaultExecutor(), fn);
    }
    public CompletableFuture<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
        return uniComposeExceptionallyStage(screenExecutor(executor), fn);
    }
    /**多任务组合*/
    public static CompletableFuture<Void> allOf(CompletableFuture<?>... cfs) {
        return andTree(cfs, 0, cfs.length - 1);
    }
    public static CompletableFuture<Object> anyOf(CompletableFuture<?>... cfs) {
        int n;
        Object r;
        if ((n = cfs.length) <= 1)
            return (n == 0)
                    ? new CompletableFuture<Object>()
                    : uniCopyStage(cfs[0]);
        for (CompletableFuture<?> cf : cfs)
            if ((r = cf.result) != null)
                return new CompletableFuture<Object>(encodeRelay(r));
        cfs = cfs.clone();
        CompletableFuture<Object> d = new CompletableFuture<>();
        for (CompletableFuture<?> cf : cfs)
            cf.unipush(new AnyOf(d, cf, cfs));
        if (d.result != null)
            for (int i = 0, len = cfs.length; i < len; i++)
                if (cfs[i].result != null)
                    for (i++; i < len; i++)
                        if (cfs[i].result == null)
                            cfs[i].cleanStack();
        return d;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = (result == null) &&
                internalComplete(new AltResult(new CancellationException()));
        postComplete();
        return cancelled || isCancelled();
    }
    public boolean isCancelled() {
        Object r;
        return ((r = result) instanceof AltResult) &&
                (((AltResult) r).ex instanceof CancellationException);
    }
    public boolean isCompletedExceptionally() {
        Object r;
        return ((r = result) instanceof AltResult) && r != NIL;
    }
    public void obtrudeValue(T value) {
        result = (value == null) ? NIL : value;
        postComplete();
    }
    public void obtrudeException(Throwable ex) {
        if (ex == null) throw new NullPointerException();
        result = new AltResult(ex);
        postComplete();
    }
    public int getNumberOfDependents() {
        int count = 0;
        for (Completion p = stack; p != null; p = p.next)
            ++count;
        return count;
    }
    public String toString() {
        Object r = result;
        int count = 0; // avoid call to getNumberOfDependents in case disabled
        for (Completion p = stack; p != null; p = p.next)
            ++count;
        return super.toString() +
                ((r == null)
                        ? ((count == 0)
                        ? "[Not completed]"
                        : "[Not completed, " + count + " dependents]")
                        : (((r instanceof AltResult) && ((AltResult) r).ex != null)
                        ? "[Completed exceptionally: " + ((AltResult) r).ex + "]"
                        : "[Completed normally]"));
    }
    public <U> CompletableFuture<U> newIncompleteFuture() {
        return new CompletableFuture<U>();
    }
    public Executor defaultExecutor() {
        return ASYNC_POOL;
    }
    public CompletableFuture<T> copy() {
        return uniCopyStage(this);
    }
    public CompletionStage<T> minimalCompletionStage() {
        return uniAsMinimalStage();
    }
    public CompletableFuture<T> completeAsync(Supplier<? extends T> supplier, Executor executor) {
        if (supplier == null || executor == null)
            throw new NullPointerException();
        executor.execute(new AsyncSupply<T>(this, supplier));
        return this;
    }
    public CompletableFuture<T> completeAsync(Supplier<? extends T> supplier) {
        return completeAsync(supplier, defaultExecutor());
    }
    public CompletableFuture<T> orTimeout(long timeout, TimeUnit unit) {
        if (unit == null)
            throw new NullPointerException();
        if (result == null)
            whenComplete(new Canceller(Delayer.delay(new Timeout(this),
                    timeout, unit)));
        return this;
    }
    public CompletableFuture<T> completeOnTimeout(T value, long timeout, TimeUnit unit) {
        if (unit == null)
            throw new NullPointerException();
        if (result == null)
            whenComplete(new Canceller(Delayer.delay(
                    new DelayedCompleter<T>(this, value),
                    timeout, unit)));
        return this;
    }





    static final class AltResult {
        final Throwable ex;
        AltResult(Throwable x) {
            this.ex = x;
        }
    }
    public static interface AsynchronousCompletionTask {
    }
    static final class ThreadPerTaskExecutor implements Executor {
        public void execute(Runnable r) {
            new Thread(r).start();
        }
    }
    @SuppressWarnings("serial")
    abstract static class Completion extends ForkJoinTask<Void> implements Runnable, AsynchronousCompletionTask {
        volatile Completion next;      // Treiber stack link

        abstract CompletableFuture<?> tryFire(int mode);

        abstract boolean isLive();

        public final void run() {
            tryFire(ASYNC);
        }

        public final boolean exec() {
            tryFire(ASYNC);
            return false;
        }

        public final Void getRawResult() {
            return null;
        }

        public final void setRawResult(Void v) {
        }
    }
    @SuppressWarnings("serial")
    abstract static class UniCompletion<T, V> extends Completion {
        Executor executor;                 // executor to use (null if none)
        CompletableFuture<V> dep;          // the dependent to complete
        CompletableFuture<T> src;          // source for action

        UniCompletion(Executor executor, CompletableFuture<V> dep,
                      CompletableFuture<T> src) {
            this.executor = executor;
            this.dep = dep;
            this.src = src;
        }

        final boolean claim() {
            Executor e = executor;
            if (compareAndSetForkJoinTaskTag((short) 0, (short) 1)) {
                if (e == null)
                    return true;
                executor = null; // disable
                e.execute(this);
            }
            return false;
        }

        final boolean isLive() {
            return dep != null;
        }
    }
    @SuppressWarnings("serial")
    static final class UniApply<T, V> extends UniCompletion<T, V> {
        Function<? super T, ? extends V> fn;

        UniApply(Executor executor, CompletableFuture<V> dep,
                 CompletableFuture<T> src,
                 Function<? super T, ? extends V> fn) {
            super(executor, dep, src);
            this.fn = fn;
        }

        final CompletableFuture<V> tryFire(int mode) {
            CompletableFuture<V> d;
            CompletableFuture<T> a;
            Object r;
            Throwable x;
            Function<? super T, ? extends V> f;
            if ((a = src) == null || (r = a.result) == null
                    || (d = dep) == null || (f = fn) == null)
                return null;
            tryComplete:
            if (d.result == null) {
                if (r instanceof AltResult) {
                    if ((x = ((AltResult) r).ex) != null) {
                        d.completeThrowable(x, r);
                        break tryComplete;
                    }
                    r = null;
                }
                try {
                    if (mode <= 0 && !claim())
                        return null;
                    else {
                        @SuppressWarnings("unchecked") T t = (T) r;
                        d.completeValue(f.apply(t));
                    }
                } catch (Throwable ex) {
                    d.completeThrowable(ex);
                }
            }
            src = null;
            dep = null;
            fn = null;
            return d.postFire(a, mode);
        }
    }
    @SuppressWarnings("serial")
    static final class UniAccept<T> extends UniCompletion<T, Void> {
        Consumer<? super T> fn;

        UniAccept(Executor executor, CompletableFuture<Void> dep,
                  CompletableFuture<T> src, Consumer<? super T> fn) {
            super(executor, dep, src);
            this.fn = fn;
        }

        final CompletableFuture<Void> tryFire(int mode) {
            CompletableFuture<Void> d;
            CompletableFuture<T> a;
            Object r;
            Throwable x;
            Consumer<? super T> f;
            if ((a = src) == null || (r = a.result) == null
                    || (d = dep) == null || (f = fn) == null)
                return null;
            tryComplete:
            if (d.result == null) {
                if (r instanceof AltResult) {
                    if ((x = ((AltResult) r).ex) != null) {
                        d.completeThrowable(x, r);
                        break tryComplete;
                    }
                    r = null;
                }
                try {
                    if (mode <= 0 && !claim())
                        return null;
                    else {
                        @SuppressWarnings("unchecked") T t = (T) r;
                        f.accept(t);
                        d.completeNull();
                    }
                } catch (Throwable ex) {
                    d.completeThrowable(ex);
                }
            }
            src = null;
            dep = null;
            fn = null;
            return d.postFire(a, mode);
        }
    }
    @SuppressWarnings("serial")
    static final class UniRun<T> extends UniCompletion<T, Void> {
        Runnable fn;

        UniRun(Executor executor, CompletableFuture<Void> dep, CompletableFuture<T> src, Runnable fn) {
            super(executor, dep, src);
            this.fn = fn;
        }

        final CompletableFuture<Void> tryFire(int mode) {
            CompletableFuture<Void> d;
            CompletableFuture<T> a;
            Object r;
            Throwable x;
            Runnable f;
            if ((a = src) == null || (r = a.result) == null
                    || (d = dep) == null || (f = fn) == null)
                return null;
            if (d.result == null) {
                if (r instanceof AltResult && (x = ((AltResult) r).ex) != null)
                    d.completeThrowable(x, r);
                else
                    try {
                        if (mode <= 0 && !claim())
                            return null;
                        else {
                            f.run();
                            d.completeNull();
                        }
                    } catch (Throwable ex) {
                        d.completeThrowable(ex);
                    }
            }
            src = null;
            dep = null;
            fn = null;
            return d.postFire(a, mode);
        }
    }
    @SuppressWarnings("serial")
    static final class UniWhenComplete<T> extends UniCompletion<T, T> {
        BiConsumer<? super T, ? super Throwable> fn;

        UniWhenComplete(Executor executor, CompletableFuture<T> dep, CompletableFuture<T> src, BiConsumer<? super T, ? super Throwable> fn) {
            super(executor, dep, src);
            this.fn = fn;
        }

        final CompletableFuture<T> tryFire(int mode) {
            CompletableFuture<T> d;
            CompletableFuture<T> a;
            Object r;
            BiConsumer<? super T, ? super Throwable> f;
            if ((a = src) == null || (r = a.result) == null
                    || (d = dep) == null || (f = fn) == null
                    || !d.uniWhenComplete(r, f, mode > 0 ? null : this))
                return null;
            src = null;
            dep = null;
            fn = null;
            return d.postFire(a, mode);
        }
    }
    @SuppressWarnings("serial")
    static final class UniHandle<T, V> extends UniCompletion<T, V> {
        BiFunction<? super T, Throwable, ? extends V> fn;

        UniHandle(Executor executor, CompletableFuture<V> dep,
                  CompletableFuture<T> src,
                  BiFunction<? super T, Throwable, ? extends V> fn) {
            super(executor, dep, src);
            this.fn = fn;
        }

        final CompletableFuture<V> tryFire(int mode) {
            CompletableFuture<V> d;
            CompletableFuture<T> a;
            Object r;
            BiFunction<? super T, Throwable, ? extends V> f;
            if ((a = src) == null || (r = a.result) == null
                    || (d = dep) == null || (f = fn) == null
                    || !d.uniHandle(r, f, mode > 0 ? null : this))
                return null;
            src = null;
            dep = null;
            fn = null;
            return d.postFire(a, mode);
        }
    }
    @SuppressWarnings("serial")
    static final class UniExceptionally<T> extends UniCompletion<T, T> {
        Function<? super Throwable, ? extends T> fn;

        UniExceptionally(Executor executor, CompletableFuture<T> dep, CompletableFuture<T> src, Function<? super Throwable, ? extends T> fn) {
            super(executor, dep, src);
            this.fn = fn;
        }

        final CompletableFuture<T> tryFire(int mode) {
            CompletableFuture<T> d;
            CompletableFuture<T> a;
            Object r;
            Function<? super Throwable, ? extends T> f;
            if ((a = src) == null || (r = a.result) == null
                    || (d = dep) == null || (f = fn) == null
                    || !d.uniExceptionally(r, f, mode > 0 ? null : this))
                return null;
            src = null;
            dep = null;
            fn = null;
            return d.postFire(a, mode);
        }
    }
    @SuppressWarnings("serial")
    static final class UniComposeExceptionally<T> extends UniCompletion<T, T> {
        Function<Throwable, ? extends CompletionStage<T>> fn;

        UniComposeExceptionally(Executor executor, CompletableFuture<T> dep, CompletableFuture<T> src, Function<Throwable, ? extends CompletionStage<T>> fn) {
            super(executor, dep, src);
            this.fn = fn;
        }

        final CompletableFuture<T> tryFire(int mode) {
            CompletableFuture<T> d;
            CompletableFuture<T> a;
            Function<Throwable, ? extends CompletionStage<T>> f;
            Object r;
            Throwable x;
            if ((a = src) == null || (r = a.result) == null
                    || (d = dep) == null || (f = fn) == null)
                return null;
            if (d.result == null) {
                if ((r instanceof AltResult) &&
                        (x = ((AltResult) r).ex) != null) {
                    try {
                        if (mode <= 0 && !claim())
                            return null;
                        CompletableFuture<T> g = f.apply(x).toCompletableFuture();
                        if ((r = g.result) != null)
                            d.completeRelay(r);
                        else {
                            g.unipush(new UniRelay<T, T>(d, g));
                            if (d.result == null)
                                return null;
                        }
                    } catch (Throwable ex) {
                        d.completeThrowable(ex);
                    }
                } else
                    d.internalComplete(r);
            }
            src = null;
            dep = null;
            fn = null;
            return d.postFire(a, mode);
        }
    }
    @SuppressWarnings("serial")
    static final class UniRelay<U, T extends U> extends UniCompletion<T, U> {
        UniRelay(CompletableFuture<U> dep, CompletableFuture<T> src) {
            super(null, dep, src);
        }

        final CompletableFuture<U> tryFire(int mode) {
            CompletableFuture<U> d;
            CompletableFuture<T> a;
            Object r;
            if ((a = src) == null || (r = a.result) == null
                    || (d = dep) == null)
                return null;
            if (d.result == null)
                d.completeRelay(r);
            src = null;
            dep = null;
            return d.postFire(a, mode);
        }
    }
    @SuppressWarnings("serial")
    static final class UniCompose<T, V> extends UniCompletion<T, V> {
        Function<? super T, ? extends CompletionStage<V>> fn;

        UniCompose(Executor executor, CompletableFuture<V> dep,
                   CompletableFuture<T> src,
                   Function<? super T, ? extends CompletionStage<V>> fn) {
            super(executor, dep, src);
            this.fn = fn;
        }

        final CompletableFuture<V> tryFire(int mode) {
            CompletableFuture<V> d;
            CompletableFuture<T> a;
            Function<? super T, ? extends CompletionStage<V>> f;
            Object r;
            Throwable x;
            if ((a = src) == null || (r = a.result) == null
                    || (d = dep) == null || (f = fn) == null)
                return null;
            tryComplete:
            if (d.result == null) {
                if (r instanceof AltResult) {
                    if ((x = ((AltResult) r).ex) != null) {
                        d.completeThrowable(x, r);
                        break tryComplete;
                    }
                    r = null;
                }
                try {
                    if (mode <= 0 && !claim())
                        return null;
                    @SuppressWarnings("unchecked") T t = (T) r;
                    CompletableFuture<V> g = f.apply(t).toCompletableFuture();
                    if ((r = g.result) != null)
                        d.completeRelay(r);
                    else {
                        g.unipush(new UniRelay<V, V>(d, g));
                        if (d.result == null)
                            return null;
                    }
                } catch (Throwable ex) {
                    d.completeThrowable(ex);
                }
            }
            src = null;
            dep = null;
            fn = null;
            return d.postFire(a, mode);
        }
    }
    @SuppressWarnings("serial")
    abstract static class BiCompletion<T, U, V> extends UniCompletion<T, V> {
        CompletableFuture<U> snd; // second source for action

        BiCompletion(Executor executor, CompletableFuture<V> dep,
                     CompletableFuture<T> src, CompletableFuture<U> snd) {
            super(executor, dep, src);
            this.snd = snd;
        }
    }
    @SuppressWarnings("serial")
    static final class BiApply<T, U, V> extends BiCompletion<T, U, V> {
        BiFunction<? super T, ? super U, ? extends V> fn;

        BiApply(Executor executor, CompletableFuture<V> dep,
                CompletableFuture<T> src, CompletableFuture<U> snd,
                BiFunction<? super T, ? super U, ? extends V> fn) {
            super(executor, dep, src, snd);
            this.fn = fn;
        }

        final CompletableFuture<V> tryFire(int mode) {
            CompletableFuture<V> d;
            CompletableFuture<T> a;
            CompletableFuture<U> b;
            Object r, s;
            BiFunction<? super T, ? super U, ? extends V> f;
            if ((a = src) == null || (r = a.result) == null
                    || (b = snd) == null || (s = b.result) == null
                    || (d = dep) == null || (f = fn) == null
                    || !d.biApply(r, s, f, mode > 0 ? null : this))
                return null;
            src = null;
            snd = null;
            dep = null;
            fn = null;
            return d.postFire(a, b, mode);
        }
    }
    @SuppressWarnings("serial")
    static final class CoCompletion extends Completion {
        BiCompletion<?, ?, ?> base;

        CoCompletion(BiCompletion<?, ?, ?> base) {
            this.base = base;
        }

        final CompletableFuture<?> tryFire(int mode) {
            BiCompletion<?, ?, ?> c;
            CompletableFuture<?> d;
            if ((c = base) == null || (d = c.tryFire(mode)) == null)
                return null;
            base = null; // detach
            return d;
        }

        final boolean isLive() {
            BiCompletion<?, ?, ?> c;
            return (c = base) != null
                    // && c.isLive()
                    && c.dep != null;
        }
    }
    @SuppressWarnings("serial")
    static final class BiAccept<T, U> extends BiCompletion<T, U, Void> {
        BiConsumer<? super T, ? super U> fn;

        BiAccept(Executor executor, CompletableFuture<Void> dep,
                 CompletableFuture<T> src, CompletableFuture<U> snd,
                 BiConsumer<? super T, ? super U> fn) {
            super(executor, dep, src, snd);
            this.fn = fn;
        }

        final CompletableFuture<Void> tryFire(int mode) {
            CompletableFuture<Void> d;
            CompletableFuture<T> a;
            CompletableFuture<U> b;
            Object r, s;
            BiConsumer<? super T, ? super U> f;
            if ((a = src) == null || (r = a.result) == null
                    || (b = snd) == null || (s = b.result) == null
                    || (d = dep) == null || (f = fn) == null
                    || !d.biAccept(r, s, f, mode > 0 ? null : this))
                return null;
            src = null;
            snd = null;
            dep = null;
            fn = null;
            return d.postFire(a, b, mode);
        }
    }
    @SuppressWarnings("serial")
    static final class BiRun<T, U> extends BiCompletion<T, U, Void> {
        Runnable fn;

        BiRun(Executor executor, CompletableFuture<Void> dep,
              CompletableFuture<T> src, CompletableFuture<U> snd,
              Runnable fn) {
            super(executor, dep, src, snd);
            this.fn = fn;
        }

        final CompletableFuture<Void> tryFire(int mode) {
            CompletableFuture<Void> d;
            CompletableFuture<T> a;
            CompletableFuture<U> b;
            Object r, s;
            Runnable f;
            if ((a = src) == null || (r = a.result) == null
                    || (b = snd) == null || (s = b.result) == null
                    || (d = dep) == null || (f = fn) == null
                    || !d.biRun(r, s, f, mode > 0 ? null : this))
                return null;
            src = null;
            snd = null;
            dep = null;
            fn = null;
            return d.postFire(a, b, mode);
        }
    }
    @SuppressWarnings("serial")
    static final class BiRelay<T, U> extends BiCompletion<T, U, Void> { // for And
        BiRelay(CompletableFuture<Void> dep,
                CompletableFuture<T> src, CompletableFuture<U> snd) {
            super(null, dep, src, snd);
        }

        final CompletableFuture<Void> tryFire(int mode) {
            CompletableFuture<Void> d;
            CompletableFuture<T> a;
            CompletableFuture<U> b;
            Object r, s, z;
            Throwable x;
            if ((a = src) == null || (r = a.result) == null
                    || (b = snd) == null || (s = b.result) == null
                    || (d = dep) == null)
                return null;
            if (d.result == null) {
                if ((r instanceof AltResult
                        && (x = ((AltResult) (z = r)).ex) != null) ||
                        (s instanceof AltResult
                                && (x = ((AltResult) (z = s)).ex) != null))
                    d.completeThrowable(x, z);
                else
                    d.completeNull();
            }
            src = null;
            snd = null;
            dep = null;
            return d.postFire(a, b, mode);
        }
    }
    @SuppressWarnings("serial")
    static final class OrApply<T, U extends T, V> extends BiCompletion<T, U, V> {
        Function<? super T, ? extends V> fn;

        OrApply(Executor executor, CompletableFuture<V> dep,
                CompletableFuture<T> src, CompletableFuture<U> snd,
                Function<? super T, ? extends V> fn) {
            super(executor, dep, src, snd);
            this.fn = fn;
        }

        final CompletableFuture<V> tryFire(int mode) {
            CompletableFuture<V> d;
            CompletableFuture<? extends T> a, b;
            Object r;
            Throwable x;
            Function<? super T, ? extends V> f;
            if ((a = src) == null || (b = snd) == null
                    || ((r = a.result) == null && (r = b.result) == null)
                    || (d = dep) == null || (f = fn) == null)
                return null;
            tryComplete:
            if (d.result == null) {
                try {
                    if (mode <= 0 && !claim())
                        return null;
                    if (r instanceof AltResult) {
                        if ((x = ((AltResult) r).ex) != null) {
                            d.completeThrowable(x, r);
                            break tryComplete;
                        }
                        r = null;
                    }
                    @SuppressWarnings("unchecked") T t = (T) r;
                    d.completeValue(f.apply(t));
                } catch (Throwable ex) {
                    d.completeThrowable(ex);
                }
            }
            src = null;
            snd = null;
            dep = null;
            fn = null;
            return d.postFire(a, b, mode);
        }
    }
    @SuppressWarnings("serial")
    static final class OrAccept<T, U extends T> extends BiCompletion<T, U, Void> {
        Consumer<? super T> fn;

        OrAccept(Executor executor, CompletableFuture<Void> dep,
                 CompletableFuture<T> src, CompletableFuture<U> snd,
                 Consumer<? super T> fn) {
            super(executor, dep, src, snd);
            this.fn = fn;
        }

        final CompletableFuture<Void> tryFire(int mode) {
            CompletableFuture<Void> d;
            CompletableFuture<? extends T> a, b;
            Object r;
            Throwable x;
            Consumer<? super T> f;
            if ((a = src) == null || (b = snd) == null
                    || ((r = a.result) == null && (r = b.result) == null)
                    || (d = dep) == null || (f = fn) == null)
                return null;
            tryComplete:
            if (d.result == null) {
                try {
                    if (mode <= 0 && !claim())
                        return null;
                    if (r instanceof AltResult) {
                        if ((x = ((AltResult) r).ex) != null) {
                            d.completeThrowable(x, r);
                            break tryComplete;
                        }
                        r = null;
                    }
                    @SuppressWarnings("unchecked") T t = (T) r;
                    f.accept(t);
                    d.completeNull();
                } catch (Throwable ex) {
                    d.completeThrowable(ex);
                }
            }
            src = null;
            snd = null;
            dep = null;
            fn = null;
            return d.postFire(a, b, mode);
        }
    }
    @SuppressWarnings("serial")
    static final class OrRun<T, U> extends BiCompletion<T, U, Void> {
        Runnable fn;

        OrRun(Executor executor, CompletableFuture<Void> dep,
              CompletableFuture<T> src, CompletableFuture<U> snd,
              Runnable fn) {
            super(executor, dep, src, snd);
            this.fn = fn;
        }

        final CompletableFuture<Void> tryFire(int mode) {
            CompletableFuture<Void> d;
            CompletableFuture<?> a, b;
            Object r;
            Throwable x;
            Runnable f;
            if ((a = src) == null || (b = snd) == null
                    || ((r = a.result) == null && (r = b.result) == null)
                    || (d = dep) == null || (f = fn) == null)
                return null;
            if (d.result == null) {
                try {
                    if (mode <= 0 && !claim())
                        return null;
                    else if (r instanceof AltResult
                            && (x = ((AltResult) r).ex) != null)
                        d.completeThrowable(x, r);
                    else {
                        f.run();
                        d.completeNull();
                    }
                } catch (Throwable ex) {
                    d.completeThrowable(ex);
                }
            }
            src = null;
            snd = null;
            dep = null;
            fn = null;
            return d.postFire(a, b, mode);
        }
    }
    @SuppressWarnings("serial")
    static class AnyOf extends Completion {
        CompletableFuture<Object> dep;
        CompletableFuture<?> src;
        CompletableFuture<?>[] srcs;

        AnyOf(CompletableFuture<Object> dep, CompletableFuture<?> src,
              CompletableFuture<?>[] srcs) {
            this.dep = dep;
            this.src = src;
            this.srcs = srcs;
        }

        final CompletableFuture<Object> tryFire(int mode) {
            CompletableFuture<Object> d;
            CompletableFuture<?> a;
            CompletableFuture<?>[] as;
            Object r;
            if ((a = src) == null || (r = a.result) == null
                    || (d = dep) == null || (as = srcs) == null)
                return null;
            src = null;
            dep = null;
            srcs = null;
            if (d.completeRelay(r)) {
                for (CompletableFuture<?> b : as)
                    if (b != a)
                        b.cleanStack();
                if (mode < 0)
                    return d;
                else
                    d.postComplete();
            }
            return null;
        }

        final boolean isLive() {
            CompletableFuture<Object> d;
            return (d = dep) != null && d.result == null;
        }
    }
    @SuppressWarnings("serial")
    static final class AsyncSupply<T> extends ForkJoinTask<Void> implements Runnable, AsynchronousCompletionTask {
        CompletableFuture<T> dep;
        Supplier<? extends T> fn;

        AsyncSupply(CompletableFuture<T> dep, Supplier<? extends T> fn) {
            this.dep = dep;
            this.fn = fn;
        }

        public final Void getRawResult() {
            return null;
        }

        public final void setRawResult(Void v) {
        }

        public final boolean exec() {
            run();
            return false;
        }

        public void run() {
            CompletableFuture<T> d;
            Supplier<? extends T> f;
            if ((d = dep) != null && (f = fn) != null) {
                dep = null;
                fn = null;
                if (d.result == null) {
                    try {
                        d.completeValue(f.get());
                    } catch (Throwable ex) {
                        d.completeThrowable(ex);
                    }
                }
                d.postComplete();
            }
        }
    }
    @SuppressWarnings("serial")
    static final class AsyncRun extends ForkJoinTask<Void> implements Runnable, AsynchronousCompletionTask {
        CompletableFuture<Void> dep;
        Runnable fn;

        AsyncRun(CompletableFuture<Void> dep, Runnable fn) {
            this.dep = dep;
            this.fn = fn;
        }

        public final Void getRawResult() {
            return null;
        }

        public final void setRawResult(Void v) {
        }

        public final boolean exec() {
            run();
            return false;
        }

        public void run() {
            CompletableFuture<Void> d;
            Runnable f;
            if ((d = dep) != null && (f = fn) != null) {
                dep = null;
                fn = null;
                if (d.result == null) {
                    try {
                        f.run();
                        d.completeNull();
                    } catch (Throwable ex) {
                        d.completeThrowable(ex);
                    }
                }
                d.postComplete();
            }
        }
    }
    @SuppressWarnings("serial")
    static final class Signaller extends Completion implements ForkJoinPool.ManagedBlocker {
        long nanos;                    // remaining wait time if timed
        final long deadline;           // non-zero if timed
        final boolean interruptible;
        boolean interrupted;
        volatile Thread thread;

        Signaller(boolean interruptible, long nanos, long deadline) {
            this.thread = Thread.currentThread();
            this.interruptible = interruptible;
            this.nanos = nanos;
            this.deadline = deadline;
        }

        final CompletableFuture<?> tryFire(int ignore) {
            Thread w;
            if ((w = thread) != null) {
                thread = null;
                LockSupport.unpark(w);
            }
            return null;
        }

        public boolean isReleasable() {
            if (Thread.interrupted())
                interrupted = true;
            return ((interrupted && interruptible) ||
                    (deadline != 0L &&
                            (nanos <= 0L ||
                                    (nanos = deadline - System.nanoTime()) <= 0L)) ||
                    thread == null);
        }

        public boolean block() {
            while (!isReleasable()) {
                if (deadline == 0L)
                    LockSupport.park(this);
                else
                    LockSupport.parkNanos(this, nanos);
            }
            return true;
        }

        final boolean isLive() {
            return thread != null;
        }
    }
    static final class Delayer {
        static ScheduledFuture<?> delay(Runnable command, long delay,
                                        TimeUnit unit) {
            return delayer.schedule(command, delay, unit);
        }

        static final class DaemonThreadFactory implements ThreadFactory {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("CompletableFutureDelayScheduler");
                return t;
            }
        }

        static final ScheduledThreadPoolExecutor delayer;

        static {
            (delayer = new ScheduledThreadPoolExecutor(
                    1, new DaemonThreadFactory())).
                    setRemoveOnCancelPolicy(true);
        }
    }
    static final class DelayedExecutor implements Executor {
        final long delay;
        final TimeUnit unit;
        final Executor executor;

        DelayedExecutor(long delay, TimeUnit unit, Executor executor) {
            this.delay = delay;
            this.unit = unit;
            this.executor = executor;
        }

        public void execute(Runnable r) {
            Delayer.delay(new TaskSubmitter(executor, r), delay, unit);
        }
    }
    static final class TaskSubmitter implements Runnable {
        final Executor executor;
        final Runnable action;

        TaskSubmitter(Executor executor, Runnable action) {
            this.executor = executor;
            this.action = action;
        }

        public void run() {
            executor.execute(action);
        }
    }
    static final class Timeout implements Runnable {
        final CompletableFuture<?> f;

        Timeout(CompletableFuture<?> f) {
            this.f = f;
        }

        public void run() {
            if (f != null && !f.isDone())
                f.completeExceptionally(new TimeoutException());
        }
    }
    static final class DelayedCompleter<U> implements Runnable {
        final CompletableFuture<U> f;
        final U u;

        DelayedCompleter(CompletableFuture<U> f, U u) {
            this.f = f;
            this.u = u;
        }

        public void run() {
            if (f != null)
                f.complete(u);
        }
    }
    static final class Canceller implements BiConsumer<Object, Throwable> {
        final Future<?> f;

        Canceller(Future<?> f) {
            this.f = f;
        }

        public void accept(Object ignore, Throwable ex) {
            if (ex == null && f != null && !f.isDone())
                f.cancel(false);
        }
    }
    static final class MinimalStage<T> extends CompletableFuture<T> {
        MinimalStage() {
        }

        MinimalStage(Object r) {
            super(r);
        }

        @Override
        public <U> CompletableFuture<U> newIncompleteFuture() {
            return new MinimalStage<U>();
        }

        @Override
        public T get() {
            throw new UnsupportedOperationException();
        }

        @Override
        public T get(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public T getNow(T valueIfAbsent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public T join() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean complete(T value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean completeExceptionally(Throwable ex) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void obtrudeValue(T value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void obtrudeException(Throwable ex) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isDone() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCancelled() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCompletedExceptionally() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getNumberOfDependents() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<T> completeAsync
                (Supplier<? extends T> supplier, Executor executor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<T> completeAsync
                (Supplier<? extends T> supplier) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<T> orTimeout
                (long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<T> completeOnTimeout
                (T value, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<T> toCompletableFuture() {
            Object r;
            if ((r = result) != null)
                return new CompletableFuture<T>(encodeRelay(r));
            else {
                CompletableFuture<T> d = new CompletableFuture<>();
                unipush(new UniRelay<T, T>(d, this));
                return d;
            }
        }
    }


}
