package io.github.sogoesagain.proximityalarm;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

/**
 * Created by sogoesagain on 2017. 3. 23..
 * RemoveActivity
 * - 삭제할 위치명을 사용자로부터 받는 액티비티.
 * - 입력받은 장소명을 MainActivity에 전달한다.
 */
public class RemoveActivity extends AppCompatActivity {
    // 디버깅을 위한 로그 출력시 태그값
    private static final String TAG = "sogoesagain_REMOVE";
    private EditText etLocationName;    // 삭제할 위치명을 받는 EditText

    /**
     * onCreate(Bundle savedInstanceState)
     * @param savedInstanceState
     * - UI 뷰를 변수와 연결한다.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remove);

        etLocationName = (EditText) findViewById(R.id.locationName);
    }

    /**
     * onClickDelete(View v)
     * @param v - '삭제' 버튼
     * 입력된 장소명을 MainActivity로 전달한다.
     */
    public void onClickDelete(View v) {
        String locationName = etLocationName.getText().toString();
        Log.d(TAG, locationName);

        Intent intent = new Intent();
        intent.putExtra("LOCATION_TO_REMOVE", locationName);
        setResult(RESULT_OK, intent);
        finish();
    }
}
