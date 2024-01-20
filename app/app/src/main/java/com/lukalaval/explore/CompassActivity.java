package com.lukalaval.explore;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.widget.Button;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.IOException;

public class CompassActivity extends AppCompatActivity {

    // GLobal variables
    double userLatitude = 0.0;
    double userLongitude = 0.0;
    double userHeading = 0.0;

    String destName = null;
    double destLatitude = 0.0;
    double destLongitude = 0.0;

    final int EARTH_RADIUS = 6371;

    static DatabaseHelper dbHelper;
    SQLiteDatabase database;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compass);

        // parse intent to define destination point (name and coordinates)
        Intent intent = getIntent();
        destName = intent.getStringExtra("address");
        destLatitude = intent.getDoubleExtra("latitude", 0.0);
        destLongitude = intent.getDoubleExtra("longitude", 0.0);

        // display destination name
        TextView destinationTV = findViewById(R.id.destination);
        destinationTV.setText(destName);




        // activate database
        dbHelper = new DatabaseHelper(this);
        try {
            dbHelper.createDataBase();
        } catch (IOException ioe) {
        }
        database = dbHelper.getDataBase();

        // track location and update user interface
        trackPosition();
        trackHeading();
    }

    public void trackPosition() {

        // enable usage of gps
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        LocationListener locationListener = new LocationListener() {

            public void onLocationChanged(Location location) {
                if (location != null) {
                    // save user location + timestamp in the database
                    userLatitude = location.getLatitude();
                    userLongitude = location.getLongitude();
                    Long tsLong = System.currentTimeMillis();
                    database.execSQL("INSERT INTO tracks VALUES ('" + tsLong + "','" + userLatitude + "','" + userLongitude + "');");

                    // update distance and display it
                    double distance = calculateDistance(userLatitude, userLongitude, destLatitude, destLongitude);
                    TextView distanceField = findViewById(R.id.distance);
                    distanceField.setText((int) distance + "m");

                }
            }
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };

        // launch main function and check for permissions
        @SuppressLint("MissingPermission") ActivityResultLauncher<String[]> locationPermissionRequest = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    Boolean fineLocationGranted = null;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        fineLocationGranted = result.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false);
                    }
                    Boolean coarseLocationGranted = null;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        coarseLocationGranted = result.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false);
                    }

                    if (fineLocationGranted != null && fineLocationGranted) {
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, locationListener);
                    } else if (coarseLocationGranted != null && coarseLocationGranted) {
                        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
                    } else {
                        Toast.makeText(this, "Location cannot be obtained due to missing permission.", Toast.LENGTH_LONG).show();
                    }
                }
        );

        String[] PERMISSIONS = {
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        };
        locationPermissionRequest.launch(PERMISSIONS);
    }

    public void trackHeading() {

        // derive heading from magnetic field sensor and accelerometer
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        float[] lastAccelerometer = new float[3];
        float[] lastMagnetometer = new float[3];
        final boolean[] lastAccelerometerSet = {false};
        final boolean[] lastMagnetometerSet = {false};
        float[] rotationMatrix = new float[9];
        float[] orientation = new float[3];

        SensorEventListener sensorListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (event.sensor == magnetometer) {
                    System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.length);
                    lastMagnetometerSet[0] = true;
                } else if (event.sensor == accelerometer) {
                    System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.length);
                    lastAccelerometerSet[0] = true;
                }

                // in this case we habe values for the magnetometer and the accelerometer, we can now computer the heading
                if (lastMagnetometerSet[0] && lastAccelerometerSet[0]) {
                    SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer);
                    SensorManager.getOrientation(rotationMatrix, orientation);

                    // compute user heading from 0 to 360
                    float azimuthInRadians = orientation[0];
                    double azimuthInDegrees = (Math.toDegrees(azimuthInRadians) + 360) % 360;
                    userHeading = azimuthInDegrees;

                    // calculate the angle between the user heading and a line between user location and destination
                    double angle = calculateAngle(userHeading, userLatitude, userLongitude, destLatitude, destLongitude);

                    // update image orientation on UI to point towards the destination
                    ImageView arrowImage = findViewById(R.id.arrow);
                    arrowImage.setRotation((float) angle);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };

        // activate sensor with fast delay (equiv. to function launch)
        sensorManager.registerListener(sensorListener, magnetometer, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
    }

    private double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        // calculate the distance between two coordinates
        // https://www.baeldung.com/java-find-distance-between-points

        if(lat1 != null) {
            Double lat1Rad = Math.toRadians(lat1);
            Double lat2Rad = Math.toRadians(lat2);
            Double lon1Rad = Math.toRadians(lon1);
            Double lon2Rad = Math.toRadians(lon2);

            Double x = (lon2Rad - lon1Rad) * Math.cos((lat1Rad + lat2Rad) / 2);
            Double y = (lat2Rad - lat1Rad);
            Double distance = (Math.sqrt(x * x + y * y) * EARTH_RADIUS) * 1000;

            return distance;
        }

        // safe return of a value
        return 0.0;
    }

    private double calculateAngle(double heading, double lat1, double lon1, double lat2, double lon2) {
        // calculate the angle between a heading (in deg.) and the line between two coordinates
        // !! double check is needed for long distance between points

        double longDiff = lon2-lon1;
        double y = Math.sin(longDiff)*Math.cos(lat2);
        double x = Math.cos(lat1)*Math.sin(lat2)-Math.sin(lat1)*Math.cos(lat2)*Math.cos(longDiff);

        double res = ( Math.toDegrees(Math.atan2(y, x)) + 360 ) % 360 - heading;

        return res;
    }

    public void startAddressActivity(View view) {
        // start address activity without animation
        Intent intent = new Intent(this, AddressActivity.class);
        startActivity(intent);
        overridePendingTransition(0,0);
    }

    public void startMainActivity(View view) {
        // start main activity without animation
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        overridePendingTransition(0,0);
    }

}