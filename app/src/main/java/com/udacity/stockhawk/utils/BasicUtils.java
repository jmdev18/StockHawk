package com.udacity.stockhawk.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

/**
 * Created by cropin on 21/4/17.
 */

public class BasicUtils {

    public static Toast toast = null;

    public static void showToast(Context c, String msg) {
        toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    public static boolean isNetworkUp(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnectedOrConnecting();
    }
}
