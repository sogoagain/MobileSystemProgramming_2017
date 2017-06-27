package io.github.sogoesagain.activitytracker;

/**
 * Created by K&Y on 2017-06-09.
 */

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static android.location.LocationManager.GPS_PROVIDER;

public class ActivityMonitorService extends Service{
    private static final String LOG = "AT_MonitorService";
    private static final String BROADCAST_ACTION_ACTIVITY = "io.github.sogoesagain.activity";
    private static final String BROADCAST_ALARM = "io.github.sogoesagain.alarm";
    private static final long ACTIVE_TIME = 1000;
    private static final long PERIOD_FOR_MOVING = 5000;
    private static final long PERIOD_INCREMENT = 5000;
    private static final long PERIOD_MAX = 30000;

    // 본 프로젝트에서 정의한 상수
    private static final float INITIAL_GPS_ACCURACY = 50.f;
    private static final long MOVING_TIME_THRESHOLD = 60000;    // 1분
    private static final long STAYING_TIME_THRESHOLD = 210000;  // 3분 30초
    private static final float GPS_ACCURACY_THRESHOLD = 25.f;

    // 좌표
    private final double GROUND_X = 37.5740339;
    private final double GROUND_Y = 126.976775;
    private final double UNIVHQ_X = 37.5740339;
    private final double UNIVHQ_Y = 126.976775;

    // WIFI
    private final String INDOOR1_MAC = "78:4a:cg";
    private final String INDOOR2_MAC = "24:9c:78";

    // 기록
    private SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm", java.util.Locale.getDefault());

    // 센싱
    private AlarmManager alarmManager;
    private WifiManager wifiManager;
    private PendingIntent pendingIntent;
    private PowerManager.WakeLock wakeLock;
    private CountDownTimer countDownTimer;

    private SensorManager sensorManager;
    private Sensor accelLinear;
    private StepMonitor stepMonitor = new StepMonitor();

    // 상태 측정 관련 객체
    private MovingMonitor movingMonitor;
    private LocationManager locationManager = null;

    private long period = 10000;
    private boolean previousMovingState = false;
    private boolean isRequestRegistered = false;

    // GPS 값
    private double longitude = 0.0;
    private double latitude = 0.0;
    private float accuracy = INITIAL_GPS_ACCURACY;

    // 현재 위치명
    private String placeName;

    // 상태 시간 측정
    private long startTime;
    private long endTime;

    /*************************************서비스 생명주기******************************************/
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // intent: startService() 호출 시 넘기는 intent 객체
        // flags: service start 요청에 대한 부가 정보. 0, START_FLAG_REDELIVERY, START_FLAG_RETRY
        // startId: start 요청을 나타내는 unique integer id
        Toast.makeText(this, R.string.start_tracking, Toast.LENGTH_SHORT).show();

