package dev.ankigate;
import java.net.URI;
final class UpdatePolicy {
 static final long MAX_SIZE=64L*1024*1024;
 static boolean newer(long declared,long installed){return declared>installed;}
 static String https(String value){URI u=URI.create(value.trim());if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null)throw new IllegalArgumentException("地址必须是HTTPS且无账号或片段");return u.toASCIIString();}
 static void metadata(long version,long size,String hash){if(version<=0||size<=0||size>MAX_SIZE||!hash.matches("(?i)[0-9a-f]{64}"))throw new IllegalArgumentException("更新版本、大小或SHA256无效");}
 static void downloaded(long actual,long expected,String actualHash,String expectedHash){if(actual!=expected||!actualHash.equalsIgnoreCase(expectedHash))throw new IllegalArgumentException("APK大小或SHA256不匹配，拒绝安装");}
 static void archive(String pkg,long archive,long declared,long installed,boolean sameSigner){if(!"dev.ankigate".equals(pkg)||archive!=declared||archive<=installed||!sameSigner)throw new IllegalArgumentException("APK包名、精确版本或签名不符合覆盖安装条件");}
}
