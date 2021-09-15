package java.util;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

public interface Spliterator<T> {
    /**
     * If a remaining element exists, performs the given action on it,
     * returning {@code true}; else returns {@code false}.  If this
     * Spliterator is {@link #ORDERED} the action is performed on the
     * next element in encounter order.  Exceptions thrown by the
     * action are relayed to the caller.
     *
     * @param action The action
     * @return {@code false} if no remaining elements existed
     * upon entry to this method, else {@code true}.
     * @throws NullPointerException if the specified action is null
     */
    boolean tryAdvance(Consumer<? super T> action);

    /**
     * Performs the given action for each remaining element, sequentially in
     * the current thread, until all elements have been processed or the action
     * throws an exception.  If this Spliterator is {@link #ORDERED}, actions
     * are performed in encounter order.  Exceptions thrown by the action
     * are relayed to the caller.
     *
     * @implSpec
     * The default implementation repeatedly invokes {@link #tryAdvance} until
     * it returns {@code false}.  It should be overridden whenever possible.
     *
     * @param action The action
     * @throws NullPointerException if the specified action is null
     */
    default void forEachRemaining(Consumer<? super T> action) {
        do { } while (tryAdvance(action));
    }

    /**
     * If this spliterator can be partitioned, returns a Spliterator
     * covering elements, that will, upon return from this method, not
     * be covered by this Spliterator.
     *
     * <p>If this Spliterator is {@link #ORDERED}, the returned Spliterator
     * must cover a strict prefix of the elements.
     *
     * <p>Unless this Spliterator covers an infinite number of elements,
     * repeated calls to {@code trySplit()} must eventually return {@code null}.
     * Upon non-null return:
     * <ul>
     * <li>the value reported for {@code estimateSize()} before splitting,
     * must, after splitting, be greater than or equal to {@code estimateSize()}
     * for this and the returned Spliterator; and</li>
     * <li>if this Spliterator is {@code SUBSIZED}, then {@code estimateSize()}
     * for this spliterator before splitting must be equal to the sum of
     * {@code estimateSize()} for this and the returned Spliterator after
     * splitting.</li>
     * </ul>
     *
     * <p>This method may return {@code null} for any reason,
     * including emptiness, inability to split after traversal has
     * commenced, data structure constraints, and efficiency
     * considerations.
     *
     * @apiNote
     * An ideal {@code trySplit} method efficiently (without
     * traversal) divides its elements exactly in half, allowing
     * balanced parallel computation.  Many departures from this ideal
     * remain highly effective; for example, only approximately
     * splitting an approximately balanced tree, or for a tree in
     * which leaf nodes may contain either one or two elements,
     * failing to further split these nodes.  However, large
     * deviations in balance and/or overly inefficient {@code
     * trySplit} mechanics typically result in poor parallel
     * performance.
     *
     * @return a {@code Spliterator} covering some portion of the
     * elements, or {@code null} if this spliterator cannot be split
     */
    Spliterator<T> trySplit();

    /**
     * Returns an estimate of the number of elements that would be
     * encountered by a {@link #forEachRemaining} traversal, or returns {@link
     * Long#MAX_VALUE} if infinite, unknown, or too expensive to compute.
     *
     * <p>If this Spliterator is {@link #SIZED} and has not yet been partially
     * traversed or split, or this Spliterator is {@link #SUBSIZED} and has
     * not yet been partially traversed, this estimate must be an accurate
     * count of elements that would be encountered by a complete traversal.
     * Otherwise, this estimate may be arbitrarily inaccurate, but must decrease
     * as specified across invocations of {@link #trySplit}.
     *
     * @apiNote
     * Even an inexact estimate is often useful and inexpensive to compute.
     * For example, a sub-spliterator of an approximately balanced binary tree
     * may return a value that estimates the number of elements to be half of
     * that of its parent; if the root Spliterator does not maintain an
     * accurate count, it could estimate size to be the power of two
     * corresponding to its maximum depth.
     *
     * @return the estimated size, or {@code Long.MAX_VALUE} if infinite,
     *         unknown, or too expensive to compute.
     */
    long estimateSize();

