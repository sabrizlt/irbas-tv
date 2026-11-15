package it.irbas.tv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/** Tiene acceso il ricevitore del telecomando del telefono, anche quando sullo schermo c'è un'altra app. */
public class RemoteService extends Service {
    private static final String CHANNEL = "irbas-remote";

    static void start(Context context) {
        try {
            context.startForegroundService(new Intent(context, RemoteService.class));
        } catch (Exception e) {
            RemoteServer.start(context);   // almeno finché l'app è aperta
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Telecomando di Irbas", NotificationManager.IMPORTANCE_MIN));
        }
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setContentTitle("Irbas TV")
                .setContentText("Pronta a ricevere i comandi dal telefono")
                .setSmallIcon(R.drawable.ic_irbas)
                .build();
        startForeground(1, notification);
        RemoteServer.start(this);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
