package de.feelspace.fslibtest;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import de.feelspace.fslib.BeltCommandInterface;
//import de.feelspace.fslib.BeltConnectionController;
import de.feelspace.fslib.BeltMode;
import de.feelspace.fslib.BeltVibrationSignal;
import de.feelspace.fslib.NavigationController;

public class InterpretationModeActivity extends AppCompatActivity {

    private TextView statusText;
    private Button startTestButton, backButton;
    private Handler handler = new Handler();
    private boolean testRunning = false;
    private long lastCriticalTime = 0;
    private ArrayList<Long> reactionTimes = new ArrayList<>();
    private static final int TEST_DURATION = 180000; // 3 Minuten (in ms)
    private long testStartTime;
    private AppController appController;
    private NavigationController navController;
    private BeltCommandInterface beltCommand;

    //private BeltConnectionController beltConnection;
    private long lastSentTime;
    private static final int UPDATE_RATE_MS = 1000; // 1 Sekunde
    private FileWriter logFileWriter;
    private int currentIndex = 0;

    // Feste Herzfrequenzwerte
    private static final int[] HEART_RATE_VALUES = {
            94, 62, 75, 77, 60, 87, 91, 79, 38, 69, 93, 85, 79, 82, 99, 64, 74, 81, 82, 64,
            74, 94, 53, 79, 60, 82, 67, 79, 84, 82, 51, 80, 94, 94, 90, 94, 60, 85, 174, 86,
            64, 78, 79, 67, 80, 90, 58, 77, 83, 67, 99, 70, 77, 78, 80, 95, 85, 81, 83, 79,
            69, 98, 83, 86, 69, 89, 97, 78, 66, 72, 77, 66, 73, 164, 73, 97, 99, 136, 97, 96,
            98, 79, 88, 76, 84, 73, 128, 31, 75, 65, 74, 66, 84, 95, 86, 62, 91, 84, 96, 61,
            80, 98, 70, 99, 77, 64, 89, 119, 90, 93, 97, 89, 72, 98, 81, 92, 94, 70, 87, 83,
            99, 61, 64, 68, 78, 81, 94, 72, 66, 70, 88, 75, 75, 69, 73, 63, 90, 80, 76, 80,
            91, 168, 63, 73, 84, 73, 67, 70, 85, 86, 61, 76, 92, 89, 63, 84, 62, 76, 97, 61,
            96, 89, 97, 133, 70, 69, 86, 99, 90, 64, 75, 67, 99, 87, 86, 71, 98, 87, 68, 143
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_interpretation_mode);

        appController = AppController.getInstance();
        navController = appController.getNavigationController();
        beltCommand = navController.getBeltConnection().getCommandInterface();

        statusText = findViewById(R.id.statusText);
        startTestButton = findViewById(R.id.startTestButton);
        backButton = findViewById(R.id.backButton);

        startTestButton.setOnClickListener(v -> startTest());
        backButton.setOnClickListener(v -> finish());

        findViewById(android.R.id.content).setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && testRunning) {
                recordReaction();
                return true;
            }
            return false;
        });

        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File logFile = new File(downloadDir, "interpretation_test_log.txt");
            logFileWriter = new FileWriter(logFile, true);
            Log.d("InterpretationTest", "Log-Datei gespeichert unter: " + logFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e("InterpretationTest", "Fehler beim Öffnen der Log-Datei", e);
        }
    }

    private void startTest() {
        testRunning = true;
        startTestButton.setVisibility(View.GONE);
        backButton.setVisibility(View.GONE);
        statusText.setText("Test läuft...");
        testStartTime = SystemClock.elapsedRealtime();
        currentIndex = 0;
        scheduleNextStep();
    }

    private void scheduleNextStep() {
        if (SystemClock.elapsedRealtime() - testStartTime >= TEST_DURATION || currentIndex >= HEART_RATE_VALUES.length) {
            endTest();
            return;
        }

        handler.postDelayed(this::generateNextVibration, UPDATE_RATE_MS);
    }

    private void generateNextVibration() {
        if (!testRunning) return;

        int heartRate = HEART_RATE_VALUES[currentIndex];
        int vibrationPosition = mapHeartRateToPosition(heartRate);
        lastSentTime = SystemClock.elapsedRealtime();

        beltCommand.changeMode(BeltMode.APP);
        navController.startNavigation(0, false, null);
        beltCommand.changeMode(BeltMode.APP);
        //beltCommand.vibrateAtPositions(new int[]{vibrationPosition}, 70, BeltVibrationSignal.NEXT_WAYPOINT_AREA_REACHED, 1, false);
        beltCommand.pulseAtPositions(new int[]{vibrationPosition}, 1000, 1000, 1, 50, 1, true);

        if (heartRate > 140 || heartRate < 50) {
            lastCriticalTime = lastSentTime;
            Log.d("InterpretationTest", "⚠ Kritischer Wert gesendet: " + heartRate + " BPM");
            writeToLogFile("Kritischer Wert gesendet: " + heartRate + " BPM um " + lastCriticalTime + " ms\n");
        }

        currentIndex++;
        scheduleNextStep();
    }

    private int mapHeartRateToPosition(int heartRate) {
        return (int) ((heartRate - 30) / 150.0 * 15);
    }

    private void recordReaction() {
        // Berechnet die Reaktionszeit, indem die aktuelle Zeit mit der letzten gesendeten Vibration verglichen wird
        long reactionTime = SystemClock.elapsedRealtime() - lastSentTime;
        reactionTimes.add(reactionTime);
        Log.d("InterpretationTest", "Reaktion erfasst: " + reactionTime + " ms");
        writeToLogFile("Reaktion erfasst: " + reactionTime + " ms\n");
    }

    private void endTest() {
        testRunning = false;
        startTestButton.setVisibility(View.VISIBLE);
        backButton.setVisibility(View.VISIBLE);
        statusText.setText("Test beendet");

        // Durchschnittliche Reaktionszeit berechnen
        long sum = 0;
        for (long time : reactionTimes) {
            sum += time;
        }
        long avgReactionTime = reactionTimes.isEmpty() ? 0 : sum / reactionTimes.size();
        Log.d("InterpretationTest", "Durchschnittliche Reaktionszeit: " + avgReactionTime + " ms");
        writeToLogFile("Test beendet. Durchschnittliche Reaktionszeit: " + avgReactionTime + " ms\n");

        // Navigation und Vibration stoppen
        navController.stopNavigation();
        beltCommand.vibrateAtPositions(new int[]{}, 0, BeltVibrationSignal.NEXT_WAYPOINT_AREA_REACHED, 0, false);
    }

    private void writeToLogFile(String data) {
        // Schreibt Log-Daten in die Datei
        try {
            logFileWriter.write(data);
            logFileWriter.flush();
        } catch (IOException e) {
            Log.e("InterpretationTest", "Fehler beim Schreiben in die Log-Datei", e);
        }
    }

}
