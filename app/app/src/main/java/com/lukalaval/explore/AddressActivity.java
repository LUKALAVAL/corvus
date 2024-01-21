package com.lukalaval.explore;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.Spanned;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.text.Html;
import java.io.IOException;
import java.util.List;

import android.database.Cursor;
import android.widget.ImageView;
import android.widget.ListView;

public class AddressActivity extends AppCompatActivity {

    // Global variables
    DatabaseHelper dbHelper;
    SQLiteDatabase database;
    Cursor dbCursor;
    ListView listView;
    ImageView backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_address);

        backButton = findViewById(R.id.back);
        listView = findViewById(R.id.listAddress);

        // activate database
        dbHelper = new DatabaseHelper(this);
        try {
            dbHelper.createDataBase();
        } catch (IOException ioe) {
        }
        database = dbHelper.getDataBase();

        // update UI with database content
        refreshList();

        // function assigned to each item of the list (triggered when item is clicked)
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                // read the item name and find its information in the database
                String address = parent.getItemAtPosition(position).toString();
                dbCursor = database.rawQuery("SELECT latitude, longitude FROM addresses WHERE address = '"+ address +"';",null );
                dbCursor.moveToFirst();
                double latitude = dbCursor.getDouble(0);
                double longitude = dbCursor.getDouble(1);

                // update the database to put the selected item at the top of the list next time it is updated
                updateDatabase(address, latitude, longitude);

                // communicate destination information to the compass activity
                startCompassActivity(address, latitude, longitude);
            }
        });
    }

    public void updateDatabase(String addressName, double addressLatitude, double addressLongitude) {
        // puts address in the top of the database to make sure it is on the top of the list next time the UI is updated
        database.execSQL("DELETE FROM addresses WHERE address = '" + addressName + "';");
        database.execSQL("INSERT INTO addresses VALUES ('" + addressName + "','" + addressLatitude + "','" + addressLongitude + "');");
    }






    public void refreshList() {
        // update the list view in the user interface
        dbCursor = database.rawQuery("SELECT * FROM addresses LIMIT 30;",null );
        ArrayAdapter<CharSequence> adapter = createAdapterHtml(dbCursor);
        listView.setAdapter(adapter);
    }



    private ArrayAdapter<CharSequence> createAdapterHtml(Cursor cursor) {
        // create and adapter to update each item of the list view
        int length = cursor.getCount();
        cursor.moveToFirst();

        Spanned[] html_array = new Spanned[length+2];

        int index_address = cursor.getColumnIndex("address");

        // make items appear in the right order
        for (int i = length-1; i >= 0; i--) {

            html_array[i] = Html.fromHtml(cursor.getString(index_address));
            cursor.moveToNext();
        }

        // adding space at the bottom so that the return arrow doesn't hide any item
        html_array[length] = Html.fromHtml("");
        html_array[length+1] = Html.fromHtml("");


        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this,
                R.layout.list_item, html_array);

        return adapter;
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



    public void onClickStartPreviousActivity(View view) {
        // returns to previous activity without animation
        finish();
        overridePendingTransition(0,0);
    }

    public void onClickAddToDatabase(View view) throws IOException {

        // read the string that the user wrote inside the search bar
        EditText editText = findViewById(R.id.searchText);
        String strAddress = String.valueOf(editText.getText());

        if(strAddress.length() > 0) {
            // find address from coordinates
            Geocoder coder = new Geocoder(this);
            List<Address> address = coder.getFromLocationName(strAddress, 1);

            // get the address official name and coordinates
            String addressName = null;
            double addressLatitude = 0.0;
            double addressLongitude = 0.0;
            if(!address.isEmpty()) {
                // if the address actually exists
                addressName = address.get(0).getAddressLine(0);
                addressLatitude = address.get(0).getLatitude();
                addressLongitude = address.get(0).getLongitude();

                // hide keyboard
                InputMethodManager inm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                inm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),0);

                // empty search bar
                editText.setText("");
            }
            else {
                // the address was not found
            }


            // insert the new values in the database
            if(addressName != null) {
                updateDatabase(addressName, addressLatitude, addressLongitude);
            }
        }

        // update list view with the updated database
        refreshList();
    }

}