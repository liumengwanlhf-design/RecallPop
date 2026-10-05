package dev.ankigate;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.webkit.WebResourceResponse;
import java.util.*;

final class MediaAccess {
 private static final MediaIndexCache<Uri> INDEX=new MediaIndexCache<>();
 final Context context; volatile Map<String,Uri> files=Collections.emptyMap();
 MediaAccess(Context c){context=c;}
 void prepare(List<String> required,CardRead.Request request) throws Exception {
  request.stage("枚举媒体");
  String saved=context.getSharedPreferences("gate",0).getString("media","");
  if(saved.isEmpty()){INDEX.clear();if(required.isEmpty())return;throw new IllegalStateException("本卡包含媒体；请先授权现有collection.media目录");}
  Uri tree=Uri.parse(saved);
  // A re-granted directory has a new persisted grant identity, even at the same URI.
  String identity=saved+"|unpersisted";
  for(UriPermission grant:context.getContentResolver().getPersistedUriPermissions())if(grant.getUri().equals(tree))identity=saved+"|"+grant.getPersistedTime()+"|"+grant.isReadPermission()+"|"+grant.isWritePermission();
  files=INDEX.obtain(identity,SystemClock.elapsedRealtime(),required,()->{
   Map<String,Uri> indexed=new HashMap<>();
   android.util.Log.d("AnkiGateMedia","directory index scan begin");
   Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
   try(Cursor c=context.getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_DOCUMENT_ID},null,null,null,request.signal)){
    request.check();
    if(c==null)throw new IllegalStateException("无法读取媒体目录");
    while(c.moveToNext()){request.check();indexed.put(c.getString(0),DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(1)));}
   }
   request.check();android.util.Log.d("AnkiGateMedia","directory index scan complete entries="+indexed.size());return indexed;
  },request::check);
  try{
   request.stage("校验媒体");
   for(String name:required){request.check();Uri uri=files.get(name);if(uri==null)throw new IllegalStateException("媒体缺失："+name);
    try(android.content.res.AssetFileDescriptor descriptor=context.getContentResolver().openAssetFileDescriptor(uri,"r",request.signal)){
     request.check();if(descriptor==null)throw new IllegalStateException("媒体不可读："+name);
     try(java.io.InputStream stream=descriptor.createInputStream()){request.check();}
    }
   }
  }catch(Exception e){INDEX.invalidate(files);throw e;}
 }
 WebResourceResponse read(Uri request) {
  try {
   String name=request.getLastPathSegment();Uri file=files.get(name);
   if(file==null){INDEX.invalidate(files);return new WebResourceResponse("text/plain","UTF-8",404,"Media not found",Collections.emptyMap(),new java.io.ByteArrayInputStream(new byte[0]));}
   String mime=context.getContentResolver().getType(file);if(mime==null)mime="application/octet-stream";
   java.io.InputStream stream=context.getContentResolver().openInputStream(file);
   if(stream==null)throw new java.io.IOException("Media stream unavailable");
   return new WebResourceResponse(mime,null,stream);
  }catch(Exception e){INDEX.invalidate(files);return new WebResourceResponse("text/plain","UTF-8",500,"Media read failed",Collections.emptyMap(),new java.io.ByteArrayInputStream(new byte[0]));}
 }
}
