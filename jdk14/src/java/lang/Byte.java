package java.lang;

import jdk.internal.misc.VM;

public final class Byte extends Number implements Comparable<Byte> {
    @java.io.Serial
    private static final long serialVersionUID = -7183698231559129828L;
    public static final byte   MIN_VALUE = -128;            // 缓存最小值
    public static final byte   MAX_VALUE = 127;             // 缓存最大值
    public static final int SIZE = 8;                       // 一个byte占8位
    public static final int BYTES = SIZE / Byte.SIZE;       // 占8/8=1个字节
    private final byte value;                               // 存储数据的成员变量
    @SuppressWarnings("unchecked")
    public static final Class<Byte> TYPE = (Class<Byte>) Class.getPrimitiveClass("byte"); 

    /**
     * -128到127的值会被缓存
     */
    private static class ByteCache {
        private ByteCache() {}
        static final Byte[] cache;
        static Byte[] archivedCache;
        static {
            final int size = -(-128) + 127 + 1;// 总共256
            VM.initializeFromArchive(ByteCache.class);
            if (archivedCache == null || archivedCache.length != size) {
                Byte[] c = new Byte[size];
                byte value = (byte)-128;
                for(int i = 0; i < size; i++) {
                    c[i] = new Byte(value++); // 将-128到127的值存入数组中
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
    public Byte(byte value) {}
    @Deprecated(since="9")
    public Byte(String s) throws NumberFormatException {} // 传入10进制的字符串s进行转换

    /**
     * byte转其它类型数据
     */
    @HotSpotIntrinsicCandidate
    public byte byteValue() {}                  // 返回byte值
    public short shortValue() {}                // 返回short类型的值
    public int intValue() {}                    // 返回int类型的值
    public float floatValue() {}                // 返回float类型的值
    public long longValue() {}                  // 返回long类型的值
    public double doubleValue() {}              // 返回double类型的值
    public boolean equals(Object obj) {}        // 判断当前值和obj是否相等
    public int compareTo(Byte anotherByte) {}   // 比较当前值和anotherByte的大小
    public String toString() {}                 // 将当前值转换成字符串
    public int hashCode() {}                    // 当前值的hash值


    /**
     * 静态方法
     */
    @HotSpotIntrinsicCandidate
    public static Byte valueOf(byte b) {}                   // 将b封装为Byte
    public static int hashCode(byte value) {}               // value的hash值
    public static String toString(byte b) {}                // 转换为字符串
    public static int compare(byte x, byte y) {}            // 比较x，y的大小
    public static int compareUnsigned(byte x, byte y) {}    // 比较无符号x，y的大小
    public static int toUnsignedInt(byte x) {}              // 转换为无符号int值
    public static long toUnsignedLong(byte x) {}            // 转换为无符号long值
    public static byte parseByte(String s) throws NumberFormatException {}          // 按默认10进制解析字符串s为byte数据
    public static byte parseByte(String s, int radix) throws NumberFormatException{}// 按照radix进制，解析字符串s
    public static Byte valueOf(String s, int radix) throws NumberFormatException {} // 将字符串按照radix进制转换并返回
    public static Byte valueOf(String s) throws NumberFormatException {}            // 将字符串按照10进制转换并返回
    public static Byte decode(String nm) throws NumberFormatException {}            // 将nm字符串进行解码并返回转换后的值


}

