package java.lang;

import jdk.internal.misc.VM;

import java.lang.annotation.Native;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.invoke.MethodHandles;
import java.math.BigInteger;
import java.util.Optional;

public final class Long extends Number implements Comparable<Long>, Constable, ConstantDesc {

    @java.io.Serial
    @Native
    private static final long serialVersionUID = 4290774380558885855L;// 序列化版本号
    @Native public static final long MIN_VALUE = 0x8000000000000000L;   // 最小值-2的63次方
    @Native public static final long MAX_VALUE = 0x7fffffffffffffffL;   // 最大值2的63次方
    private final long value;                                           // 存储值得成员变量
    @Native public static final int SIZE = 64;                          // 长度64bit
    public static final int BYTES = SIZE / Byte.SIZE;                   // 8个byte数
    public static final Class<Long> TYPE = (Class<Long>) Class.getPrimitiveClass("long"); // Class实例表示基本类型long

    /**
     * 缓存值
     */
    private static class LongCache {
        private LongCache() {}
        static final Long[] cache; // 缓存的long值存入数组
        static Long[] archivedCache; // 和 cache指向同一个地址
        static {
            int size = -(-128) + 127 + 1;  // 数组的长度256
            VM.initializeFromArchive(LongCache.class);
            if (archivedCache == null || archivedCache.length != size) {
                Long[] c = new Long[size]; // 创建长度256的数组
                long value = -128; // 最小值-128，最大值则是-128+256=127
                for(int i = 0; i < size; i++) { // 循环256次
                    c[i] = new Long(value++); // 将-128 到 127 存入数组中 因为size=256
                }
                archivedCache = c;
            }
            cache = archivedCache;
        }
    }

    /**
     * 构造方法
     */
    @Deprecated(since="9")
    public Long(long value) {} // 传入long得构造方法
    @Deprecated(since="9")
    public Long(String s) throws NumberFormatException {} // 传入String得构造方法

    /**
     * 转换其它类型的值：byte、short、int、float、double、long
     */
    public byte byteValue() {}      // 转换为btye
    public short shortValue() {}    // 转换为short
    public int intValue() {}        // 转换为int
    public float floatValue() {}    // 转换为float
    public double doubleValue() {}  // 转换为double
    @HotSpotIntrinsicCandidate
    public long longValue() {}      // 得到long类型的值

    /**
     * 其它常用的实例方法
     */
    public String toString() {}                 // 转换为字符串
    public int compareTo(Long anotherLong) {}   // 比较大小大于anthoerLong返回正值，-1是小于，0相等
    public boolean equals(Object obj) {}        // 判断当前值是否和obj相等
    @Override public int hashCode() {}          // 得到hash值
    @Override public Optional<Long> describeConstable() {} // 将当前值封装成Optional

    @Override public Long resolveConstantDesc(MethodHandles.Lookup lookup) {} // 返回当前对象

    /**
     * ==================================
     * 下面的都是静态方法，作为工具类对外使用
     * =================================
     */


    /**
     * 常用方法：求和。最大值，最小值
     */
    public static int hashCode(long value) {}             // 得到value的hash值
    public static long sum(long a, long b) {}             // 求和
    public static long divideUnsigned(long dividend,long divisor) {}// 除法。返回dividend 除以 divisor 的值
    public static long remainderUnsigned(long dividend,long divisor) {}// 求余。返回dividend 对divisor的余数
    public static long max(long a, long b) {}             // 两数最大值
    public static long min(long a, long b) {}             // 两数最小值
    public static int compare(long x, long y) {}          // 比较x，y的大小
    public static int compareUnsigned(long x, long y) {}  // 比较无符号的x，y的大小
    /**
     * 数字转字符串
     */
    public static String toString(long i, int radix) {}         // 按照基数radix将long类型的i转换成utf16字符串
    private static String toStringUTF16(long i, int radix) {}   // 被上面的toString调用
    public static String toUnsignedString(long i, int radix) {} // 按进制radix(2、4、8、10、16、32)转换成无符号字符串
    private static BigInteger toUnsignedBigInteger(long i) {}   // 将long类型的i转换为BigInteger对象
    public static String toString(long i) {}                    // 将i转换成字符串
    public static String toBinaryString(long i) {}              // 将long类型的i转换为2进制字符串
    public static String toOctalString(long i){}                // 将long类型的i转换为10进制字符串
    public static String toHexString(long i) {}                 // 将long类型的i转换为16进制字符串
    public static String toUnsignedString(long i) {}            // 将long类型的i转换为无符号字符串

