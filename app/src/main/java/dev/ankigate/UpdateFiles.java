package dev.ankigate;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.*;

/** Private immutable identities; temporary downloads are never installation URIs. */
final class UpdateFiles {
 record Identity(long code,String hash){String name(){return "update-"+code+"-"+hash+".apk";}}
 static final Pattern NAME=Pattern.compile("update-([1-9][0-9]{0,18})-([0-9a-f]{64})\\.apk");
 static Identity identity(long code,String hash){if(code<=0||hash==null||!hash.matches("(?i)[0-9a-f]{64}"))throw new IllegalArgumentException("无效APK身份");return new Identity(code,hash.toLowerCase(Locale.ROOT));}
 static Identity named(String name){Matcher match=NAME.matcher(name);if(!match.matches())throw new IllegalArgumentException("Unsupported update identity");try{return identity(Long.parseLong(match.group(1)),match.group(2));}catch(NumberFormatException e){throw new IllegalArgumentException("Unsupported version",e);}}
 static Identity path(String path){if(path==null||!path.startsWith("/apk/"))throw new IllegalArgumentException("Unsupported update URI");return named(path.substring(5));}
 static File directory(File cache)throws IOException{return new File(cache.getCanonicalFile(),"updates");}
 static File finalFile(File cache,Identity identity)throws IOException{File file=new File(directory(cache),identity.name());if(!file.getCanonicalFile().equals(file.getAbsoluteFile()))throw new IOException("Unsupported update path");return file;}
 static File temporary(File cache)throws IOException{File dir=directory(cache);if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建更新缓存目录");return File.createTempFile("download-",".part",dir);}
 static synchronized void publish(File temporary,File target)throws IOException{
  if(!temporary.getCanonicalFile().getParentFile().equals(target.getCanonicalFile().getParentFile())||!temporary.getName().startsWith("download-")||!temporary.getName().endsWith(".part"))throw new IOException("Unsupported update publication");
  named(target.getName());if(target.exists())return;
  // Every application publisher uses this lock; final files are never overwritten/deleted.
  Files.move(temporary.toPath(),target.toPath());target.setReadOnly();
 }
 static String hash(InputStream input,Runnable check)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] bytes=new byte[16384];int count;while((count=input.read(bytes))!=-1){if(check!=null)check.run();digest.update(bytes,0,count);}if(check!=null)check.run();StringBuilder value=new StringBuilder();for(byte b:digest.digest())value.append(String.format(Locale.ROOT,"%02x",b&255));return value.toString();}
}
