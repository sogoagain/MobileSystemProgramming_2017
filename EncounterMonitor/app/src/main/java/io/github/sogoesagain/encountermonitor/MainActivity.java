package io.github.sogoesagain.encountermonitor;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Created by sogoesagain on 2017. 4. 7..
 * MainActivity
 * - 사용자 UI를 구성하여 모니터링할 블루투스 기기와 사용자 명을 입력받고, 모니터링을 실행하거나 중지할 수 있다.
 * 또한, 모니터링 기록을 확인하거나 지울 수 있다.
 */
public class MainActivity extends AppCompatActivity {

    private boolean isPermitted = false;    // 위치, 파일 쓰기 권한 획득 여부
    private final int MY_PERMISSIONS_REQUEST = 1;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_ENABLE_DISCOVER = 2;

    private String bluetoothName;   // 블루투스 기기 이름
    private String userName;        // 대상자 이름
    private EditText etBluetoothName;   // 블루투스 기기 이름을 입력하는 EditText
    private EditText etUserName;        // 대상자 이름을 입력하는 EditText


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI View들 연결
        etBluetoothName = (EditText) findViewById(R.id.bluetoothName);
        etUserName = (EditText) findViewById(R.id.userName);

        // 런타임 권한 요청
        requestRuntimePermission();

        // Bluetooth Adapter 얻기
        // JELLY_BEAN_MR2 이상에서는 BluetoothManager을 이용해 Bluetooth Adapter를 얻는다.
        BluetoothAdapter bluetoothAdapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();

