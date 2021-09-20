package java.util.stream;

import java.util.Arrays;

/**
 * @link 详细的csdn文章介绍：{https://yumbo.blog.csdn.net/article/details/120384948}
 * @author 诗水人间
 * @link 博客:{https://yumbo.blog.csdn.net/}
 * @link github:{https://github.com/1015770492}
 * @date 2021/9/19 23:05
 */
public class ReduceDemo {

    public static void main(String[] args) throws Exception {

        // 一定要是并行流才会触发后面的
        final Integer reduce = Arrays.asList("1", "2", "3", "4").stream().parallel().reduce(0, (u, v) -> {
            System.out.println("第一个接口 ");
            System.out.println(u + "u---v" + v);
            return Integer.parseInt(v);
        }, (x, y) -> {
            System.out.println("第二个接口 ");
            System.out.println(x + "x---y" + y);
            return x+y;
        });
        System.out.println(reduce);
    }
}
