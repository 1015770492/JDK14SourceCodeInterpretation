package java.util.stream;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @link 详细的csdn文章介绍：{https://yumbo.blog.csdn.net/article/details/120384948}
 * @author 诗水人间
 * @link 博客:{https://yumbo.blog.csdn.net/}
 * @link github:{https://github.com/1015770492}
 * @date 2021/9/20 0:06
 */
public class CollectDemo {

    public static void main(String[] args) {

        ArrayList<Object> collect;
        collect = Arrays.asList(
                User.builder().age(1).build(),
                User.builder().age(2).build(),
                User.builder().age(1).build(),
                User.builder().age(2).build(),
                User.builder().age(5).build()
        ).stream().collect(ArrayList::new, ArrayList::add, (x, y) -> {
        });
        // 单线程进行收集
        System.out.println(collect);

        // 多线程下进行收集，会发现没有收集所有元素
        collect = Arrays.asList(
                User.builder().age(1).build(),
                User.builder().age(2).build(),
                User.builder().age(1).build(),
                User.builder().age(2).build(),
                User.builder().age(5).build()
        ).stream().parallel().collect(ArrayList::new, ArrayList::add, (x, y) -> {
        });
        System.out.println(collect);

        // 正确的并发收集
        collect = Arrays.asList(
                User.builder().age(1).build(),
                User.builder().age(2).build(),
                User.builder().age(1).build(),
                User.builder().age(2).build(),
                User.builder().age(5).build()
        ).stream().parallel().collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        System.out.println(collect);

    }
}
