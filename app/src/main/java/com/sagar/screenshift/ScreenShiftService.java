package com.sagar.screenshift;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

import static com.sagar.screenshift.PreferencesHelper.KEY_DENSITY_ENABLED;
import static com.sagar.screenshift.PreferencesHelper.KEY_DENSITY_VALUE;
import static com.sagar.screenshift.PreferencesHelper.KEY_DISPLAY_HEIGHT;
import static com.sagar.screenshift.PreferencesHelper.KEY_DISPLAY_WIDTH;
import static com.sagar.screenshift.PreferencesHelper.KEY_OVERSCAN_BOTTOM;
import static com.sagar.screenshift.PreferencesHelper.KEY_OVERSCAN_ENABLED;
import static com.sagar.screenshift.PreferencesHelper.KEY_OVERSCAN_LEFT;
import static com.sagar.screenshift.PreferencesHelper.KEY_OVERSCAN_RIGHT;
import static com.sagar.screenshift.PreferencesHelper.KEY_OVERSCAN_TOP;
import static com.sagar.screenshift.PreferencesHelper.KEY_RESOLUTION_ENABLED;
import static com.sagar.screenshift.PreferencesHelper.KEY_RESOLUTION_HEIGHT;
import static com.sagar.screenshift.PreferencesHelper.KEY_RESOLUTION_WIDTH;

public class ScreenShiftService extends Service {
    public static final String ACTION_START = "action_start";
    public static final String ACTION_STOP = "action_stop";
    public static final String ACTION_TOGGLE = "action_toggle";
    public static final String ACTION_SAVE_HEIGHT_WIDTH = "action_save_height_width";
    public static final String ACTION_SET_NOTIFICATION = "set_notification";

    public static final String EXTRA_SEND_BROADCAST = "send_broadcast";
    public static final String EXTRA_POST_NOTIFICATION = "post_notification";
    public static final String EXTRA_SET_NOTIFICATION = "extra_set_notification";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    private void postNotification(String text) {
        postNotification(text, null);
    }
    private void postNotification(String text, Boolean override) {
        if(override == null) {
            if (PreferencesHelper.getBoolPreference(this, getString(R.string.key_show_notification), true)) {
                startForeground(1, buildNotification(text));
            } else {
                stopForeground(true);
            }
        } else {
            if(override) {
                startForeground(1, buildNotification(text));
            } else {
                stopForeground(true);
            }
        }
    }

    private Notification buildNotification(String text){
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
        Intent intent = new Intent(this, ScreenShiftService.class);
        intent.setAction(ACTION_TOGGLE);
        intent.putExtra(EXTRA_SEND_BROADCAST, true);
        PendingIntent pi = PendingIntent.getService(this, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return notificationBuilder.setContentTitle(getString(R.string.app_name)).
                setSmallIcon(R.mipmap.ic_launcher).setContentText(text).setPriority(NotificationCompat.PRIORITY_MIN).
                /*addAction()*/
                setContentIntent(pi).build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null){
            String action = intent.getAction();
            boolean sendBroadcast = intent.getBooleanExtra(EXTRA_SEND_BROADCAST, false);
            boolean postNotification = intent.getBooleanExtra(EXTRA_POST_NOTIFICATION, true);
            if(ACTION_START.equals(action)) {
                start(sendBroadcast, postNotification);
            } else if(ACTION_STOP.equals(action)) {
                stop(sendBroadcast, postNotification);
            } else if(ACTION_TOGGLE.equals(action)) {
                toggle(sendBroadcast, postNotification);
            } else if(ACTION_SAVE_HEIGHT_WIDTH.equals(action)) {
                saveWidthHeight();
            } else if(ACTION_SET_NOTIFICATION.equals(action)) {
                if(intent.getBooleanExtra(EXTRA_SET_NOTIFICATION, true)) {
                    postNotification(getString(R.string.touch_to_toggle), true);
                } else {
                    postNotification("", false);
                }
            }
        }
        return START_STICKY;
    }

