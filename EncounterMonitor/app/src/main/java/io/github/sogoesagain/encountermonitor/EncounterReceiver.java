package io.github.sogoesagain.encountermonitor;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;

/**
 * Created by sogoesagain on 2017. 4. 7..
 * EncounterReceiver
 * - 아래 3개 액션에 해당하는 브로드캐스트를 수신하는 리시버 클래스
 * BluetoothDevice.ACTION_FOUND)
 * BluetoothAdapter.ACTION_DISCOVERY_STARTED
 * BluetoothAdapter.ACTION_DISCOVERY_FINISHED
 *
 * ACTION_FOUND 브로드캐스트를 통해 현재 모니터링하는 블루투스 기기가 주변에 있는지 파악하고
 * ACTION_DISCOVERY_FINISHED 브로드캐스트를 받는 시점에서 만난 상태인지, 헤어진 상태인지 판별한다.
 * ACTION_DISCOVERY_STARTED 브로드캐스트는 디버깅을 위해 수신한다. (블루투스 탐지 시작이 올바른지 확인하기 위해서)
 */
public class EncounterReceiver extends BroadcastReceiver {
    private static final String TAG = "EncounterReceiver";  // 디버깅을 위한 로그의 태그

    /**
     * EncounterTimeStamp 클래스
     * 블루투스 기기가 감지되었는지, 주변에 없었는지여부를 해당 시각과 함께 기록하는 클래스
     * 감지 여부와 해당 시각을 쌍으로 저장한다.
     * 3번의 기록을 통해 만났는지 헤어졌는지를 결정한다.
     * 만남여부: 3번 연속으로 블루투스 기기가 감지되었을 경우 (0번 인덱스에 있는 기록이 최초 만난 시점이 된다.)
     * 헤어짐여부: 3번 연속으로 블루투스 기기를 찾지 못하였을 경우 (0번 인덱스에 있는 기록이 헤어진 시점이 된다.)
     */
    private class EncounterTimeStamp {
        private static final int MAX_COUNT = 3;
        private LinkedList<Boolean> isEncounters = new LinkedList<>();
        private LinkedList<Long> times = new LinkedList<>();

        // TimeStamp를 찍는다.
        // 감지 여부와 해당 시각을 저장
        void record(boolean isEncounter) {
            isEncounters.add(isEncounter);
            times.add(System.currentTimeMillis());

            // MAX_COUNT를 벗어나는 데이터는 삭제한다.
            // 해당 클래스는 항상 최근 3개의 데이터만을 저장한다.
            if (isEncounters.size() > MAX_COUNT) {
                isEncounters.remove(0);
                times.remove(0);
            }
        }

        // 만남 여부를 계산후 반환한다.
        boolean isHello() {
            if (times.size() < MAX_COUNT) {
                return false;
            }
            boolean result = true;

            // MAX_COUNT개의 기록이 모두 true이면 만난것으로 인지
            for (boolean temp : isEncounters) {
                result = result && temp;
            }

            return result;
        }

        // 헤어짐 여부를 계산후 반환한다.
        boolean isGoodBye() {
            if (times.size() < MAX_COUNT) {
                return false;
            }
            boolean result = true;

            // MAX_COUNT개의 기록이 모두 false이면 헤어진 것으로 인지
            for (boolean temp : isEncounters) {
                result = result && !temp;
            }

            return result;
        }

        // 첫번째 인덱스의 시각 값을 반환한다.
        long getFirstTime() {
            return times.get(0);
        }
    }

    private String bluetoothName;   // 검색 대상이 되는 블루투스 기기 이름
    private String userName;        // 대상자 이름

    // 텍스트 파일로 기록하기 위한 객체
    private TextFileManager textFileManager = new TextFileManager();

