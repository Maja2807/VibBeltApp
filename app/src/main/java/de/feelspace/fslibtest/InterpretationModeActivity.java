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
import de.feelspace.fslib.BeltConnectionInterface;
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
    private BeltCommandInterface beltController;
    private BeltConnectionInterface beltConnection;
    private long lastSentTime;
    private static final int UPDATE_RATE_MS = 1000; // 1 Sekunde
    private FileWriter logFileWriter;
    private int currentIndex = 0;

    // Feste Herzfrequenzwerte
    private static final int[] HEART_RATE_VALUES = { //6x zu niedrig, 6x zu hoch
            94, 62, 75, 77, 60, 87, 91, 79, 38, 69, 93, 85, 79, 82, 99, 64, 74, 81, 82, 64,
            74, 94, 53, 79, 60, 82, 67, 79, 84, 82, 51, 80, 94, 94, 90, 94, 60, 85, 174, 86,
            64, 78, 79, 67, 80, 90, 58, 77, 83, 67, 99, 70, 77, 78, 80, 95, 85, 81, 83, 79,
            69, 98, 83, 86, 69, 89, 97, 78, 66, 72, 77, 66, 73, 164, 73, 97, 99, 136, 97, 96,
            98, 79, 88, 76, 84, 73, 128, 80, 75, 65, 74, 66, 84, 95, 86, 62, 91, 84, 96, 61,
            80, 98, 70, 99, 77, 64, 89, 119, 90, 93, 97, 89, 72, 98, 81, 92, 94, 70, 87, 83,
            99, 61, 64, 68, 78, 81, 94, 72, 66, 70, 88, 75, 75, 69, 73, 63, 90, 80, 76, 80,
            91, 168, 63, 73, 84, 73, 67, 70, 85, 86, 61, 76, 92, 89, 63, 84, 62, 76, 97, 61,
            96, 89, 97, 86, 70, 69, 59, 41, 90, 64, 75, 67, 99, 100, 96, 92, 98, 87, 70, 73
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_interpretation_mode);

        appController = AppController.getInstance();
        beltController = appController.getBeltController();
        beltConnection = appController.getBeltConnection();

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

        handler.postDelayed(() -> {
            BeltMode currentMode = beltController.getMode();
            //Log.d("BeltDebug", "Aktueller Modus: " + currentMode);
            beltController.stopVibration();
        }, 500);

        Log.d("MappingDebug", "HeartRate: " + heartRate + " → Position: " + vibrationPosition);

        beltController.changeMode(BeltMode.APP);
        beltController.pulseAtPositions(new int[]{vibrationPosition}, 1000, 1000, 1, 50, 1, true);

        if (heartRate > 100 || heartRate < 60) {
            lastCriticalTime = lastSentTime;
            Log.d("InterpretationTest", "⚠ Kritischer Wert gesendet: " + heartRate + " BPM");
            writeToLogFile("Kritischer Wert gesendet: " + heartRate + " BPM um " + lastCriticalTime + " ms\n");
        }

        currentIndex++;
        scheduleNextStep();
    }

    private int mapHeartRateToPosition(int heartRate) {
        if (heartRate < 60) return 12;  // Kritisch niedrige Werte → links (12)
        if (heartRate > 100) return 4; // Kritisch hohe Werte → rechts (4)

        // Lineares Mapping der Herzfrequenz auf den Bereich zwischen 13 und 3
        int position = 13 + (int) ((heartRate - 60) / 40.0 * 7);  // Berechnung im Bereich von 13 bis 3

        return position % 16;  // Positionen im Bereich 12-4
    }

    private void recordReaction() {
        if (testRunning) {
            // Überprüfen, ob die Reaktion auf den richtigen Pulswert war
            int heartRate = HEART_RATE_VALUES[currentIndex - 1]; // Der Wert, auf den wir reagieren sollten
            if (heartRate < 60 || heartRate > 100) {
                long reactionTime = SystemClock.elapsedRealtime() - lastCriticalTime;
                reactionTimes.add(reactionTime);
                Log.d("Interpretationstest", "Korrekte Reaktion erfasst: " + reactionTime + " ms");
                writeToLogFile("Korrekte Reaktion erfasst: " + reactionTime + " ms\n");
            } else {
                long reactionTime = SystemClock.elapsedRealtime() - lastCriticalTime;
                reactionTimes.add(reactionTime);
                // Falsche Reaktion, keine Reaktionszeit messen
                Log.d("Interpretationstest", "Falsche Reaktion oder verspätet: " + reactionTime + " ms");
                writeToLogFile("Falsche Reaktion oder verspätet: " + reactionTime + " ms\n");
            }
        }
    }

    /*private void recordReaction() {
        if (testRunning) {
            // Überprüfen, ob die Reaktion auf den richtigen Pulswert war
            int heartRate = HEART_RATE_VALUES[currentIndex - 1]; // Der Wert, auf den wir reagieren sollten
            long currentTime = SystemClock.elapsedRealtime();

            // Überprüfen, ob es sich um einen kritischen Wert handelt (zu niedrig oder zu hoch)
            if (heartRate < 60 || heartRate > 100) {
                // Überprüfen, ob die Reaktion innerhalb des Zeitlimits (3 Sekunden) erfolgt ist
                if (currentTime - lastCriticalTime <= 3000) { // 3000 ms = 3 Sekunden
                    long reactionTime = currentTime - lastCriticalTime;
                    reactionTimes.add(reactionTime);
                    Log.d("Interpretationstest", "Korrekte Reaktion erfasst: " + reactionTime + " ms");
                    writeToLogFile("Korrekte Reaktion erfasst: " + reactionTime + " ms\n");
                } else {
                    // Reaktion zu spät (mehr als 3 Sekunden nach dem kritischen Wert)
                    Log.d("Interpretationstest", "Reaktion zu spät (mehr als 3 Sekunden nach kritischem Wert).");
                    writeToLogFile("Reaktion zu spät (mehr als 3 Sekunden nach kritischem Wert).\n");
                }
            } else {
                // Falsche Reaktion, keine Reaktionszeit messen
                Log.d("Interpretationstest", "Falsche Reaktion: Keine Reaktionszeit gemessen.");
                writeToLogFile("Falsche Reaktion");
            }
        }
    }*/

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

        // Vibration stoppen
        beltController.stopVibration();
        beltController.changeMode(BeltMode.WAIT);
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