    /**
     * 字符串转数字（前面的逆作用）
     *
     * 转换过程都可能 throws NumberFormatException
     */
    public static Long decode(String nm){}                      // 解析字符串中代表的long值，例如：0x开头、0开头
    public static long parseLong(String s, int radix)  {}       // 按基数radix解析字符串s中代表的long值
    public static long parseLong(String s){}                    // 默认10进制为基数，调用上面的方法进行解析
    public static long parseUnsignedLong(String s, int radix){} // 按基数radix解析字符串s中代表的无符号long值
    public static long parseUnsignedLong(String s) {}           // 默认10 进制，调用上面一个的方法
    // 以radix进制解析字符串中索引值从beginIndex到endIndex内表示的字符串
    public static long parseLong(CharSequence s, int beginIndex, int endIndex, int radix) {}
    // 以radix进制解析字符串中索引值从beginIndex到endIndex内表示的字符串（返回无符号long值）
    public static long parseUnsignedLong(CharSequence s, int beginIndex, int endIndex, int radix) {}
    @HotSpotIntrinsicCandidate
    public static Long valueOf(long l) {}                 // 得到l的包装对象
    public static Long valueOf(String s, int radix){}     // 按基数radis解析s为Long对象
    public static Long valueOf(String s) {}               // 得到字符串s中代表的Long对象
    public static Long getLong(String nm) {}              // 得到系统定义的值例如传入：sun.arch.data.model 会得到64
    /**
     * 例如：
     * Long aLong = Long.getLong("sun.arch.data.model",100); 得到的是64不是100
     * Long aLong = Long.getLong("系统变量的值",100); // 因为前面没有所以返回100
     * Long aLong = Long.getLong("100"); // 返回null，因为100在系统变量中获取不到值
     *
     */
    public static Long getLong(String nm, long val) {}    // 如果nm所代表的系统值由则返回nm所代表的值，否则返回val值
    public static Long getLong(String nm, Long val) {}    // 被getLong(String nm, long val)调用，实际上它才是真正处理的函数

    /**
     * 位操作
     * 左移、右移、反转
     */
    public static int signum(long i) {}                    // 返回符合位，负数返回-1，正数返回1，0返回0
    public static long rotateLeft(long i, int distance){}  // 将i所代表的2进制进行左移distance位（相当于将 i * 2的distance次方法）
    public static long rotateRight(long i, int distance){} // 将i（二进制表示）右移distance位
    public static long reverse(long i) {}                  // 将i(二进制表示)进行反转
    public static long highestOneBit(long i) {}            // 返回最高位的值，例如：100(需要二进制表示的最高位) 得到的是64，128得到的是128
    public static long lowestOneBit(long i) {}             // 返回最低位的值，例如：5（2进制表示101） 返回1
    @HotSpotIntrinsicCandidate
    public static int bitCount(long i) {}                  // 返回i表示的二进制中1的个数
    @HotSpotIntrinsicCandidate
    public static long reverseBytes(long i) {}             // 将i存储的8个字节内容进行逆序排序得到新的long值
    @HotSpotIntrinsicCandidate
    public static int numberOfLeadingZeros(long i) {}      // 返回i表示的二进制左边0的个数（总共64位，将i用二进制表示，返回最高位1左边0的个数）
    @HotSpotIntrinsicCandidate
    public static int numberOfTrailingZeros(long i) {}     // 返回i表示的二进制右边0的个数（总共64位，将i用二进制表示，返回最低位1左边0的个数）




    /**
     * 不对外使用的方法
     */
    static String toUnsignedString0(long val, int shift) {}     // 将long类型的val按照shift进制进行转换得到字符串
    private static void formatUnsignedLong0(long val, int shift, byte[] buf, int offset, int len) {}
    private static void formatUnsignedLong0UTF16(long val, int shift, byte[] buf, int offset, int len) {}
    static String fastUUID(long lsb, long msb) {}
    static int getChars(long i, int index, byte[] buf) {}
    static int stringSize(long x) {}
    
}