    private int scanTimeInterval = 0;   // 블루투스 탐지 시간 간격
    private EncounterTimeStamp timeStamp = new EncounterTimeStamp();    // 시각을 기록하기 위한 객체
    private long helloTime = 0;         // 만난 시각
    private long goodByeTime = 0;       // 헤어진 시각
    private boolean isFind = false;     // 대상 블루투스 기기를 찾았는지 여부
    private boolean isRecording = false;// 현재 대상을 만나서 기록중인지 여부
    // Date 출력형식을 위한 객체
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault());

    // EncounterReceiver의 생성자
    // 대상 블루투스 기기이름과 사용자 이름, 탐색 주기를 받아온다.
    EncounterReceiver(String bluetoothName, String userName, int scanTimeInterval) {
        this.bluetoothName = bluetoothName;
        this.userName = userName;
        this.scanTimeInterval = scanTimeInterval;
    }

    /**
     * 브로드캐스트를 수신하는 메소드
     * ACTION_FOUND 브로드캐스트를 통해 현재 모니터링하는 블루투스 기기가 주변에 있는지 파악하고
     * ACTION_DISCOVERY_FINISHED 브로드캐스트를 받는 시점에서 만난 상태인지, 헤어진 상태인지 판별한다.
     * ACTION_DISCOVERY_STARTED 브로드캐스트는 디버깅을 위해 수신한다. (블루투스 탐지 시작이 올바른지 확인하기 위해서)
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
            // discovery 시작됨
            Log.d(TAG, "블루투스 스캔 시작");
        } else if (action.equals(BluetoothDevice.ACTION_FOUND)) {
            // Bluetooth device가 검색 됨
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (bluetoothName.equals(device.getName())) {
                // 검색된 디바이스 이름이 등록된 디바이스 이름이 같으면 isFind를 true로 설정
                isFind = true;
            }
        } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
            // discovery가 완료
            Log.d(TAG, "블루투스 스캔 완료");
            if (isFind) {
                Log.d(TAG, "발견: " + userName);
            } else {
                Log.d(TAG, "미발견: " + userName);
            }
            // 현재 시각과 발견/미발견 여부를 timeStamp에 기록한다.
            timeStamp.record(isFind);
            isFind = false;

            // 현재 기록중이 아니라면
            if(!isRecording) {
                // 만남 여부를 검사하고 맞다면 로그 파일에 기록한다.
                if(timeStamp.isHello()) {
                    recordHello();
                }
            } else {
                // 현재 기록중이라면
                // 헤어짐 여부를 검사하고 맞다면 로그 파일에 기록한다.
                if(timeStamp.isGoodBye()) {
                    recordGoodBye();
                }
            }
        }
    }

    /**
     * 만났을 때 해당 시각을 텍스트 파일에 기록하는 메소드
     */
    private void recordHello() {
        // timeStamp의 첫번째 시각에서 탐지 주기의 반을 뺀 시각을 만난 시각으로 설정한다.
        helloTime = timeStamp.getFirstTime() - scanTimeInterval / 2;

        // 날짜 기록용 객체들
        Date date = new Date(helloTime);

        // 만난 시각을 텍스트 파일에 기록한다.
        textFileManager.save("You encounter " + userName + "\n" + dateFormat.format(date) + " ~ ");
        Log.d(TAG, "You encounter " + userName + "\n" + dateFormat.format(date) + " ~ ");
        isRecording = true;
    }

    /**
     * 헤어졌을 때 해당 시각을 텍스트 파일에 기록하며,
     * 총 같이 있었던 시간을 계산해 덧붙여 기록한다.
     */
    private void recordGoodBye() {
        // timeStamp의 첫번째 시각에서 탐지 주기의 반을 뺀 시각을 헤어진 시각으로 설정한다.
        goodByeTime = timeStamp.getFirstTime() - scanTimeInterval / 2;

        Date date = new Date(goodByeTime);

        textFileManager.save(dateFormat.format(date) + " (" + formatMillis(goodByeTime - helloTime) + ")\n");
        Log.d(TAG, dateFormat.format(date) + " (" + formatMillis(goodByeTime - helloTime) + ")\n");
        isRecording = false;
    }

    /**
     * 두 시각 사이의 간격(시간)을 계산하여 String값으로 반환하는 메소드
     * H:mm:ss.밀리초 형식
     * 출처: http://stackoverflow.com/questions/6710094/how-to-format-an-elapsed-time-interval-in-hhmmss-sss-format-in-java
     */
    private String formatMillis(long val) {
        StringBuilder buf = new StringBuilder(20);
        String sgn = "";

        if (val < 0) {
            sgn = "-";
            val = Math.abs(val);
        }

        append(buf, sgn, 0, (val / 3600000));
        append(buf, ":", 2, ((val % 3600000) / 60000));
        append(buf, ":", 2, ((val % 60000) / 1000));
        append(buf, ".", 3, (val % 1000));
        return buf.toString();
    }

    /**
     * Append a right-aligned and zero-padded numeric value to a `StringBuilder`.
     */
    private void append(StringBuilder tgt, String pfx, int dgt, long val) {
        tgt.append(pfx);
        if (dgt > 1) {
            int pad = (dgt - 1);
            for (long xa = val; xa > 9 && pad > 0; xa /= 10) {
                pad--;
            }
            for (int xa = 0; xa < pad; xa++) {
                tgt.append('0');
            }
        }
        tgt.append(val);
    }
}
