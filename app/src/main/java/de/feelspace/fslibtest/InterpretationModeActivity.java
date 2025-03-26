package de.feelspace.fslibtest;

import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Random;

import de.feelspace.fslib.BeltCommandInterface;
import de.feelspace.fslib.BeltMode;
import de.feelspace.fslib.BeltVibrationSignal;
import de.feelspace.fslib.NavigationController;

public class InterpretationModeActivity extends AppCompatActivity {

    private TextView statusText;
    private Button startTestButton, backButton;
    private Handler handler = new Handler();
    private boolean testRunning = false;
    private long lastPeakTime = 0;
    private long lastValleyTime = 0;
    private ArrayList<Long> peakReactions = new ArrayList<>();
    private ArrayList<Long> valleyReactions = new ArrayList<>();
    private Random random = new Random();

    private final int TEST_DURATION = 180000; // 3 Minuten (in Millisekunden)
    private long testStartTime;
    private int currentLevel = 0;
    private boolean isGoingUp = true;

    private AppController appController;
    private NavigationController navController;
    private BeltCommandInterface beltCommand;

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
    }

    private void startTest() {
        testRunning = true;
        startTestButton.setVisibility(View.GONE);
        backButton.setVisibility(View.GONE);
        statusText.setText("Test läuft...");

        testStartTime = SystemClock.elapsedRealtime();
        scheduleNextStep();
    }

    private void scheduleNextStep() {
        long elapsedTime = SystemClock.elapsedRealtime() - testStartTime;

        if (elapsedTime >= TEST_DURATION) {
            endTest();
            return;
        }

        int nextEventTime = random.nextInt(3000) + 1000;
        handler.postDelayed(this::generateNextCurveStep, nextEventTime);
    }

    private void generateNextCurveStep() {
        if (!testRunning) return;

        if (isGoingUp) {
            currentLevel++;
        } else {
            currentLevel--;
        }

        if (currentLevel >= 5) {
            triggerPeak();
            isGoingUp = false;
        } else if (currentLevel <= -5) {
            triggerValley();
            isGoingUp = true;
        } else {
            triggerIntermediateState();
        }

        scheduleNextStep();
    }

    private void triggerPeak() {
        lastPeakTime = SystemClock.elapsedRealtime();
        navController.startNavigation(0, false, null); // Wait Modus ausschalten
        beltCommand.changeMode(BeltMode.APP); // Gürtel in App-Modus setzen
        beltCommand.vibrateAtPositions(new int[]{11}, 70, BeltVibrationSignal.APPROACHING_DESTINATION, 1, false); // Hochpunkt = Vibration rechts
        //runOnUiThread(() -> statusText.setText("🟢 Hochpunkt erreicht!"));
        Log.d("InterpretationTest", "🟢 Hochpunkt erreicht um " + lastPeakTime + " ms");
    }

    private void triggerValley() {
        lastValleyTime = SystemClock.elapsedRealtime();
        navController.startNavigation(0, false, null); // Wait Modus ausschalten
        beltCommand.changeMode(BeltMode.APP); // Gürtel in App-Modus setzen
        beltCommand.vibrateAtPositions(new int[]{0}, 70, BeltVibrationSignal.APPROACHING_DESTINATION, 1, false); //Tiefpunkt = Vibration links
        //runOnUiThread(() -> statusText.setText("🔵 Tiefpunkt erreicht!"));
        Log.d("InterpretationTest", "🔵 Tiefpunkt erreicht um " + lastValleyTime + " ms");
    }

    private void triggerIntermediateState() {
        if (isGoingUp) {
            navController.startNavigation(0, false, null); // Wait Modus ausschalten
            beltCommand.changeMode(BeltMode.APP); // Gürtel in App-Modus setzen
            beltCommand.vibrateAtPositions(new int[]{5}, 10, BeltVibrationSignal.APPROACHING_DESTINATION, 1, false); // Vibration Mitte
            //runOnUiThread(() -> statusText.setText("📈 Steigend..."));
            Log.d("InterpretationTest", "📈 Steigende Kurve");
        } else {
            navController.startNavigation(0, false, null); // Wait Modus ausschalten
            beltCommand.changeMode(BeltMode.APP); // Gürtel in App-Modus setzen
            beltCommand.vibrateAtPositions(new int[]{5}, 10, BeltVibrationSignal.APPROACHING_DESTINATION, 1, false); // Vibration Mitte
            //runOnUiThread(() -> statusText.setText("📉 Fallend..."));
            Log.d("InterpretationTest", "📉 Fallende Kurve");
        }
    }

    private void recordReaction() {
        long reactionTime = SystemClock.elapsedRealtime();

        if (lastPeakTime > 0 && reactionTime > lastPeakTime && reactionTime - lastPeakTime < 3000) {
            long reactionDelay = reactionTime - lastPeakTime;
            peakReactions.add(reactionDelay);
            //runOnUiThread(() -> statusText.setText("Reaktion auf Hochpunkt: " + reactionDelay + " ms"));
            Log.d("InterpretationTest", "✅ Reaktion auf Hochpunkt: " + reactionDelay + " ms");
        } else if (lastValleyTime > 0 && reactionTime > lastValleyTime && reactionTime - lastValleyTime < 3000) {
            long reactionDelay = reactionTime - lastValleyTime;
            valleyReactions.add(reactionDelay);
            //runOnUiThread(() -> statusText.setText("Reaktion auf Tiefpunkt: " + reactionDelay + " ms"));
            Log.d("InterpretationTest", "✅ Reaktion auf Tiefpunkt: " + reactionDelay + " ms");
        } else {
            //runOnUiThread(() -> statusText.setText("⚠ Falsche Reaktion!"));
            Log.d("InterpretationTest", "⚠ Falsche Reaktion!");
        }
    }

    private void endTest() {
        testRunning = false;
        backButton.setVisibility(View.VISIBLE);
        startTestButton.setVisibility(View.VISIBLE);

        beltCommand.stopVibration(1);

        if (peakReactions.isEmpty() && valleyReactions.isEmpty()) {
            //statusText.setText("❌ Keine korrekten Reaktionen erfasst.");
            Log.d("InterpretationTest", "❌ Keine korrekten Reaktionen erfasst.");
            return;
        }

        long avgPeakReaction = peakReactions.stream().mapToLong(Long::longValue).sum() / (peakReactions.isEmpty() ? 1 : peakReactions.size());
        long avgValleyReaction = valleyReactions.stream().mapToLong(Long::longValue).sum() / (valleyReactions.isEmpty() ? 1 : valleyReactions.size());

        String result = "Durchschnittliche Reaktionszeiten:\n" +
                "🟢 Hochpunkt: " + avgPeakReaction + " ms\n" +
                "🔵 Tiefpunkt: " + avgValleyReaction + " ms";

        statusText.setText(result);
        Log.d("InterpretationTest", "Durchschnittliche Reaktionszeiten:");
        Log.d("InterpretationTest", "🟢 Hochpunkt: " + avgPeakReaction + " ms");
        Log.d("InterpretationTest", "🔵 Tiefpunkt: " + avgValleyReaction + " ms");
    }
}