    /**
     * Convenience method that returns {@link #estimateSize()} if this
     * Spliterator is {@link #SIZED}, else {@code -1}.
     * @implSpec
     * The default implementation returns the result of {@code estimateSize()}
     * if the Spliterator reports a characteristic of {@code SIZED}, and
     * {@code -1} otherwise.
     *
     * @return the exact size, if known, else {@code -1}.
     */
    default long getExactSizeIfKnown() {
        return (characteristics() & SIZED) == 0 ? -1L : estimateSize();
    }

    /**
     * Returns a set of characteristics of this Spliterator and its
     * elements. The result is represented as ORed values from {@link
     * #ORDERED}, {@link #DISTINCT}, {@link #SORTED}, {@link #SIZED},
     * {@link #NONNULL}, {@link #IMMUTABLE}, {@link #CONCURRENT},
     * {@link #SUBSIZED}.  Repeated calls to {@code characteristics()} on
     * a given spliterator, prior to or in-between calls to {@code trySplit},
     * should always return the same result.
     *
     * <p>If a Spliterator reports an inconsistent set of
     * characteristics (either those returned from a single invocation
     * or across multiple invocations), no guarantees can be made
     * about any computation using this Spliterator.
     *
     * @apiNote The characteristics of a given spliterator before splitting
     * may differ from the characteristics after splitting.  For specific
     * examples see the characteristic values {@link #SIZED}, {@link #SUBSIZED}
     * and {@link #CONCURRENT}.
     *
     * @return a representation of characteristics
     */
    int characteristics();

    /**
     * Returns {@code true} if this Spliterator's {@link
     * #characteristics} contain all of the given characteristics.
     *
     * @implSpec
     * The default implementation returns true if the corresponding bits
     * of the given characteristics are set.
     *
     * @param characteristics the characteristics to check for
     * @return {@code true} if all the specified characteristics are present,
     * else {@code false}
     */
    default boolean hasCharacteristics(int characteristics) {
        return (characteristics() & characteristics) == characteristics;
    }

    /**
     * If this Spliterator's source is {@link #SORTED} by a {@link Comparator},
     * returns that {@code Comparator}. If the source is {@code SORTED} in
     * {@linkplain Comparable natural order}, returns {@code null}.  Otherwise,
     * if the source is not {@code SORTED}, throws {@link IllegalStateException}.
     *
     * @implSpec
     * The default implementation always throws {@link IllegalStateException}.
     *
     * @return a Comparator, or {@code null} if the elements are sorted in the
     * natural order.
     * @throws IllegalStateException if the spliterator does not report
     *         a characteristic of {@code SORTED}.
     */
    default Comparator<? super T> getComparator() {
        throw new IllegalStateException();
    }

    /**
     * Characteristic value signifying that an encounter order is defined for
     * elements. If so, this Spliterator guarantees that method
     * {@link #trySplit} splits a strict prefix of elements, that method
     * {@link #tryAdvance} steps by one element in prefix order, and that
     * {@link #forEachRemaining} performs actions in encounter order.
     *
     * <p>A {@link Collection} has an encounter order if the corresponding
     * {@link Collection#iterator} documents an order. If so, the encounter
     * order is the same as the documented order. Otherwise, a collection does
     * not have an encounter order.
     *
     * @apiNote Encounter order is guaranteed to be ascending index order for
     * any {@link List}. But no order is guaranteed for hash-based collections
     * such as {@link HashSet}. Clients of a Spliterator that reports
     * {@code ORDERED} are expected to preserve ordering constraints in
     * non-commutative parallel computations.
     */
    public static final int ORDERED    = 0x00000010;

    /**
     * Characteristic value signifying that, for each pair of
     * encountered elements {@code x, y}, {@code !x.equals(y)}. This
     * applies for example, to a Spliterator based on a {@link Set}.
     */
    public static final int DISTINCT   = 0x00000001;

    /**
     * Characteristic value signifying that encounter order follows a defined
     * sort order. If so, method {@link #getComparator()} returns the associated
     * Comparator, or {@code null} if all elements are {@link Comparable} and
     * are sorted by their natural ordering.
     *
     * <p>A Spliterator that reports {@code SORTED} must also report
     * {@code ORDERED}.
     *
     * @apiNote The spliterators for {@code Collection} classes in the JDK that
     * implement {@link NavigableSet} or {@link SortedSet} report {@code SORTED}.
     */
    public static final int SORTED     = 0x00000004;

