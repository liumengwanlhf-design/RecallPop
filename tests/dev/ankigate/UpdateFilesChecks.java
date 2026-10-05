package dev.ankigate;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class UpdateFilesChecks {
 static void expect(boolean value,String why){if(!value)throw new AssertionError(why);}
 static void rejects(Runnable call){try{call.run();throw new AssertionError("unsafe update identity accepted");}catch(IllegalArgumentException expected){}}
 public static void main(String[] args)throws Exception{
  String hash="a".repeat(64);UpdateFiles.Identity identity=UpdateFiles.identity(15,hash.toUpperCase(Locale.ROOT));expect(UpdateFiles.path("/apk/"+identity.name()).equals(identity),"roundtrip canonical identity");
  for(String path:new String[]{"/update.apk","/apk/../update.apk","/apk/"+identity.name()+"/x","/apk/%2e%2e/"+identity.name(),"/apk/update-015-"+hash+".apk","/apk/update-9223372036854775808-"+hash+".apk","/apk/update-15-"+hash.toUpperCase(Locale.ROOT)+".apk","/apk/download-123.part"})rejects(()->UpdateFiles.path(path));
  rejects(()->UpdateFiles.identity(0,hash));rejects(()->UpdateFiles.identity(15,"bad"));
  Path root=Files.createTempDirectory("pop-update-files-");try{
   File first=UpdateFiles.temporary(root.toFile()),second=UpdateFiles.temporary(root.toFile());expect(!first.equals(second),"each request has independent temp");Files.writeString(first.toPath(),"first validated bytes");Files.writeString(second.toPath(),"second bytes");
   File finalFile=UpdateFiles.finalFile(root.toFile(),identity);UpdateFiles.publish(first,finalFile);UpdateFiles.publish(second,finalFile);expect(Files.readString(finalFile.toPath()).equals("first validated bytes")&&second.isFile(),"later publisher cannot overwrite immutable final");
   File next=UpdateFiles.temporary(root.toFile());Files.writeString(next.toPath(),"new request bytes");second.delete();expect(finalFile.isFile()&&next.isFile(),"cancel old temp cannot delete installed identity or newer temp");
   File another=UpdateFiles.finalFile(root.toFile(),UpdateFiles.identity(16,"b".repeat(64)));UpdateFiles.publish(next,another);expect(!another.equals(finalFile)&&Files.readString(finalFile.toPath()).equals("first validated bytes"),"different declaration has distinct final path");
   try(InputStream input=new FileInputStream(finalFile)){expect(UpdateFiles.hash(input,null).length()==64,"full stream digest");}
  }finally{try(var paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList()){path.toFile().setWritable(true);Files.delete(path);}}}
  System.out.println("identity traversal/overflow/legacy rejection, separate temps, no overwrite and cancellation isolation passed");
 }
}
