package java.util.stream;

import java.util.Arrays;
import java.util.Comparator;

/**
 * @link 详细的csdn文章介绍：{https://yumbo.blog.csdn.net/article/details/120384948}
 * @author 诗水人间
 * @link 博客:{https://yumbo.blog.csdn.net/}
 * @link github:{https://github.com/1015770492}
 * @date 2021/9/19 22:22
 */
public class SortedDemo {

    public static void main(String[] args) {
        // 打印 13，15，20，23，50
        Arrays.asList(20, 15, 13, 23, 50).stream().sorted().forEach(System.out::println);
        // 如果自定义对象可以传入一个比较其
        Arrays.asList(21, 16, 14, 24, 51).stream().sorted(Integer::compareTo).forEach(System.out::println);
        // 不可以比较的可以传入一个Comparator接口
        Arrays.asList("22", "17", "15", "25", "52").stream().sorted((x, y) -> {
            return Integer.parseInt(x) - Integer.parseInt(y);
        }).forEach(System.out::println);
        // 不使用lambda表达式可以这样写
        Arrays.asList("22", "17", "15", "25", "52").stream().sorted(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return Integer.parseInt(o1) - Integer.parseInt(o2);
            }
        }).forEach(System.out::println);

    }
}
