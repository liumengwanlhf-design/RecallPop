package dev.ankigate;
import android.app.Application;

/** A one-shot diagnostic read, independent of service startup and review workers. */
public final class GateApplication extends Application {
 @Override public void onCreate(){super.onCreate();if(getPackageName().equals(Application.getProcessName()))new Thread(()->ExitDiagnostics.read(this),"exit-diagnostics").start();}
}
