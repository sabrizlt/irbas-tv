package it.irbas.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** All'accensione del box: parte il ricevitore del telecomando del telefono. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        RemoteService.start(context);
    }
}
