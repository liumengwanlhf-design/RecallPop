package dev.ankigate;

import java.util.*;

public final class MediaIndexChecks {
 static void expect(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  MediaIndexCache<String> cache=new MediaIndexCache<>();int[] scans={0};
  Map<String,String> directory=new HashMap<>(Map.of("first","uri:1","css","uri:css"));
  MediaIndexCache.Scan<String> scan=()->{scans[0]++;return directory;};
  MediaIndexCache.Check check=()->{};
  Map<String,String> first=cache.obtain("tree/grant1",100,List.of("first"),scan,check);
  expect(scans[0]==1&&first.containsKey("css"),"first enumeration keeps ancillary resources");
  directory.put("second","uri:2");
  expect(!first.containsKey("second"),"index is a snapshot, not the provider's mutable map");
  try{first.put("x","y");throw new AssertionError("mutable snapshot");}catch(UnsupportedOperationException expected){}
  expect(cache.obtain("tree/grant1",101,List.of("first"),scan,check)==first&&scans[0]==1,"subsequent card reuses one index");
  Map<String,String> second=cache.obtain("tree/grant1",102,List.of("second"),scan,check);
  expect(scans[0]==2&&second.containsKey("second"),"new required media refreshes once");
  cache.invalidate(first);
  expect(cache.obtain("tree/grant1",103,List.of("second"),scan,check)==second&&scans[0]==2,"old WebView failure cannot clear a newer snapshot");
  cache.invalidate(second);
  Map<String,String> third=cache.obtain("tree/grant1",104,List.of("second"),scan,check);
  expect(scans[0]==3&&third!=second,"read failure forces next card to enumerate");
  Map<String,String> grant=cache.obtain("tree/grant2",105,List.of("second"),scan,check);
  expect(scans[0]==4&&grant!=third,"same directory with new authorization refreshes");
  cache.obtain("other/grant2",106,List.of(),scan,check);
  expect(scans[0]==5,"directory change refreshes even without declared media");
  cache.obtain("other/grant2",106+MediaIndexCache.MAX_AGE_MILLIS,List.of(),scan,check);
  expect(scans[0]==6,"expiry refreshes");
  cache.obtain("other/grant2",105,List.of(),scan,check);
  expect(scans[0]==7,"clock reversal cannot extend cache age");
  Map<String,String> current=cache.obtain("other/grant2",107,List.of(),scan,check);
  try{cache.obtain("changed",108,List.of(),()->{throw new Exception("failed scan");},check);throw new AssertionError("scan failure swallowed");}catch(Exception expected){expect(expected.getMessage().equals("failed scan"),"original failure retained");}
  expect(cache.obtain("other/grant2",109,List.of(),scan,check)==current,"failed scan does not publish partial or replace complete index");
  int[] checks={0};
  try{cache.obtain("changed",110,List.of(),scan,()->{if(++checks[0]==2)throw new IllegalStateException("cancelled");});throw new AssertionError("cancel swallowed");}catch(IllegalStateException expected){expect(expected.getMessage().equals("cancelled"),"cancel propagated");}
  expect(cache.obtain("other/grant2",111,List.of(),scan,check)==current,"cancellation after enumeration prevents publication");
  try{cache.obtain("other/grant2",112,List.of(),scan,()->{throw new IllegalStateException("cancelled");});throw new AssertionError("cache hit skipped cancellation");}catch(IllegalStateException expected){}
  int before=scans[0];cache.obtain("other/grant2",113,List.of("absent"),scan,check);
  expect(scans[0]==before+1,"still missing file performs exactly one refresh, no retry loop");
  cache.clear();cache.obtain("other/grant2",114,List.of(),scan,check);
  expect(scans[0]==before+2,"removing directory authorization clears cache");
  System.out.println("media index reuse, required miss, expiry/grant change, immutable snapshots, selective invalidation, failure and cancellation passed");
 }
}
