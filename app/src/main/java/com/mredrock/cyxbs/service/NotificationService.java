package com.mredrock.cyxbs.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import com.mredrock.cyxbs.APP;
import com.mredrock.cyxbs.model.Course;
import com.mredrock.cyxbs.network.RequestManager;
import com.mredrock.cyxbs.receiver.RemindReceiver;
import com.mredrock.cyxbs.subscriber.SimpleSubscriber;
import com.mredrock.cyxbs.subscriber.SubscriberListener;
import com.mredrock.cyxbs.util.CourseTimeUtils;
import com.mredrock.cyxbs.util.SchoolCalendar;
import com.mredrock.cyxbs.util.TimeUtils;

import java.util.Calendar;
import java.util.List;

import static com.mredrock.cyxbs.receiver.RebootReceiver.EXTRA_COURSE_CLASSROOM;
import static com.mredrock.cyxbs.receiver.RebootReceiver.EXTRA_COURSE_NAME;
import static com.mredrock.cyxbs.ui.fragment.me.RemindFragment.ALARM_FLAG_BY_DAY;
import static com.mredrock.cyxbs.ui.fragment.me.RemindFragment.INTENT_FLAG_BY_CLASS;
import static com.mredrock.cyxbs.ui.fragment.me.RemindFragment.INTENT_FLAG_BY_DAY;
import static com.mredrock.cyxbs.ui.fragment.me.RemindFragment.INTENT_MODE;
import static com.mredrock.cyxbs.ui.fragment.me.RemindFragment.SP_REMIND_EVERY_CLASS;
import static com.mredrock.cyxbs.ui.fragment.me.RemindFragment.SP_REMIND_EVERY_CLASS_DELAY;
import static com.mredrock.cyxbs.ui.fragment.me.RemindFragment.SP_REMIND_EVERY_DAY;
import static com.mredrock.cyxbs.ui.fragment.me.RemindFragment.SP_REMIND_EVERY_DAY_TIME;

/**
 * Created by simonla on 2016/11/2.
 * 下午8:42
 */

public class NotificationService extends Service {

    public static final String TAG = "NotificationService";

    private AlarmManager mAlarmManager;
    private SharedPreferences mSp;

