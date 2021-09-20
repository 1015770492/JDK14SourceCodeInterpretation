package java.util.stream;

/**
 * @link 详细的csdn文章介绍：{https://yumbo.blog.csdn.net/article/details/120384948}
 * @author 诗水人间
 * @link 博客:{https://yumbo.blog.csdn.net/}
 * @link github:{https://github.com/1015770492}
 * @date 2021/9/19 22:58
 */
public class SkipDemo {

    public static void main(String[] args) {
        // 1-10，跳过前3个元素，限制元素为2个，故打印4，5
        IntStream.rangeClosed(1,10).skip(3).limit(2).forEach(System.out::println);
    }
}
