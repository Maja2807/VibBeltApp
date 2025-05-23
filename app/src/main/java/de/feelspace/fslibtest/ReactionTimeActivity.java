package de.feelspace.fslibtest;

import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import de.feelspace.fslib.BeltCommandInterface;
import de.feelspace.fslib.BeltConnectionInterface;
import de.feelspace.fslib.BeltConnectionState;
import de.feelspace.fslib.BeltMode;
import de.feelspace.fslib.BeltVibrationSignal;
import de.feelspace.fslib.NavigationController;

public class ReactionTimeActivity extends AppCompatActivity {
    private Handler handler = new Handler();
    private Random random = new Random();
    private long vibrationStartTime;
    private boolean waitingForReaction = false;
    private int remainingVibrations = 30; // Anzahl der Vibrationen pro Testlauf
    private List<Long> reactionTimes = new ArrayList<>(); // Speicherung der Reaktionszeiten
    private Button startTestButton, backButton;

    private AppController appController;

    private BeltCommandInterface beltController;
    private BeltConnectionInterface beltConnection;

    private List<Long> latencyTimes = new ArrayList<>();
    private long sendTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reaction_time);

        appController = AppController.getInstance();
        beltController = appController.getBeltController();
        beltConnection = appController.getBeltConnection();

        startTestButton = findViewById(R.id.startTestButton);
        backButton = findViewById(R.id.backButton);

        startTestButton.setOnClickListener(v -> startTest());
        backButton.setOnClickListener(v -> finish());
    }

    private void startTest() {
        reactionTimes.clear();
        remainingVibrations = 30;
        backButton.setVisibility(View.GONE); // Zurück-Button ausblenden
        startTestButton.setEnabled(false);

        if (beltController == null) {
            Log.e("BeltError", "BeltCommandInterface ist null! Verbindung fehlt?");
        } else {
            Log.d("BeltSuccess", "BeltCommandInterface vorhanden. Wechsle in APP-Modus.");
            beltController.changeMode(BeltMode.APP);
        }

        BeltConnectionState connectionState = beltConnection.getState();
        Log.d("BeltDebug", "Verbindungsstatus: " + connectionState);
        scheduleNextVibration();
    }
    private void scheduleNextVibration() {
        if (remainingVibrations <= 0) {
            finishTest();
            return;
        }

        long delay = random.nextInt(9990) + 15; // Zufällige Pause (10 ms – 15 s)
        Log.d("ReactionTimeTest", "Delay bis nächste Vibration: " + delay + " ms");

        handler.postDelayed(() -> {
            beltController.changeMode(BeltMode.APP);

            sendTime = SystemClock.elapsedRealtime();
            beltController.pulseAtPositions(new int[]{15, 0}, 1000, 1000, 1, 50, 3, true); // Vibrationen vorn

            // Latenz
            vibrationStartTime = SystemClock.elapsedRealtime();
            long latency = vibrationStartTime - sendTime;
            latencyTimes.add(latency);
            Log.d("ReactionTimeTest", "Latenz: " + latency + " ms");

            waitingForReaction = true;

            // Handler für Reaktionszeit ohne Eingabe nach 5 Sekunden
            handler.postDelayed(() -> {
                if (waitingForReaction) {
                    Log.d("ReactionTimeTest", "Keine Reaktion innerhalb von 5 Sekunden.");
                    long reactionTime = 5000; // 5 Sekunden Reaktionszeit
                    reactionTimes.add(reactionTime);  // Füge die 5 Sekunden Reaktionszeit hinzu
                    Log.d("ReactionTimeTest", "Reaktion (Keine Antwort): " + reactionTime + " ms");
                    waitingForReaction = false;
                    remainingVibrations--;
                    scheduleNextVibration();
                }
            }, 5000); // 5 Sekunden warten

        }, delay);
    }


    //ohne 5s Timeout
    /*private void scheduleNextVibration() {

        if (remainingVibrations <= 0) {
            finishTest();
            return;
        }

        long delay = random.nextInt(9990) + 15; // Zufällige Pause (10 ms – 15 s)
        Log.d("ReactionTimeTest", "Delay bis nächste Vibration: " + delay + " ms");

        handler.postDelayed(() -> {
            //int position = random.nextInt(8); // Zufällige Gürtel-Position (0-7)
            //Log.d("ReactionTimeTest", "Vibration an Position: " + position);

            beltController.changeMode(BeltMode.APP);
            handler.postDelayed(() -> {
                BeltMode currentMode = beltController.getMode();
                Log.d("BeltDebug", "Aktueller Modus: " + currentMode);
                beltController.stopVibration();
            }, 500);
            //beltController.stopVibration();
            // Log-Ausgabe, wann die Vibration gesendet wird
            Log.d("ReactionTimeTest", "Vibration wird jetzt gesendet.");
            beltController.pulseAtPositions(new int[]{15, 0}, 1000, 1000, 1, 50, 3, true);

            vibrationStartTime = SystemClock.elapsedRealtime();
            waitingForReaction = true;
        }, delay);
    }*/

    private void recordReactionTime() {
        if (waitingForReaction) {
            long reactionTime = SystemClock.elapsedRealtime() - vibrationStartTime;
            reactionTimes.add(reactionTime);
            Log.d("ReactionTimeTest", "Reaktion: " + reactionTime + " ms");

            waitingForReaction = false;
            remainingVibrations--;
            scheduleNextVibration();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            recordReactionTime(); // Reaktion auf beliebige Berührung registrieren
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void finishTest() {
        Log.d("ReactionTimeTest", "Alle Reaktionszeiten: " + reactionTimes.toString());

        // Reaktionszeiten berechnen
        if (!reactionTimes.isEmpty()) {
            long sum = 0;
            for (long time : reactionTimes) {
                sum += time;
            }
            long averageReactionTime = sum / reactionTimes.size();
            Log.d("ReactionTimeTest", "Durchschnittliche Reaktionszeit: " + averageReactionTime + " ms");
        } else {
            Log.d("ReactionTimeTest", "Keine gültigen Reaktionszeiten erfasst.");
        }

        // Latenzzeiten berechnen
        if (!latencyTimes.isEmpty()) {
            long sum = 0;
            for (long time : latencyTimes) {
                sum += time;
            }
            long averageLatency = sum / latencyTimes.size();
            Log.d("ReactionTimeTest", "Durchschnittliche Latenz: " + averageLatency + " ms");
        } else {
            Log.d("ReactionTimeTest", "Keine gültigen Latenzen erfasst.");
        }


        backButton.setVisibility(View.VISIBLE);
        startTestButton.setEnabled(true);
    }
}
