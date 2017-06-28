package io.github.sogoagain.proximityalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by sogoagain on 2017. 4. 1..
 * ProximityAlertReceiver
 * - 근접 경보 발생 브로드캐스드를 수신하는 리시버.
 * - 전달받은 인텐트를 통해 근접 경보가 발생한 장소명을 알 수 있다.
 * - 전달받은 인텐트를 통해 영역에 들어옴/나감 여부를 알 수 있다.
 */

public class ProximityAlertReceiver extends BroadcastReceiver {
    // 디버깅 Log 출력시 사용되는 태그
    private static final String TAG = "sogoagain_RECEIVER";

    /**
     * onReceive(Context context, Intent intent)
     * @param context
     * @param intent - 해당 인텐트를 통해 현재 방송을 수신한 장소에 대해 들어옴/나감 여부, 장소명을 알 수 있다.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean isEntering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false);
        String locationName = intent.getStringExtra("PROXIMITY_LOCATION_NAME");

        if (isEntering) {
            Log.d(TAG, "목표 지점에 접근중입니다..");
            Toast.makeText(context, locationName + "에 접근중입니다...", Toast.LENGTH_LONG).show();
        } else {
            Log.d(TAG, "목표 지점에서 벗어납니다..");
            Toast.makeText(context, locationName + "에서 벗어납니다...", Toast.LENGTH_LONG).show();
        }
    }
}
