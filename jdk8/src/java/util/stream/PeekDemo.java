package java.util.stream;

import lombok.Builder;
import lombok.Data;

import java.util.Arrays;

/**
 * @link 详细的csdn文章介绍：{https://yumbo.blog.csdn.net/article/details/120384948}
 * @author 诗水人间
 * @link 博客:{https://yumbo.blog.csdn.net/}
 * @link github:{https://github.com/1015770492}
 * @date 2021/9/19 22:30
 */
@Data
@Builder
class User {
    int age;
}

public class PeekDemo {

    public static void main(String[] args) {
        Arrays.asList(
                User.builder().age(1).build(),
                User.builder().age(2).build(),
                User.builder().age(3).build(),
                User.builder().age(4).build(),
                User.builder().age(5).build()
        ).stream().peek(x -> {
            System.out.println(x.age);
            x.setAge(x.age + 100);
            x = User.builder().age(3).build();
        }).forEach(System.out::println);
    }
}
