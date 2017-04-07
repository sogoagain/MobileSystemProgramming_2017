package io.github.sogoesagain.proximityalarm;

import java.io.Serializable;

/**
 * Created by sogoesagain on 2017. 4. 1..
 * UserLocation
 * - 장소에 대한 정보를 갖고있는 VO(Value Object) 클래스
 * - AddActivity에서 MainActivity로 인텐트를 통해 전달되므로 Serializable 인터페이스를 상속. (직렬화)
 * - Serializable은 함수를 제외한 변수를 직렬화 시켜 intent로 객체 전달을 가능하게 해준다.
 */
@SuppressWarnings("serial")
public class UserLocation implements Serializable {

    // private static final long serialVersionUID = 1L;
    private String locationName;    // 장소명
    private double latitude;        // 위도
    private double longitude;       // 경도
    private float radius;           // 반경

    // 생성자
    public UserLocation(String locationName, double latitude, double longitude, float radius) {
        this.locationName = locationName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    public String getLocationName() {
        return locationName;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public float getRadius() {
        return radius;
    }
}
