package java.util.stream;

import java.util.Arrays;

/**
 * @link 详细的csdn文章介绍：{https://yumbo.blog.csdn.net/article/details/120384948}
 * @author 诗水人间
 * @link 博客:{https://yumbo.blog.csdn.net/}
 * @link github:{https://github.com/1015770492}
 * @date 2021/9/19 21:41
 */
public class MapDemo {

    public static void main(String[] args) {
        // map映射，打印A1,A2,A3,A4,A5
        Arrays.asList(1,2,3,4,5).stream().map(x->"A"+x).forEach(System.out::println);
    }
}