    private void toggle(boolean sendBroadcast, boolean postNotification) {
        if(PreferencesHelper.getBoolPreference(this, PreferencesHelper.KEY_MASTER_SWITCH_ON)) {
            stop(sendBroadcast, postNotification);
        } else {
            start(sendBroadcast, postNotification);
        }
    }

    private void start(boolean sendBroadcast, boolean postNotification){
        new AsyncTask<Boolean, Void, Boolean>() {
            private boolean mSendBroadcast, mPostNotification;
            @Override
            protected Boolean doInBackground(Boolean... booleans) {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    if (!Shell.SU.available()) return false;
                }
                List<String> commands = new ArrayList<>();
                int height, width;
                height = PreferencesHelper.getIntPreference(ScreenShiftService.this, KEY_DISPLAY_HEIGHT, 1280);
                width = PreferencesHelper.getIntPreference(ScreenShiftService.this, KEY_DISPLAY_WIDTH, 768);
                if(PreferencesHelper.getBoolPreference(ScreenShiftService.this, KEY_RESOLUTION_ENABLED)) {
                    height = PreferencesHelper.getIntPreference(ScreenShiftService.this, KEY_RESOLUTION_HEIGHT, -1);
                    width = PreferencesHelper.getIntPreference(ScreenShiftService.this, KEY_RESOLUTION_WIDTH, -1);
                    if(height == -1) height = PreferencesHelper.getIntPreference(ScreenShiftService.this, KEY_DISPLAY_HEIGHT, 1280);
                    if(width == -1) width = PreferencesHelper.getIntPreference(ScreenShiftService.this, KEY_DISPLAY_WIDTH, 768);
                    commands.add(getResolutionCommand(width, height));
                } else {
                    commands.add(getResolutionCommand(-1, -1));
                }
                if(PreferencesHelper.getBoolPreference(ScreenShiftService.this, KEY_OVERSCAN_ENABLED)
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    int leftOverscan = (int) (PreferencesHelper.getIntPreference(ScreenShiftService.this, KEY_OVERSCAN_LEFT, 0) * width / 100f);
                    int rightOverscan = (int) (PreferencesHelper.getIntPreference(ScreenShiftService.this, KEY_OVERSCAN_RIGHT, 0) * width / 100f);
                    int topOverscan = (int) (PreferencesHelper.getIntPreference(ScreenShiftService.this, KEY_OVERSCAN_TOP, 0) * height / 100f);
                    int bottomOverscan = (int) (PreferencesHelper.getIntPreference(ScreenShiftService.this, KEY_OVERSCAN_BOTTOM, 0) * height / 100f);
                    commands.add("wm overscan " + leftOverscan + "," + topOverscan + "," + rightOverscan + "," + bottomOverscan);
                } else {
                    commands.add("wm overscan reset");
                }
                if(PreferencesHelper.getBoolPreference(ScreenShiftService.this, KEY_DENSITY_ENABLED)) {
                    int density = PreferencesHelper.getIntPreference(ScreenShiftService.this, KEY_DENSITY_VALUE, -1);
                    if(density != -1) {
                        commands.add(getDensityCommand(density));
                    }
                } else {
                    commands.add(getDensityCommand(-1));
                }
                PreferencesHelper.setPreference(ScreenShiftService.this,
                        PreferencesHelper.KEY_MASTER_SWITCH_ON, true);
                List<String> results;
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    results = Shell.SU.run(commands);
                } else {
                    results =Shell.SH.run(commands);
                }
                for (String result : results){
                    Log.i("ScreenShiftService", result);
                }
                mSendBroadcast = booleans[0];
                mPostNotification = booleans[1];
                return true;
            }