    //第一次创建
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: 服务启动了");
        initService();
        if (mSp.getBoolean(SP_REMIND_EVERY_DAY, false) && !isLate()) {
            byDay(this);
        }
        if (mSp.getBoolean(SP_REMIND_EVERY_CLASS, false)) {
            byClass(this);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private boolean isLate() {
        int delay = Integer.valueOf(mSp.getString(SP_REMIND_EVERY_DAY_TIME, "22"));
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        Log.d(TAG, "isLate now hour: " + calendar.get(Calendar.HOUR_OF_DAY) + " delay: " + delay);
        return calendar.get(Calendar.HOUR_OF_DAY) >= delay;
    }

    private void byDay(Context context) {
        int dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 1;
        getCourseList(this, new CourseCallback() {
                    @Override
                    public void onSuccess(List<Course> courses) {
                        for (Course c : courses) {
                            if (c.hash_day + 2 == dayOfWeek % 7) {
                                Intent intent = new Intent(context, RemindReceiver.class);
                                intent.putExtra(INTENT_MODE, INTENT_FLAG_BY_DAY);
                                PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                                        ALARM_FLAG_BY_DAY, intent, 0);
                                Calendar calendar = Calendar.getInstance();
                                calendar.setTimeInMillis(System.currentTimeMillis());
                                calendar.set(Calendar.HOUR_OF_DAY, Integer.valueOf(mSp.
                                        getString(SP_REMIND_EVERY_DAY_TIME, "22")));
                                calendar.set(Calendar.MINUTE, 0);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                    mAlarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.
                                            getTimeInMillis(), pendingIntent);
                                } else {
                                    mAlarmManager.set(AlarmManager.RTC_WAKEUP, calendar.
                                            getTimeInMillis(), pendingIntent);
                                }
                                Log.d(TAG, "每日提醒:  " + TimeUtils.timeStampToStr(calendar.
                                        getTimeInMillis() / 1000));
                                break;
                            }
                        }
                    }

                    @Override
                    public void onFail(Throwable e) {
                        e.printStackTrace();
                    }
                }

        );
    }

    private void byClass(Context context) {
        getCourseList(this, new CourseCallback() {
            @Override
            public void onSuccess(List<Course> courses) {
                for (Course c : courses) {
                    Calendar today = Calendar.getInstance();
                    today.setTimeInMillis(System.currentTimeMillis());
                    Calendar courseCalendar = CourseTimeUtils.CourseToCalendar(c);
                    //如果不是今天的课就退出
                    if (courseCalendar.get(Calendar.DAY_OF_WEEK) !=
                            today.get(Calendar.DAY_OF_WEEK) ||
                            System.currentTimeMillis() > courseCalendar.getTimeInMillis()) {
                        continue;
                    }
                    int hourDelay = courseCalendar.get(Calendar.MINUTE) - Integer.valueOf(mSp.
                            getString(SP_REMIND_EVERY_CLASS_DELAY, "0")) < 0 ? 1 : 0;
                    courseCalendar.set(Calendar.HOUR_OF_DAY, courseCalendar.get(Calendar.HOUR_OF_DAY)
                            - hourDelay);
                    if (hourDelay != 0) {
                        courseCalendar.set(Calendar.MINUTE, 60 + courseCalendar.get(Calendar.MINUTE) -
                                Integer.valueOf(mSp.
                                        getString(SP_REMIND_EVERY_CLASS_DELAY, "0")));
                    }
                    Log.d(TAG, "byClass: 下一节课时间是：" + courseCalendar.get(Calendar.HOUR_OF_DAY) +
                            " : " + courseCalendar.get(Calendar.MINUTE) + "课程名:" +
                            c.course + "教室：" + c.classroom + " intent_flag:" + c.hash_lesson + 10
                            + " 设置的时间是:" + TimeUtils.timeStampToStr(courseCalendar
                            .getTimeInMillis() / 1000));
                    Intent intent = new Intent(context, RemindReceiver.class);
                    intent.putExtra(INTENT_MODE, INTENT_FLAG_BY_CLASS);
                    intent.putExtra(EXTRA_COURSE_NAME, c.course);
                    intent.putExtra(EXTRA_COURSE_CLASSROOM, c.classroom);
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, c.hash_lesson +
                            10, intent, 0);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        mAlarmManager.setExact(AlarmManager.RTC_WAKEUP, courseCalendar.
                                getTimeInMillis(), pendingIntent);
                    } else {
                        mAlarmManager.set(AlarmManager.RTC_WAKEUP, courseCalendar.
                                getTimeInMillis(), pendingIntent);
                    }
                }
            }

            @Override
            public void onFail(Throwable e) {
                e.printStackTrace();
            }
        });
    }

    private void initService() {
        mAlarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        mSp = PreferenceManager.getDefaultSharedPreferences(this);
    }

    private void getCourseList(Context context, CourseCallback courseCallback) {
        RequestManager.getInstance().getCourseList(new SimpleSubscriber<>(context, false
                        , false, new SubscriberListener<List<Course>>() {
                    @Override
                    public void onStart() {
                        super.onStart();
                    }

                    @Override
                    public void onCompleted() {
                        super.onCompleted();
                    }

                    @Override
                    public boolean onError(Throwable e) {
                        super.onError(e);
                        e.printStackTrace();
                        courseCallback.onFail(e);
                        return false;
                    }

                    @Override
                    public void onNext(List<Course> courses) {
                        super.onNext(courses);
                        courseCallback.onSuccess(courses);
                    }
                }),
                APP.getUser(context).stuNum, APP.getUser(context).idNum, new SchoolCalendar()
                        .getWeekOfTerm(), false);
    }

    private interface CourseCallback {
        void onSuccess(List<Course> courses);

        void onFail(Throwable e);

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}