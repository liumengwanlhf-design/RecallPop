package dev.ankigate;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** User-driven HTTPS update with immutable per-declaration installation files. */
public final class UpdateActivity extends Activity {
 static boolean inProgress,configuring;static UpdateActivity current;
 boolean visible,busy,owns,grantSettings,needsCheck;volatile boolean cancelled;volatile long generation;volatile HttpsURLConnection connection;
 final ExecutorService worker=Executors.newSingleThreadExecutor();TextView status;EditText address;Button download,install;
 android.content.SharedPreferences prefs;JSONObject manifest;File apk;long installed;
 @Override public void onCreate(Bundle state){super.onCreate(state);if(inProgress){finish();return;}owns=true;current=this;inProgress=true;configuring=false;prefs=getSharedPreferences("gate",0);try{installed=getPackageManager().getPackageInfo(getPackageName(),0).getLongVersionCode();}catch(Exception e){finish();return;}
  LinearLayout root=ShellUi.column(this);ScrollView scroll=new ScrollView(this);scroll.addView(root);ShellUi.safeInsets(scroll);setContentView(scroll);
  root.addView(ShellUi.title(this,"检查应用更新"));root.addView(ShellUi.text(this,"检查、下载和安装均由你确认；安装由系统处理。更新页面期间暂停自动介入，离开会取消下载。",15));
  address=new EditText(this);address.setSingleLine(true);address.setText(prefs.getString("update_url","https://pop.382910.xyz/update.json"));root.addView(address);
  status=ShellUi.text(this,"当前版本代码："+installed,15);root.addView(status);
  root.addView(ShellUi.button(this,"保存地址 / 检查更新",v->check()));
  download=ShellUi.button(this,"下载已确认的更新",v->download());download.setEnabled(false);root.addView(download);
  install=ShellUi.button(this,"打开系统覆盖安装",v->install());install.setEnabled(false);root.addView(install);
  root.addView(ShellUi.button(this,"返回",v->finish()));
 }
 void checkCancelled(long request){if(cancelled||generation!=request||Thread.currentThread().isInterrupted())throw new IllegalStateException("已取消");}
 HttpsURLConnection open(String url,long request)throws Exception{checkCancelled(request);HttpsURLConnection c=(HttpsURLConnection)new URL(UpdatePolicy.https(url)).openConnection();connection=c;c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setRequestProperty("Cache-Control","no-cache, no-store");c.setRequestProperty("Pragma","no-cache");c.setConnectTimeout(15000);c.setReadTimeout(20000);try{c.connect();checkCancelled(request);int response=c.getResponseCode();if(response!=200)throw new IOException("HTTP "+response+"（仅接受直接HTTPS 200响应）");return c;}catch(Exception e){c.disconnect();if(connection==c)connection=null;throw e;}}
 long begin(){busy=true;cancelled=false;long request=++generation;download.setEnabled(false);install.setEnabled(false);status.setText("处理中…可随时返回取消。");return request;}
 void result(long request,String message,Runnable ready){runOnUiThread(()->{if(isDestroyed()||generation!=request||current!=this)return;busy=false;if(!visible||cancelled){needsCheck=true;return;}status.setText(message);if(ready!=null)ready.run();});}
 void check(){if(busy)return;String url;try{url=UpdatePolicy.https(address.getText().toString());prefs.edit().putString("update_url",url).apply();}catch(Exception e){status.setText(e.getMessage());return;}manifest=null;apk=null;long request=begin();worker.execute(()->{try{HttpsURLConnection c=open(url,request);byte[] data;try(InputStream input=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=input.read(b))!=-1){checkCancelled(request);if(out.size()+n>65536)throw new IOException("更新说明过大");out.write(b,0,n);}data=out.toByteArray();}finally{c.disconnect();if(connection==c)connection=null;}
   JSONObject m=new JSONObject(new String(data,java.nio.charset.StandardCharsets.UTF_8));long v=m.getLong("versionCode"),size=m.getLong("size");UpdatePolicy.metadata(v,size,m.getString("sha256"));UpdatePolicy.https(m.getString("apkUrl"));if(!UpdatePolicy.newer(v,installed)){result(request,"当前已是此地址提供的最新版本；不会下载或降级。",null);return;}String info="声明版本："+m.getString("versionName")+"（"+v+"）\n大小："+size+"字节\n"+m.optString("notes","");result(request,info,()->{manifest=m;download.setEnabled(true);});
  }catch(Exception e){result(request,"检查失败："+e.getMessage(),null);}});}
 PackageInfo validate(File file,JSONObject m,long request)throws Exception{
  String hash;try(InputStream input=new FileInputStream(file)){hash=UpdateFiles.hash(input,()->checkCancelled(request));}UpdatePolicy.downloaded(file.length(),m.getLong("size"),hash,m.getString("sha256"));
  PackageManager pm=getPackageManager();PackageInfo archive=pm.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES),currentPackage=pm.getPackageInfo(getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
  if(archive==null||archive.signingInfo==null)throw new IOException("APK无法解析签名");boolean same=new HashSet<>(Arrays.asList(archive.signingInfo.getApkContentsSigners())).equals(new HashSet<>(Arrays.asList(currentPackage.signingInfo.getApkContentsSigners())));
  UpdatePolicy.archive(archive.packageName,archive.getLongVersionCode(),m.getLong("versionCode"),currentPackage.getLongVersionCode(),same);checkCancelled(request);return archive;
 }
 String actual(PackageInfo archive,File file,JSONObject m)throws Exception{return "已验证实际安装包："+archive.versionName+"（"+archive.getLongVersionCode()+"）\n文件："+file.getName()+"\n大小："+file.length()+"字节\nSHA256："+m.getString("sha256").substring(0,16)+"…";}
 void download(){if(busy||manifest==null)return;JSONObject m=manifest;long request=begin();worker.execute(()->{File temporary=null;try{
   UpdateFiles.Identity identity=UpdateFiles.identity(m.getLong("versionCode"),m.getString("sha256"));File file=UpdateFiles.finalFile(getCacheDir(),identity);
   if(!file.exists()){temporary=UpdateFiles.temporary(getCacheDir());HttpsURLConnection c=open(m.getString("apkUrl"),request);long expected=m.getLong("size");try(InputStream input=c.getInputStream();OutputStream out=new FileOutputStream(temporary)){byte[] b=new byte[16384];int n;long total=0,deadline=android.os.SystemClock.elapsedRealtime()+120000;while((n=input.read(b))!=-1){if(android.os.SystemClock.elapsedRealtime()>deadline)throw new IOException("下载超过两分钟，请重试");checkCancelled(request);total+=n;if(total>expected||total>UpdatePolicy.MAX_SIZE)throw new IOException("下载超过声明大小");out.write(b,0,n);}}finally{c.disconnect();if(connection==c)connection=null;}
    validate(temporary,m,request);checkCancelled(request);UpdateFiles.publish(temporary,file);
   }
   PackageInfo archive=validate(file,m,request);String info=actual(archive,file,m)+"\n请点击打开系统安装。";result(request,info,()->{apk=file;install.setEnabled(true);});
  }catch(Exception e){result(request,"下载/校验失败："+e.getMessage(),()->download.setEnabled(true));}finally{if(temporary!=null&&temporary.exists())temporary.delete();}});}
 void install(){if(busy||apk==null||manifest==null)return;if(!getPackageManager().canRequestPackageInstalls()){status.setText("请仅允许本应用安装未知应用，返回后再次点击安装；届时会重新核对实际包。");grantSettings=true;configuring=true;startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));return;}
  File file=apk;JSONObject m=manifest;long request=begin();worker.execute(()->{try{PackageInfo archive=validate(file,m,request);String info=actual(archive,file,m);Uri uri=Uri.parse("content://dev.ankigate.update/apk/"+file.getName());result(request,info+"\n由系统请求覆盖安装。",()->{Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri(file.getName(),uri));try{configuring=true;startActivity(intent);}catch(Exception e){configuring=false;status.setText("系统安装器无法打开："+e.getMessage());install.setEnabled(true);}});}catch(Exception e){result(request,"安装前校验失败："+e.getMessage(),null);}});}
 @Override protected void onStart(){super.onStart();if(!owns)return;if(inProgress&&current!=this){owns=false;finish();return;}current=this;inProgress=true;}
 @Override protected void onResume(){super.onResume();if(!owns)return;visible=true;configuring=false;if(needsCheck){needsCheck=false;manifest=null;apk=null;download.setEnabled(false);install.setEnabled(false);status.setText("本次在后台结束，未交接安装；请重新检查。");}if(grantSettings){grantSettings=false;if(apk!=null&&manifest!=null){install.setEnabled(true);status.setText("安装来源设置已返回，请再次点击安装以核对实际包；不会自动打开安装器。");}}if(GateService.instance!=null)GateService.instance.ownActivity(true);}
 @Override protected void onPause(){visible=false;super.onPause();}
 @Override protected void onStop(){cancelled=true;generation++;busy=false;HttpsURLConnection c=connection;if(c!=null)c.disconnect();if(!grantSettings){manifest=null;apk=null;}if(owns){download.setEnabled(false);install.setEnabled(false);status.setText("已离开更新页面；返回不会自动打开安装器。");}if(current==this){inProgress=false;if(GateService.instance!=null)GateService.instance.ownActivity(false);}super.onStop();}
 @Override protected void onDestroy(){cancelled=true;generation++;worker.shutdownNow();if(current==this){current=null;inProgress=false;configuring=false;}super.onDestroy();}
}
