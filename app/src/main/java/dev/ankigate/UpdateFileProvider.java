package dev.ankigate;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.util.*;
/** Only identity-named private immutable APKs with a read grant to the native installer. */
public final class UpdateFileProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 File file(Uri uri)throws IOException{if(!"content".equals(uri.getScheme())||!"dev.ankigate.update".equals(uri.getAuthority())||uri.getQuery()!=null||uri.getFragment()!=null)throw new IOException("Unsupported update URI");try{File file=UpdateFiles.finalFile(getContext().getCacheDir(),UpdateFiles.path(uri.getPath()));if(!file.isFile())throw new IOException("APK not found");return file;}catch(IllegalArgumentException e){throw new IOException("Unsupported update URI",e);}}
 public String getType(Uri uri){try{file(uri);return "application/vnd.android.package-archive";}catch(IOException e){throw new IllegalArgumentException(e);}}
 public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
  ParcelFileDescriptor descriptor=null;try{if(!"r".equals(mode))throw new IOException("Read only");File file=file(uri);UpdateFiles.Identity identity=UpdateFiles.path(uri.getPath());descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
   // Verify the actual FD returned to the installer and rewind its shared offset.
   String hash;try(InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(descriptor.getFileDescriptor()))){hash=UpdateFiles.hash(input,null);}
   android.system.Os.lseek(descriptor.getFileDescriptor(),0,android.system.OsConstants.SEEK_SET);
   if(!hash.equals(identity.hash()))throw new IOException("APK identity SHA256 mismatch");
   PackageManager pm=getContext().getPackageManager();PackageInfo archive=pm.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES),current=pm.getPackageInfo(getContext().getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
   if(archive==null||archive.signingInfo==null)throw new IOException("APK cannot be parsed");boolean same=new HashSet<>(Arrays.asList(archive.signingInfo.getApkContentsSigners())).equals(new HashSet<>(Arrays.asList(current.signingInfo.getApkContentsSigners())));
   UpdatePolicy.archive(archive.packageName,archive.getLongVersionCode(),identity.code(),0,same);android.util.Log.i("UpdateFileProvider","validated fd "+identity.name()+" size="+descriptor.getStatSize());return descriptor;
  }catch(Exception e){if(descriptor!=null)try{descriptor.close();}catch(IOException ignored){}FileNotFoundException failure=new FileNotFoundException("Rejected update APK: "+e.getMessage());failure.initCause(e);throw failure;}
 }
 public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){try{File file=file(uri);String[] columns=projection==null?new String[]{"_display_name","_size"}:projection;android.database.MatrixCursor result=new android.database.MatrixCursor(columns);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++)row[i]="_display_name".equals(columns[i])?file.getName():"_size".equals(columns[i])?file.length():null;result.addRow(row);return result;}catch(IOException e){throw new IllegalArgumentException(e);}}
 public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
 public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
 public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
