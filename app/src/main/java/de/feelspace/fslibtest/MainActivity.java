package de.feelspace.fslibtest;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.feelspace.fslib.BeltBatteryStatus;
import de.feelspace.fslib.BeltButtonPressEvent;
import de.feelspace.fslib.BeltCommandInterface;
import de.feelspace.fslib.BeltCommandListener;
import de.feelspace.fslib.BeltCommunicationController;
import de.feelspace.fslib.BeltCommunicationListener;
import de.feelspace.fslib.BeltConnectionInterface;
import de.feelspace.fslib.BeltConnectionController;
import de.feelspace.fslib.BeltConnectionListener;
import de.feelspace.fslib.BeltConnectionState;
import de.feelspace.fslib.BeltMode;
import de.feelspace.fslib.BeltOrientation;
import de.feelspace.fslib.BeltParameter;
import de.feelspace.fslib.BeltVibrationSignal;
import de.feelspace.fslib.NavigationController;
import de.feelspace.fslib.NavigationEventListener;
import de.feelspace.fslib.NavigationState;
import de.feelspace.fslib.OrientationType;
import de.feelspace.fslib.PowerStatus;
import de.feelspace.fslib.ResetProgressOption;

import android.Manifest;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import java.util.ArrayList;
import java.util.Random;


