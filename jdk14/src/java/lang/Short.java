package java.lang;

import jdk.internal.misc.VM;

public final class Short extends Number implements Comparable<Short> {
    @java.io.Serial
    private static final long serialVersionUID = 7515723908773894738L; // 序列化版本号
    public static final short   MIN_VALUE = -32768;         // 能够存储的最小值
    public static final short   MAX_VALUE = 32767;          // 能够存储的最大值
    private final short value;                              // 真正存储值得地方
    public static final int SIZE = 16;                      // 占2个字节16位
    public static final int BYTES = SIZE / Byte.SIZE;       // 16/8=2个字节
    @SuppressWarnings("unchecked")
    public static final Class<Short>    TYPE = (Class<Short>) Class.getPrimitiveClass("short");

    /**
     * 缓存值由来得代码
     */
    private static class ShortCache {
        private ShortCache() {}
        static final Short[] cache;
        static Short[] archivedCache;
        static {
            int size = -(-128) + 127 + 1; // 256
            VM.initializeFromArchive(ShortCache.class);
            if (archivedCache == null || archivedCache.length != size) {
                Short[] c = new Short[size];
                short value = -128;
                for(int i = 0; i < size; i++) { // 循环256次
                    c[i] = new Short(value++); // 缓存得-128 到 127存入数组中
                }
                archivedCache = c;
            }
            cache = archivedCache;
        }
    }

    /**
     * 构造方法
     */
    @Deprecated(since="9") public Short(short value) {}                          // 将value直接赋值给成员变量
    @Deprecated(since="9") public Short(String s) throws NumberFormatException{} // 将s按照10进制得方式解析成short赋值给value


    public byte byteValue() {}                  // 得到byte值
    @HotSpotIntrinsicCandidate
    public short shortValue() {}                // 得到short值
    public int intValue() {}                    // 得到int值
    public float floatValue() {}                // 得到float值
    public long longValue() {}                  // 得到long值
    public double doubleValue() {}              // 得到double值
    @Override public int hashCode() {}          // 得到当前值得hash值
    public int compareTo(Short anotherShort) {} // 比较当前值和anotherShort得大小
    public String toString() {}                 // 转换为字符串
    public boolean equals(Object obj) {}        // 判断当前值和obj是否相等

    /**
     * 静态方法
     */
    public static String toString(short s) {}               // 转换为字符串
    public static short parseShort(String s, int radix){}   // 按照radix进制解析字符串为short 可能解析失败 throws NumberFormatException
    public static short parseShort(String s) {}             // 按照10进制解析字符串为short 可能解析失败 throws NumberFormatException
    public static Short valueOf(String s, int radix) {}     // 按照radix进制解析字符串为Short 可能解析失败 throws NumberFormatException
    public static Short valueOf(String s) {}                // 按照radix进制解析字符串为Short 可能解析失败 throws NumberFormatException
    @HotSpotIntrinsicCandidate
    public static Short valueOf(short s) {}                 // 将s封装为Short
    public static Short decode(String nm) {}                // 将字符串nm节码，nm可以是十六进制得字符，也可以是10进制的...
    public static int hashCode(short value) {}              // 得到value的hash值
    public static int compare(short x, short y) {}          // 比较x，y的大小
    public static int compareUnsigned(short x, short y) {}  // 比较无符号x，y的大小
    @HotSpotIntrinsicCandidate
    public static short reverseBytes(short i) {}            // 将i底层二进制按照字节进行逆序排序
    public static int toUnsignedInt(short x) {}             // 转换为无符号的int值
    public static long toUnsignedLong(short x) {}           // 转换为无符号的long值


}