    /**
     * Characteristic value signifying that the value returned from
     * {@code estimateSize()} prior to traversal or splitting represents a
     * finite size that, in the absence of structural source modification,
     * represents an exact count of the number of elements that would be
     * encountered by a complete traversal.
     *
     * @apiNote Most Spliterators for Collections, that cover all elements of a
     * {@code Collection} report this characteristic. Sub-spliterators, such as
     * those for {@link HashSet}, that cover a sub-set of elements and
     * approximate their reported size do not.
     */
    public static final int SIZED      = 0x00000040;

    /**
     * Characteristic value signifying that the source guarantees that
     * encountered elements will not be {@code null}. (This applies,
     * for example, to most concurrent collections, queues, and maps.)
     */
    public static final int NONNULL    = 0x00000100;

    /**
     * Characteristic value signifying that the element source cannot be
     * structurally modified; that is, elements cannot be added, replaced, or
     * removed, so such changes cannot occur during traversal. A Spliterator
     * that does not report {@code IMMUTABLE} or {@code CONCURRENT} is expected
     * to have a documented policy (for example throwing
     * {@link ConcurrentModificationException}) concerning structural
     * interference detected during traversal.
     */
    public static final int IMMUTABLE  = 0x00000400;

    /**
     * Characteristic value signifying that the element source may be safely
     * concurrently modified (allowing additions, replacements, and/or removals)
     * by multiple threads without external synchronization. If so, the
     * Spliterator is expected to have a documented policy concerning the impact
     * of modifications during traversal.
     *
     * <p>A top-level Spliterator should not report both {@code CONCURRENT} and
     * {@code SIZED}, since the finite size, if known, may change if the source
     * is concurrently modified during traversal. Such a Spliterator is
     * inconsistent and no guarantees can be made about any computation using
     * that Spliterator. Sub-spliterators may report {@code SIZED} if the
     * sub-split size is known and additions or removals to the source are not
     * reflected when traversing.
     *
     * <p>A top-level Spliterator should not report both {@code CONCURRENT} and
     * {@code IMMUTABLE}, since they are mutually exclusive. Such a Spliterator
     * is inconsistent and no guarantees can be made about any computation using
     * that Spliterator. Sub-spliterators may report {@code IMMUTABLE} if
     * additions or removals to the source are not reflected when traversing.
     *
     * @apiNote Most concurrent collections maintain a consistency policy
     * guaranteeing accuracy with respect to elements present at the point of
     * Spliterator construction, but possibly not reflecting subsequent
     * additions or removals.
     */
    public static final int CONCURRENT = 0x00001000;

    /**
     * Characteristic value signifying that all Spliterators resulting from
     * {@code trySplit()} will be both {@link #SIZED} and {@link #SUBSIZED}.
     * (This means that all child Spliterators, whether direct or indirect, will
     * be {@code SIZED}.)
     *
     * <p>A Spliterator that does not report {@code SIZED} as required by
     * {@code SUBSIZED} is inconsistent and no guarantees can be made about any
     * computation using that Spliterator.
     *
     * @apiNote Some spliterators, such as the top-level spliterator for an
     * approximately balanced binary tree, will report {@code SIZED} but not
     * {@code SUBSIZED}, since it is common to know the size of the entire tree
     * but not the exact sizes of subtrees.
     */
    public static final int SUBSIZED = 0x00004000;

