package dev.ankigate;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.webkit.WebResourceResponse;
import java.util.*;

final class MediaAccess {
 final Context context; final Map<String,Uri> files=new HashMap<>();
 MediaAccess(Context c){context=c;}
 void prepare(List<String> required) throws Exception {
  String saved=context.getSharedPreferences("gate",0).getString("media","");
  if(saved.isEmpty()){if(required.isEmpty())return;throw new IllegalStateException("本卡包含媒体；请先授权现有collection.media目录");}
  Uri tree=Uri.parse(saved);
  Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
  try(Cursor c=context.getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_DOCUMENT_ID},null,null,null)){
   if(c==null)throw new IllegalStateException("无法读取媒体目录");
   while(c.moveToNext())files.put(c.getString(0),DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(1)));
  }
  for(String name:required){Uri uri=files.get(name);if(uri==null)throw new IllegalStateException("媒体缺失："+name);
   try(java.io.InputStream stream=context.getContentResolver().openInputStream(uri)){if(stream==null)throw new IllegalStateException("媒体不可读："+name);}
  }
 }
 WebResourceResponse read(Uri request) {
  try {
   String name=request.getLastPathSegment();Uri file=files.get(name);
   if(file==null)return new WebResourceResponse("text/plain","UTF-8",404,"Media not found",Collections.emptyMap(),new java.io.ByteArrayInputStream(new byte[0]));
   String mime=context.getContentResolver().getType(file);if(mime==null)mime="application/octet-stream";
   return new WebResourceResponse(mime,null,context.getContentResolver().openInputStream(file));
  }catch(Exception e){return new WebResourceResponse("text/plain","UTF-8",500,"Media read failed",Collections.emptyMap(),new java.io.ByteArrayInputStream(new byte[0]));}
 }
}
