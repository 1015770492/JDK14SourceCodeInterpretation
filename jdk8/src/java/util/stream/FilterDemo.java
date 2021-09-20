package java.util.stream;

import java.util.Arrays;

/**
 * @link 详细的csdn文章介绍：{https://yumbo.blog.csdn.net/article/details/120384948}
 * @author 诗水人间
 * @link 博客:{https://yumbo.blog.csdn.net/}
 * @link github:{https://github.com/1015770492}
 * @date 2021/9/19 21:32
 */
public class FilterDemo {

    public static void main(String[] args) {
        // 保留符合条件的值,打印2，4
        Arrays.asList(1, 2, 3, 4, 5).stream().map(x -> x % 2 == 0).forEach(System.out::println);
    }
}
