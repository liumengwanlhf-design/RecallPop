package dev.ankigate;

/** A planned user review check, never a heartbeat or offline usage credit. */
final class ReviewAlarmPolicy {
 static long usageDelay(long remaining,long protection){return Math.max(1,Math.max(remaining,protection));}
 static String reject(boolean enabled,String type,String savedType,long token,long savedToken,int boot,int savedBoot,long at,long now){
  if(!enabled)return "已停用";if(!"usage".equals(type)&&!"wake".equals(type))return "未知检查类型";
  if(token<=0||token!=savedToken||!type.equals(savedType))return "旧或重复token";
  if(boot<0||boot!=savedBoot)return "开机epoch已改变";if(at<=0||now<at)return "不是已到时计划";return null;
 }
 static boolean keep(String type,String wanted,int boot,int savedBoot,long at,long now,long desired){return type.equals(wanted)&&boot>=0&&boot==savedBoot&&at>now&&Math.abs(at-desired)<=1000;}
}
