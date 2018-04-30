package edu.illinois.cs.cs125.mp7;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Button;
import android.widget.Switch;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AttendanceHax extends AppCompatActivity {
    /** Default logging tag for messages from the main activity. */
    private static final String TAG = "AttendanceHax";

    private static RequestQueue requestQueue;

    private static final int READ_REQUEST_CODE = 42;

    private Bitmap currentBitmap;

    private boolean canWriteToPublicStorage = false;

    /** Constant to request permission to write to the external storage device. */
    private static final int REQUEST_WRITE_STORAGE = 112;

    private static final int IMAGE_CAPTURE_REQUEST_CODE = 1;

    public static String jsonexample = "";

    public static String json2example = "";

    public static String json3example = "";

    public static String[] jsonStringArray = {json2example, json3example};

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        requestQueue = Volley.newRequestQueue(this);

        super.onCreate(savedInstanceState);

        // Load the main layout for our activity
        setContentView(R.layout.activity_attendance_hax);

        /*
         * Set up handlers for each button in our UI. These run when the buttons are clicked.
         */
        /**
         * takes the first picture and sends it to be analyzed.
         */
        final Button getAPI = findViewById(R.id.attendance_hax);
        getAPI.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                Log.d(TAG, "Start logging images");
                Toast.makeText(getApplicationContext(), "Apagando las luces!",
                        Toast.LENGTH_LONG).show();
                 startTakePhoto();
                /**
                 * use this call when you have a picture to retrieve back. Put it here or somewhere else.
                 * startProcessImage();
                 */
            }
        });

        final Switch turnOnVibration = findViewById(R.id.switch2);
        turnOnVibration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                Log.d(TAG, "vibration enabled");
                Vibrator x = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                x.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                Toast.makeText(getApplicationContext(), "Ya estoy lista para ese trago.",
                        Toast.LENGTH_LONG).show();
            }
        });
        // There are a few button that we disable into an image has been loaded
        enableOrDisableButtons(false);

        // We also want to make sure that our progress bar isn't spinning, and style it a bit
        ProgressBar progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.INVISIBLE);
        progressBar.getIndeterminateDrawable()
                .setColorFilter(getResources()
                        .getColor(R.color.colorPrimaryDark), PorterDuff.Mode.SRC_IN);

        /*
         * Here we check for permission to write to external storage and request it if necessary.
         * Normally you would not want to do this on ever start, but we want to be persistent
         * since it makes development a lot easier.
         */
        canWriteToPublicStorage = (ContextCompat.checkSelfPermission(AttendanceHax.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
        Log.d(TAG, "Do we have permission to write to external storage: "
                + canWriteToPublicStorage);
        if (!canWriteToPublicStorage) {
            ActivityCompat.requestPermissions(AttendanceHax.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
        }
    }
    /** Current file that we are using for our image request. */

    private boolean photoRequestActive = false;


    /** Whether a current photo request is being processed. */

    private File currentPhotoFile = null;

    private void startTakePhoto() {
        if (photoRequestActive) {
            Log.w(TAG, "Overlapping photo requests");
            return;
        }
        Toast.makeText(getApplicationContext(), "Photo being taken",
                Toast.LENGTH_LONG).show();
        // Set up an intent to launch the camera app and have it take a photo for us
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        currentPhotoFile = getSaveFilename();
        Toast.makeText(getApplicationContext(), "clear",
                Toast.LENGTH_LONG).show();

        if (takePictureIntent.resolveActivity(getPackageManager()) == null
                || currentPhotoFile == null) {
            // Alert the user if there was a problem taking the photo
            Toast.makeText(getApplicationContext(), "Problem taking photo",
                    Toast.LENGTH_LONG).show();
            Log.w(TAG, "Problem taking photo");
            return;
        }

        // Configure and launch the intent
        Uri photoURI = FileProvider.getUriForFile(this,
                "edu.illinois.cs.cs125.mp7.fileprovider", currentPhotoFile);
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
        photoRequestActive = true;
        startActivityForResult(takePictureIntent, IMAGE_CAPTURE_REQUEST_CODE);


    }
    
    private void startProcessImage() {
        if (currentBitmap == null) {
            Toast.makeText(getApplicationContext(), "No image selected",
                    Toast.LENGTH_LONG).show();
            Log.w(TAG, "No image selected");
            return;
        }


        /*
         * Launch our background task which actually makes the request. It will call
         * finishProcessImage below with the JSON string when it finishes.
         */
        new APIRetrieval.ProcessImageTask(AttendanceHax.this, requestQueue)
                .execute(currentBitmap);
    }
    private void enableOrDisableButtons(final boolean enableOrDisable) {
        final Button attendanceHax = findViewById(R.id.attendance_hax);
        attendanceHax.setClickable(enableOrDisable);
        attendanceHax.setEnabled(enableOrDisable);
    }
    /**
     * Process the result from making the API call.
     *
     * @param jsonResult the result of the API call as a string
     * */
    public void finishProcessImage(final String jsonResult) {

        if (jsonStringArray[0].equals("")) {
            jsonStringArray[0] = jsonResult;
        }
        if (jsonStringArray[1].equals("") && jsonStringArray[0].equals("")) {
            jsonStringArray[0] = jsonResult;
        }
        if (jsonStringArray[1].equals("") && !(jsonStringArray[0].equals(""))) {
            jsonStringArray[1] = jsonResult;
            finalCompareImages(jsonStringArray[0], jsonStringArray[1]);
        }
        if (!(jsonStringArray[0].equals("")) && !(jsonStringArray[1].equals(""))) {
            jsonStringArray[1] = jsonStringArray[0];
            jsonStringArray[0] = jsonResult;
            finalCompareImages(jsonStringArray[0], jsonStringArray[1]);
        }
    }
    public void compareImages(final String json) {
        try {
            JsonParser parser = new JsonParser();
            JsonObject object = parser.parse(json).getAsJsonObject();
            JsonArray tag = object.getAsJsonArray("regions");
            for (JsonElement name : tag) {
                JsonObject yes = name.getAsJsonObject();
                JsonArray secondArray = yes.getAsJsonArray("lines");
                for (JsonElement arrghh : secondArray) {
                    JsonObject ha = arrghh.getAsJsonObject();
                    JsonArray thirdArray = ha.getAsJsonArray("words");
                    for (JsonElement lastOne: thirdArray) {
                        JsonObject no = lastOne.getAsJsonObject();
                        String rick = no.get("text").getAsString();
                        jsonexample += rick;
                    }
                }
            }
        } catch (NullPointerException f) {
        }
        finishProcessImage(jsonexample);
    }

    public boolean finalCompareImages(final String jsonExample, final String json2Example) {
        Toast.makeText(getApplicationContext(), "The images should be compared",
                Toast.LENGTH_LONG).show();
        Log.w(TAG, "Have the images been compared?");
        if (jsonExample.equals(json2Example)) {
            Log.w(TAG, "The images are the same");
            return true;
            /**
             * from here you'll want to call the vibration
             */
        }
        else {
            Log.w(TAG, "The images are different");
            return false;
        }
    }

    void updateCurrentBitmap(final Bitmap setCurrentBitmap, final boolean resetInfo) {
        currentBitmap = setCurrentBitmap;
        ImageView photoView = findViewById(R.id.imageView2);
        photoView.setImageBitmap(currentBitmap);
        enableOrDisableButtons(true);
        if (resetInfo) {
            TextView textView = findViewById(R.id.newText);
            textView.setText("");
        }
    }
    File getSaveFilename() {
        String imageFileName = "MP7_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date());
        File storageDir;
        if (canWriteToPublicStorage) {
            storageDir = Environment
                    .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        } else {
            storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        }
        try {
            return File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (IOException e) {
            Log.w(TAG, "Problem saving file: " + e);
            return null;
        }
    }
    /**
     * Called when an intent that we requested has finished.
     *
     * In our case, we either asked the file browser to open a file, or the camera to take a
     * photo. We respond appropriately below.
     *
     * @param requestCode the code that we used to make the request
     * @param resultCode a code indicating what happened: success or failure
     * @param resultData any data returned by the activity
     */
    @Override
    public void onActivityResult(final int requestCode, final int resultCode,
                                 final Intent resultData) {

        // If something went wrong we simply log a warning and return
        if (resultCode != Activity.RESULT_OK) {
            Log.w(TAG, "onActivityResult with code " + requestCode + " failed");
            if (requestCode == IMAGE_CAPTURE_REQUEST_CODE) {
                photoRequestActive = false;
            }
            return;
        }

        // Otherwise we get a link to the photo either from the file browser or the camera,
        Uri currentPhotoURI;
        if (requestCode == READ_REQUEST_CODE) {
            currentPhotoURI = resultData.getData();
        } else if (requestCode == IMAGE_CAPTURE_REQUEST_CODE) {
            currentPhotoURI = Uri.fromFile(currentPhotoFile);
            photoRequestActive = false;
        } else {
            Log.w(TAG, "Unhandled activityResult with code " + requestCode);
            return;
        }

        // Now load the photo into the view
        Log.d(TAG, "Photo selection produced URI " + currentPhotoURI);
        loadPhoto(currentPhotoURI);
    }
    private void loadPhoto(final Uri currentPhotoURI) {

        if (currentPhotoURI == null) {
            Toast.makeText(getApplicationContext(), "No image selected",
                    Toast.LENGTH_LONG).show();
            Log.w(TAG, "No image selected");
            return;
        }
        String uriScheme = currentPhotoURI.getScheme();

        byte[] imageData;
        try {
            switch (uriScheme) {
                case "file":
                    imageData = FileUtils.readFileToByteArray(new File(currentPhotoURI.getPath()));
                    break;
                case "content":
                    InputStream inputStream = getContentResolver().openInputStream(currentPhotoURI);
                    assert inputStream != null;
                    imageData = IOUtils.toByteArray(inputStream);
                    inputStream.close();
                    break;
                default:
                    Toast.makeText(getApplicationContext(), "Unknown scheme " + uriScheme,
                            Toast.LENGTH_LONG).show();
                    return;
            }
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "Error processing file",
                    Toast.LENGTH_LONG).show();
            Log.w(TAG, "Error processing file: " + e);
            return;
        }

        /*
         * Resize the image appropriately for the display.
         */
        final ImageView photoView = findViewById(R.id.imageView2);
        int targetWidth = photoView.getWidth();
        int targetHeight = photoView.getHeight();

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(imageData, 0, imageData.length, decodeOptions);

        int actualWidth = decodeOptions.outWidth;
        int actualHeight = decodeOptions.outHeight;
        int scaleFactor = Math.min(actualWidth / targetWidth, actualHeight / targetHeight);

        BitmapFactory.Options modifyOptions = new BitmapFactory.Options();
        modifyOptions.inJustDecodeBounds = false;
        modifyOptions.inSampleSize = scaleFactor;
        modifyOptions.inPurgeable = true;

        // Actually draw the image
        updateCurrentBitmap(BitmapFactory.decodeByteArray(imageData,
                0, imageData.length, modifyOptions), true);
    }
}

