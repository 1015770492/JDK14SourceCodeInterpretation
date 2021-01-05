package java.lang;

import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.invoke.MethodHandles;
import java.util.Optional;

public final class Double extends Number implements Comparable<Double>, Constable, ConstantDesc {
   
    @java.io.Serial
    private static final long serialVersionUID = -9172774392245257468L; //序列化版本号
    private final double value; // 实际存储double数据的地方
    public static final double POSITIVE_INFINITY = 1.0 / 0.0;   // 正无穷常量
    public static final double NEGATIVE_INFINITY = -1.0 / 0.0;  // 负无穷常量
    public static final double NaN = 0.0d / 0.0;                // NaN 值
    public static final double MAX_VALUE = 0x1.fffffffffffffP+1023; // 最大值： 1.7976931348623157e+308
    public static final double MIN_NORMAL = 0x1.0p-1022; // 最大正常值：2.2250738585072014E-308
    public static final double MIN_VALUE = 0x0.0000000000001P-1022; // 最小值：4.9e-324
    public static final int MAX_EXPONENT = 1023;
    public static final int MIN_EXPONENT = -1022;
    public static final int SIZE = 64;              // 底层double占用的位数
    public static final int BYTES = SIZE / Byte.SIZE;
    public static final Class<Double>   TYPE = (Class<Double>) Class.getPrimitiveClass("double");

    /**
     * 构造方法
     */
    @Deprecated(since="9")
    public Double(double value) {}  // 传入的是一个double
    @Deprecated(since="9")
    public Double(String s) throws NumberFormatException {} // 传入的是一个字符串
    
    public boolean isNaN() {}           // 是否为NaN
    public boolean isInfinite() {}      // 当前值是否为无穷数（正、负都可以）
    public int compareTo(Double anotherDouble) {} // 当前值和anotherDouble比较大小
    public String toString() {}     // double转换为字符串

    /**
     * double转化为其它基本类型的数据
     */
    public byte byteValue() {}      // 得到byte类型
    public short shortValue() {}    // 得到short类型
    public int intValue() {}        // 得到int类型
    public float floatValue() {}    // 得到float类型
    public long longValue() {}      // 得到long类型
    @HotSpotIntrinsicCandidate
    public double doubleValue() {}  // 返回double的值
    @Override
    public int hashCode() {}        // 当前double值得hash值
    public boolean equals(Object obj) {} // 当前double值和obj是否相等


    /**
     * 静态方法是做工具对外提供使用的
     * 功能无非是两double数和、最大值、最小值、比较大小、double值得hash值
     */
    public static int hashCode(double value) {}         // 得到value的hash值
    public static boolean isNaN(double v) {}            // v是否为NaN
    public static boolean isInfinite(double v) {}       // v是否为无穷数
    public static boolean isFinite(double d) {}
    public static double sum(double a, double b) {}     // a、b求和
    public static double max(double a, double b) {}     // a、b最大值
    public static double min(double a, double b) {}     // a、b最小值
    public static int compare(double d1, double d2) {}  // a、b两数比大小
    public static String toString(double d) {}          // d 转字符串
    public static String toHexString(double d) {}       // d 转16进制字符串
    @HotSpotIntrinsicCandidate
    public static Double valueOf(double d) {}           // double转Double
    public static Double valueOf(String s) throws NumberFormatException {}  // 字符串转Double
    public static double parseDouble(String s) throws NumberFormatException {} // 解析字符串转为double
    
    
    @HotSpotIntrinsicCandidate
    public static long doubleToLongBits(double value) {}        // double转换为2进制
    @HotSpotIntrinsicCandidate
    public static native long doubleToRawLongBits(double value);// double转换为2进制的数字   
    @HotSpotIntrinsicCandidate
    public static native double longBitsToDouble(long bits);    // 2进制数转换为double
    
    @Override
    public Optional<Double> describeConstable() {
        return Optional.of(this);				// 将当前double值包装为Optional对象
    }
    @Override
    public Double resolveConstantDesc(MethodHandles.Lookup lookup) {
        return this;
    }
}
