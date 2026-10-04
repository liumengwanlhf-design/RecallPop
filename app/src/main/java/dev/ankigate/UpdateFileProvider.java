package dev.ankigate;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
/** One read-only cache APK; Android grants the native installer access to this URI. */
public final class UpdateFileProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 public String getType(Uri uri){return "application/vnd.android.package-archive";}
 public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode)||!"/update.apk".equals(uri.getPath()))throw new FileNotFoundException("Unsupported update URI");return ParcelFileDescriptor.open(new File(getContext().getCacheDir(),"update.apk"),ParcelFileDescriptor.MODE_READ_ONLY);}
 public Cursor query(Uri u,String[] p,String s,String[] a,String o){if(!"/update.apk".equals(u.getPath()))throw new IllegalArgumentException("Unsupported update URI");String[] columns=p==null?new String[]{"_display_name","_size"}:p;android.database.MatrixCursor result=new android.database.MatrixCursor(columns);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++)row[i]="_display_name".equals(columns[i])?"update.apk":"_size".equals(columns[i])?new File(getContext().getCacheDir(),"update.apk").length():null;result.addRow(row);return result;}
 public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
 public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
 public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
