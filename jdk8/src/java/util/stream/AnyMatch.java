package java.util.stream;

import java.util.Arrays;

/**
 * @link 详细的csdn文章介绍：{https://yumbo.blog.csdn.net/article/details/120384948}
 * @author 诗水人间
 * @link 博客:{https://yumbo.blog.csdn.net/}
 * @link github:{https://github.com/1015770492}
 * @date 2021/9/20 0:48
 */
public class AnyMatch {

    public static void main(String[] args) {
        final boolean b = Arrays.asList(1, 2, 3, 4, 5).stream().anyMatch(x -> x > 6);
        final boolean b2 = Arrays.asList(1, 2, 3, 4, 5).stream().anyMatch(x -> x > 4);
        System.out.println(b);// false
        System.out.println(b2);// true
        final boolean b3 = Arrays.asList(1, 2, 3, 4, 5).stream().allMatch(x -> x > 0);
        final boolean b4 = Arrays.asList(1, 2, 3, 4, 5).stream().allMatch(x -> x > 5);
        System.out.println(b3);// true
        System.out.println(b4);// false
        final boolean b5 = Arrays.asList(1, 2, 3, 4, 5).stream().noneMatch(x -> x > 5);
        System.out.println(b5);// true
    }
}
