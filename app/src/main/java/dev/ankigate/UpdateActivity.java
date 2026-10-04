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
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** User-driven HTTPS update. Never installs or opens dialogs after leaving this page. */
public final class UpdateActivity extends Activity {
 static boolean inProgress,configuring;boolean visible,busy,owns;volatile boolean cancelled;volatile HttpsURLConnection connection;
 final ExecutorService worker=Executors.newSingleThreadExecutor();TextView status;EditText address;Button download,install;
 android.content.SharedPreferences prefs;JSONObject manifest;File apk;long installed;
 @Override public void onCreate(Bundle state){super.onCreate(state);if(inProgress){finish();return;}owns=true;inProgress=true;prefs=getSharedPreferences("gate",0);try{installed=getPackageManager().getPackageInfo(getPackageName(),0).getLongVersionCode();}catch(Exception e){finish();return;}
  LinearLayout root=ShellUi.column(this);ScrollView scroll=new ScrollView(this);scroll.addView(root);ShellUi.safeInsets(scroll);setContentView(scroll);
  root.addView(ShellUi.title(this,"检查应用更新"));root.addView(ShellUi.text(this,"检查、下载和安装均由你确认；安装由系统处理。更新页面期间暂停自动介入，离开会取消下载。",15));
  address=new EditText(this);address.setSingleLine(true);address.setText(prefs.getString("update_url","https://pop.382910.xyz/update.json"));root.addView(address);
  status=ShellUi.text(this,"当前版本代码："+installed,15);root.addView(status);
  root.addView(ShellUi.button(this,"保存地址 / 检查更新",v->check()));
  download=ShellUi.button(this,"下载已确认的更新",v->download());download.setEnabled(false);root.addView(download);
  install=ShellUi.button(this,"打开系统覆盖安装",v->install());install.setEnabled(false);root.addView(install);
  root.addView(ShellUi.button(this,"返回",v->finish()));
 }
 HttpsURLConnection open(String url)throws Exception{HttpsURLConnection c=(HttpsURLConnection)new URL(UpdatePolicy.https(url)).openConnection();connection=c;c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(20000);c.connect();if(c.getResponseCode()!=200){c.disconnect();throw new IOException("HTTP "+c.getResponseCode()+"（仅接受直接HTTPS 200响应）");}return c;}
 void begin(){busy=true;cancelled=false;download.setEnabled(false);install.setEnabled(false);status.setText("处理中…可随时返回取消。");}
 void result(String message,Runnable ready){runOnUiThread(()->{busy=false;if(isDestroyed()||!visible||cancelled)return;status.setText(message);if(ready!=null)ready.run();});}
 void check(){if(busy)return;String url;try{url=UpdatePolicy.https(address.getText().toString());prefs.edit().putString("update_url",url).apply();}catch(Exception e){status.setText(e.getMessage());return;}manifest=null;apk=null;begin();worker.execute(()->{try{HttpsURLConnection c=open(url);byte[] data;try(InputStream input=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=input.read(b))!=-1){if(cancelled)throw new IOException("已取消");if(out.size()+n>65536)throw new IOException("更新说明过大");out.write(b,0,n);}data=out.toByteArray();}finally{c.disconnect();connection=null;}
   JSONObject m=new JSONObject(new String(data,java.nio.charset.StandardCharsets.UTF_8));long v=m.getLong("versionCode"),size=m.getLong("size");UpdatePolicy.metadata(v,size,m.getString("sha256"));UpdatePolicy.https(m.getString("apkUrl"));if(!UpdatePolicy.newer(v,installed)){result("当前已是此地址提供的最新版本；不会下载或降级。",null);return;}String info="版本："+m.getString("versionName")+"（"+v+"）\n大小："+size+"字节\n"+m.optString("notes","");result(info,()->{manifest=m;download.setEnabled(true);});
  }catch(Exception e){result("检查失败："+e.getMessage(),null);}});}
 void validate(File file,JSONObject m)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream input=new FileInputStream(file)){byte[] b=new byte[16384];int n;while((n=input.read(b))!=-1){if(cancelled)throw new IOException("已取消");digest.update(b,0,n);}}StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));UpdatePolicy.downloaded(file.length(),m.getLong("size"),hash.toString(),m.getString("sha256"));PackageManager pm=getPackageManager();PackageInfo archive=pm.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES),current=pm.getPackageInfo(getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);if(archive==null||archive.signingInfo==null)throw new IOException("APK无法解析签名");android.content.pm.Signature[] a=archive.signingInfo.getApkContentsSigners(),b=current.signingInfo.getApkContentsSigners();boolean same=a.length==b.length&&new HashSet<>(Arrays.asList(a)).equals(new HashSet<>(Arrays.asList(b)));UpdatePolicy.archive(archive.packageName,archive.getLongVersionCode(),m.getLong("versionCode"),current.getLongVersionCode(),same);}
 void download(){if(busy||manifest==null)return;JSONObject m=manifest;begin();worker.execute(()->{File file=new File(getCacheDir(),"update.apk");try{HttpsURLConnection c=open(m.getString("apkUrl"));long expected=m.getLong("size");try(InputStream input=c.getInputStream();OutputStream out=new FileOutputStream(file)){byte[] b=new byte[16384];int n;long total=0,deadline=android.os.SystemClock.elapsedRealtime()+120000;while((n=input.read(b))!=-1){if(android.os.SystemClock.elapsedRealtime()>deadline)throw new IOException("下载超过两分钟，请重试");if(cancelled)throw new IOException("已取消");total+=n;if(total>expected||total>UpdatePolicy.MAX_SIZE)throw new IOException("下载超过声明大小");out.write(b,0,n);}}finally{c.disconnect();connection=null;}validate(file,m);result("下载、字节数、SHA256、包名、版本和同证书校验通过。请点击打开系统安装。",()->{apk=file;install.setEnabled(true);});}catch(Exception e){file.delete();result("下载/校验失败："+e.getMessage(),()->download.setEnabled(true));}});}
 void install(){if(busy||apk==null||manifest==null)return;if(!getPackageManager().canRequestPackageInstalls()){status.setText("请仅允许本应用安装未知应用，返回后再次点击安装。");configuring=true;startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));return;}begin();worker.execute(()->{try{validate(apk,manifest);result("校验通过，即将由系统请求覆盖安装。",()->{Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://dev.ankigate.update/update.apk"),"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("APK",i.getData()));try{configuring=true;startActivity(i);}catch(Exception e){configuring=false;status.setText("系统安装器无法打开："+e.getMessage());}install.setEnabled(true);});}catch(Exception e){result("安装前校验失败："+e.getMessage(),null);}});}
 @Override protected void onResume(){super.onResume();if(!owns)return;visible=true;configuring=false;if(GateService.instance!=null)GateService.instance.ownActivity(true);}
 @Override protected void onPause(){visible=false;super.onPause();}
 @Override protected void onStop(){cancelled=true;HttpsURLConnection c=connection;if(c!=null)c.disconnect();if(owns){inProgress=false;if(GateService.instance!=null)GateService.instance.ownActivity(false);}super.onStop();}
 @Override protected void onStart(){super.onStart();if(owns)inProgress=true;}
 @Override protected void onDestroy(){cancelled=true;worker.shutdownNow();if(owns){inProgress=false;configuring=false;}super.onDestroy();}
}
