package java.io;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import java.security.SecureRandom;

public class File implements Serializable, Comparable<File>{
    private final String path;// 存储路径
    private final transient int prefixLength;// 前缀长度
    private static final FileSystem fs = DefaultFileSystem.getFileSystem(); //获取文件系统，例如ntfs、ext3等
    public static final char separatorChar = fs.getSeparator();             // 路径分割符
    public static final char pathSeparatorChar = fs.getPathSeparator();     // 文件系统的路径分割符
    public static final String separator = "" + separatorChar;              // 返回字符串类型的
    public static final String pathSeparator = "" + pathSeparatorChar;      // 存储路径
    private static enum PathStatus { INVALID, CHECKED };// 文件状态
    private transient PathStatus status = null;         // 默认为null
    private static final jdk.internal.misc.Unsafe UNSAFE = jdk.internal.misc.Unsafe.getUnsafe();
    private static final long PATH_OFFSET = UNSAFE.objectFieldOffset(File.class, "path");
    private static final long PREFIX_LENGTH_OFFSET = UNSAFE.objectFieldOffset(File.class, "prefixLength");
    @java.io.Serial
    private static final long serialVersionUID = 301077366599181567L;  // 序列化版本号
    private transient volatile Path filePath; //文件路径对象


    /**
     * 构造方法
     */
    public File(String pathname) {} // 最常用的
    private File(String pathname, int prefixLength) {}
    private File(String child, File parent) {}
    public File(String parent, String child) {}
    public File(File parent, String child) {}
    public File(URI uri) {}
    
    
    /**
     * 文件状态操作
     */
    public boolean canRead() {}     // 文件是否可读
    public boolean canWrite() {}    // 文件是否可写
    public boolean isDirectory() {} // 是否是目录
    public boolean exists() {}      // 文件是否已存在
    public boolean isFile() {}      // 是否是文件
    public boolean isHidden() {}    // 是否是隐藏文件
    final boolean isInvalid() {}    // 判断文件状态是否是无效的，无效返回true
    public boolean setLastModified(long time) {}// 设置修改文件的时间
    public long lastModified() {}               // 返回上一次修改文件时间戳
    public boolean setReadOnly() {} // 设置为只读，true表示成功设置，flase表示失败
    public boolean setWritable(boolean writable, boolean ownerOnly) {} //设置为可写
    public boolean setWritable(boolean writable) {} // 设置为可写
    public boolean setReadable(boolean readable, boolean ownerOnly) {}// 设置为可读
    public boolean setReadable(boolean readable) {}// 设置为可读
    public boolean setExecutable(boolean executable, boolean ownerOnly) {}// 设置为可执行
    public boolean setExecutable(boolean executable) {}
	/**
	 * 常用文件操作
	 */
    public long length() {}                 // 返回文件大小
    public String getName() {}              // 获取文件名称
    public boolean renameTo(File dest) {}   // 重命名文件
    public boolean delete() {}              // 删除文件
    public String getParent() {}            // 获取父级目录名
    public File getParentFile() {}          //返回父级目录
    public String getPath() {}              //返回文件路径
    public boolean isAbsolute() {}          // 绝对路径返回true
    public String getAbsolutePath(){}       // 返回文件的绝对路径
    public File getAbsoluteFile() {}        // 返回绝对路径文件
    public String getCanonicalPath() {}     // 返回规范的文件路径
    public Path toPath() {}         // path字符串转Path对象
    int getPrefixLength() {}        // 获取文件前缀名长度值

    /**
     * 常用的目录操作
     * 获取目录下所有文件、创建目录
     */
    public static File[] listRoots() {} // 返回根目录
    public String[] list() {}   // 返回path目录下的所有文件名
    public String[] list(FilenameFilter filter) {} // 返回经过过滤后的所有文件名
    public File[] listFiles() {} // 获取目录下的所有文件对象
    public File[] listFiles(FilenameFilter filter) {} // 获得过滤后的文件对象
    public File[] listFiles(FileFilter filter) {}   // 获得过滤后的文件对象
    public boolean mkdir() {}// 目录不存在则创建目录并返回true，否则返回false
    public boolean mkdirs() {}// 创建文件夹，父级目录不存在也会创建父级目录
    public boolean equals(Object obj) {}    // 判断文件是否是同一个文件
    public String toString() {} // 返回文件路径
    public int compareTo(File pathname) {}  //比较pathname文件是否与当前文件相同
    public int hashCode() {}                // 返回当前文件的hash值

    /**
     * 下面的都是不常用方法
     * 可以不用看
     */
    public boolean createNewFile() throws IOException {} // 文件已存在返回flase，不存在则创建并返回true
    public void deleteOnExit() {} // 程序终止时（jvm退出时）删除文件
    private static String slashify(String path, boolean isDirectory) {} // isDirectory=true返回标准化的路径
    public File getCanonicalFile() throws IOException {}// 返回规范的文件
    @Deprecated
    public URL toURL() throws MalformedURLException {}// 返回等效文件URL的URL对象
    public URI toURI() {} // 转换为uri
    public boolean canExecute() {} // 是否可执行
    public long getTotalSpace() {} // 获取文件大小（byte）
    public long getFreeSpace() {} // 获取文件剩余空间大小
    public long getUsableSpace() {} //获取可使用空间大小
    public static File createTempFile(String prefix, String suffix,File directory) throws IOException {} // 指定路径创建临时文件
    public static File createTempFile(String prefix, String suffix) throws IOException {} // 当前路径下创建临时文件
    // 序列化
    @java.io.Serial
    private synchronized void writeObject(java.io.ObjectOutputStream s) throws IOException {}
    // 反序列化
    @java.io.Serial
    private synchronized void readObject(java.io.ObjectInputStream s) throws IOException, ClassNotFoundException {}
    
    private static class TempDirectory {
        private static final SecureRandom random = new SecureRandom();
        private TempDirectory() { }
        private static final File tmpdir = new File(GetPropertyAction.privilegedGetProperty("java.io.tmpdir"));
        static File location() {} // 返回临时目录对象
        private static int shortenSubName(int subNameLength, int excess,int nameMin) {}  // 
        static File generateFile(String prefix, String suffix, File dir) throws IOException{} // 生称文件
    }
}
