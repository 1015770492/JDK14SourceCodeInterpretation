package java.lang;

import jdk.internal.misc.VM;

import java.lang.annotation.Native;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.invoke.MethodHandles;
import java.util.Optional;

public final class Integer extends Number implements Comparable<Integer>, Constable, ConstantDesc {

    private final int value;        // 真正存储int类型的值
    @java.io.Serial
    @Native
    private static final long serialVersionUID = 1360826667806852920L; // 序列化版本号
    @Native
    public static final int MIN_VALUE = 0x80000000;
    @Native
    public static final int MAX_VALUE = 0x7fffffff;
    @Native
    public static final int SIZE = 32;
    public static final int BYTES = SIZE / Byte.SIZE;
    public static final Class<Integer> TYPE = (Class<Integer>) Class.getPrimitiveClass("int");
    static final int[] sizeTable = {9, 99, 999, 9999, 99999, 999999, 9999999, 99999999, 999999999, Integer.MAX_VALUE};
    static final char[] digits = {
            '0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', 'a', 'b',
            'c', 'd', 'e', 'f', 'g', 'h',
            'i', 'j', 'k', 'l', 'm', 'n',
            'o', 'p', 'q', 'r', 's', 't',
            'u', 'v', 'w', 'x', 'y', 'z'
    };
    static final byte[] DigitTens = {
            '0', '0', '0', '0', '0', '0', '0', '0', '0', '0',
            '1', '1', '1', '1', '1', '1', '1', '1', '1', '1',
            '2', '2', '2', '2', '2', '2', '2', '2', '2', '2',
            '3', '3', '3', '3', '3', '3', '3', '3', '3', '3',
            '4', '4', '4', '4', '4', '4', '4', '4', '4', '4',
            '5', '5', '5', '5', '5', '5', '5', '5', '5', '5',
            '6', '6', '6', '6', '6', '6', '6', '6', '6', '6',
            '7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
            '8', '8', '8', '8', '8', '8', '8', '8', '8', '8',
            '9', '9', '9', '9', '9', '9', '9', '9', '9', '9',
    };