        // Alarm이 발생할 시간이 되었을 때, 안드로이드 시스템에 전송을 요청할 broadcast를 지정
        Intent in = new Intent(BROADCAST_ALARM);
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, in, 0);

        // Alarm이 발생할 시간 및 alarm 발생시 이용할 pending intent 설정
        // 설정한 시간 (5000-> 5초, 10000->10초) 후 alarm 발생
        alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + period, pendingIntent);

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        Log.d(LOG, "onCreate");

        // Alarm 발생 시 전송되는 broadcast를 수신할 receiver 등록
        IntentFilter intentFilter = new IntentFilter(BROADCAST_ALARM);
        registerReceiver(alarmReceiver, intentFilter);

        // AlarmManager 객체 얻기
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        // WifiManager 객체 얻기
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        IntentFilter wifiIntentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiReceiver, wifiIntentFilter);

        // StepMonitor 객체
        sensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        accelLinear = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        // 초기화
        startTime = System.currentTimeMillis();
        endTime = System.currentTimeMillis();
    }

    public void onDestroy() {
        Toast.makeText(this, R.string.finish_tracking, Toast.LENGTH_SHORT).show();

        writeRecord(previousMovingState);
        sendBroadcastToActivity();

        try {
            // Alarm 발생 시 전송되는 broadcast 수신 receiver를 해제
            unregisterReceiver(alarmReceiver);
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        }
        // AlarmManager에 등록한 alarm 취소
        alarmManager.cancel(pendingIntent);
        unregisterReceiver(wifiReceiver);

        // release all the resources you use
        if (countDownTimer != null)
            countDownTimer.cancel();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    /**********************************************************************************************/

    /****************************************Total Sensing*****************************************/
    /**
     * Alarm을 이용한 Sensing
     */
    private BroadcastReceiver alarmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BROADCAST_ALARM)) {
                Log.d(LOG, "Alarm fired!!!!");
                //-----------------
                // Alarm receiver에서는 장시간에 걸친 연산을 수행하지 않도록 한다
                // Alarm을 발생할 때 안드로이드 시스템에서 wakelock을 잡기 때문에 CPU를 사용할 수 있지만
                // 그 시간은 제한적이기 때문에 애플리케이션에서 필요하면 wakelock을 잡아서 연산을 수행해야 함
                //-----------------

                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HS_Wakelock");
                // ACQUIRE a wakelock here to collect and process accelerometer data and control location updates
                wakeLock.acquire();

                movingMonitor = new MovingMonitor(context);
                movingMonitor.onStart();

                countDownTimer = new CountDownTimer(ACTIVE_TIME, 1000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                    }

                    @Override
                    public void onFinish() {
                        Log.d(LOG, "1-second accel data collected!!");
                        // stop the accel data update

                        boolean currentMovingState = movingMonitor.isMoving();
                        // 정지 여부에 따라 GPS location update 요청 처리
                        if (!currentMovingState) {
                            Log.d(LOG, "before calling requestLocation");
                            if (!isRequestRegistered) {
                                requestLocation();
                                Log.d(LOG, "after calling requestLocation");
                            }
                            sensorManager.unregisterListener(stepMonitor);
                        } else {
                            Log.d(LOG, "before calling cancelLocationRequest");
                            if (isRequestRegistered) {
                                cancelLocationRequest();
                                Log.d(LOG, "after calling cancelLocationRequest");
                            }
                            sensorManager.registerListener(stepMonitor, accelLinear, SensorManager.SENSOR_DELAY_GAME);
                        }
                        // 움직임 여부에 따라 다음 alarm 설정
                        setNextAlarm(currentMovingState);

                        // 상태변화 확인
                        if (isChangeState(currentMovingState)) {
                            /**** 상태가 바뀌었다!!! 이전 상태를 기록해야해!!!! ****/
                            endTime = System.currentTimeMillis();
                            writeRecord(previousMovingState);
                            sendBroadcastToActivity();
                            // 이제는 커런트야! 이제 지금 장소를 확인해보자!
                            // 아! 그리고 시간도 기록해야해
                            previousMovingState = currentMovingState;
                            startTime = System.currentTimeMillis();
                            // check Place
                            if (isIndoor()) {
                                // 실내 특정 장소 확인
                                identifyIndoorPlace();
                            } else {
                                // 실외 특정 장소 확인
                                identifyOutdoorPlace();
                            }
                        }
                        // When you finish your job, RELEASE the wakelock
                        wakeLock.release();
                        wakeLock = null;
                    }
                };
                countDownTimer.start();
            }
        }
    };

    private void setNextAlarm(boolean moving) {
        // 움직임이면 5초 period로 등록
        // 움직임이 아니면 5초 증가, max 30초로 제한
        if (moving) {
            Log.d(LOG, "MOVING!!");
            period = PERIOD_FOR_MOVING;
        } else {
            Log.d(LOG, "NOT MOVING!!");
            period = period + PERIOD_INCREMENT;
            if (period >= PERIOD_MAX) {
                period = PERIOD_MAX;
            }
        }
        Log.d(LOG, "Next alarm: " + period);

        // 다음 alarm 등록
        Intent in = new Intent(BROADCAST_ALARM);
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, in, 0);
        alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + period - ACTIVE_TIME, pendingIntent);
    }
    /**********************************************************************************************/

    /*****************************************Classification***************************************/
    /**
     *
     * @param currentMovingState
     * @return 상태 변경 여부
     */
