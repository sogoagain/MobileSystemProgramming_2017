package io.github.sogoagain.proximityalarm;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Created by sogoagain on 2017. 3. 23..
 * AddActivity
 * - 새롭게 근접 경보를 등록할 위치 정보를 사용자로부터 받는 액티비티.
 * - LocationListener 인터페이스를 구현함으로써 현재 위치값(위도, 경도)가 실시간으로 표시된다.
 * - 사용자는 '현재 위치 가져오기' 버튼을 통해 위도, 경도값을 현재 위치 값으로 바로 설정할 수 있다.
 * - 모든 위치 정보를 채우고 '등록'버튼을 누르게 되면 위치 정보들이 MainActivity로 전달된다.
 * - 위치 정보들은 UserLocation 객체 형태로 전달된다.
 */
public class AddActivity extends AppCompatActivity implements LocationListener {
    // 디버깅을 위한 로그 출력시 태그값
    private static final String TAG = "sogoagain_ADD";

    private String locationName;        // 장소명
    private double latitude, longitude; // 위도, 경도
    private float radius;               // 반경
    // UI 뷰들
    private EditText etName, etLatitude, etLongitude, etRadius;
    private TextView tvCurrentLatitude, tvCurrentLongitude;

    private boolean isPermitted = false;    // 위치 접근 권한 여부를 나타냄
    private LocationManager locationManager;    // 위치 제공자 객체, 현재 위치를 받아온다.


    /**
     * onCreate()
     * @param savedInstanceState
     * - UI 뷰들을 변수들과 연결한다.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add);

        // 현재 위치의 위도, 경도값을 표시해주는 텍스트 뷰
        tvCurrentLatitude = (TextView) findViewById(R.id.currentLatitude);
        tvCurrentLongitude = (TextView) findViewById(R.id.currentLongitude);

        // 위치 정보를 입력할 수 있는 EditText들
        etName = (EditText) findViewById(R.id.locationName);
        etLatitude = (EditText) findViewById(R.id.locationLatitude);
        etLongitude = (EditText) findViewById(R.id.locationLongtitude);
        etRadius = (EditText) findViewById(R.id.locationRadius);


    }

    /**
     * onResume()
     * - 위치 접근 권한을 확인한다.
     * - MainActivity에서 위치 권한이 없으면 AddActivity를 띄우지 못하게 막아놨으나 혹시나 예상치 못한 경우를 대비하였다.
     * - 접근 권한이 있다면 locationManager객체를 받아오고 설정한다.
     */
    @Override
    protected void onResume() {
        super.onResume();
        //*******************************************************************
        // Runtime permission check
        //*******************************************************************
        if (ContextCompat.checkSelfPermission(AddActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            // ACCESS_FINE_LOCATION 권한이 있는 것
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            isPermitted = true;
        } else {
            Toast.makeText(this, R.string.permission_error, Toast.LENGTH_LONG).show();
        }
        //*********************************************************************

    }

    /**
     * onPause()
     * - 위치 업데이트를 제거한다.
     */
    @Override
    protected void onPause() {
        super.onPause();

        if (isPermitted) {
            locationManager.removeUpdates(this);
        }
    }

    /**
     * onClickRegister(View v)
     * @param v - '등록' 버튼
     * - 입력된 값들에 대한 적절성 여부를 체크한다. (위도, 경도, 반경 입력범위 초과 등)
     * - 입력된 값들이 모두 적절하다면 해당 값들을 UserLocation 객체로 묶어 MainActivity로 전달한다.
     */
    public void onClickRegister(View v) {
        if (checkInputValue()) {
            UserLocation userLocation = new UserLocation(locationName, latitude, longitude, radius);
            Intent intent = new Intent();
            intent.putExtra("LOCATION_TO_ADD", userLocation);
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    /**
     * onClickGetCurrentLocation(View v)
     * @param v - '현재 위치 가져오기' 버튼
     * - 현재 위치에 표시되는 위도, 경도값을 해당 정보 입력 부분에 넣는다.
     * - 현재 위치에 표시되는 값이 없을 시(위치를 찾지 못할 시) 경고 메세지를 띄운다.
     */
    public void onClickGetCurrentLocation(View v) {
        TextView tvCurrentLatitude = (TextView) findViewById(R.id.currentLatitude);
        TextView tvCurrentLongitude = (TextView) findViewById(R.id.currentLongitude);

        if (tvCurrentLatitude.getText().toString().trim().isEmpty() ||
                tvCurrentLongitude.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, R.string.current_location_warning, Toast.LENGTH_SHORT).show();
            return;
        } else {
            etLatitude.setText(tvCurrentLatitude.getText());
            etLongitude.setText(tvCurrentLongitude.getText());
            latitude = Double.parseDouble(etLatitude.getText().toString());
            longitude = Double.parseDouble(etLongitude.getText().toString());
        }
    }

    /**
     * onLocationChanged(Location location)
     * @param location
     * - LocationListener 인터페이스를 구현하였기에 정의된 메소드.
     * - 현재 위치를 받아와 텍스트뷰에 표시한다.
     */
    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "" + location.getLatitude() + " " + location.getLongitude());
        tvCurrentLatitude.setText("" + location.getLatitude());
        tvCurrentLongitude.setText("" + location.getLongitude());
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    /**
     * checkInputValue()
     * @return - 입력된 값들에 대한 적절성 여부 (true, false)
     * - 입력해야할 4개의 항목 중 빈칸이 하나라도 있을 시 경고 메세지를 띄운다.
     * - 위도, 경도, 반경값은 아래의 조건을 만족시켜야 한다. 하나라도 만족하지 않을 시 경고 메세지를 띄운다.
     * - 반경값은 0일 수 없다.
     * - 위도값의 범위는 -90 ~ 90이다.
     * - 경도값의 범위는 -180 ~ 180이다.
     */
    private boolean checkInputValue() {
        if (etName.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, String.format(getString(R.string.input_warning), getString(R.string.location_name))
                    , Toast.LENGTH_SHORT).show();
            return false;
        }
        if (etLatitude.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, String.format(getString(R.string.input_warning), getString(R.string.location_latitude))
                    , Toast.LENGTH_SHORT).show();
            return false;
        }
        if (etLongitude.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, String.format(getString(R.string.input_warning), getString(R.string.location_longitude))
                    , Toast.LENGTH_SHORT).show();
            return false;
        }
        if (etRadius.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, String.format(getString(R.string.input_warning), getString(R.string.location_radius))
                    , Toast.LENGTH_SHORT).show();
            return false;
        }
        if (Float.parseFloat(etRadius.getText().toString()) == 0) {
            Toast.makeText(this, String.format(getString(R.string.range_warning), getString(R.string.location_radius))
                    , Toast.LENGTH_SHORT).show();
            return false;
        }

        locationName = etName.getText().toString();
        latitude = Double.parseDouble(etLatitude.getText().toString());
        longitude = Double.parseDouble(etLongitude.getText().toString());
        radius = Float.parseFloat(etRadius.getText().toString());

        if (!(latitude <= 90 && latitude >= -90)) {
            Toast.makeText(this, String.format(getString(R.string.range_warning), getString(R.string.location_latitude))
                    , Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!(longitude <= 180 && longitude >= -180)) {
            Toast.makeText(this, String.format(getString(R.string.range_warning), getString(R.string.location_longitude))
                    , Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;

    }
}