    static final byte[] DigitOnes = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    };

    /**
     * 重点掌握内容，面试题可能会出现
     * 对于-128 ~ 127 以内得数做了缓存，因此-128 ~ 127 内Integer类型得对象是同一个对象
     * 例如：   Integer i1 = 12;
     *         Integer i2 = 12;
     *         System.out.println(i1 == i2);// true
     *
     *         Integer i3 = 128;
     *         Integer i4 = 128;
     *         System.out.println(i3==i4);// false
     * 之所以产生区别就在于-128 ~ 127 内得数做了缓存
     * 拆包：   Integer i5 = 128;
     *         int i6 = 128;
     *         System.out.println(i5 == i6);// true 进行拆包比较所以为true
     * 直接new Integer();比较的是地址，肯定是false
     */
    private static class IntegerCache {
        static final int low = -128; // 缓存最小值-128
        static final int high;// 缓存最大值，后面被赋值为127，在类的初始化过程中静态代码块被执行
        static final Integer[] cache; // 缓存值，缓存的数据都存在这个对象数组中
        static Integer[] archivedCache;
        static {
            int h = 127; // 127以内的值做了缓存，
            String integerCacheHighPropValue = VM.getSavedProperty("java.lang.Integer.IntegerCache.high");// 获取jvm启动参数中的最大值
            if (integerCacheHighPropValue != null) {
                try {
                    h = Math.max(parseInt(integerCacheHighPropValue), 127); // 将手动从jvm启动参数值和127进行比较，得到最大值
                    h = Math.min(h, Integer.MAX_VALUE - (-low) - 1);//
                } catch (NumberFormatException nfe) {}
            }
            high = h;   // high=127，low=-128
            VM.initializeFromArchive(IntegerCache.class);
            int size = (high - low) + 1;
            if (archivedCache == null || size > archivedCache.length) {
                Integer[] c = new Integer[size];
                int j = low;
                for (int i = 0; i < c.length; i++) {
                    c[i] = new Integer(j++);
                }
                archivedCache = c;
            }
            cache = archivedCache;
            assert IntegerCache.high >= 127;
        }
        private IntegerCache() {}
    }

    /**
     * 构造方法
     */
    @Deprecated(since = "9")
    public Integer(int value) {}
    @Deprecated(since = "9")
    public Integer(String s) throws NumberFormatException {}

    public byte byteValue() {}      // 转换成byte类型的值
    public short shortValue() {}    // 转换成short类型的值
    public int intValue() {}        // 得到int值  该方法加了注解：@HotSpotIntrinsicCandidate
    public float floatValue() {}    // 转换成float类型的值
    public long longValue() {}      // 转换成long类型的值
    public double doubleValue() {}  // 转换成double类型的值
    @Override
    public int hashCode() {}        // int类型的hash值就是它本身
    public String toString() {}     // 转换成字符串
    public int compareTo(Integer anotherInteger) {} // Integer比较大小
    public boolean equals(Object obj) {}    // 对象是否是Integer子类，比较其中的int值是否相等


    /**
     * 静态方法
     */
    public static int sum(int a, int b) {}      // 两数之和
    public static int max(int a, int b) {}      // 两数中最大值
    public static int min(int a, int b) {}      // 两数中最小值
    public static Integer getInteger(String nm) {}              // 得到值
    public static Integer getInteger(String nm, int val) {}     // 得到值
    public static Integer getInteger(String nm, Integer val) {} // 得到值
    public static int hashCode(int value) {}            // 得到value代表的整数的hash值
    public static int compare(int x, int y) {}          // 比较两数大小
    public static int compareUnsigned(int x, int y) {}  // 比较无符号两数大小
    public static long toUnsignedLong(int x) {}         // x转换为无符号long类型数据
    /**
     * 字符串转int
     * 如果传入的字符串不是数字，以下方法都可能会 throws NumberFormatException
     */
    public static int parseInt(String s) {}                // 字符串转换成int，默认10进制
    public static int parseInt(String s, int radix) {}     // 字符串转成radix值所代表的进制
    public static int parseInt(CharSequence s, int beginIndex, int endIndex, int radix) {}          //
    public static int parseUnsignedInt(String s) {}       // 解析字符串代表的无符号整数，默认10进制
    public static int parseUnsignedInt(String s,int radix){} // 解析字符串代表的无符号整数，radix代表进制
    public static int parseUnsignedInt(CharSequence s, int beginIndex, int endIndex, int radix) {}  //
    @HotSpotIntrinsicCandidate
    public static Integer valueOf(int i) {}             // 得到i的包装对象
    public static Integer valueOf(String s) {}          // 得到s转换后的包装对象
    public static Integer valueOf(String s, int radix){}// 得到s转换后，经过radix进制转换的包装对象
    public static Integer decode(String nm) {}          // 根据nm表示的意思（各进制例如:0xAF、正负数：-100）转换为Integer包装类型的数据

    /**
     * 转换为各种类型的字符串
     */
    @HotSpotIntrinsicCandidate
    public static String toString(int i) {}                     // 转换为字符串
    public static String toString(int i, int radix) {}          // 按照radix进制转换为字符串
    private static String toStringUTF16(int i, int radix) {}    // 转换为UTF16编码的字符串
    public static String toUnsignedString(int i) {}             // 转换为无符号整数的字符串，默认10进制
    public static String toUnsignedString(int i, int radix) {}  // 按照radix进制转换为无符号整数的字符串
    public static String toHexString(int i) {}                  // 转换为16进制的字符串
    public static String toOctalString(int i) {}                // 转换为10进制的字符串
    public static String toBinaryString(int i) {}               // 转换为2进制的字符串

    /**
     * 不常用方法
     */
    public static int divideUnsigned(int dividend, int divisor) {}      // 得到无符号dividend 除于 divisor
    public static int remainderUnsigned(int dividend, int divisor) {}   // 返回余数
    public static int highestOneBit(int i) {}           // 得到最高位的值（十进制）
    public static int lowestOneBit(int i) {}			// 得到最低位的值
    public static int rotateLeft(int i, int distance) {}// 将i的二进制左移distance位
    public static int rotateRight(int i, int distance){}// 将i的二进制右移distance位
    public static int reverse(int i) {}					// 将
    public static int signum(int i) {}					// 得到i的符号位，负数返回-1，正数返回1，0返回0
    @HotSpotIntrinsicCandidate
    public static int numberOfLeadingZeros(int i) {} // 返回二进制中左边0的个数（int类型数据总共32位所以不会超过64）
    @HotSpotIntrinsicCandidate
    public static int numberOfTrailingZeros(int i) {}// 返回二进制中右边0的个数（int类型数据总共32位所以不会超过64）
    @HotSpotIntrinsicCandidate
    public static int bitCount(int i) {}             // 返回i的二进制表示，1的个数
    @HotSpotIntrinsicCandidate
    public static int reverseBytes(int i) {}		 // 将i二进制表示的位按照字节逆序排序（每8位一个byte像数组一样逆序）

    @Override
    public Optional<Integer> describeConstable() {}

    @Override
    public Integer resolveConstantDesc(MethodHandles.Lookup lookup) {}

    /**
     * 外部使用不到的方法
     */
    static int getChars(int i, int index, byte[] buf) {}
    static int stringSize(int x) {}
    private static String toUnsignedString0(int val, int shift) {}
    private static void formatUnsignedInt(int val, int shift, byte[] buf, int len) {}
    private static void formatUnsignedIntUTF16(int val, int shift, byte[] buf, int len) {}


}
