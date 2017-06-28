package io.github.sogoagain.proximityalarm;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by sogoagain on 2017. 3. 23..
 * MainActivity
 * - 등록된 근접 경보들을 표시해주는 액티비티다.
 * - '등록'버튼, '삭제'버튼을 통해 근접 경보를 추가/삭제 하는 액티비티로 전환할 수 있다.
 * - AddActivity에서 받아온 정보를 통해 근접 경보를 추가한다.
 * - RemoveActivity에서 받아온 정보를 통해 근접 경보를 해제한다.
 * - sharedPreferences를 통해 이전 실행 상태를 보관하고 불러온다.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "sogoagain_MAIN";   // 디버깅을 위한 로그 출력시 태그값

    // IntentFilter 생성시 사용 하는 문자열 상수
    // 액션이 ALERT_ACTION_NAME+"장소명"인 브로드캐스트 메시지를 받는다.
    private static final String ALERT_ACTION_NAME = "io.github.sogoagain.ProximityAlert";
    private final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private static final int LOCATION_TO_ADD = 1;       // AddActivity의 requestCode
    private static final int LOCATION_TO_REMOVE = 2;    // RemoveActivity의 requestCode
    private static final int MAX_LOCATION = 3;          // 최대 위치 등록 갯수

    private LocationManager locationManager;                // 위치 제공자 객체 locationManager
    private ProximityAlertReceiver proximityAlertReceiver;  // 근접 경보 발생 브로드캐스트를 받는 리시버 객체
    private Map<String, PendingIntent> pendingIntentHashMap = new HashMap<>();  // 각 위치에 대응되는 PendingIntent를 저장하는 Map

    // 기기의 현재 위치가 변경 되었을 때 해당 사항을 전달 받아야 하므로 LocationListener 인터페이스를 구현한 객체를 생성한다.
    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            // 위치 데이터 전달을 확인하기 위해 Log에 위도, 경도 좌표를 출력한다.
            Log.d(TAG, "" + location.getLatitude() + " " + location.getLongitude());
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };

    private SharedPreferences sharedPreferences;    // 현재 등록된 위치들을 저장하고 저장된 위치 정보를 불러오기 위한 SharedPreferences 객체
    private ListView lvLocationList;                // 근접 경보가 등록된 장소들을 나열해서 보여주는 ListView
    private ArrayAdapter<String> listViewAdapter;   // ListView에 쓰이는 adapter
    private boolean isPermitted = false;            // 위치 접근 권한 여부를 나타냄
    private boolean isAlertRegistered = false;      // 근접 경보가 등록되었는지 여부를 나타냄

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI에 존재하는 뷰들을 변수들과 연결시킨다.
        lvLocationList = (ListView) findViewById(R.id.locationListView);
        listViewAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        lvLocationList.setAdapter(listViewAdapter);

        // RuntimePermission 검사 및 요청
        requestRuntimePermission();

        // SharedPreferences 객체 생성
        sharedPreferences = getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);

        // SharedPreferences 객체를 통해 이전에 근접 경보 등록되었던 장소들을 불러와 등록한다.
        addSavedLocation();
    }

    /**
     * addSavedLocation()
     * - onCreate()에서 한번 호출 되는 메소드.
     * - 이전 실행에서 등록했던 위치들을 SharedPreferences를 통해 읽어온다.
     * - 읽어온 위치들에 대해 다시 근접 경보를 등록한다.
     */
    private void addSavedLocation() {
        // keys는 sharedPreferences에 저장된 모든 데이터들을 Map의 형태로 갖고있다.
        Map<String,?> keys = sharedPreferences.getAll();
        // sharedPreferences에서 불러온 정보들을 정리해 다시 저장하는 Map
        Map<String, UserLocation> savedLocations = new HashMap<>();

        // keys에 저장된 데이터들의 순서가 일정하지 않기 때문에 정리할 필요가 있다.
        // 위치 정보들을 sharedPreferences에 저장할 때, "'장소명' + '자료종류 번호'"을 key값으로 저장한다.
        // 자료종류 번호는 다음과 같다.
        // 0: 장소명, 1: 위도, 2: 경도, 3: 반경
        // 저장된 자료들에서 자료명은 유일하므로 이것을 key값으로 하는 새로운 Map인 savedLocations를 이용해 불러온 자료를 정리한다.
        for(Map.Entry<String,?> entry : keys.entrySet()){
            // 불러온 자료에서 장소명과 자료종류 번호를 분리해낸다.
            String key = entry.getKey();    // 불러온 데이터
            int type = Character.getNumericValue(key.charAt(key.length() - 1)); // 자료종류 번호
            key = key.substring(0, key.length() - 1);   // 장소명

            // 만일 장소명이 savedLocations에 key값으로 없다면, 해당 장소의 데이터를 처음 불러온 것이다.
            // 따라서 자료 내용을 저장할 UserLocation객체를 생성해 장소명을 key값으로 savedLocations에 저장한다.
            if(!savedLocations.containsKey(key)) {
                savedLocations.put(key, new UserLocation(key, 0, 0, 0));
            }

            // 자료 번호에 따라 해당 장소명의 UserLocation객체에 정보를 적절히 변환하여 저장한다.
            switch(type) {
                case 1:
                    savedLocations.get(key).setLatitude(Double.parseDouble(entry.getValue().toString()));
                    break;
                case 2:
                    savedLocations.get(key).setLongitude(Double.parseDouble(entry.getValue().toString()));
                    break;
                case 3:
                    savedLocations.get(key).setRadius((Float)entry.getValue());
                    break;
            }
        }

        // 디버깅을 위해 불러온 데이터를 Log로 띄워 확인한다.
        // addProximityAlert()를 이용해 근접 경보를 등록한다.
        for (Map.Entry<String, UserLocation> entry : savedLocations.entrySet()) {
            UserLocation savedLocation = entry.getValue();
            Log.d(TAG, "불러온 데이터: " + savedLocation.getLocationName() + "\n" + savedLocation.getLatitude()
                    + "\n" + savedLocation.getLongitude() + "\n" + savedLocation.getRadius());
            addProximityAlert(savedLocation);
        }

        // 불러온 데이터가 있다면 이전 위치들에 대해 근접 경보를 시작한다는 알림을 띄운다.
        if(!savedLocations.isEmpty()) {
            Toast.makeText(this, getString(R.string.add_all), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * onDestroy()
     * - 근접 경보를 해제하고 자원을 정리한다.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 자원 사용 해제
        if (isAlertRegistered) {
            clearProximityAlert();
        }
    }

    /**
     * onClickRegister(View v)
     * @param v - 등록 버튼
     * - '등록' 버튼의 콜백 메소드.
     */
    public void onClickRegister(View v) {
        // 위치 접근 권한이 없을 시 경고 토스트 메세지를 띄운다.
        if(!isPermitted) {
            Toast.makeText(this, R.string.permission_error, Toast.LENGTH_LONG).show();
            return;
        }
        // 현재 등록된 위치의 갯수가 허용치라면 경고 토스트 메세지를 띄운다.
        if(pendingIntentHashMap.size() >= MAX_LOCATION) {
            Toast.makeText(this, R.string.location_max, Toast.LENGTH_LONG).show();
            return;
        }

        // 위 두 상황이 아닐시 AddActivity를 실행한다.
        Intent intent = new Intent(this, AddActivity.class);
        startActivityForResult(intent, LOCATION_TO_ADD);
    }

    /**
     * onClickDelete(View v)
     * @param v - 삭제 버튼
     * - '삭제' 버튼의 콜백 메소드.
     */
    public void onClickDelete(View v) {
        // 위치 접근 권한이 없을 시 경고 토스트 메세지를 띄운다.
        if(!isPermitted) {
            Toast.makeText(this, R.string.permission_error, Toast.LENGTH_LONG).show();
            return;
        }

        // 위치 접근 권한이 있다면 RemoveActivity를 실행한다.
        Intent intent = new Intent(this, RemoveActivity.class);
        startActivityForResult(intent, LOCATION_TO_REMOVE);
    }

    /**
     * onActivityResult(int requestCode, int resultCode, Intent data)
     * @param requestCode - LOCATION_TO_ADD: AddActivity, LOCATION_TO_REMOVE: RemoveActivity
     * @param resultCode
     * @param data - AddActivity로 부터온 데이터: UserLocation 객체, RemoveActivity로 부터온 데이터: 삭제할 장소명 String
     * - AddActivity와 RemoveActivity로 부터 전달받은 데이터를 처리하는 메서드.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // AddActivity로 부터온 데이터 처리. 근접 경보를 등록할 위치의 정보를 담고있는 UserLocation객체를 받아온다.
        if (requestCode == LOCATION_TO_ADD) {
            if (resultCode == RESULT_OK) {
                // 데이터를 가져온다. Intent에 사용자 정의 객체를 전달받았기 때문에 getSerializableExtra메서드를 통해 가져온다.
                UserLocation locationToAdd = (UserLocation) data.getSerializableExtra("LOCATION_TO_ADD");

                // 전달받은 데이터를 확인하기 위해 Log에 띄운다. (디버깅)
                Log.d(TAG, locationToAdd.getLocationName() + "\n" + locationToAdd.getLatitude()
                        + "\n" + locationToAdd.getLongitude() + "\n" + locationToAdd.getRadius());

                // 등록할 장소명이 이미 등록되어 있는 장소들과 겹치지 않는다면 addProximityAlert()메소드를 호출해 근접 경보를 등록한다.
                // saveLocationInSharedPreference() 메서드를 통해 sharedPreferences에 해당 장소 정보를 저장해둔다.
                // 토스트 메세지를 통해 사용자에게 등록하였음을 알려준다.
                // 등록할 장소명이 중복된다면 경고 메세지를 띄운다.
                if (!pendingIntentHashMap.containsKey(locationToAdd.getLocationName())) {
                    addProximityAlert(locationToAdd);
                    saveLocationInSharedPreference(locationToAdd);
                    Toast.makeText(this, String.format(getString(R.string.add_alert), locationToAdd.getLocationName()),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Log.d(TAG, locationToAdd.getLocationName() + "은 등록된 위치가 아닙니다.");
                    Toast.makeText(this, String.format(getString(R.string.already_add), locationToAdd.getLocationName()),
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
        // RemoveActivity로 부터온 데이터 처리. 삭제할 장소명의 String을 받아온다.
        else if (requestCode == LOCATION_TO_REMOVE) {
            if (resultCode == RESULT_OK) {
                // 데이터를 전달받고 Log를 통해 확인한다.
                String locationName = data.getStringExtra("LOCATION_TO_REMOVE");
                Log.d(TAG, locationName);

                // 전달받은 장소명이 근접 경보중인 장소명과 일치하는 것이 있다면 removeProximityAlert() 메서드를 통해 근접 경보를 해제한다.
                // 일치하는 장소명이 없다면 경고 메세지를 띄운다.
                if (pendingIntentHashMap.containsKey(locationName)) {
                    removeProximityAlert(locationName);
                    Toast.makeText(this, String.format(getString(R.string.remove_alert), locationName), Toast.LENGTH_SHORT).show();
                } else {
                    Log.d(TAG, locationName + "은 등록된 위치가 아닙니다.");
                    Toast.makeText(this, String.format(getString(R.string.find_fail), locationName),
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * addProximityAlert(UserLocation userLocation)
     * @param userLocation - 등록할 위치의 정보를 갖고 있는 UserLocation 객체
     * - userLocation에는 등록할 위치의 장소명, 위도, 경도, 반경 정보가 담겨있다.
     */
    private void addProximityAlert(UserLocation userLocation) {
        // 브로드캐스트 리시버 객체를 등록한다.
        // ALERT_ACTION_NAME + "장소명" 액션을 갖는 브로드캐스드를 수신한다.
        IntentFilter filter = new IntentFilter(ALERT_ACTION_NAME + userLocation.getLocationName());
        registerReceiver(proximityAlertReceiver, filter);
        isAlertRegistered = true;

        // ProximityAlert 등록을 위한 PendingIntent 객체 얻기
        Intent intent = new Intent(ALERT_ACTION_NAME + userLocation.getLocationName());
        intent.putExtra("PROXIMITY_LOCATION_NAME", userLocation.getLocationName()); // 장소명을 intent에 넣어둔다.
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);

        // 얻은 PendingIntent 객체를 Map 형태로 저장한다. (key값은 장소명)
        pendingIntentHashMap.put(userLocation.getLocationName(), pendingIntent);
        try {
            // 근접 경보 등록
            locationManager.addProximityAlert(userLocation.getLatitude(), userLocation.getLongitude(),
                    userLocation.getRadius(), -1, pendingIntent);
            // listView에 반영한다.
            listViewAdapter.add(userLocation.getLocationName());

            // 디버깅을 위해 등록한 장소의 정보를 Log로 출력한다.
            Log.d(TAG, "근접 경보 등록: " + userLocation.getLocationName() + "\n" + userLocation.getLatitude()
                    + "\n" + userLocation.getLongitude() + "\n" + userLocation.getRadius());
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    /**
     * saveLocationInSharedPreference(UserLocation userLocation)
     * @param userLocation
     * - userLocation의 정보들을 sharedPreferences로 저장한다.
     * - 저장할떄의 key값은 "장소명"+'자료종류 번호'다.
     * - 자료종류 번호: 0 - 장소명, 1 - 위도, 2 - 경도, 3 - 반경
     */
    private void saveLocationInSharedPreference(UserLocation userLocation) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString( userLocation.getLocationName() + 0, userLocation.getLocationName());
        editor.putString( userLocation.getLocationName() + 1, "" + userLocation.getLatitude());
        editor.putString(userLocation.getLocationName() + 2, "" + userLocation.getLongitude());
        editor.putFloat(userLocation.getLocationName() + 3, userLocation.getRadius());
        editor.commit();

        // 저장한 정보를 확인하기 위해 Log에 출력한다.
        Log.d(TAG, "SharedPreference 저장"
                + "\n" + sharedPreferences.getString(userLocation.getLocationName() + 0,"")
                + "\n" + sharedPreferences.getString( userLocation.getLocationName() + 1,"")
                + "\n" + sharedPreferences.getString( userLocation.getLocationName()  + 2,"")
                + "\n" + sharedPreferences.getFloat(userLocation.getLocationName() + 3,0));
    }

    /**
     * removeProximityAlert(String locationName)
     * @param locationName
     * - locationName에 해당하는 장소의 근접 경보를 해제한다.
     */
    private void removeProximityAlert(String locationName) {
        try {
            // locationName에 해당하는 PendingIntent를 불러와 locationManager를 통해 해제한다.
            locationManager.removeProximityAlert(pendingIntentHashMap.get(locationName));
            // listView에서 해당 장소명을 삭제한다.
            listViewAdapter.remove(locationName);
            // 근접 경보가 등록된 위치들의 pendingIntent를 저장하는 자료구조에서 해당 위치에 관한 정보를 삭제한다.
            pendingIntentHashMap.remove(locationName);

            // sharedPreferences에서도 삭제한다.
            removeLocationInSharedPreference(locationName);
            Log.d(TAG, "근접 경보 해제: " + locationName);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    /**
     * removeLocationInSharedPreference(String locationName)
     * @param locationName
     * - sharedPreferences에서 locationName 장소명에 해당하는 정보들을 삭제한다.
     */
    private void removeLocationInSharedPreference(String locationName) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        // 총 4개의 자료를 삭제한다.
        for(int i = 0; i < 4; i++) {
            editor.remove(locationName + i);
        }
        editor.commit();

        Log.d(TAG, "SharedPreference 삭제: " + locationName);
    }

    /**
     * clearProximityAlert()
     * - onDestroy()에서 호출되는 메서드
     * - 현재 근접 경보가 등록된 모든 위치들에 대해 근접 경보를 해제한다.
     * - 브로드캐스트 리시버 등록을 해제한다.
     * - sharedPreferences에 저장된 정보들은 삭제하지 않음에 유의. (다음에 다시 불러와야함)
     */
    private void clearProximityAlert() {
        try {
            // 현재 근접 경보중인 모든 위치들에 대해 근접 경보를 해제한다.
            for (Map.Entry<String, PendingIntent> entry : pendingIntentHashMap.entrySet()) {
                String locationName = entry.getKey();
                PendingIntent pendingIntent = entry.getValue();

                // 근접경보 해제
                locationManager.removeProximityAlert(pendingIntent);

                // 리스트뷰에서 삭제
                listViewAdapter.remove(locationName);
                Log.d(TAG, "근접 경보 해제: " + locationName);
            }
            pendingIntentHashMap.clear();
            unregisterReceiver(proximityAlertReceiver);
            Toast.makeText(this, getString(R.string.remove_all), Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void requestRuntimePermission() {
        //*******************************************************************
        // Runtime permission check
        //*******************************************************************
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            }
        } else {
            // ACCESS_FINE_LOCATION 권한이 있는 것
            isPermitted = true;
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            proximityAlertReceiver = new ProximityAlertReceiver();
        }
        //*********************************************************************
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // read_external_storage-related task you need to do.

                    // ACCESS_FINE_LOCATION 권한을 얻음
                    isPermitted = true;
                    locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                            0, 0, locationListener);
                    proximityAlertReceiver = new ProximityAlertReceiver();

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.

                    // 권한을 얻지 못 하였으므로 location 요청 작업을 수행할 수 없다
                    // 적절히 대처한다
                    isPermitted = false;
                    Toast.makeText(this, R.string.permission_error, Toast.LENGTH_LONG).show();
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }
}