            @Override
            protected void onPostExecute(Boolean result) {
                super.onPostExecute(result);
                if(mPostNotification) {
                    postNotification(getString(R.string.custom_display));
                }
                if(!result){
                    Toast.makeText(ScreenShiftService.this,
                            R.string.no_root_string, Toast.LENGTH_SHORT).show();
                    stop(true, true);
                }
                if(mSendBroadcast){
                    LocalBroadcastManager.getInstance(ScreenShiftService.this)
                            .sendBroadcast(new Intent(ACTION_START));
                }
            }
        }.execute(sendBroadcast, postNotification);
    }

    private void stop(boolean sendBroadcast, boolean postNotification){
        new AsyncTask<Boolean, Void, Void>() {
            private boolean mSendBroadcast, mPostNotification;
            @Override
            protected Void doInBackground(Boolean... booleans) {
                PreferencesHelper.setPreference(ScreenShiftService.this,
                        PreferencesHelper.KEY_MASTER_SWITCH_ON, false);
                List<String> results;
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    results = Shell.SU.run(getResetCommands());
                } else {
                    results = Shell.SH.run(getResetCommands());
                }
                for (String result : results){
                    Log.i("ScreenShiftService", result);
                }
                mSendBroadcast = booleans[0];
                mPostNotification = booleans[1];
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                if(mPostNotification) {
                    postNotification(getString(R.string.default_display));
                }
                if(mSendBroadcast){
                    LocalBroadcastManager.getInstance(ScreenShiftService.this)
                            .sendBroadcast(new Intent(ACTION_STOP));
                }
            }
        }.execute(sendBroadcast, postNotification);
    }

    private void saveWidthHeight(){
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                if(PreferencesHelper.getIntPreference(ScreenShiftService.this, KEY_DISPLAY_HEIGHT, -1) == -1
                        || PreferencesHelper.getIntPreference(ScreenShiftService.this, KEY_DISPLAY_WIDTH, -1) == -1) {
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        Shell.SU.run(getResetCommands());
                    } else {
                        Shell.SH.run(getResetCommands());
                    }
                    DisplaySize displaySize = DisplaySize.getDeviceDisplaySize(ScreenShiftService.this);
                    PreferencesHelper.setPreference(ScreenShiftService.this, KEY_DISPLAY_HEIGHT, displaySize.height);
                    PreferencesHelper.setPreference(ScreenShiftService.this, KEY_DISPLAY_WIDTH, displaySize.width);
                }
                return null;
            }
        }.execute();
    }

    List<String> getResetCommands() {
        List<String> commands = new ArrayList<>();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            commands.add("wm size reset");
            commands.add("wm density reset");
            commands.add("wm overscan reset");
        } else {
            commands.add("am display-size reset");
            commands.add("am display-density reset");
        }
        return commands;
    }

    String getResolutionCommand(int width, int height) {
        if(width == -1 || height == -1) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                return "wm size reset";
            } else {
                return "am display-size reset";
            }
        } else {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                return "wm size " + width + "x" + height;
            } else {
                return "am display-size " + width + "x" + height;
            }
        }
    }

    String getDensityCommand(int density) {
        if(density == -1) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                return "wm density reset";
            } else {
                return "am display-density reset";
            }
        } else {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                return "wm density " + density;
            } else {
                return "am display-density " + density;
            }
        }
    }



    public static class DisplaySize {
        public int height, width;

        public DisplaySize(int h, int w){
            height = h;
            width = w;
        }
        public static DisplaySize getDeviceDisplaySize(Context context) {
            WindowManager w = (WindowManager) context.getSystemService(WINDOW_SERVICE);
            Display d = w.getDefaultDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            d.getMetrics(metrics);
// since SDK_INT = 1;
            int widthPixels = metrics.widthPixels;
            int heightPixels = metrics.heightPixels;
// includes window decorations (statusbar bar/menu bar)
            if (Build.VERSION.SDK_INT >= 14 && Build.VERSION.SDK_INT < 17)
                try {
                    widthPixels = (Integer) Display.class.getMethod("getRawWidth").invoke(d);
                    heightPixels = (Integer) Display.class.getMethod("getRawHeight").invoke(d);
                } catch (Exception ignored) {
                }
// includes window decorations (statusbar bar/menu bar)
            if (Build.VERSION.SDK_INT >= 17)
                try {
                    Point realSize = new Point();
                    Display.class.getMethod("getRealSize", Point.class).invoke(d, realSize);
                    widthPixels = realSize.x;
                    heightPixels = realSize.y;
                } catch (Exception ignored) {
                }
            return new DisplaySize(heightPixels, widthPixels);
        }
    }
}
