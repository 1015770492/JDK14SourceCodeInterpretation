package java.util.stream;

import java.util.Arrays;

/**
 * @link 详细的csdn文章介绍：{https://yumbo.blog.csdn.net/article/details/120384948}
 * @author 诗水人间
 * @link 博客:{https://yumbo.blog.csdn.net/}
 * @link github:{https://github.com/1015770492}
 * @date 2021/9/19 21:47
 */
public class MapToNumDemo {

    public static void main(String[] args) {
        // 将字符串类型的数转换为int
        Arrays.asList("1","2","3").stream().mapToInt(Integer::parseInt).forEach(System.out::println);
        // 将字符串类型的数转换为long
        Arrays.asList("1","2","3").stream().mapToLong(Long::parseLong).forEach(System.out::println);
        // 将字符串类型的数转换为double
        Arrays.asList("1","2","3").stream().mapToDouble(Double::parseDouble).forEach(System.out::println);

    }
}