    /**
     * A Spliterator specialized for primitive values.
     *
     * @param <T> the type of elements returned by this Spliterator.  The
     * type must be a wrapper type for a primitive type, such as {@code Integer}
     * for the primitive {@code int} type.
     * @param <T_CONS> the type of primitive consumer.  The type must be a
     * primitive specialization of {@link java.util.function.Consumer} for
     * {@code T}, such as {@link java.util.function.IntConsumer} for
     * {@code Integer}.
     * @param <T_SPLITR> the type of primitive Spliterator.  The type must be
     * a primitive specialization of Spliterator for {@code T}, such as
     * {@link Spliterator.OfInt} for {@code Integer}.
     *
     * @see Spliterator.OfInt
     * @see Spliterator.OfLong
     * @see Spliterator.OfDouble
     * @since 1.8
     */
    public interface OfPrimitive<T, T_CONS, T_SPLITR extends Spliterator.OfPrimitive<T, T_CONS, T_SPLITR>>
            extends Spliterator<T> {
        @Override
        T_SPLITR trySplit();

        /**
         * If a remaining element exists, performs the given action on it,
         * returning {@code true}; else returns {@code false}.  If this
         * Spliterator is {@link #ORDERED} the action is performed on the
         * next element in encounter order.  Exceptions thrown by the
         * action are relayed to the caller.
         *
         * @param action The action
         * @return {@code false} if no remaining elements existed
         * upon entry to this method, else {@code true}.
         * @throws NullPointerException if the specified action is null
         */
        @SuppressWarnings("overloads")
        boolean tryAdvance(T_CONS action);

        /**
         * Performs the given action for each remaining element, sequentially in
         * the current thread, until all elements have been processed or the
         * action throws an exception.  If this Spliterator is {@link #ORDERED},
         * actions are performed in encounter order.  Exceptions thrown by the
         * action are relayed to the caller.
         *
         * @implSpec
         * The default implementation repeatedly invokes {@link #tryAdvance}
         * until it returns {@code false}.  It should be overridden whenever
         * possible.
         *
         * @param action The action
         * @throws NullPointerException if the specified action is null
         */
        @SuppressWarnings("overloads")
        default void forEachRemaining(T_CONS action) {
            do { } while (tryAdvance(action));
        }
    }

    /**
     * A Spliterator specialized for {@code int} values.
     * @since 1.8
     */
    public interface OfInt extends OfPrimitive<Integer, IntConsumer, OfInt> {

        @Override
        OfInt trySplit();

        @Override
        boolean tryAdvance(IntConsumer action);

        @Override
        default void forEachRemaining(IntConsumer action) {
            do { } while (tryAdvance(action));
        }

        /**
         * {@inheritDoc}
         * @implSpec
         * If the action is an instance of {@code IntConsumer} then it is cast
         * to {@code IntConsumer} and passed to
         * {@link #tryAdvance(java.util.function.IntConsumer)}; otherwise
         * the action is adapted to an instance of {@code IntConsumer}, by
         * boxing the argument of {@code IntConsumer}, and then passed to
         * {@link #tryAdvance(java.util.function.IntConsumer)}.
         */
        @Override
        default boolean tryAdvance(Consumer<? super Integer> action) {
            if (action instanceof IntConsumer) {
                return tryAdvance((IntConsumer) action);
            }
            else {
                if (Tripwire.ENABLED)
                    Tripwire.trip(getClass(),
                                  "{0} calling Spliterator.OfInt.tryAdvance((IntConsumer) action::accept)");
                return tryAdvance((IntConsumer) action::accept);
            }
        }

        /**
         * {@inheritDoc}
         * @implSpec
         * If the action is an instance of {@code IntConsumer} then it is cast
         * to {@code IntConsumer} and passed to
         * {@link #forEachRemaining(java.util.function.IntConsumer)}; otherwise
         * the action is adapted to an instance of {@code IntConsumer}, by
         * boxing the argument of {@code IntConsumer}, and then passed to
         * {@link #forEachRemaining(java.util.function.IntConsumer)}.
         */
        @Override
        default void forEachRemaining(Consumer<? super Integer> action) {
            if (action instanceof IntConsumer) {
                forEachRemaining((IntConsumer) action);
            }
            else {
                if (Tripwire.ENABLED)
                    Tripwire.trip(getClass(),
                                  "{0} calling Spliterator.OfInt.forEachRemaining((IntConsumer) action::accept)");
                forEachRemaining((IntConsumer) action::accept);
            }
        }
    }

    /**
     * A Spliterator specialized for {@code long} values.
     * @since 1.8
     */
    public interface OfLong extends OfPrimitive<Long, LongConsumer, OfLong> {

        @Override
        OfLong trySplit();

        @Override
        boolean tryAdvance(LongConsumer action);

        @Override
        default void forEachRemaining(LongConsumer action) {
            do { } while (tryAdvance(action));
        }

