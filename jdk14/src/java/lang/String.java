package java.lang;

import jdk.internal.vm.annotation.Stable;

import java.io.ObjectStreamField;
import java.lang.annotation.Native;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.util.Comparator;

public final class String implements java.io.Serializable, Comparable<String>, CharSequence, Constable, ConstantDesc {

    @java.io.Serial
    private static final long serialVersionUID = -6849794470754667710L; // 序列化版本号
    @java.io.Serial
    private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];
    @Stable
    private final byte[] value;  // 值 jdk9开始，java9以前用char存储
    @Native
    static final byte LATIN1 = 0; // 拉丁字符
    @Native
    static final byte UTF16 = 1; // utf16编码
    private final byte coder; // 编码
    private int hash; // Default to 0
    private boolean hashIsZero; // Default to false;
    static final boolean COMPACT_STRINGS;
    public static final Comparator<String> CASE_INSENSITIVE_ORDER = new CaseInsensitiveComparator();

    static {
        COMPACT_STRINGS = true;
    }

    /**
     * 构造方法，不用太过关注，后面的方法才是主角
     */
    String(char[] value, int off, int len, Void sig) {}
    String(AbstractStringBuilder asb, Void sig) {}
    String(byte[] value, byte coder) {}
    public String() {}
    @HotSpotIntrinsicCandidate
    public String(String original) {}
    public String(char value[]) {}
    public String(char value[], int offset, int count) {}
    public String(int[] codePoints, int offset, int count) {}
    public String(byte bytes[], int offset, int length, String charsetName)throws UnsupportedEncodingException {}
    public String(byte bytes[], int offset, int length, Charset charset) {}
    public String(byte bytes[], String charsetName) throws UnsupportedEncodingException {}
    public String(byte bytes[], Charset charset) {}
    public String(byte bytes[], int offset, int length) {}
    public String(byte[] bytes) {}
    public String(StringBuffer buffer) {}
    public String(StringBuilder builder) {}
    @Deprecated(since = "1.1") // jdk1.1开始废弃
    public String(byte ascii[], int hibyte) {}
    @Deprecated(since = "1.1")
    public String(byte ascii[], int hibyte, int offset, int count) {}

    /**
     * 常用的字符串方法，以下方法需要重点掌握了解
     */
    // 字符串的hash算法再hashMap中经常作为键，因此需要重点了解一下String.hashCode()
    public int hashCode() {
        int h = hash;
        if (h == 0 && !hashIsZero) {
            h = isLatin1() ? StringLatin1.hashCode(value) : StringUTF16.hashCode(value);
            if (h == 0) {
                hashIsZero = true;
            } else {
                hash = h;
            }
        }
        return h;
    }
    public <R> R transform(Function<? super String, ? extends R> f) {} // java8加入的函数式接口，目的是将字符串转换成另一个类型
    public int length() {}              // 返回字符串长度
    public String toString() {}         // 返回字符串
    public native String intern();      // 存入StringTable中可以减少创建对象来节省空间
    byte[] value() {}                   // java9开始将底层char改成byte 返回真实存储字符串的结构：字节数组
    public String trim() {}             // 去除拉字符串首尾的丁字符中的空格和制表符，新版的使用strip
    public String strip() {}            // 去除任意字符集的首尾空格或制表符，新版本建议使用的代替trim（过时）
    public String stripLeading() {}     // 去除首部的空格或制表符
    public String stripTrailing() {}    // 去除尾部的空格或制表符
    public Stream<String> lines() {}    // 将字符串按照`\n`进行拆分，得到一个Stream流，为了方便使用java8的流操作
    public String indent(int n) {}      // 调整每一行的缩进空格数
    public char charAt(int index) {}    // 获取index索引下的字符
    public char[] toCharArray() {}      // 返回char类型的数组
    public byte[] getBytes() {}                     // 无参的方式获取字节数组
    public byte[] getBytes(String charsetName) {}   // 传入字符集的名字,字符集不存在会抛异常：UnsupportedEncodingException
    public byte[] getBytes(Charset charset) {}      // Charset类型的方式获取字节数组
    @Deprecated(since = "1.1")
    public void getBytes(int srcBegin, int srcEnd, byte dst[], int dstBegin) {}  // 已被废弃
    void getBytes(byte dst[], int dstBegin, byte coder) {}
    public String toLowerCase(Locale locale) {}     // 转换为小写字母，传入地区进行转换
    public String toLowerCase() {}                  // 转换成小写，默认的地区
    public String toUpperCase(Locale locale) {}     // 转换成大写，传入地区
    public String toUpperCase() {}                  // 转换成大小，默认的地区
    public static String format(String format, Object... args) {} // 字符串格式化方法，将format字符串中的%s替换成后面的字符串对象
    public static String format(Locale l, String format, Object... args) {}

    /**
     * 字符串与字符串之间的操作，例如拼串、子串、字符串替换、字符串拆分
     */
    public String repeat(int count) {}                          //字符串重复拼接count次
    public String concat(String str) {}                         // 字符串拼接
    public String substring(int beginIndex) {}                  // 获取子串从开始索引到结束
    public String substring(int beginIndex, int endIndex) {}    // 截取索引从beginIndex 到 endIndex内的字符串
    public static String join(CharSequence delimiter, CharSequence... elements) {}// 中间加delimiter为分隔符进行拼串
    public static String join(CharSequence delimiter,Iterable<? extends CharSequence> elements) {} // 迭代器的方式中间加分割符拼串
    public String replace(char oldChar, char newChar) {}        //替换某个字符
    public String replaceFirst(String regex, String replacement) {} // 替换字符串中第一次符合正则表达式的字符串
    public String replaceAll(String regex, String replacement) {} // 替换字符串中所有符合正则表达式的字符串
    public String replace(CharSequence target, CharSequence replacement) {} // 替换字符串target为replacement字符串
        /**
              按照正则匹配进行拆分，
          如果 limit=0                      返回的数组长度没有限制值
              limit<0 则可以调用多次split方法，返回的数组长度没有限制值
              limit>0 只能调用一次split，并且返回的数组长度小于limit
         */
    public String[] split(String regex) {} // 按照正则表达式进行拆分，调用的是两个参数的split方法
    public String[] split(String regex, int limit) {} // 字符串拆分

    /**
     * 字符串 比较、判断
     */
    boolean isLatin1() {}                           // 判断是否是拉丁字符
    public boolean equals(Object anObject) {}       // 判断两字符串是否相同，字符串比较用这个方法不要使用==进行比较
    public int compareTo(String anotherString) {}   // 字符串比较大小例如 "abc" "ABC" 按序比较Ascii码值
    public boolean isEmpty() {}                     // 判断是否为空串null或者""
    public boolean isBlank() {}                     // 判断是否为 "" 或者只包含空格的字符串例如："  "
    public boolean startsWith(String prefix){}      // 字符串是否以参数字符串开始
    public boolean startsWith(String prefix, int toffset) {} // 字符串是否以参数字符串开始
    public boolean endsWith(String suffix){}        // 字符串是否以参数字符串结尾
    public boolean matches(String regex) {}         // 是否符合正则匹配，调用的是Pattern.matches(regex, this);
    public boolean contains(CharSequence s) {}      // 是否包含字符串s，传入的是一个接口，实际可以用实现类String，例如"hello world"字符串
    public boolean contentEquals(StringBuffer sb) {}            // 字符串序列相同（字符串内容相同）返回true
    public boolean contentEquals(CharSequence cs) {}            // 字符串序列相同（字符串内容相同）返回true
    public boolean equalsIgnoreCase(String anotherString) {}    // 字符串比较忽略大小写
    public int compareToIgnoreCase(String str) {}               // 忽略大小写比较字符串的大小 零或正整数作为指定字符串大于，等于或小于该字符串
    // true如果该字符串指定的子区域字符串参数指定的子区域完全一致; false否则。
    public boolean regionMatches(int toffset, String other, int ooffset, int len) {}
    public boolean regionMatches(boolean ignoreCase, int toffset, String other, int ooffset, int len) {}
    /**
     * 获取子串的索引，如果不存在则返回-1
     */
    public int indexOf(String str) {}
    public int indexOf(String str, int fromIndex) {}
    static int indexOf(byte[] src, byte srcCoder, int srcCount, String tgtStr, int fromIndex) {}
    public int lastIndexOf(int ch) {}
    public int lastIndexOf(int ch, int fromIndex) {}
    public int lastIndexOf(String str) {}
    public int lastIndexOf(String str, int fromIndex) {}
    public CharSequence subSequence(int beginIndex, int endIndex) {}
    /**
     * 传入不同类型的数据转换成字符串，实际上不如 ""+1 这样方便，一般不常用下面这些方法
     */
    public static String valueOf(Object obj) {}
    public static String valueOf(char data[]) {}
    public static String valueOf(char data[], int offset, int count) {}
    public static String valueOf(boolean b) {}
    public static String valueOf(char c) {}
    public static String valueOf(int i) {}
    public static String valueOf(long l) {}
    public static String valueOf(float f) {}
    public static String valueOf(double d) {}
    public static String copyValueOf(char data[], int offset, int count) {}
    public static String copyValueOf(char data[]) {}
    
    /**
     * 预览新特性
     */
    @jdk.internal.PreviewFeature(feature = jdk.internal.PreviewFeature.Feature.TEXT_BLOCKS, essentialAPI = true)
    public String stripIndent() {}

    @jdk.internal.PreviewFeature(feature = jdk.internal.PreviewFeature.Feature.TEXT_BLOCKS, essentialAPI = true)
    public String translateEscapes() {} // jdk13预览新特性，

    @jdk.internal.PreviewFeature(feature = jdk.internal.PreviewFeature.Feature.TEXT_BLOCKS,essentialAPI = true)
    public String formatted(Object... args) {} // jdk13预览新特性，使用这个字符串作为格式字符串，以及所提供的参数格式


    
    public int codePointAt(int index) {}
    public int codePointBefore(int index) {}
    public int codePointCount(int beginIndex, int endIndex) {}
    public int offsetByCodePoints(int index, int codePointOffset) {}
    public void getChars(int srcBegin, int srcEnd, char dst[], int dstBegin) {}
    public int indexOf(int ch) {}
    public int indexOf(int ch, int fromIndex) {}

    /**
     * 默认的作用范围，因此下面这些方法在我们的程序中基本上使用不到，除非是public方法
     */
    byte coder() {}  // 得到字符串使用的编码 LATIN1 还是 UTF16
    static int lastIndexOf(byte[] src, byte srcCoder, int srcCount, String tgtStr, int fromIndex) {}
    static void checkIndex(int index, int length) {}
    static void checkOffset(int offset, int length) {}
    static void checkBoundsOffCount(int offset, int count, int length) {}
    static void checkBoundsBeginEnd(int begin, int end, int length) {}
    static String valueOfCodePoint(int codePoint) {}

    /**
     * 私有方法,不提供外部使用，因此不需要非常了解
     */
    private int indexOfNonWhitespace() {} // 私有方法，不提供外部使用不用了解
    private boolean nonSyncContentEquals(AbstractStringBuilder sb) {}
    private int lastIndexOfNonWhitespace() {}
    private static int outdent(List<String> lines) {}
    private static Void rangeCheck(char[] value, int offset, int count) {}
    @Override
    public IntStream chars() {}
    @Override
    public IntStream codePoints() {}
    @Override
    public Optional<String> describeConstable() {
        return Optional.of(this);
    }
    @Override
    public String resolveConstantDesc(MethodHandles.Lookup lookup) {
        return this;
    }

    private static class CaseInsensitiveComparator implements Comparator<String>, java.io.Serializable {
        @java.io.Serial
        private static final long serialVersionUID = 8575799808933029326L;
        public int compare(String s1, String s2) {}
        @java.io.Serial
        private Object readResolve() {}
    }


}
