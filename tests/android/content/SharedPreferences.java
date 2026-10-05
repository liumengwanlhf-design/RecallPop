package android.content;
public interface SharedPreferences {
 long getLong(String key,long fallback);int getInt(String key,int fallback);String getString(String key,String fallback);Editor edit();
 interface Editor {Editor putLong(String key,long value);Editor putInt(String key,int value);Editor putString(String key,String value);void apply();default boolean commit(){apply();return true;}}
}
