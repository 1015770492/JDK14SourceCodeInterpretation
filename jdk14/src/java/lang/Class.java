package java.lang;

import Unsafe;
import jdk.internal.HotSpotIntrinsicCandidate;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.vm.annotation.ForceInline;
import sun.reflect.ReflectionFactory;
import sun.reflect.generics.factory.GenericsFactory;
import sun.reflect.generics.repository.ClassRepository;

import java.io.InputStream;
import java.io.ObjectStreamField;
import java.lang.constant.ClassDesc;
import java.lang.constant.Constable;
import java.lang.invoke.TypeDescriptor;
import java.lang.ref.SoftReference;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;

public final class Class<T> implements java.io.Serializable, GenericDeclaration, Type, AnnotatedElement, TypeDescriptor.OfField<Class<?>>, Constable {
    /**
     * 成员属性
     */
    // 静态常量部分
    @java.io.Serial
    private static final long serialVersionUID = 3206093459760846163L;              // 序列化版本号
    @java.io.Serial
    private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];//
    private static final int ANNOTATION = 0x00002000;                   // 十进制 8192
    private static final int ENUM = 0x00004000;                         // 十进制 16384
    private static final int SYNTHETIC = 0x00001000;                    // 十进制 4096
    private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];// 空类数组常量
    // 静态属性
    private static ReflectionFactory reflectionFactory;                 // 反射工厂
    private static java.security.ProtectionDomain allPermDomain;        // 保护域
    private transient String name;                                      // 类名称
    private transient Module module;                                    // 模块
    private final ClassLoader classLoader;                              // 类加载器（只允许赋值一次，因此间接说明一个类的加载器只能有一个）
    private final Class<?> componentType;
    private transient String packageName;                               // 包名
    @SuppressWarnings("UnusedDeclaration")
    private transient volatile AnnotationData annotationData;           // 注解数据
    @SuppressWarnings("UnusedDeclaration")
    private transient volatile AnnotationType annotationType;           // 注解类型
    private transient volatile T[] enumConstants;                       // 枚举常量
    private transient volatile Map<String, T> enumConstantDirectory;    // 枚举常量目录
    private transient volatile Constructor<T> cachedConstructor;        // 缓存的构造器
    private transient volatile SoftReference<ReflectionData<T>> reflectionData; // 反射数据的软引用
    private transient volatile int classRedefinedCount;                 // 类重定义计数
    private transient volatile ClassRepository genericInfo;             // 生成的信息
    transient ClassValue.ClassValueMap classValueMap;                   // 类名的名称
    static {
        registerNatives();
    }

    /**
     * public方法
     */
    public String toString() {}                                 // 返回字符串
    @SuppressWarnings("preview")
    public String toGenericString() {}                          // 生成字符串
    @CallerSensitive
    public static Class<?> forName(String className) throws ClassNotFoundException {}   // 根据类名反射创建对象，默认调用的是下3个参数的forName方法，其中initialize=true，意思是初始化类
    @CallerSensitive
    public static Class<?> forName(String name, boolean initialize, ClassLoader loader) throws ClassNotFoundException {} // 创建对象，可以传入initialize=false不对类进行初始化。loader则是当前类的加载器
    @CallerSensitive
    public static Class<?> forName(Module module, String name) {}   // 创建对象，java9（模块化子系统特性新增的方法）根据模块和类名创建对象
    @CallerSensitive
    public URL getResource(String name) {}                      // 获取资源文件
    @CallerSensitive
    @Deprecated(since = "9")
    public T newInstance() throws InstantiationException, IllegalAccessException {} // 实际上就是调用类的空参构造方法创建一个对象，需要泛型
    @HotSpotIntrinsicCandidate
    public native boolean isInstance(Object obj); // 等价于 instanceof 关键字的作用，用于判断类是否是obj的子类或实例对象
    @HotSpotIntrinsicCandidate
    public native boolean isAssignableFrom(Class<?> cls); // 类是否和cls是同一个类、接口 或者 是cls的父类、父接口。是返回true和instanceof一样的意思
    @HotSpotIntrinsicCandidate
    public native boolean isInterface();        // 是否是接口类
    @HotSpotIntrinsicCandidate
    public native boolean isArray();            // 是否是数组类
    @HotSpotIntrinsicCandidate
    public native boolean isPrimitive();        // 是否是基本类型boolean、byte、char、short、int、float、long、double
    public boolean isAnnotation() {}            // 是否是注解类
    public boolean isSynthetic() {}             // 是否是合成类
    /**
     * getName方法返回的字符串对比
     * 不同类型的class调用getName方法返回的字符串如下
     *
     *      class or interface类型的二进制名称是 类全路径
     *
     *      boolean  类型的二进制名称是 Z
     *      byte     类型的二进制名称是 B
     *      char     类型的二进制名称是 C
     *      double   类型的二进制名称是 D
     *      float    类型的二进制名称是 F
     *      int      类型的二进制名称是 I
     *      long     类型的二进制名称是 J
     *      short    类型的二进制名称是 S
     *例如：
     *  String.class.getName() 将 returns "java.lang.String"
     *  byte.class.getName() 将 returns "byte"
     *  (new Object[3]).getClass().getName() 将 returns "[Ljava.lang.Object;"
     *  (new int[3][4][5][6][7][8][9]).getClass().getName() 将 returns "[[[[[[[I"
     */
    public String getName() {}                  // 返回声明类的二进制名称
    @HotSpotIntrinsicCandidate
    public native Class<? super T> getSuperclass();// 获取父类
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public ClassLoader getClassLoader() {}      // 获取当前类的类加载器
    public Module getModule() {}                // 获取当前模块
    @SuppressWarnings("unchecked")
    public TypeVariable<Class<T>>[] getTypeParameters() {}  // 返回类型变量
    public Type getGenericSuperclass() {}       //
    public Package getPackage() {}              // 获取包
    public String getPackageName() {}           // 获取包名字符串
    public Class<?>[] getInterfaces() {}        // 获取类的接口
    public Type[] getGenericInterfaces() {}     //
    public Class<?> getComponentType() {}       //
    @HotSpotIntrinsicCandidate
    public native int getModifiers();           //
    public native Object[] getSigners();        //
    @CallerSensitive
    public Method getEnclosingMethod() throws SecurityException {}              //
    @CallerSensitive
    public Constructor<?> getEnclosingConstructor() throws SecurityException {} //
    @CallerSensitive
    public Class<?> getDeclaringClass() throws SecurityException {}             //
    @CallerSensitive
    public Class<?> getEnclosingClass() throws SecurityException {}             //
    public String getSimpleName() {}            //
    public String getTypeName() {}              //
    public String getCanonicalName() {}         //
    public boolean isAnonymousClass() {}        //
    public boolean isLocalClass() {}            //
    public boolean isMemberClass() {}           //
    @CallerSensitive
    public Class<?>[] getClasses() {}           //
    @CallerSensitive
    public Field[] getFields() throws SecurityException {}      // 获取类的成员属性（变量或常量）
    @CallerSensitive
    public Method[] getMethods() throws SecurityException {}    // 获取类的方法
    @CallerSensitive
    public Constructor<?>[] getConstructors() throws SecurityException {}   // 获取类的构造方法
    @CallerSensitive
    public Field getField(String name) throws NoSuchFieldException, SecurityException {} // 获取成员属性为 name值相同的成员属性
    @CallerSensitive
    public Method getMethod(String name, Class<?>... parameterTypes) throws NoSuchMethodException, SecurityException {} // 获取方法名为name值指定的方法，后面的是方法需要的参数
    @CallerSensitive
    public Constructor<T> getConstructor(Class<?>... parameterTypes) throws NoSuchMethodException, SecurityException {}// 获取构造方法。带参或无参的构造方法
    @CallerSensitive
    public Class<?>[] getDeclaredClasses() throws SecurityException {}   //
    @CallerSensitive
    public Field[] getDeclaredFields() throws SecurityException {}      //
    @jdk.internal.PreviewFeature(feature = jdk.internal.PreviewFeature.Feature.RECORDS, essentialAPI = false)
    @SuppressWarnings("preview")
    @CallerSensitive
    public RecordComponent[] getRecordComponents() {}
    @CallerSensitive
    public Method[] getDeclaredMethods() throws SecurityException {}
    @CallerSensitive
    public Constructor<?>[] getDeclaredConstructors() throws SecurityException {}   // 获取默认的构造方法（空参构造方法）
    @CallerSensitive
    public Field getDeclaredField(String name) throws NoSuchFieldException, SecurityException {}    // 获取默认的成员属性
    @CallerSensitive
    public Method getDeclaredMethod(String name, Class<?>... parameterTypes) throws NoSuchMethodException, SecurityException {}
    @CallerSensitive
    public Constructor<T> getDeclaredConstructor(Class<?>... parameterTypes) throws NoSuchMethodException, SecurityException {}
    @CallerSensitive
    public InputStream getResourceAsStream(String name) {}
    public java.security.ProtectionDomain getProtectionDomain() {}
    public boolean desiredAssertionStatus() {}
    public boolean isEnum() {}
    @jdk.internal.PreviewFeature(feature = jdk.internal.PreviewFeature.Feature.RECORDS,essentialAPI = false)
    public boolean isRecord() {}
    public T[] getEnumConstants() {}
    @SuppressWarnings("unchecked")
    @HotSpotIntrinsicCandidate
    public T cast(Object obj) {}
    @SuppressWarnings("unchecked")
    public <U> Class<? extends U> asSubclass(Class<U> clazz) {}
    @SuppressWarnings("unchecked")
    public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {}
    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {}
    @Override
    public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationClass) {}
    public Annotation[] getAnnotations() {}
    @Override
    @SuppressWarnings("unchecked")
    public <A extends Annotation> A getDeclaredAnnotation(Class<A> annotationClass) {}
    @Override
    public <A extends Annotation> A[] getDeclaredAnnotationsByType(Class<A> annotationClass) {}
    public Annotation[] getDeclaredAnnotations() {}
    public AnnotatedType getAnnotatedSuperclass() {}
    public AnnotatedType[] getAnnotatedInterfaces() {}
    @CallerSensitive
    public Class<?> getNestHost() {}
    public boolean isNestmateOf(Class<?> c) {}
    @CallerSensitive
    public Class<?>[] getNestMembers() {}
    @Override
    public String descriptorString() {}
    @Override
    public Class<?> componentType() {}
    @Override
    public Class<?> arrayType() {}
    @Override
    public Optional<ClassDesc> describeConstable() {}



    private static native Class<?> forName0(String name, boolean initialize, ClassLoader loader, Class<?> caller) throws ClassNotFoundException;
    private static native boolean desiredAssertionStatus0(Class<?> clazz);
    private static native void registerNatives();

    static native Class<?> getPrimitiveClass(String name);
    native byte[] getRawAnnotations();
    native byte[] getRawTypeAnnotations();
    native void setSigners(Object[] signers);
    private native Class<?>[] getNestMembers0();
    private native Class<?> getNestHost0();
    private native Class<?>[] getInterfaces0();
    private native String initClassName();
    private static final Class<?> JAVA_LANG_RECORD_CLASS = javaLangRecordClass();
    private native Object[] getEnclosingMethod0();
    private native Class<?> getDeclaringClass0();
    private native String getSimpleBinaryName0();
    private native String getGenericSignature0();
    native ConstantPool getConstantPool();
    private native Field[] getDeclaredFields0(boolean publicOnly);
    private native Method[] getDeclaredMethods0(boolean publicOnly);
    private native Constructor<T>[] getDeclaredConstructors0(boolean publicOnly);
    private native Class<?>[] getDeclaredClasses0();
    @SuppressWarnings("preview")
    private native RecordComponent[] getRecordComponents0();
    private native boolean isRecord0();


    /**
     * private方法
     */
    List<Method> getDeclaredPublicMethods(String name, Class<?>... parameterTypes) {}
    ClassLoader getClassLoader0() {}
    static String typeVarBounds(TypeVariable<?> typeVar) {}
    static byte[] getExecutableTypeAnnotationBytes(Executable ex) {}
    private static Class<?> javaLangRecordClass() {}
    private static ReflectionFactory getReflectionFactory() {}
    private AnnotationData annotationData() {}
    private AnnotationData createAnnotationData(int classRedefinedCount) {}
    boolean casAnnotationType(AnnotationType oldType, AnnotationType newType) {}
    AnnotationType getAnnotationType() {}
    Map<Class<? extends Annotation>, Annotation> getDeclaredAnnotationMap() {}
    T[] getEnumConstantsShared() {}
    Map<String, T> enumConstantDirectory() {}
    private String cannotCastMsg(Object obj) {}
    private Class(ClassLoader loader, Class<?> arrayComponentType) {}
    private Class<?>[] getInterfaces(boolean cloneArray) {}
    private EnclosingMethodInfo getEnclosingMethodInfo() {}
    private static Class<?> toClass(Type o) {}
    private String getSimpleName0() {}
    private String getCanonicalName0() {}
    private String getSimpleBinaryName() {}
    private boolean isTopLevelClass() {}
    private boolean isLocalOrAnonymousClass() {}
    private boolean hasEnclosingMethodInfo() {}
    private boolean isOpenToCaller(String name, Class<?> caller) {}
    private native java.security.ProtectionDomain getProtectionDomain0();
    private void checkMemberAccess(SecurityManager sm, int which,Class<?> caller, boolean checkProxyInterfaces) {}
    private void checkPackageAccess(SecurityManager sm, final ClassLoader ccl, boolean checkProxyInterfaces) {}
    private String resolveName(String name) {}
    private ReflectionData<T> reflectionData() {}
    private ReflectionData<T> newReflectionData(SoftReference<ReflectionData<T>> oldReflectionData, int classRedefinedCount) {}
    private Field[] privateGetDeclaredFields(boolean publicOnly) {}
    private Field[] privateGetPublicFields() {}
    private GenericsFactory getFactory() {}
    private ClassRepository getGenericInfo() {}
    private static void addAll(Collection<Field> c, Field[] o) {}
    private Constructor<T>[] privateGetDeclaredConstructors(boolean publicOnly) {}
    private Method[] privateGetDeclaredMethods(boolean publicOnly) {}
    private Method[] privateGetPublicMethods() {}
    private static Field searchFields(Field[] fields, String name) {}
    private Field getField0(String name) {}
    private static Method searchMethods(Method[] methods, String name, Class<?>[] parameterTypes) {}
    private Method getMethod0(String name, Class<?>[] parameterTypes) {}
    private PublicMethods.MethodList getMethodsRecursive(String name, Class<?>[] parameterTypes, boolean includeStatic) {}
    private Constructor<T> getConstructor0(Class<?>[] parameterTypes, int which) throws NoSuchMethodException {}
    private static boolean arrayContentsEq(Object[] a1, Object[] a2) {}
    private static Field[] copyFields(Field[] arg) {}
    private static Method[] copyMethods(Method[] arg) {}
    private static <U> Constructor<U>[] copyConstructors(Constructor<U>[] arg) {}
    private String methodToString(String name, Class<?>[] argTypes) {}


    /**
     * 静态内部类
     */
    private static final class EnclosingMethodInfo {
        private final Class<?> enclosingClass;
        private final String name;
        private final String descriptor;
        static void validate(Object[] enclosingInfo) {}
        EnclosingMethodInfo(Object[] enclosingInfo) {}
        boolean isPartial() {}
        boolean isConstructor() {}
        boolean isMethod() {}
        Class<?> getEnclosingClass() {}
        String getName() {}
        String getDescriptor() {}

    }
    private static class Atomic {
        private static final Unsafe unsafe = Unsafe.getUnsafe();
        private static final long reflectionDataOffset = unsafe.objectFieldOffset(Class.class, "reflectionData");
        private static final long annotationTypeOffset = unsafe.objectFieldOffset(Class.class, "annotationType");
        private static final long annotationDataOffset = unsafe.objectFieldOffset(Class.class, "annotationData");
        static <T> boolean casReflectionData(Class<?> clazz, SoftReference<ReflectionData<T>> oldData, SoftReference<ReflectionData<T>> newData) {}
        static <T> boolean casAnnotationType(Class<?> clazz, AnnotationType oldType, AnnotationType newType) {}
        static <T> boolean casAnnotationData(Class<?> clazz, AnnotationData oldData, AnnotationData newData) {}
    }


    private static class ReflectionData<T> {
        volatile Field[] declaredFields;
        volatile Field[] publicFields;
        volatile Method[] declaredMethods;
        volatile Method[] publicMethods;
        volatile Constructor<T>[] declaredConstructors;
        volatile Constructor<T>[] publicConstructors;
        volatile Field[] declaredPublicFields;
        volatile Method[] declaredPublicMethods;
        volatile Class<?>[] interfaces;
        String simpleName;
        String canonicalName;
        static final String NULL_SENTINEL = new String();
        final int redefinedCount;
        ReflectionData(int redefinedCount) {}
    }

    private static class AnnotationData {
        final Map<Class<? extends Annotation>, Annotation> annotations;
        final Map<Class<? extends Annotation>, Annotation> declaredAnnotations;
        final int redefinedCount;
        AnnotationData(Map<Class<? extends Annotation>, Annotation> annotations, Map<Class<? extends Annotation>, Annotation> declaredAnnotations, int redefinedCount) {}
    }




}
