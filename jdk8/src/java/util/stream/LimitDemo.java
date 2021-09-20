package java.util.stream;

/**
 * @link 详细的csdn文章介绍：{https://yumbo.blog.csdn.net/article/details/120384948}
 * @author 诗水人间
 * @link 博客:{https://yumbo.blog.csdn.net/}
 * @link github:{https://github.com/1015770492}
 * @date 2021/9/19 22:54
 */
public class LimitDemo {

    public static void main(String[] args) {
        // 打印1-10
        IntStream.rangeClosed(1,10).forEach(System.out::println);
        // 限制流元素个数为3个，打印1，2，3
        IntStream.rangeClosed(1,10).limit(3).forEach(System.out::println);

    }
}
