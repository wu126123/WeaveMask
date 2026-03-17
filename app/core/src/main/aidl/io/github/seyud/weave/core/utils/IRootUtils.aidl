// IRootUtils.aidl
package io.github.seyud.weave.core.utils;

// Declare any non-default types here with import statements

interface IRootUtils {
    android.app.ActivityManager.RunningAppProcessInfo getAppProcess(int pid);
    IBinder getFileSystem();
    boolean addSystemlessHosts();
    List<android.content.pm.ApplicationInfo> getInstalledApplications(int flags);
    int[] getUserIds();
    List<android.content.pm.PackageInfo> getPackages(int flags);
}
