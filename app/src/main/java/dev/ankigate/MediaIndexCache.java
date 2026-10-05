package dev.ankigate;

import java.util.*;

/** One short-lived directory index; no media bytes or scheduled cards are cached. */
final class MediaIndexCache<T> {
 static final long MAX_AGE_MILLIS=120_000;
 interface Scan<T>{Map<String,T> read()throws Exception;}
 interface Check{void run();}
 private String identity;
 private long scannedAt;
 private Map<String,T> files;
 Map<String,T> obtain(String key,long now,Collection<String> required,Scan<T> scan,Check check)throws Exception {
  check.run();
  synchronized(this){
   long age=now-scannedAt;
   if(files!=null&&key.equals(identity)&&age>=0&&age<MAX_AGE_MILLIS&&files.keySet().containsAll(required))return files;
  }
  Map<String,T> complete=Collections.unmodifiableMap(new HashMap<>(scan.read()));
  check.run(); // A failed/cancelled enumeration never publishes a partial index.
  synchronized(this){identity=key;scannedAt=now;files=complete;}
  return complete;
 }
 synchronized void invalidate(Map<String,T> expected){if(files==expected){identity=null;files=null;}}
 synchronized void clear(){identity=null;files=null;}
}
