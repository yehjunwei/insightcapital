package com.insight.capital;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;

import io.fabric.sdk.android.Fabric;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends Activity
        implements EasyPermissions.PermissionCallbacks {
    public SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
    public final static String TAG = "Insight";

    private GoogleAccountCredential mCredential;
    private Button mCallApiButton;
    private ProgressDialog mProgress;
    private ImageView mTodayFace;
    private TextView mTodayBalance;
    private TextView mTodayBalanceTitle;
    private TextView mMonthBalance;
    private TextView mMonthBalanceRate;
    private TextView mQuarterBalance;
    private TextView mQuarterBalanceRate;
    private TextView mCurrentNetValue;
    private TextView mOutputText;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = {SheetsScopes.SPREADSHEETS_READONLY};
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    /**
     * Create the main activity.
     *
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics());
        setContentView(R.layout.activity_main);

        mCallApiButton = (Button) findViewById(R.id.btn_get_data);
        mCallApiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCallApiButton.setEnabled(false);
                mOutputText.setText("");
                getResultsFromApi();
                mCallApiButton.setEnabled(true);
            }
        });

        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Calling Google Sheets API ...");

        mTodayFace = (ImageView) findViewById(R.id.iv_today_face);
        mTodayBalanceTitle = (TextView) findViewById(R.id.tv_today_balance_title);
        mTodayBalance = (TextView) findViewById(R.id.tv_today_balance);
        mMonthBalance = (TextView) findViewById(R.id.tv_month_balance);
        mQuarterBalance = (TextView) findViewById(R.id.tv_season_balance);
        mMonthBalanceRate = (TextView) findViewById(R.id.tv_month_balance_rate);
        mQuarterBalanceRate = (TextView) findViewById(R.id.tv_season_balance_rate);
        mCurrentNetValue = (TextView) findViewById(R.id.tv_current_net_value);
        mOutputText = (TextView) findViewById(R.id.tv_output_text);

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();

        getResultsFromApi();

    }

    public void updateUIFromResult(List<String> result) {
        String dateStr = result.get(0);
        try {
            Date date = df.parse(dateStr);
            String formattedDate = new SimpleDateFormat("MMM dd E").format(date);
            mTodayBalanceTitle.setText(formattedDate);
        } catch (ParseException e) {
            e.printStackTrace();
        }


        DecimalFormat formatter = new DecimalFormat("#,###");

        int todayBalance = Integer.valueOf(result.get(1));
        if(todayBalance >= 0) {
            mTodayBalance.setTextColor(getResources().getColor(R.color.red));
            mTodayBalance.setText("$"+formatter.format(todayBalance));
            mTodayFace.setImageResource(R.drawable.smile_face);
        } else {
            mTodayBalance.setTextColor(getResources().getColor(R.color.green));
            mTodayBalance.setText("-$"+formatter.format(Math.abs(todayBalance)));
            mTodayFace.setImageResource(R.drawable.crying_face);
        }

        int monthBalance = Integer.valueOf(result.get(2));
        if(monthBalance >= 0) {
            mMonthBalance.setTextColor(getResources().getColor(R.color.red));
            mMonthBalance.setText("$"+formatter.format(monthBalance));
        } else {
            mMonthBalance.setTextColor(getResources().getColor(R.color.green));
            mMonthBalance.setText("-$"+formatter.format(Math.abs(monthBalance)));
        }

        float monthBalanceRate = Float.valueOf(result.get(3));
        if(monthBalanceRate > 0) {
            mMonthBalanceRate.setTextColor(getResources().getColor(R.color.red));
        } else {
            mMonthBalanceRate.setTextColor(getResources().getColor(R.color.green));
        }
        mMonthBalanceRate.setText(String.format("%.2f%%", monthBalanceRate));

        int quarterBalance = Integer.valueOf(result.get(4));
        if(quarterBalance >= 0) {
            mQuarterBalance.setTextColor(getResources().getColor(R.color.red));
            mQuarterBalance.setText("$"+formatter.format(quarterBalance));
        } else {
            mQuarterBalance.setTextColor(getResources().getColor(R.color.green));
            mQuarterBalance.setText("-$"+formatter.format(Math.abs(quarterBalance)));
        }

        float quarterBalanceRate = Float.valueOf(result.get(5));
        if(quarterBalanceRate > 0) {
            mQuarterBalanceRate.setTextColor(getResources().getColor(R.color.red));
        } else {
            mQuarterBalanceRate.setTextColor(getResources().getColor(R.color.green));
        }
        mQuarterBalanceRate.setText(String.format("%.2f%%", quarterBalanceRate));

        float currentNetValue = Float.valueOf(result.get(6));
        if(currentNetValue >= 100) {
            mCurrentNetValue.setTextColor(getResources().getColor(R.color.red));
        } else {
            mCurrentNetValue.setTextColor(getResources().getColor(R.color.green));
        }
        mCurrentNetValue.setText(String.format("%.2f", currentNetValue));
    }

    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (!isDeviceOnline()) {
            mOutputText.setText("No network connection available.");
        } else {
            new MakeRequestTask(mCredential).execute();
        }
    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     *
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode  code indicating the result of the incoming
     *                    activity result.
     * @param data        Intent (containing result data) returned by incoming
     *                    activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    mOutputText.setText(
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.");
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     *
     * @param requestCode  The request code passed in
     *                     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Checks whether the device currently has a network connection.
     *
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     *
     * @return true if Google Play Services is available and up to
     * date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     *
     * @param connectionStatusCode code describing the presence (or lack of)
     *                             Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://www.yehjunwei.com"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.insight.capital/http/www.yehjunwei.com")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://www.yehjunwei.com"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.insight.capital/http/www.yehjunwei.com")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }

    /**
     * An asynchronous task that handles the Google Sheets API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, List<String>> {
        private Sheets mService = null;
        private Exception mLastError = null;

        public MakeRequestTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new Sheets.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName(getResources().getString(R.string.app_name))
                    .build();
        }

        /**
         * Background task to call Google Sheets API.
         *
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<String> doInBackground(Void... params) {
            try {
                return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * Fetch a list of names and majors of students in a sample spreadsheet:
         * https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/edit
         * https://docs.google.com/spreadsheets/d/1MtrjWBGEv_oeBWU8mf3rShBSANmQyTuCi0-F3yH8Spc/edit#gid=0
         *
         * @return List of names and majors
         * @throws IOException
         */

        private List<String> getDataFromApi() throws IOException {
            // String spreadsheetId = "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms";
            NumberFormat usNumber = NumberFormat.getNumberInstance(Locale.US);
            String spreadsheetId = "1MtrjWBGEv_oeBWU8mf3rShBSANmQyTuCi0-F3yH8Spc";
            String range = "A185:J";
            List<String> results = new ArrayList<String>();
            ValueRange response = this.mService.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .execute();
            List<List<Object>> values = response.getValues();
            int monthlyBalance = 0;
            int quarterBalance = 0;
            int todaysBalance = 0;
            float dailyNetValue = 0.0f;
            float monthStartNetValue = 0.0f;
            float quarterStartNetValue = 0.0f;
            float todaysNetValue = 0.0f;

            String todayStr = "";
            if (values != null) {
                for (int i = 0 ; i < values.size() ; ++i) {
                    List row = values.get(i);
                    String dateString = (String) row.get(0);
                    int dailyBalance = 0;
                    if(row.size() < 10)
                        continue;
                    try {
                        Date date = df.parse(dateString);
                        String dailyBalanceStr = (String) row.get(4);
                        String dailyNetValueStr = (String) row.get(9);

                        if (!TextUtils.isEmpty(dailyBalanceStr)) {
                            dailyBalance = usNumber.parse(dailyBalanceStr).intValue();
                            dailyNetValue = Float.valueOf(dailyNetValueStr);

                            if (isThisRowSameQuarter(date)) {
                                quarterBalance += dailyBalance;
                                if (quarterStartNetValue == 0.0f) {
                                    quarterStartNetValue = monthStartNetValue;
                                }
                            }
                            if (isThisRowSameMonth(date)) {
                                monthlyBalance += dailyBalance;

                                // the last assign is today's value
                                todayStr = dateString;
                                todaysBalance = dailyBalance;
                                todaysNetValue = dailyNetValue;
                            }
                        } else {
                            continue;
                        }
                    } catch (ParseException e) {
                        monthStartNetValue = Float.valueOf((String) row.get(9));
                        Log.d(TAG, "month start net value = "+monthStartNetValue);
                        Log.d(TAG, "transaction log: " + dateString);
                    }
                }
                results.add(todayStr);
                results.add(String.valueOf(todaysBalance));
                results.add(String.valueOf(monthlyBalance));
                results.add(String.valueOf(todaysNetValue-monthStartNetValue));
                results.add(String.valueOf(quarterBalance));
                results.add(String.valueOf(todaysNetValue-quarterStartNetValue));
                results.add(String.valueOf(todaysNetValue));
            }
            return results;
        }

        private boolean isThisRowSameMonth(Date date) {
            Date now = new Date();
            if(now.getYear() == date.getYear() && now.getMonth() == date.getMonth())
                return true;
            return false;
        }

        private boolean isThisRowSameQuarter(Date date) {
            Date now = new Date();
            if(now.getYear() == date.getYear()) {
                int dateQuarter = (date.getMonth() / 3) + 1;
                int nowQuarter = (now.getMonth() / 3) + 1;
                if(dateQuarter == nowQuarter)
                    return true;
            }
            return false;
        }

        @Override
        protected void onPreExecute() {
            mOutputText.setText("");
            Log.d(TAG, "progress.show()");
            mProgress.show();
        }

        @Override
        protected void onPostExecute(List<String> output) {
            Log.d(TAG, "progress.hide()");
            mProgress.hide();
            if (output == null || output.size() == 0) {
                mOutputText.setText("No results returned.");
            } else {
                updateUIFromResult(output);
                // mOutputText.setText(TextUtils.join("\n", output));
            }
        }

        @Override
        protected void onCancelled() {
            mProgress.hide();
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    mOutputText.setText("The following error occurred:\n"
                            + mLastError.getMessage());
                }
            } else {
                mOutputText.setText("Request cancelled.");
            }
        }
    }
}