package java.lang;



import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 这个类的代码和方法太多,我就直接去掉方法体内容
 */
public abstract class ClassLoader {

    private final ClassLoader parent;
    private final String name;          // 类加载器名称
    private final Module unnamedModule; // 当前类加载器的未命名模块
    private final String nameAndId;     // 存储异常消息
    private final ConcurrentHashMap<String, Object> parallelLockMap;    // 将已加载类信息存入ConcurrentHashMap
    private final Map <String, Certificate[]> package2certs;
    private static String usr_paths[];
    private static String sys_paths[];
    private static final Certificate[] nocerts = new Certificate[0];    // 证书空数组
    private final ConcurrentHashMap<String, NamedPackage> packages = new ConcurrentHashMap<>();
    private final Vector<java.lang.Class<?>> classes = new Vector<>();
    private final ProtectionDomain defaultDomain = new ProtectionDomain(new CodeSource(null, (Certificate[]) null), null, this, null);
    private static final Set<String> loadedLibraryNames = new HashSet<>();
    private static volatile Map<String, NativeLibrary> systemNativeLibraries;
    private volatile Map<String, NativeLibrary> nativeLibraries;
    final Object assertionLock;
    private boolean defaultAssertionStatus = false;
    private Map<String, Boolean> packageAssertionStatus = null;
    Map<String, Boolean> classAssertionStatus = null;
    static {
        registerNatives();
    }

    /**
     * 构造方法
     */
    private ClassLoader(Void unused, String name, ClassLoader parent) {} // 构造方法
    protected ClassLoader(String name, ClassLoader parent) {}
    protected ClassLoader(ClassLoader parent) {}
    protected ClassLoader() {}

    /**
     * ==================================
     * public方法
     * ==================================
     */

    /**
     * 获取类加载器
     */
    @CallerSensitive public final ClassLoader getParent() {}                // 获取父类加载器
    @CallerSensitive public static ClassLoader getPlatformClassLoader() {}  // 获取PlatformClassLoader
    @CallerSensitive public static ClassLoader getSystemClassLoader() {}    // 获取SystemClassLoader

    public java.lang.Class<?> loadClass(String name) throws ClassNotFoundException {} // 加载类

    public static URL getSystemResource(String name) {}
    public static Enumeration<URL> getSystemResources(String name) throws IOException {}
    public static InputStream getSystemResourceAsStream(String name) {}
    public URL getResource(String name) {}
    public Enumeration<URL> getResources(String name) throws IOException {}
    public Stream<URL> resources(String name) {}
    public InputStream getResourceAsStream(String name) {}
    public void setDefaultAssertionStatus(boolean enabled) {}
    public void setPackageAssertionStatus(String packageName, boolean enabled) {}
    public void setClassAssertionStatus(String className, boolean enabled) {}
    public void clearAssertionStatus() {}
    public String getName() {}
    public final boolean isRegisteredAsParallelCapable() {}
    public final Module getUnnamedModule() {}
    public final Package getDefinedPackage(String name) {}
    public final Package[] getDefinedPackages() {}




    /**
     * =======================
     * native方法
     * =======================
     */
    private static native void registerNatives();
    private native java.lang.Class<?> findBootstrapClass(String name);
    private final native java.lang.Class<?> findLoadedClass0(String name);
    static native java.lang.Class<?> defineClass1(ClassLoader loader, String name, byte[] b, int off, int len, ProtectionDomain pd, String source);
    static native java.lang.Class<?> defineClass2(ClassLoader loader, String name, java.nio.ByteBuffer b, int off, int len, ProtectionDomain pd, String source);
    private static native String findBuiltinLib(String name);
    private static native AssertionStatusDirectives retrieveDirectives();


    /**
     * ============================
     * 私有方法
     * ============================
     */