// 이건 상태 변경만 확인해야 해!
    private boolean isChangeState(boolean currentMovingState) {
        // 상태가 바뀌면
        // 이전에 기록된 정보에 대한 확인후 전송
        if(previousMovingState != currentMovingState) {
            long threshold;
            if (currentMovingState) {
                threshold = MOVING_TIME_THRESHOLD;
            }
            else {
                threshold = STAYING_TIME_THRESHOLD;
            }
            long elapsedTime = System.currentTimeMillis() - startTime;
            if (elapsedTime >= threshold) {
                Log.d(LOG, "상태가 바뀌었어!!");
                return true;
            }
        }
        Log.d(LOG, "이건 바뀌지 않은거야");
        return false;
    }


    private boolean isIndoor() {
        if(accuracy > GPS_ACCURACY_THRESHOLD) {
            Log.d(LOG, "실내에 위치");
            return true;
        }
        Log.d(LOG, "실외에 위치");
        return false;
    }

    private void identifyOutdoorPlace() {
        if((int)calcDistance(GROUND_X,GROUND_Y) <= 80+20) {
            placeName = "실외지정장소1";
        } else if((int)calcDistance(UNIVHQ_X,UNIVHQ_Y) <= 50+20) {
            placeName = "실외지정장소2";
        } else {
            placeName = "실외";
        }
        Log.d(LOG, placeName);
    }

    private void identifyIndoorPlace() {
        List<ScanResult> scanList = wifiManager.getScanResults();
        int indoor1 = 0;
        int indoor2 = 0;

        // 현재 스캔된 wifi 결과 중 미리 정의한 장소의 wifi fingerprinter가 일치하는지 판별함.
        // 2개 이상이 기준치에 충족되면 실내라고 판단
        for(int i = 1; i < scanList.size(); i++) {
            ScanResult result = scanList.get(i);
            if((result.BSSID.substring(0, 8)).equals(indoor1_MAC) && result.level > -50) {
                indoor1++;
            }
            else if((result.BSSID.substring(0, 8)).equals(INDOOR2_MAC) && result.level > -70) {
                indoor2++;
            }
        }
        if (indoor1 >= 2) {
            placeName = "실내지정장소1";

        } else if (indoor2 >= 2){
            placeName = "실내지정장소2";
        } else {
            placeName = "실내";
        }
        Log.d(LOG, placeName);
    }
    /**********************************************************************************************/

    /*************************************GPS Sensing**********************************************/
    LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            Log.d(LOG, "Longitude : " + location.getLongitude()
                    + " Latitude : " + location.getLatitude() + " Altitude: " + location.getAltitude()
                    + " Accuracy : " + location.getAccuracy());
            longitude = location.getLongitude();
            latitude = location.getLatitude();
            accuracy = location.getAccuracy();
        }

        public void onStatusChanged(String provider, int status, Bundle bundle) {
            Log.d(LOG, "GPS status changed. status code: " + status);
            if (status == 2)
                Log.d(LOG, "status: Available");
            else if (status == 1)
                Log.d(LOG, "status: Temporarily unavailable");
            else if (status == 0)
                Log.d(LOG, "status: Out of service");
            Toast.makeText(getApplicationContext(), "GPS status changed.", Toast.LENGTH_SHORT).show();
        }

        public void onProviderEnabled(String provider) {
            Log.d(LOG, "GPS onProviderEnabled: " + provider);
        }

        public void onProviderDisabled(String provider) {
            Log.d(LOG, "GPS onProviderDisabled: " + provider);
            Toast.makeText(getApplicationContext(), "GPS is off, please turn on!", Toast.LENGTH_LONG).show();
        }
    };

    private void requestLocation() {
        try {
            if (locationManager == null) {
                locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            }
            locationManager.requestLocationUpdates(GPS_PROVIDER,
                    3000,
                    0,
                    locationListener);
            isRequestRegistered = true;

        } catch (SecurityException se) {
            se.printStackTrace();
            Log.e(LOG, "PERMISSION_NOT_GRANTED");
        }
    }

    private void cancelLocationRequest() {
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (SecurityException se) {
                se.printStackTrace();
            }
        }
        locationManager = null;
        isRequestRegistered = false;
    }
    /**********************************************************************************************/

    /*************************************WIFI Sensing*********************************************/
    BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
                identifyIndoorPlace();
        }
    };
    /**********************************************************************************************/

    /********************************************Recording*****************************************/
    private void writeRecord(boolean moving) {
        // 날짜 기록용 객체들
        // 만난 시각을 텍스트 파일에 기록한다.
        String outputSentence = dateFormat.format(new Date(startTime)) + "~" + dateFormat.format(new Date(endTime)) + " " +
                msTominute(endTime - startTime) + "분 " + ((previousMovingState == true) ? "이동 " + (int) stepMonitor.getSteps() + "걸음" : "정지 " + placeName) + "\n";
        stepMonitor.resetSteps();

        Log.d(LOG, outputSentence);
        new TextFileManager().save(outputSentence);
    }

    private void sendBroadcastToActivity() {
        Intent intent = new Intent(BROADCAST_ACTION_ACTIVITY);
        sendBroadcast(intent);
    }

    /**********************************************************************************************/

    /*******************************Caclculate Value***********************************************/
    /**
     * 두 시각 사이의 간격(시간)을 계산하여 String값으로 반환하는 메소드
     * 분 형식
     * 출처: http://stackoverflow.com/questions/6710094/how-to-format-an-elapsed-time-interval-in-hhmmss-sss-format-in-java
     */
    private int msTominute(long val) {
        return (int) (val / 60000);
    }

    public double calcDistance(double lat1, double lon1){
        double EARTH_R, Rad, radLat1, radLat2, radDist;
        double distance, ret;

        EARTH_R = 6371000.0;
        Rad = Math.PI/180;
        radLat1 = Rad * lat1;
        radLat2 = Rad * latitude;
        radDist = Rad * (lon1 - longitude);

        distance = Math.sin(radLat1) * Math.sin(radLat2);
        distance = distance + Math.cos(radLat1) * Math.cos(radLat2) * Math.cos(radDist);
        ret = EARTH_R * Math.acos(distance);

        double rslt = Math.round(Math.round(ret) / 1000);
        double result = rslt;
        result = Math.round(ret);

        return result;
    }
    /*******************************Caclculate Value***********************************************/
}
