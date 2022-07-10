package com.example.democamera2.utils;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionUtils {
    public static final String[] checkSelfPermissions(Context context, String[] permissions) {
        if (null == context || null == permissions) {
            return null;
        }
        if (permissions.length == 0) {
            return new String[]{};
        }

        List<String> deniedPermissionList = new ArrayList<>();
        for (int i = 0; i < permissions.length; i++) {
            String permission = permissions[i];
            if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                deniedPermissionList.add(permission);
            }
        }
        if (deniedPermissionList.size() == 0) {
            return new String[]{};
        } else {
            // String[] deniedPermissions = new String[deniedPermissionList.size()];
            String[] deniedPermissions = new String[0];
            return deniedPermissionList.toArray(deniedPermissions);
        }
    }

    public static final void checkAndRequestPermissions(android.app.Activity activity,
                                                        String[] permissions,
                                                        int requestCode) {
        if (null == activity || null == permissions || permissions.length == 0 || requestCode < 0) {
            return;
        }
        permissions = checkSelfPermissions(activity, permissions);
        if (null == permissions || permissions.length == 0) {
            return;
        }
        ActivityCompat.requestPermissions(activity, permissions, requestCode);
    }

    public static String[] getDeniedPermissionsFromOnRequestPermissionsResult(String[] permissions, int[] grantResults) {
        if (null == permissions || permissions.length == 0 || null == grantResults || grantResults.length == 0 || permissions.length != grantResults.length) {
            return new String[]{};
        }
        List<String> deniedPermissionList = new ArrayList<>();
        for (int i = 0; i < grantResults.length; i++) {
            int result = grantResults[i];
            if (result != PackageManager.PERMISSION_GRANTED) {
                deniedPermissionList.add(permissions[i]);
            }
        }
        if (deniedPermissionList.size() == 0) {
            return new String[]{};
        } else {
            // String[] deniedPermissions = new String[deniedPermissionList.size()];
            String[] deniedPermissions = new String[0];
            return deniedPermissionList.toArray(deniedPermissions);
        }
    }
}