    void addClass(java.lang.Class<?> c) {}
    private NamedPackage getNamedPackage(String pn, Module m) {}
    private static Void checkCreateClassLoader() {}
    private static Void checkCreateClassLoader(String name) {}
    private static String nameAndId(ClassLoader ld) {}
    final String name() {}
    protected java.lang.Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {}
    final java.lang.Class<?> loadClass(Module module, String name) {}
    protected Object getClassLoadingLock(String className) {}
    private void checkPackageAccess(java.lang.Class<?> cls, ProtectionDomain pd) {}
    protected java.lang.Class<?> findClass(String name) throws ClassNotFoundException {}
    protected java.lang.Class<?> findClass(String moduleName, String name) {}
    @Deprecated(since="1.1")
    protected final java.lang.Class<?> defineClass(byte[] b, int off, int len) throws ClassFormatError {}
    protected final java.lang.Class<?> defineClass(String name, byte[] b, int off, int len) throws ClassFormatError {}
    private ProtectionDomain preDefineClass(String name, ProtectionDomain pd) {}
    private String defineClassSourceLocation(ProtectionDomain pd) {}
    private void postDefineClass(java.lang.Class<?> c, ProtectionDomain pd) {}
    protected final java.lang.Class<?> defineClass(String name, byte[] b, int off, int len, ProtectionDomain protectionDomain) throws ClassFormatError {}
    protected final java.lang.Class<?> defineClass(String name, java.nio.ByteBuffer b, ProtectionDomain protectionDomain) throws ClassFormatError {}
    private boolean checkName(String name) {}
    private void checkCerts(String name, CodeSource cs) {}
    private boolean compareCerts(Certificate[] pcerts, Certificate[] certs) {}
    protected final void resolveClass(java.lang.Class<?> c) {}
    protected final java.lang.Class<?> findSystemClass(String name) throws ClassNotFoundException {}
    java.lang.Class<?> findBootstrapClassOrNull(String name) {}
    protected final java.lang.Class<?> findLoadedClass(String name) {}
    protected final void setSigners(java.lang.Class<?> c, Object[] signers) {}
    protected URL findResource(String moduleName, String name) throws IOException {}
    protected URL findResource(String name) {}
    protected Enumeration<URL> findResources(String name) throws IOException {}
    @CallerSensitive
    protected static boolean registerAsParallelCapable() {}
    static ClassLoader getBuiltinPlatformClassLoader() {}
    static ClassLoader getBuiltinAppClassLoader() {}
    static synchronized ClassLoader initSystemClassLoader() {}
    static void initLibraryPaths() {}
    boolean isAncestor(ClassLoader cl) {}
    private static boolean needsClassLoaderPermissionCheck(ClassLoader from, ClassLoader to) {}
    static ClassLoader getClassLoader(java.lang.Class<?> caller) {}
    static void checkClassLoaderPermission(ClassLoader cl, java.lang.Class<?> caller) {}
    private static volatile ClassLoader scl;
    Package definePackage(java.lang.Class<?> c) {}
    Package definePackage(String name, Module m) {}
    private Package toPackage(String name, NamedPackage p, Module m) {}
    protected Package definePackage(String name, String specTitle, String specVersion, String specVendor,
                                    String implTitle, String implVersion, String implVendor, URL sealBase) {}
    @Deprecated(since="9")
    protected Package getPackage(String name) {}
    protected Package[] getPackages() {}
    Stream<Package> packages() {}
    protected String findLibrary(String libname) {}
    private static String[] initializePath(String propName) {}
    static void loadLibrary(java.lang.Class<?> fromClass, String name, boolean isAbsolute) {}
    private static boolean loadLibrary0(java.lang.Class<?> fromClass, final File file) {}
    private static long findNative(ClassLoader loader, String entryName) {}
    private static Map<String, NativeLibrary> systemNativeLibraries() {}
    private Map<String, NativeLibrary> nativeLibraries() {}
    boolean desiredAssertionStatus(String className) {}
    private void initializeJavaAssertionMaps() {}
    ConcurrentHashMap<?, ?> createOrGetClassLoaderValueMap() {}
    private volatile ConcurrentHashMap<?, ?> classLoaderValueMap;
    private boolean trySetObjectField(String name, Object obj) {}

    /**
     * 静态内部类
     */
    private static class ParallelLoaders {
        private ParallelLoaders() {}
        private static final Set<java.lang.Class<? extends ClassLoader>> loaderTypes = Collections.newSetFromMap(new WeakHashMap<>());
        static {
            synchronized (loaderTypes) { loaderTypes.add(ClassLoader.class); }
        }
        static boolean register(java.lang.Class<? extends ClassLoader> c) {}
        static boolean isRegistered(java.lang.Class<? extends ClassLoader> c) {}
    }

    static class NativeLibrary {
        final java.lang.Class<?> fromClass;
        final String name;
        final boolean isBuiltin;
        long handle;
        int jniVersion;
        static Deque<NativeLibrary> nativeLibraryContext = new ArrayDeque<>(8);
        native boolean load0(String name, boolean isBuiltin);
        native long findEntry(String name);
        NativeLibrary(java.lang.Class<?> fromClass, String name, boolean isBuiltin) {}
        boolean load() {}
        static boolean loadLibrary(java.lang.Class<?> fromClass, String name, boolean isBuiltin) {}
        static java.lang.Class<?> getFromClass() {}
        static class Unloader implements Runnable {
            static final NativeLibrary UNLOADER = new NativeLibrary(null, "dummy", false);
            final String name;
            final long handle;
            final boolean isBuiltin;
            Unloader(String name, long handle, boolean isBuiltin) {}
            @Override public void run() {}
        }
        static native void unload(String name, boolean isBuiltin, long handle);
    }
}

final class CompoundEnumeration<E> implements Enumeration<E> {
    private final Enumeration<E>[] enums;
    private int index;
    public CompoundEnumeration(Enumeration<E>[] enums) {}
    private boolean next() {}
    public boolean hasMoreElements() {}
    public E nextElement() {}
}
