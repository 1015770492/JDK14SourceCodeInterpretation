package java.util.stream;

import java.util.Arrays;
import java.util.List;

/**
 * @link 详细的csdn文章介绍：{https://yumbo.blog.csdn.net/article/details/120384948}
 * @author 诗水人间
 * @link 博客:{https://yumbo.blog.csdn.net/}
 * @link github:{https://github.com/1015770492}
 * @date 2021/9/19 21:51
 */
public class FlatMapDemo {

    public static void main(String[] args) {
        final List<List<String>> lists = Arrays.asList(
                Arrays.asList("Hello World !!!", "I am Tom", "How are you doing?"),
                Arrays.asList("1", "2", "3"),
                Arrays.asList("你好世界！！！", "我是tom", "你正在做什么？")
        );
        String result="";
        // flatMap需要返回的上一个Stream类型的
        lists.stream().flatMap(x->x.stream()).forEach(System.out::println);
        lists.stream().map(x->x.stream()).forEach(System.out::println);
    }
}
