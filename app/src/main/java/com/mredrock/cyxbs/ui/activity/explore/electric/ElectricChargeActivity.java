package com.mredrock.cyxbs.ui.activity.explore.electric;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.jaeger.library.StatusBarUtil;
import com.mredrock.cyxbs.APP;
import com.mredrock.cyxbs.R;
import com.mredrock.cyxbs.model.ElectricCharge;
import com.mredrock.cyxbs.network.RequestManager;
import com.mredrock.cyxbs.subscriber.SimpleSubscriber;
import com.mredrock.cyxbs.subscriber.SubscriberListener;
import com.mredrock.cyxbs.util.LogUtils;
import com.mredrock.cyxbs.util.SPUtils;

public class ElectricChargeActivity extends AppCompatActivity {
    private static final String TAG = "ElectricChargeActivity";
    private String buildingNum;
    private String dormitoryNum;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_electric_charge);
        StatusBarUtil.setTranslucent(this, 50);
        initData();
        LogUtils.LOGE(TAG,buildingNum + ": "+dormitoryNum);
        RequestManager.INSTANCE.queryElectricCharge(new SimpleSubscriber<ElectricCharge>(this, new SubscriberListener<ElectricCharge>() {
            @Override
            public void onCompleted() {
                super.onCompleted();
            }

            @Override
            public boolean onError(Throwable e) {
                return super.onError(e);
            }

            @Override
            public void onNext(ElectricCharge electricCharge) {
                super.onNext(electricCharge);
                Log.d(TAG, "onNext: " + electricCharge.getRecordTime() +"   " + electricCharge.getElectricSpend());
            }

            @Override
            public void onStart() {
                super.onStart();
            }
        }),buildingNum,dormitoryNum);
    }

    private void initData() {
        buildingNum = (String) SPUtils.get(APP.getContext(),DormitorySettingActivity.BUILDING_KEY,String.valueOf(28));
        dormitoryNum = (String) SPUtils.get(APP.getContext(),DormitorySettingActivity.DORMITORY_KEY,String.valueOf(209));
    }
}