        // BT adapter 확인
        // 장치가 블루투스를 지원하지 않는 경우 null 반환
        if (bluetoothAdapter == null) {
            // 블루투스 지원하지 않기 때문에 블루투스를 이용할 수 없음
            // alert 메세지를 표시하고 사용자 확인 후 종료하도록 함
            // AlertDialog.Builder 이용, set method에 대한 chaining call 가능
            new AlertDialog.Builder(this)
                    .setTitle("Not compatible")
                    .setMessage("Your device does not support Bluetooth")
                    .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();
        } else if (!bluetoothAdapter.isEnabled()) {
            // 블루투스 이용 가능 - 스캔하고, 연결하고 등 작업을 할 수 있음

            // 필요한 경우, 블루투스
            // 블루투스를 지원하지만 현재 비활성화 상태이면, 활성화 상태로 변경해야 함
            // 이는 사용자의 동의를 구하는 다이얼로그가 화면에 표시되어 사용자가 활성화 하게 됨
            // 비활성화 상태
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBTIntent, REQUEST_ENABLE_BT);
        }
    }

    /**
     * "Enable Bluetooth Discoverable" 버튼을 눌렀을 때 실행되는 callback 메소드
     */
    public void onClickDiscover(View view) {
        if (!checkPermission()) {
            return;
        }
        // BT discoverable을 요청하기 위한 Intent action을 이용하여 intent 객체 생성
        Intent discoverIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);

        // 검색 가능 시간 설정. 0을 매개변수로 전달해 항상 검색 가능하도록 설정
        discoverIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);

        // 검색 가능하게 허용할 것인지 사용자에게 묻는 activity를 실행, 화면에 다이얼로그 같은 형태로 표시됨
        startActivityForResult(discoverIntent, REQUEST_ENABLE_DISCOVER);
    }

    /**
     * "Register BT device and user" 버튼을 눌렀을 때 실행되는 메소드
     * 사용자가 입력한 모니터링할 디바이스 기기명과 대상자 이름을 저장함.
     *
     * @param view
     */
    public void onClickRegister(View view) {
        if (!checkInputValue()) {
            return;
        }
        // EditText에 입력된 모니터링 대상 디바이스와 대상자 이름을 String 변수에 담음
        bluetoothName = etBluetoothName.getText().toString();
        userName = etUserName.getText().toString();

        Toast.makeText(this, R.string.add_target, Toast.LENGTH_LONG).show();
    }

    /**
     * "START ENCOUNTER MONITORING" 버튼 눌렀을 때 실행되는 메소드
     * 모니터링하는 서비스를 실행시킴
     *
     * @param view
     */
    public void onClickStart(View view) {
        if (!checkPermission()) {
            return;
        }

        // 블루투스 기기명과 사용자 이름이 등록되어있지 않다면 경고메시지를 띄운다.
        if (bluetoothName == null || userName == null) {
            Toast.makeText(this, R.string.do_not_register, Toast.LENGTH_LONG).show();
            return;
        }

        // 현재 모니터링 수행중이 아니면 설정된 블루투스 디바이스에 대해 모니터링하는 서비스를 실행시킨다.
        if (!isAliveService(MainActivity.this, MonitoringService.class.getName())) {
            Intent intent = new Intent(this, MonitoringService.class);
            intent.putExtra("BTName", bluetoothName);
            intent.putExtra("UserName", userName);

            startService(intent);
        } else {
            // 모니터링이 이미 수행중이면 경고 메세지를 띄운다.
            Toast.makeText(this, R.string.now_run_service, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * "STOP ENCOUNTER MONITORING" 버튼을 눌렀을 때 실행되는 메소드
     * 모니터링하는 서비스를 중지시킴.
     *
     * @param view
     */
    public void onClickStop(View view) {
        if (!checkPermission()) {
            return;
        }
        // 실행중인 서비스가 있다면 서비스를 종료한다.
        if (isAliveService(MainActivity.this, MonitoringService.class.getName())) {
            stopService(new Intent(this, MonitoringService.class));
        } else {
            // 실행중인 서비스가 없다면 경고 토스트 메세지를 띄운다.
            Toast.makeText(this, R.string.no_service, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * "VIEW ENCOUNTER LOG" 버튼을 눌렀을 때 실행되는 메소드
     * 기존에 텍스트 파일로 저장되어있던 ENCOUNTER 기록을 사용자 UI를 통해 보여준다.
     *
     * @param view
     */
    public void onClickView(View view) {
        if (!checkPermission()) {
            return;
        }

        // Encounter 로그 기록을 보여준다.
        TextFileManager textFileManager = new TextFileManager();
        ((TextView) findViewById(R.id.logTextView)).setText(textFileManager.load());
    }

    /**
     * "CLEAR ENCOUNTER LOG" 버튼을 눌렀을 때 실행되는 메소드
     * 기존에 텍스트 파일로 저장되어있던 ENCOUNTER 기록을 삭제한다.
     *
     * @param view
     */
    public void onClickClear(View view) {
        if (!checkPermission()) {
            return;
        }

        // Encounter 로그 기록을 지운다.
        TextFileManager textFileManager = new TextFileManager();
        textFileManager.delete();
        ((TextView) findViewById(R.id.logTextView)).setText(textFileManager.load());
        Toast.makeText(this, R.string.clear_log, Toast.LENGTH_LONG).show();
    }

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

    /**
     * @return - 입력된 값들에 대한 적절성 여부 (true, false)
     */
    private boolean checkInputValue() {
        if (etBluetoothName.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, String.format(getString(R.string.input_warning), getString(R.string.bluetooth_device_name))
                    , Toast.LENGTH_SHORT).show();
            return false;
        }
        if (etBluetoothName.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, String.format(getString(R.string.input_warning), getString(R.string.user_name))
                    , Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int responseCode, Intent data) {
        // 요청 코드에 따라 처리할 루틴을 구분해줌
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (responseCode == RESULT_CANCELED) {
                    // 사용자가 활성화 상태로 변경하는 것을 허용하지 않음
                    // 블루투스를 사용할 수 없으므로 애플리케이션 종료
                    Toast.makeText(this, R.string.cannot_use_bluetooth, Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            case REQUEST_ENABLE_DISCOVER:
                if (responseCode == RESULT_CANCELED) {
                    // 사용자가 DISCOVERABLE 허용하지 않음 (다이얼로그 화면에서 거부를 선택한 경우)
                    Toast.makeText(this, R.string.disable_discoverable, Toast.LENGTH_SHORT).show();
                }
        }
    }


    /**
     * checkPermission()
     *
     * @return - 위치 권한과 저장소 접근 권한을 갖고 있는지 여부 (true, false)
     */
    private boolean checkPermission() {
        if (!isPermitted) {
            Toast.makeText(this, String.format(getString(R.string.permission_error))
                    , Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    private void requestRuntimePermission() {
        //*******************************************************************
        // Location Runtime permission check
        //*******************************************************************
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)) {

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
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
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

                    // ACCESS_COARSE_LOCATION 권한을 얻음
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
}
