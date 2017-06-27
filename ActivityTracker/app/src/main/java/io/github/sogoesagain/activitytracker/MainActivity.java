package io.github.sogoesagain.activitytracker;

/**
 * Created by K&Y on 2017-06-09.
 */

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.StringTokenizer;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AT_MainActivity";
    private static final String BROADCAST_ACTION_ACTIVITY = "io.github.sogoesagain.activity";
    private final int MY_PERMISSIONS_REQUEST = 1;
    private boolean isPermitted = false;

    // File
    private TextFileManager textFileManager = new TextFileManager();

    // UI
    private ListView listView;
    private ArrayAdapter<String> arrayAdapter;     // ListView에 쓰일 ArrayAdapter
    private ArrayList<String> recordList;           // 메모 제목들을 담고있는 ArrayList
    private TextView totalRecordView;

    /**
     * ActivityMonitorService에서 상태(정지, 이동) 변경 시 보내는 브로드캐스트를 전달받음.
     */
    private BroadcastReceiver MyStepReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(BROADCAST_ACTION_ACTIVITY)) {
                updateView();
            }
        }
    };

    /************************************액티비티 생명주기*****************************************/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // init
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestRuntimePermission();

        // Setup BroadcaseReceiver
        IntentFilter intentFilter = new IntentFilter(BROADCAST_ACTION_ACTIVITY);
        registerReceiver(MyStepReceiver, intentFilter);

        /********** UI **********/
        // textView
        totalRecordView = (TextView) findViewById(R.id.totalRecordView);
        //listView
        listView = (ListView)findViewById(R.id.listView);
        recordList = new ArrayList<>();
        arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, recordList);
        listView.setAdapter(arrayAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateView();
        changeTextViewColor();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(MyStepReceiver);
    }
    /**********************************************************************************************/

    /*************************************UI Interface*********************************************/
    public void onClick(View v) {
        if (!checkPermission()) {
            return;
        }
        if(v.getId() == R.id.startMonitor) {
            if (!isAliveService(MainActivity.this, ActivityMonitorService.class.getName())) {
                Intent intent = new Intent(this, ActivityMonitorService.class);
                startService(intent);
            } else {
                Toast.makeText(getApplicationContext(), R.string.now_monitoring, Toast.LENGTH_SHORT).show();
            }
        } else if(v.getId() == R.id.stopMonitor) {
            if (isAliveService(MainActivity.this, ActivityMonitorService.class.getName())) {
                stopService(new Intent(this, ActivityMonitorService.class));
            } else {
                Toast.makeText(getApplicationContext(), R.string.dont_monitoring, Toast.LENGTH_SHORT).show();
            }
        } else if(v.getId() == R.id.clearLog) {
            TextFileManager textFileManager = new TextFileManager();
            textFileManager.delete();
            updateView();
            Toast.makeText(this, R.string.clear_log, Toast.LENGTH_LONG).show();
        }
        changeTextViewColor();
    }

    private void changeTextViewColor() {
        Button startButton = (Button) findViewById(R.id.startMonitor);
        if (isAliveService(MainActivity.this, ActivityMonitorService.class.getName())) {
            startButton.setBackgroundResource(R.color.colorAccent);
        } else {
            startButton.setBackgroundColor(Color.GRAY);
        }
    }

    private void updateView() {
        int totalMovingTime = 0;
        int totalSteps = 0;
        String topPlace = "-";
        int[] times = new int[4];

        arrayAdapter.clear();
        ArrayList<String> records = textFileManager.loadLine();
        Log.d(TAG, records.toString());
        for(String record : records) {
            arrayAdapter.add(record);
            if(record.contains("이동")) {
                StringTokenizer stringTokenizer = new StringTokenizer(record);
                while(stringTokenizer.hasMoreTokens()) {
                    String token = stringTokenizer.nextToken();
                    if(token.contains("분")) {
                        totalMovingTime += Integer.parseInt(token.substring(0, token.length() - 1));
                    } else if(token.contains("걸음")) {
                        totalSteps += Integer.parseInt(token.substring(0, token.length() - 2));
                    }
                }
            } else {
                String place = record.substring(record.lastIndexOf(' ') + 1);
                int time = Integer.parseInt(record.substring(12, record.indexOf("분")));

                switch(place) {
                    case "실내지정장소1":
                        times[0] += time;
                        break;
                    case "실내지정장소2":
                        times[1] += time;
                        break;
                    case "실외지정장소1":
                        times[2] += time;
                        break;
                    case "실외지정장소2":
                        times[3] += time;
                        break;
                }

                int index = 0, max = 0;
                for(int i = 0; i < 4; i++) {
                    if(max < times[i]) {
                        max = times[i];
                        index = i;
                    }
                }
                switch(index) {
                    case 0:
                        topPlace = "실내지정장소1";
                        break;
                    case 1:
                        topPlace = "실내지정장소2";
                        break;
                    case 2:
                        topPlace = "실외지정장소1";
                        break;
                    case 3:
                        topPlace = "실외지정장소2";
                        break;
                }
            }
        }

        totalRecordView.setText("Moving Time: " + totalMovingTime +"분\n" +
                                    "Steps: " + totalSteps + "걸음\n" +
                                    "Top Place: " + topPlace);
        return;
    }
    /**********************************************************************************************/

    /**
     * @param context
     * @param serviceName - 확인하고자하는 서비스의 이름
     * @return - 이름이 serviceName인 서비스가 실행중이면 true, 그렇지 않다면 false를 반환한다.
     */
    private Boolean isAliveService(Context context, String serviceName) {
        // ActivityManager 객체를 이용해 현재 시스템에서 돌고있는 서비스들의 정보를 가져온다.
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        // 현재 시스템에서 돌고있는 서비스들 중에 serviceName이 있다면 true를 반환한다.
        for (ActivityManager.RunningServiceInfo rsi : activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceName.equals(rsi.service.getClassName()))
                return true;
        }
        // 그렇지 않다면 false를 반환한다.
        return false;
    }


    /***************************************Permission*********************************************/
    /**
     * checkPermission()
     *
     * @return - 위치 권한과 저장소 접근 권한을 갖고 있는지 여부 (true, false)
     */
    private boolean checkPermission() {
        if (!isPermitted) {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void requestRuntimePermission() {
        //*******************************************************************
        // Location Runtime permission check
        //*******************************************************************
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST);
            }
        } else {
            // ACCESS_COARSE_LOCATION 권한이 있는 것
            isPermitted = true;
        }
        //*********************************************************************
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST: {
                // If request is cancelled, the result arrays are empty.
                if (hasAllPermissionsGranted(grantResults)) {
                    // permission was granted, yay! Do the
                    // read_external_storage-related task you need to do.
                    // ACCESS_FINE_LOCATION 권한을 얻음
                    isPermitted = true;
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    // 권한을 얻지 못 하였으므로 location 요청 작업을 수행할 수 없다
                    // 적절히 대처한다
                    isPermitted = false;
                }
                return;
            }
        }
    }

    public boolean hasAllPermissionsGranted(@NonNull int[] grantResults) {
        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                return false;
            }
        }
        return true;
    }
    /**********************************************************************************************/
}