        /**
         * {@inheritDoc}
         * @implSpec
         * If the action is an instance of {@code LongConsumer} then it is cast
         * to {@code LongConsumer} and passed to
         * {@link #tryAdvance(java.util.function.LongConsumer)}; otherwise
         * the action is adapted to an instance of {@code LongConsumer}, by
         * boxing the argument of {@code LongConsumer}, and then passed to
         * {@link #tryAdvance(java.util.function.LongConsumer)}.
         */
        @Override
        default boolean tryAdvance(Consumer<? super Long> action) {
            if (action instanceof LongConsumer) {
                return tryAdvance((LongConsumer) action);
            }
            else {
                if (Tripwire.ENABLED)
                    Tripwire.trip(getClass(),
                                  "{0} calling Spliterator.OfLong.tryAdvance((LongConsumer) action::accept)");
                return tryAdvance((LongConsumer) action::accept);
            }
        }

        /**
         * {@inheritDoc}
         * @implSpec
         * If the action is an instance of {@code LongConsumer} then it is cast
         * to {@code LongConsumer} and passed to
         * {@link #forEachRemaining(java.util.function.LongConsumer)}; otherwise
         * the action is adapted to an instance of {@code LongConsumer}, by
         * boxing the argument of {@code LongConsumer}, and then passed to
         * {@link #forEachRemaining(java.util.function.LongConsumer)}.
         */
        @Override
        default void forEachRemaining(Consumer<? super Long> action) {
            if (action instanceof LongConsumer) {
                forEachRemaining((LongConsumer) action);
            }
            else {
                if (Tripwire.ENABLED)
                    Tripwire.trip(getClass(),
                                  "{0} calling Spliterator.OfLong.forEachRemaining((LongConsumer) action::accept)");
                forEachRemaining((LongConsumer) action::accept);
            }
        }
    }

    /**
     * A Spliterator specialized for {@code double} values.
     * @since 1.8
     */
    public interface OfDouble extends OfPrimitive<Double, DoubleConsumer, OfDouble> {

        @Override
        OfDouble trySplit();

        @Override
        boolean tryAdvance(DoubleConsumer action);

        @Override
        default void forEachRemaining(DoubleConsumer action) {
            do { } while (tryAdvance(action));
        }

        /**
         * {@inheritDoc}
         * @implSpec
         * If the action is an instance of {@code DoubleConsumer} then it is
         * cast to {@code DoubleConsumer} and passed to
         * {@link #tryAdvance(java.util.function.DoubleConsumer)}; otherwise
         * the action is adapted to an instance of {@code DoubleConsumer}, by
         * boxing the argument of {@code DoubleConsumer}, and then passed to
         * {@link #tryAdvance(java.util.function.DoubleConsumer)}.
         */
        @Override
        default boolean tryAdvance(Consumer<? super Double> action) {
            if (action instanceof DoubleConsumer) {
                return tryAdvance((DoubleConsumer) action);
            }
            else {
                if (Tripwire.ENABLED)
                    Tripwire.trip(getClass(),
                                  "{0} calling Spliterator.OfDouble.tryAdvance((DoubleConsumer) action::accept)");
                return tryAdvance((DoubleConsumer) action::accept);
            }
        }

        /**
         * {@inheritDoc}
         * @implSpec
         * If the action is an instance of {@code DoubleConsumer} then it is
         * cast to {@code DoubleConsumer} and passed to
         * {@link #forEachRemaining(java.util.function.DoubleConsumer)};
         * otherwise the action is adapted to an instance of
         * {@code DoubleConsumer}, by boxing the argument of
         * {@code DoubleConsumer}, and then passed to
         * {@link #forEachRemaining(java.util.function.DoubleConsumer)}.
         */
        @Override
        default void forEachRemaining(Consumer<? super Double> action) {
            if (action instanceof DoubleConsumer) {
                forEachRemaining((DoubleConsumer) action);
            }
            else {
                if (Tripwire.ENABLED)
                    Tripwire.trip(getClass(),
                                  "{0} calling Spliterator.OfDouble.forEachRemaining((DoubleConsumer) action::accept)");
                forEachRemaining((DoubleConsumer) action::accept);
            }
        }
    }
}
