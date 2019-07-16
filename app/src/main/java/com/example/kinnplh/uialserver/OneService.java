package com.example.kinnplh.uialserver;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Created by kinnplh on 2019/4/5.
 */

public class OneService extends Service {
    private  final IUIALAidlInterface.Stub mBinder = new IUIALAidlInterface.Stub() {
        @Override
        public void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat, double aDouble, String aString) throws RemoteException {
        }

        @Override
        public String printLog(String inputStr) throws RemoteException {
            String packageName = UIALServer.self.getPackageName();

            String res = String.format("%s-%s", packageName, inputStr);
            Log.i("info", "printLog: " + res);
            return res;
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
