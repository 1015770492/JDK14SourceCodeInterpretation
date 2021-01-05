package java.lang;

import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.invoke.MethodHandles;
import java.util.Optional;

public final class Float extends Number implements Comparable<Float>, Constable, ConstantDesc {

    @java.io.Serial
    private static final long serialVersionUID = -2671257302660747028L; // 序列化版本号
    private final float value;                                  // 存储浮点数的值
    public static final float POSITIVE_INFINITY = 1.0f / 0.0f;  // 正无穷大常量
    public static final float NEGATIVE_INFINITY = -1.0f / 0.0f; // 负无穷大常量
    public static final float NaN = 0.0f / 0.0f;                // NaN常量
    public static final float MAX_VALUE = 0x1.fffffeP+127f;     // 最大值的常量
    public static final float MIN_VALUE = 0x0.000002P-126f;     // 最小值的常量
    public static final float MIN_NORMAL = 0x1.0p-126f;         // 最小的正常常量
    public static final int MAX_EXPONENT = 127;
    public static final int MIN_EXPONENT = -126;
    public static final int SIZE = 32;                          // 用bit表示的长度，4字节=32位
    public static final int BYTES = SIZE / Byte.SIZE;           // 用字节表示float
    public static final Class<Float> TYPE = (Class<Float>) Class.getPrimitiveClass("float");


    public int compareTo(Float anotherFloat) {}     // 当前浮点数和anotherFloat比较大小

    /**
     * 构造方法
     */
    @Deprecated(since="9")
    public Float(float value) {}
    @Deprecated(since="9")
    public Float(double value) {}
    @Deprecated(since="9")
    public Float(String s) throws NumberFormatException {}

    /**
     *
     */
    public boolean isNaN() {}       // 判断当前浮点数是否为 NaN
    public boolean isInfinite() {}  // 当前数是正无穷或负无穷则返回true，否则返回false
    public String toString() {}     // 浮点数转换成字符串
    public byte byteValue() {}      // 浮点数转换为byte
    public short shortValue() {}    // 浮点数转换short
    public int intValue() {}        // 浮点数转换为int
    public long longValue() {}      // 浮点数转换成long
    @HotSpotIntrinsicCandidate
    public float floatValue() {}    // 得到当前浮点数
    public double doubleValue() {}  // 浮点数转换成double
    @Override
    public int hashCode() {}        // 浮点数的hash值
    public boolean equals(Object obj) {}  // 当前浮点数是否于obj相等
    /**
     * 静态方法
     * 和上面非静态方法差不多意思，只不过上面的参数来自本身的value
     * 而下面是的方法是通过类名当做工具类使用的
     */
    public static int hashCode(float value) {}      //浮点数的hash值
    public static int compare(float f1, float f2){} // 比较两浮点数大小
    public static float sum(float a, float b) {}    // 两个浮点数求和
    public static float max(float a, float b) {}    // 两个浮点数最大的一个浮点数
    public static float min(float a, float b) {}    // 两个浮点数最小的一个浮点数
    public static String toString(float f) {}       // 浮点数转换成字符串
    public static String toHexString(float f) {}    // 浮点数转换成16进制字符串
    public static Float valueOf(String s) {}        // 字符串转浮点数对象
    @HotSpotIntrinsicCandidate
    public static Float valueOf(float f) throws NumberFormatException {}     // 将浮点数封装成Float对象
    public static float parseFloat(String s) throws NumberFormatException {} // 解析字符串为浮点数
    public static boolean isNaN(float v) {}         // 判断 v 是否为NaN类型，是则true，否则为false
    public static boolean isInfinite(float v) {}    // 判断 v 是否是正无穷或负无穷则返回true，否则返回false
    public static boolean isFinite(float f) {}      // f 有浮点值返回true，没有则返回false

    @HotSpotIntrinsicCandidate
    public static int floatToIntBits(float value) {} // 浮点数转换成2进制01表示的值
    @HotSpotIntrinsicCandidate
    public static native int floatToRawIntBits(float value);//
    @HotSpotIntrinsicCandidate
    public static native float intBitsToFloat(int bits); // 将int01表示的数据转换成float


    // 将Float封装在Optional容器内（允许null）
    @Override
    public Optional<Float> describeConstable() {
        return Optional.of(this);
    }
    @Override
    public Float resolveConstantDesc(MethodHandles.Lookup lookup) {
        return this;
    }
}

