package alexkang.swing;

import android.app.Activity;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.wearable.view.WatchViewStub;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.List;

public class MainActivity extends Activity implements SensorEventListener, MessageApi.MessageListener {

    private static final String LIGHT_SOUND = "x";
    private static final String HEAVY_SOUND = "y";
    private static final String UPDATE_VIBRATE = "vibrate/";
    private static final String UPDATE_SENSITIVITY = "sensitivity/";
    private static final String UPDATE_FREQUENCY = "frequency/";

    private WatchViewStub stub;
    private TextView title;
    private GoogleApiClient googleApiClient;
    private List<Node> nodes;
    protected SensorManager sManager;
    private Sensor sensor;
    private Vibrator vibrator;

    private SharedPreferences sharedPrefs;
    private boolean vibrate;
    private float sensitivity;
    private int delay;
    private boolean wait = false;

    private boolean isHeavy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub watchViewStub) {
                title = (TextView) stub.findViewById(R.id.title);
                title.setTextSize(24);

                stub.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        switchMode();
                    }
                });
            }
        });

        setUpGoogleAPIClient();

        Wearable.NodeApi.getConnectedNodes(googleApiClient)
                .setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                    @Override
                    public void onResult(NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                        nodes = getConnectedNodesResult.getNodes();
                    }
                });

        sManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensor = sManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        vibrate = sharedPrefs.getBoolean("vibrate", true);
        sensitivity = sharedPrefs.getFloat("sensitivity", 25);
        delay = sharedPrefs.getInt("frequency", 150);
    }

    @Override
    public void onPause() {
        super.onPause();
        sManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        double acceleration = Math.sqrt(x*x + y*y + z*z);

        if (acceleration > sensitivity && !wait) {
            wait = true;

            stub.setBackgroundColor(getResources().getColor(R.color.yellow));
            title.setTextSize(36);

            new CountDownTimer(delay, delay) {
                @Override
                public void onTick(long millisUntilFinished) {}

                @Override
                public void onFinish() {
                    wait = false;

                    if (isHeavy) {
                        stub.setBackgroundColor(getResources().getColor(android.R.color.white));
                    } else {
                        stub.setBackgroundColor(getResources().getColor(android.R.color.black));
                    }
                    title.setTextSize(24);
                }
            }.start();

            if (vibrate) {
                vibrator.vibrate(150);
            }

            if (isHeavy) {
                for (Node node : nodes) {
                    Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), HEAVY_SOUND, null);
                }
            } else {
                for (Node node : nodes) {
                    Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), LIGHT_SOUND, null);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onResume() {
        super.onResume();

        sManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
        Wearable.MessageApi.addListener(googleApiClient, this);
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        String message = messageEvent.getPath();
        String value = message.split("/")[1];

        if (message.contains(UPDATE_VIBRATE)) {
            vibrate = Boolean.parseBoolean(value);
            sharedPrefs.edit().putBoolean("vibrate", vibrate).apply();
        } else if (message.contains(UPDATE_SENSITIVITY)) {
            sensitivity = Float.parseFloat(value);
            sharedPrefs.edit().putFloat("sensitivity", sensitivity).apply();
        } else if (message.contains(UPDATE_FREQUENCY)) {
            delay = Integer.parseInt(value);
            sharedPrefs.edit().putInt("frequency", delay).apply();
        }
    }

    private void setUpGoogleAPIClient() {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult connectionResult) {
                        Toast.makeText(getBaseContext(), "Google Play Services not available.",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle bundle) {}

                    @Override
                    public void onConnectionSuspended(int i) {
                        Toast.makeText(getBaseContext(), "Google Play Services has stopped.",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .build();

        googleApiClient.connect();
    }

    private void switchMode() {
        isHeavy = !isHeavy;

        if (isHeavy) {
            stub.setBackgroundColor(getResources().getColor(android.R.color.white));
            title.setTextColor(getResources().getColor(android.R.color.black));
        } else {
            stub.setBackgroundColor(getResources().getColor(android.R.color.black));
            title.setTextColor(getResources().getColor(android.R.color.white));
        }
    }

}
