package java.util.stream;

import java.util.Arrays;

/**
 * @link 详细的csdn文章介绍：{https://yumbo.blog.csdn.net/article/details/120384948}
 * @author 诗水人间
 * @link 博客:{https://yumbo.blog.csdn.net/}
 * @link github:{https://github.com/1015770492}
 * @date 2021/9/19 22:12
 */
public class DistinctDemo {

    public static void main(String[] args) {
        // 打印 100，101，103，去除的底层原来就是调用对象的equals方法，实际上底层就是用一个Set保存所有元素，利用set自动去重
        Arrays.asList("100","101","100","103").stream().distinct().forEach(System.out::println);
    }
}