public class MainActivity extends BluetoothCheckActivity implements BluetoothCheckCallback,

        BeltConnectionListener, BeltCommandListener, BeltCommunicationListener {

    // Debug
    @SuppressWarnings("unused")
    private static final String DEBUG_TAG = "FeelSpace-Debug";
    @SuppressWarnings("unused")
    private static final boolean DEBUG = true;

    // Application controller
    private AppController appController;

    // UI components
    private Button connectButton;
    private Button disconnectButton;
    private TextView connectionStateTextView;
    private TextView beltHeadingTextView;
    private TextView boxOrientationTextView;
    private TextView sensorStatusTextView;

    private TextView modeView;

    // UI update parameters
    private long lastOrientationUpdateTimeMillis;
    private static final long MIN_PERIOD_ORIENTATION_UPDATE_MILLIS = 250;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;

    private Handler handler = new Handler();

    // MARK: Activity methods overriding

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Application controller
        appController = AppController.getInstance();
        appController.init(getApplicationContext());

        // Navigation controller
        appController.getBeltController().addCommandListener(this);
        appController.getBeltConnection().addConnectionListener(this);

        // Connection state
        connectionStateTextView = findViewById(R.id.activity_main_connection_state_text_view);

        // Connect button
        connectButton = findViewById(R.id.activity_main_connect_button);
        connectButton.setOnClickListener(view -> {
            activateBluetooth(this);
        });

        // Disconnect button
        disconnectButton = findViewById(R.id.activity_main_disconnect_button);
        disconnectButton.setOnClickListener(view -> {
            BeltConnectionInterface beltConnection = appController.getBeltConnection();
            if (beltConnection != null) {
                beltConnection.disconnect();
            }
        });

        // Calibration
        beltHeadingTextView = findViewById(R.id.activity_main_belt_heading_text_view);
        boxOrientationTextView = findViewById(R.id.activity_main_box_orientation_text_view);
        sensorStatusTextView = findViewById(R.id.activity_main_sensor_status_text_view);

        modeView = findViewById(R.id.modeView);

        //Training A - Min zu Max
        Button btnVibrate = findViewById(R.id.btnVibrate);
        btnVibrate.setOnClickListener(v -> vibrateLeftToRight());

        //Training B - Max zu Min
        Button btnVibrateB = findViewById(R.id.btnVibrateB);
        btnVibrateB.setOnClickListener(v -> vibrateRightToLeft());

        //Reaktionszeitmodus
        Button reactionTestButton = findViewById(R.id.button_start_reaction_mode);
        reactionTestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ReactionTimeActivity.class);
                startActivity(intent);
            }
        });

        // Update UI
        updateUI();
    }

    // MARK: Private methods

    private void showToast(final String message) {
        runOnUiThread(() -> Toast.makeText(
                MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    private void updateUI() {
        updateConnectionLabel();
        updateConnectionButtons();
        updateOrientationTextView();
        updateSensorStatusTextView();
        updateModusView();
    }

    private void updateConnectionLabel() {
        runOnUiThread(() -> {
            BeltConnectionState state = BeltConnectionState.STATE_DISCONNECTED;
            BeltConnectionInterface beltConnection = appController.getBeltConnection();
            if (beltConnection != null) {
                state = beltConnection.getState();
            }
            switch (state) {
                case STATE_DISCONNECTED:
                    connectionStateTextView.setText(R.string.disconnected);
                    break;
                case STATE_SCANNING:
                    connectionStateTextView.setText(R.string.scanning);
                    break;
                case STATE_PAIRING:
                    connectionStateTextView.setText(R.string.pairing);
                    break;
                case STATE_CONNECTING:
                    connectionStateTextView.setText(R.string.connecting);
                    break;
                case STATE_RECONNECTING:
                    connectionStateTextView.setText(R.string.reconnecting);
                    break;
                case STATE_DISCOVERING_SERVICES:
                    connectionStateTextView.setText(R.string.discovering_services);
                    break;
                case STATE_HANDSHAKE:
                    connectionStateTextView.setText(R.string.handshake);
                    break;
                case STATE_CONNECTED:
                    connectionStateTextView.setText(R.string.connected);
                    break;
            }
            /*
            NavigationController navController = appController.getNavigationController();
            if (navController != null) {
                state = navController.getConnectionState();

            }
            switch (state) {
                case STATE_DISCONNECTED:
                    connectionStateTextView.setText(R.string.disconnected);
                    break;
                case STATE_SCANNING:
                    connectionStateTextView.setText(R.string.scanning);
                    break;
                case STATE_PAIRING:
                    connectionStateTextView.setText(R.string.pairing);
                    break;
                case STATE_CONNECTING:
                    connectionStateTextView.setText(R.string.connecting);
                    break;
                case STATE_RECONNECTING:
                    connectionStateTextView.setText(R.string.reconnecting);
                    break;
                case STATE_DISCOVERING_SERVICES:
                    connectionStateTextView.setText(R.string.discovering_services);
                    break;
                case STATE_HANDSHAKE:
                    connectionStateTextView.setText(R.string.handshake);
                    break;
                case STATE_CONNECTED:
                    connectionStateTextView.setText(R.string.connected);
                    break;
            } */
        });
    }

    private void updateConnectionButtons() {
        runOnUiThread(() -> {
            BeltConnectionState state = BeltConnectionState.STATE_DISCONNECTED;
            BeltConnectionInterface beltConnection = appController.getBeltConnection();
            if (beltConnection != null) {
                state = beltConnection.getState();
            }
            switch (state) {
                case STATE_DISCONNECTED:
                    connectButton.setEnabled(true);
                    disconnectButton.setEnabled(false);
                    break;
                case STATE_SCANNING:
                case STATE_CONNECTING:
                case STATE_RECONNECTING:
                case STATE_DISCOVERING_SERVICES:
                case STATE_HANDSHAKE:
                case STATE_CONNECTED:
                    connectButton.setEnabled(false);
                    disconnectButton.setEnabled(true);
                    break;
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void updateOrientationTextView() {
        runOnUiThread(() -> {
            BeltCommandInterface beltController = appController.getBeltController();
            BeltOrientation orientation = beltController.getOrientation();
            Integer h = (orientation==null)?null:orientation.getControlBoxHeading();
            Integer p = (orientation==null)?null:orientation.getControlBoxPitch();
            Integer r = (orientation==null)?null:orientation.getControlBoxRoll();
            if (orientation == null) {
                beltHeadingTextView.setText("Belt orientation: -");
                beltHeadingTextView.setBackgroundResource(R.color.white);
            } else {
                beltHeadingTextView.setText(
                        String.format(Locale.ENGLISH, "Belt orientation: %+03d ± %+02d",
                                orientation.getBeltHeading(),
                                ((orientation.getAccuracy() == null) ? 0 : orientation.getAccuracy())));
                if (orientation.isOrientationAccurate() == null) {
                    beltHeadingTextView.setBackgroundResource(R.color.white);
                } else if (orientation.isOrientationAccurate()) {
                    beltHeadingTextView.setBackgroundResource(R.color.bg_accurate);
                } else {
                    beltHeadingTextView.setBackgroundResource(R.color.bg_inaccurate);
                }
            }
            if (h == null || p == null || r == null) {
                boxOrientationTextView.setText("H.: -, P: -, R: -");
            } else {
                boxOrientationTextView.setText(
                        String.format(Locale.ENGLISH, "H.: %+03d, P: %+03d, R: %+03d", h, p, r));
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void updateSensorStatusTextView() {
        runOnUiThread(() -> {
            BeltCommandInterface beltController = appController.getBeltController();
            BeltOrientation orientation = beltController.getOrientation();
            if (orientation == null || orientation.getMagnetometerStatus() == null) {
                sensorStatusTextView.setText("Mag. status: -");
            } else {
                sensorStatusTextView.setText(
                        String.format(Locale.ENGLISH, "Mag. Status: %d",
                                orientation.getMagnetometerStatus()));
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void updateModusView() {
        runOnUiThread(() -> {
            BeltCommandInterface beltController = appController.getBeltController();
            BeltMode mode = beltController.getMode();

            if (mode == null) {
                modeView.setText("Mode: ");
            } else {
                modeView.setText(
                        String.format(Locale.ENGLISH, "Mode: %s",
                                mode));
            }
        });
    }

    // MARK: Implementation of belt listeners

    @Override
    public void onBeltModeChanged(BeltMode mode) {
        updateModusView();
    }

    @Override
    public void onBeltButtonPressed(BeltButtonPressEvent beltButtonPressEvent) {

    }

    @Override
    public void onBeltDefaultVibrationIntensityChanged(int intensity) {

    }

    @Override
    public void onBeltBatteryStatusUpdated(BeltBatteryStatus status) {

    }

    @Override
    public void onBeltOrientationUpdated(BeltOrientation orientation) {
        long timeMillis = (System.nanoTime()/1000000);
        if ((timeMillis-lastOrientationUpdateTimeMillis) > MIN_PERIOD_ORIENTATION_UPDATE_MILLIS) {
            updateOrientationTextView();
            updateSensorStatusTextView();
            lastOrientationUpdateTimeMillis = timeMillis;
        }
    }

    @Override
    public void onBeltCompassAccuracySignalStateNotified(boolean signalEnabled) {

    }

    @Override
    public void onScanFailed() {
        showToast("Belt connection fails!");
    }

    @Override
    public void onNoBeltFound() {
        showToast("No belt found!");
    }

    @Override
    public void onBeltFound(BluetoothDevice belt) {

    }

    @Override
    public void onConnectionStateChange(BeltConnectionState state) {
        Log.e("TAG", String.valueOf(state));
        updateUI();
    }

    @Override
    public void onConnectionLost() {
        showToast("Belt connection lost!");
    }

    @Override
    public void onConnectionFailed() {
        showToast("Belt connection fails!");
    }

    @Override
    public void onPairingFailed() {
        showToast("Belt connection fails!");

    }

    @Override
    public void onBeltFound(BluetoothDevice belt) {

    }

    @Override
    public void onConnectionStateChange(BeltConnectionState state) {
        updateUI();
    }

    @Override
    public void onConnectionLost() {

    }

    @Override
    public void onConnectionFailed() {

    }

    @Override
    public void onPairingFailed() {

    }

    // MARK: Implementation of `BluetoothCheckCallback`

    @Override
    public void onBluetoothReady() {
        BeltConnectionInterface navController = appController.getBeltConnection();
        if (navController != null) {
            navController.scanAndConnect();
        }
    }

    @Override
    public void onBluetoothActivationRejected() {
        showToast("BLE activation rejected!");
    }

    @Override
    public void onBluetoothActivationFailed() {
        showToast("BLE activation failed!");
    }

    @Override
    public void onUnsupportedFeature() {
        showToast("Unsupported BLE feature!");
    }

    private void vibrateLeftToRight() {
        // Definiere die Anzahl der Positionen (z. B. 8 Positionen von 0 bis 7)
        int[] positions = new int[]{0, 1, 2, 3, 4, 5, 6, 7};

        // Dauer der Vibration für jede Position (in Millisekunden)
        int vibrationDuration = 500; // 200 ms pro Position, nach Bedarf anpassen

        BeltCommandInterface beltController = appController.getBeltController();
        beltController.changeMode(BeltMode.APP);

        // Erstelle ein Runnable, das die Vibrationen nacheinander auslöst
        handler.post(new Runnable() {
            int index = 0;

            @Override
            public void run() {
                if (index < positions.length) {
                    // Vibrieren an der aktuellen Position
                    beltController.pulseAtPositions(new int[]{positions[index]}, vibrationDuration, 500, 1, 50, 1, false);
                    Log.d("BeltDebug", "Vibration an Position " + positions[index]);

                    // Gehe zur nächsten Position
                    index++;

                    // Wiederhole den Vorgang mit einer kurzen Verzögerung (hier 300 ms)
                    handler.postDelayed(this, 300); // Pause zwischen den Vibrationen
                }
            }
        });
    }

    private void vibrateRightToLeft() {
        // Definiere die Anzahl der Positionen (z. B. 8 Positionen von 0 bis 7)
        int[] positions = new int[]{7, 6, 5, 4, 3, 2, 1, 0};

        // Dauer der Vibration für jede Position (in Millisekunden)
        int vibrationDuration = 500; // 200 ms pro Position, nach Bedarf anpassen

        BeltCommandInterface beltController = appController.getBeltController();
        beltController.changeMode(BeltMode.APP);

        // Erstelle ein Runnable, das die Vibrationen nacheinander auslöst
        handler.post(new Runnable() {
            int index = 0;

            @Override
            public void run() {
                if (index < positions.length) {
                    // Vibrieren an der aktuellen Position
                    beltController.pulseAtPositions(new int[]{positions[index]}, vibrationDuration, 500, 1, 50, 1, false);
                    Log.d("BeltDebug", "Vibration an Position " + positions[index]);

                    // Gehe zur nächsten Position
                    index++;

                    // Wiederhole den Vorgang mit einer kurzen Verzögerung (hier 300 ms)
                    handler.postDelayed(this, 300); // Pause zwischen den Vibrationen
                }
            }
        });
    }

    @Override
    public void onBeltParameterValueNotified(BeltParameter beltParameter, Object parameterValue) {

    }

    public void startInterpretationMode(View view) {
        Intent intent = new Intent(this, InterpretationModeActivity.class);
        startActivity(intent);
    }

}
