package io.github.sogoesagain.encountermonitor;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by sogoesagain on 2017. 4. 7..
 */
public class MonitoringService extends Service {
    private static final String TAG = "MonitoringService";  // 디버깅을 위한 태그
    private static final int SCAN_TIME_INTERVAL = 120000;   // 블루투스 스캔 시간 간격 2분

    private BluetoothAdapter bluetoothAdapter;
    private EncounterReceiver encounterReceiver;    // 블루투스 관련 브로드캐스트 수신 객체
    private Timer timer = new Timer();  // 타이머 객체
    private TimerTask timerTask = null;

    // Date 출력형식을 위한 객체
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault());

    //***********************************
    // wake lock을 사용
    private PowerManager.WakeLock wakeLock;
    //***********************************

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        bluetoothAdapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();

        //**************************************************************
        // wake lock을 사용한다.
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Tag: partial wake lock");
        wakeLock.acquire();
        //**************************************************************
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // 모니터링 시작을 사용자에게 알려주며 텍스트 파일에 시작 시각과 함께 기록
        Toast.makeText(this, "EncounterMonitor 시작", Toast.LENGTH_SHORT).show();
        new TextFileManager().save("모니터링 시작 - " + dateFormat.format(new Date(System.currentTimeMillis())) + "\n");
        Log.d(TAG, "onStartCommand()");

        // MainActivity에서 Service를 시작할 때 사용한 intent에 담겨진 BT 디바이스와 사용자 이름 얻음
        String bluetoothName = intent.getStringExtra("BTName");
        String userName = intent.getStringExtra("UserName");

        // 블루투스 검색 시작, 종료, 기기 검색 종류의 브로드캐스트를 받는다.
        encounterReceiver = new EncounterReceiver(bluetoothName, userName, SCAN_TIME_INTERVAL);
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(encounterReceiver, filter);

        // 주기적으로 BT discovery 수행하기 위한 timer 가동
        startTimerTask();

        return super.onStartCommand(intent, flags, startId);
    }

    public void onDestroy() {

        // 모니터링 종료를 사용자에게 알려주며 텍스트 파일에 종료 시각과 함께 기록
        new TextFileManager().save("모니터링 종료 - " + dateFormat.format(new Date(System.currentTimeMillis())) + "\n");
        Toast.makeText(this, "EncounterMonitor 중지", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "onDestroy()");

        // timer로 주기적으로 BT discovery하는 것을 중지한다.
        stopTimerTask();
        unregisterReceiver(encounterReceiver);

        //***********************************
        // wake lock을 release한다.
        wakeLock.release();
        //***********************************
    }

    private void startTimerTask() {
        // TimerTask 생성한다
        timerTask = new TimerTask() {
            @Override
            public void run() {
                bluetoothAdapter.startDiscovery();
            }
        };

        // TimerTask를 Timer를 통해 실행시킨다
        // 1초 후에 타이머를 구동하고 SCAN_TIME_INTERVAL초마다 반복한다
        timer.schedule(timerTask, 1000, SCAN_TIME_INTERVAL);
    }

    private void stopTimerTask() {
        // 모든 태스크를 중단한다
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }
}
