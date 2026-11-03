package com.frankoabarza.tecladoremoto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;

import java.util.List;

/**
 * Mantiene encendido el servidor mientras la app esté activa, con un aviso fijo que muestra
 * la dirección para el iPhone y un botón para apagarlo.
 */
public class ServerService extends Service {

    static final String ACTION_STOP = "com.frankoabarza.tecladoremoto.APAGAR";
    private static final String CHANNEL = "servidor";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            ServerHolder.stop(this);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        startAsForeground();
        ServerHolder.ensureStarted(this);
        return START_NOT_STICKY;
    }

    private void startAsForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Servidor del teclado remoto", NotificationManager.IMPORTANCE_LOW));

        List<String> ips = Config.addresses();
        String text = ips.isEmpty()
                ? "Sin red: conecta la tablet a un Wi-Fi"
                : Config.url(this, ips.get(0));

        int immutable = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent open = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class), immutable);
        PendingIntent stop = PendingIntent.getService(
                this, 1, new Intent(this, ServerService.class).setAction(ACTION_STOP), immutable);
        Notification.Action stopAction = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "Apagar", stop).build();

        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Teclado remoto encendido")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .addAction(stopAction)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
