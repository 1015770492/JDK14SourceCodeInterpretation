package java.util;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;


public final class Optional<T> {

    private final T value;// 存储值的结构

    private static final Optional<?> EMPTY = new Optional<>(null);// 创建空容器常量对象EMPTY
    public static<T> Optional<T> empty() {
        @SuppressWarnings("unchecked")
        Optional<T> t = (Optional<T>) EMPTY;
        return t;
    }

    /**
     * 私有构造器，内部其它方法
     */
    private Optional(T value) {
        this.value = value;
    }

    /**
     * 将value存入Optional容器，如果传入的值为null 则会报空指针异常
     * 调用私有构造方法得到Optional实例
     */
    public static <T> Optional<T> of(T value) {
        return new Optional<>(Objects.requireNonNull(value));
    }

    /**
     * 于of作用相同，返回包裹value的Optional容器实例，但不会报空指针异常
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> ofNullable(T value) {
        return value == null ? (Optional<T>) EMPTY
                             : new Optional<>(value);
    }

    /**
     * 返回容器中的实例对象，会抛异常，需要注意，值不能为null
     */
    public T get() {
        if (value == null) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    /**
     * 判断值是否存在，存在返回true，不存在返回false
     */
    public boolean isPresent() {
        return value != null;
    }

    /**
     * 判断值是否不存在，不存在返回true、存在则返回false
     * @since   11
     */
    public boolean isEmpty() {
        return value == null;
    }

    /**
     * 传入的是一个消费型接口如果值存在则进行其它操作
     */
    public void ifPresent(Consumer<? super T> action) {
        if (value != null) {
            action.accept(value);
        }
    }

    /**
     * Consumer和Runnable接口
     * 如果存在值，则使用该值执行给定的操作，否则执行其它操作
     * @since 9
     */
    public void ifPresentOrElse(Consumer<? super T> action, Runnable emptyAction) {
        if (value != null) {
            action.accept(value);
        } else {
            emptyAction.run();
        }
    }

    /**
     * 一个Optional描述此的值Optional ，
     * 如果一个值存在并且该值给定的谓词相匹配，否则一个空Optional
     */
    public Optional<T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        if (!isPresent()) {
            return this;
        } else {
            return predicate.test(value) ? this : empty();
        }
    }

    /**
     * 一个Optional描述应用映射函数此的值的结果Optional ，
     * 如果一个值存在，否则一个空Optional
     */
    public <U> Optional<U> map(Function<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper);
        if (!isPresent()) {
            return empty();
        } else {
            return Optional.ofNullable(mapper.apply(value));
        }
    }

    /**
     * 如果值存在将它转换为另外一个容器，否则返回一个空容器，泛型T 变成了 泛型U
     */
    public <U> Optional<U> flatMap(Function<? super T, ? extends Optional<? extends U>> mapper) {
        Objects.requireNonNull(mapper);
        if (!isPresent()) {
            return empty();
        } else {
            @SuppressWarnings("unchecked")
            Optional<U> r = (Optional<U>) mapper.apply(value);
            return Objects.requireNonNull(r);
        }
    }

    /**
     * 如果值存在则返回当前容器，否则返回Supplier接口产生的一个容器
     * @since 9
     */
    public Optional<T> or(Supplier<? extends Optional<? extends T>> supplier) {
        Objects.requireNonNull(supplier);
        if (isPresent()) {
            return this;
        } else {
            @SuppressWarnings("unchecked")
            Optional<T> r = (Optional<T>) supplier.get();
            return Objects.requireNonNull(r);
        }
    }

    /**
     * 如果存在值则返回stream流，否则返回空流
     * @since 9
     */
    public Stream<T> stream() {
        if (!isPresent()) {
            return Stream.empty();
        } else {
            return Stream.of(value);
        }
    }

    /**
     * 如果存在值则返回原值，否则返回一个自定义的值（传对象）
     */
    public T orElse(T other) {
        return value != null ? value : other;
    }

    /**
     * 如果存在值，则返回该值，否则返回一个自定义的值（传接口）
     */
    public T orElseGet(Supplier<? extends T> supplier) {
        return value != null ? value : supplier.get();
    }

    /**
     * 如果存在值，则返回该值，否则抛出NoSuchElementException
     * @since 10
     */
    public T orElseThrow() {
        if (value == null) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    /**
     * 如果存在值，则返回该值，否则由提供函数抛出异常
     */
    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (value != null) {
            return value;
        } else {
            throw exceptionSupplier.get();
        }
    }

    /**
     * 判断两个Optional容器是否相等
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Optional)) {
            return false;
        }

        Optional<?> other = (Optional<?>) obj;
        return Objects.equals(value, other.value);
    }


    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }


    @Override
    public String toString() {
        return value != null
            ? String.format("Optional[%s]", value)
            : "Optional.empty";
    }
}
