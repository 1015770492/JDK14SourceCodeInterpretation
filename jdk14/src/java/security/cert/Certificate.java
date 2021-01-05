package java.security.cert;

import sun.security.x509.X509CertImpl;

import java.security.*;
import java.util.Arrays;

public abstract class Certificate implements java.io.Serializable {

    @java.io.Serial
    private static final long serialVersionUID = -3585440601605666277L; // 序列化版本号
    private final String type;  // 证书类型
    private int hash = -1;      // 默认值-1，用于存储整数的hash值

    /**
     * 构造方法，传入证书类型
     */
    protected Certificate(String type) {}
    // 返回证书类型
    public final String getType() {
        return this.type;
    }
    // 当前整数是否和other类型相同，相同返回true
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Certificate)) {
            return false;
        }
        try {
            byte[] thisCert = X509CertImpl.getEncodedInternal(this);
            byte[] otherCert = X509CertImpl.getEncodedInternal((Certificate)other);

            return Arrays.equals(thisCert, otherCert);
        } catch (CertificateException e) {
            return false;
        }
    }
    // 得到当前整数的hash值
    public int hashCode() {
        int h = hash;
        if (h == -1) {
            try {
                h = Arrays.hashCode(X509CertImpl.getEncodedInternal(this));
            } catch (CertificateException e) {
                h = 0;
            }
            hash = h;
        }
        return h;
    }


    /**
     * 抽象方法
     */
    public abstract byte[] getEncoded() throws CertificateEncodingException; // 返回证书的编码
    public abstract void verify(PublicKey key) // 使用密钥key校验证书
        throws CertificateException, NoSuchAlgorithmException,
            InvalidKeyException, NoSuchProviderException,
            SignatureException;
    public abstract void verify(PublicKey key, String sigProvider) // 使用密钥key和签名供应商名称进行校验
        throws CertificateException, NoSuchAlgorithmException,
        InvalidKeyException, NoSuchProviderException,
        SignatureException;
    public void verify(PublicKey key, Provider sigProvider)         // 使用密钥key和签名供应商对象进行校验
        throws CertificateException, NoSuchAlgorithmException,
        InvalidKeyException, SignatureException {
        throw new UnsupportedOperationException();
    }

    public abstract String toString(); // 返回当前证书内容
    public abstract PublicKey getPublicKey(); // 从证书中获取公钥

    protected static class CertificateRep implements java.io.Serializable {
        @java.io.Serial
        private static final long serialVersionUID = -8563758940495660020L;// 序列化版本号
        private String type;    // 证书类型
        private byte[] data;    // 证书数据
        /**
         * 构造备用证书
         * 传入 证书类型、证书数据
         */
        protected CertificateRep(String type, byte[] data) {}

        // 反序列化，解析证书
        @java.io.Serial
        protected Object readResolve() throws java.io.ObjectStreamException {
            try {
                CertificateFactory cf = CertificateFactory.getInstance(type);
                return cf.generateCertificate
                        (new java.io.ByteArrayInputStream(data));
            } catch (CertificateException e) {
                throw new java.io.NotSerializableException
                        ("java.security.cert.Certificate: " +
                                type +
                                ": " +
                                e.getMessage());
            }
        }
    }

    // 更换证书序列化
    @java.io.Serial
    protected Object writeReplace() throws java.io.ObjectStreamException {
        try {
            return new CertificateRep(type, getEncoded());
        } catch (CertificateException e) {
            throw new java.io.NotSerializableException
                    ("java.security.cert.Certificate: " +
                            type +
                            ": " +
                            e.getMessage());
        }
    }
}