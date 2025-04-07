package de.feelspace.fslib;

import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class LatencyTester {

    private final BluetoothGattCharacteristic testCharacteristic;
    private final GattController gattController; // dein Controller
    private final int repetitions;
    private final LatencyResultCallback callback;

    private final List<Long> latenciesMicros = new ArrayList<>();
    private int currentAttempt = 0;
    private long lastCommandTimeNano = -1;

    public interface LatencyResultCallback {
        void onTestFinished(List<Long> latencies, double averageMicros);
    }

    public LatencyTester(BluetoothGattCharacteristic testCharacteristic,
                         GattController gattController,
                         int repetitions,
                         LatencyResultCallback callback) {
        this.testCharacteristic = testCharacteristic;
        this.gattController = gattController;
        this.repetitions = repetitions;
        this.callback = callback;
    }

    public void startTest() {
        latenciesMicros.clear();
        currentAttempt = 0;
        sendNextReadRequest();
    }

    private void sendNextReadRequest() {
        lastCommandTimeNano = System.nanoTime();
        boolean success = gattController.readCharacteristic(testCharacteristic);
        if (!success) {
            Log.e("LATENCY_TEST", "Failed to read characteristic!");
        }
    }

    public void onCharacteristicRead(BluetoothGattCharacteristic characteristic) {
        if (characteristic != testCharacteristic || lastCommandTimeNano == -1) return;

        long now = System.nanoTime();
        long latencyMicros = (now - lastCommandTimeNano) / 1000;
        latenciesMicros.add(latencyMicros);
        Log.d("LATENCY_TEST", "Messung " + (currentAttempt + 1) + ": " + latencyMicros + " µs");

        currentAttempt++;
        if (currentAttempt < repetitions) {
            new Handler(Looper.getMainLooper()).postDelayed(this::sendNextReadRequest, 500);
        } else {
            double avg = latenciesMicros.stream().mapToLong(Long::longValue).average().orElse(0.0);
            callback.onTestFinished(latenciesMicros, avg);
        }
    }
}
