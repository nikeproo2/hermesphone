package com.nikeproo2.hermeswidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TickReceiver extends BroadcastReceiver {

    static final String ACTION_TICK = "com.nikeproo2.hermeswidget.TICK";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_TICK.equals(intent.getAction())) return;
        NextLessonWidget.refreshAll(context);
    }
}
