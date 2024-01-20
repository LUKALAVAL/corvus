package com.lukalaval.explore;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.maps.android.data.geojson.GeoJsonLayer;
import com.google.maps.android.data.geojson.GeoJsonLineStringStyle;
import com.google.maps.android.heatmaps.Gradient;
import com.google.maps.android.heatmaps.HeatmapTileProvider;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    // Global variable
    DatabaseHelper dbHelper;
    SQLiteDatabase database;
    Cursor dbCursor;
    Marker marker;
    private GoogleMap mMap;
    ActivityResultLauncher<String[]> locationPermissionRequest;

    @SuppressLint("MissingPermission")
    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the Layout
        setContentView(R.layout.activity_main);

        // Create map fragment and ask for permissions
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        locationPermissionRequest = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    Boolean fineLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                    if ((fineLocationGranted != null && fineLocationGranted) || (coarseLocationGranted != null && coarseLocationGranted)) {
                        mMap.setMyLocationEnabled(true);
                        mMap.getUiSettings().setMyLocationButtonEnabled(true);
                    } else {
                        Toast.makeText(this,
                                "Location cannot be obtained due to missing permission.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    public void onMapReady(GoogleMap map) {
        // Set Map Style form JSON file
        mMap = map;
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style));

        // Check if the database is already existing in the Android file system,
        // If not, the database is copied to /data/user/0/<your.package.name>/databases if necessary
        dbHelper = new DatabaseHelper(this);
        try {
            dbHelper.createDataBase();
        } catch (IOException ioe) {
        }

        // To open the database. The access to the database is stored in the global variable database,
        // which can be reused within the MainActivity (i.e. the database does not need to be opened again)
        database = dbHelper.getDataBase();

        // Cursor: an object that moves through queried database records
        // With a SELECT statement, all data from the table (university_table) are queried and stored in a Cursor.
        dbCursor = database.rawQuery("SELECT * FROM tracks;", null);

        // The cursor is moved to its first row
        dbCursor.moveToFirst();

        // Create LatLng Builder
        LatLngBounds.Builder builder = LatLngBounds.builder();

        List<LatLng> geoPoints = new ArrayList<>();

        for (int i = 0; i < dbCursor.getCount(); i++) {

            // Create LatLng variable from the coordinates stored in database
            LatLng geoPoint = new LatLng(dbCursor.getDouble(1), dbCursor.getDouble(2));
            builder.include(geoPoint);
            geoPoints.add(geoPoint);

            // Move Cursor to the next row
            dbCursor.moveToNext();
        }



        if(dbCursor.getCount() <= 0) {
            // new user -> display welcome page
            findViewById(R.id.welcome).setVisibility(View.VISIBLE);
        }
        else {
            // define heatmap style
            int[] colors = {
                    Color.rgb(186, 28, 243),
                    Color.rgb(245, 221, 226)
            };
            float[] startPoints = {
                    0.2f, 1f
            };
            Gradient gradient = new Gradient(colors, startPoints);

            // create and display heatmap
            HeatmapTileProvider provider = new HeatmapTileProvider.Builder()
                    .data(geoPoints)
                    .gradient(gradient)
                    .opacity(0.7)
                    .radius(30)
                    .build();
            mMap.addTileOverlay(new TileOverlayOptions().tileProvider(provider));

            try {
                // see overall view of explored area
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 200));

            }
            catch(Exception e) {
                // if cannot compute, just center on the explored area
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(builder.build().getCenter(), 10));

            }
        }

        // ask for permissions
        String[] PERMISSIONS = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        };
        locationPermissionRequest.launch(PERMISSIONS);


        // function triggered when user clicks on the map
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng newPos) {
                // get the new marker
                MarkerOptions clickMarker = new MarkerOptions().position(newPos);

                // clean user interface
                resetUI(null);

                // display marker
                marker = mMap.addMarker(clickMarker);

                // animation to center map on the clicked (marker) location
                mMap.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));

                // display the footer element
                RelativeLayout startFooter = findViewById(R.id.startFooter);
                startFooter.setVisibility(View.VISIBLE);

            }
        });
    }

    public void resetUI(View view) {
        // remove the marker
        if (marker != null) {
            marker.remove();
        }

        // hide the footer
        RelativeLayout startFooter = findViewById(R.id.startFooter);
        startFooter.setVisibility(View.GONE);

        // center the map on user location with animation
        Location location = mMap.getMyLocation();
        LatLng myLocation = null;
        if (location != null) {
            myLocation = new LatLng(location.getLatitude(),
                    location.getLongitude());
        }
        mMap.animateCamera(CameraUpdateFactory.newLatLng(myLocation));
    }

    public void onClickStartAddressActivity(View view) {
        // start address activity without animation
        Intent intent = new Intent(this, AddressActivity.class);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }



    public void updateDatabase(String addressName, double addressLatitude, double addressLongitude) {
        // puts address in the top of the database to make sure it is on the top of the list next time the UI is updated
        database.execSQL("DELETE FROM addresses WHERE address = '" + addressName + "';");
        database.execSQL("INSERT INTO addresses VALUES ('" + addressName + "','" + addressLatitude + "','" + addressLongitude + "');");
    }



    public void closeWelcome(View view) {
        // hide the welcome page
        findViewById(R.id.welcome).setVisibility(View.GONE);
    }


    public void onClickStartCompassActivity(View view) throws IOException {
        // start compass activity without animation and communicate destination (marker)

        Context context = this;

        // get the marker coordinates
        LatLng markerLocation = marker.getPosition();

        // get the marker address from its coordinates
        Geocoder coder = new Geocoder(this);
        List<Address> address = coder.getFromLocation(markerLocation.latitude, markerLocation.longitude, 1);

        // update the database (with destination name and coordinates) only if an address was found
        String markerName = "Marker";
        if(!address.isEmpty()) {
            markerName = address.get(0).getAddressLine(0);

            updateDatabase(markerName, markerLocation.latitude, markerLocation.longitude);
        }
        String addressName = markerName;

        // get the user location
        Location location = mMap.getMyLocation();
        LatLng userLocation = null;
        if (location != null) {
            userLocation = new LatLng(location.getLatitude(),
                    location.getLongitude());
        }

        // center the map on user location with animation and start compass activity when animation is done
        // the activity is launched in any case because we have the coordinates
        if(userLocation != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLng(userLocation), new GoogleMap.CancelableCallback() {
                @Override
                public void onCancel() {
                    // if animation is canceled, start compass activity
                    startCompassActivity(addressName, markerLocation.latitude, markerLocation.longitude);
                }

                @Override
                public void onFinish() {
                    // wait until the animation is finished to start compass acitivity
                    startCompassActivity(addressName, markerLocation.latitude, markerLocation.longitude);
                }
            });
        }
        else {
            // in any case start compass activity
            startCompassActivity(addressName, markerLocation.latitude, markerLocation.longitude);
        }





    }

    public void startCompassActivity(String addressName, double addressLatitude, double addressLongitude) {
        // start compass activity without animation and communicate destination information
        Intent intent = new Intent(this, CompassActivity.class);
        intent.putExtra("address", addressName);
        intent.putExtra("latitude", addressLatitude);
        intent.putExtra("longitude", addressLongitude);
        startActivity(intent);
        overridePendingTransition(0,0);
    }

}




