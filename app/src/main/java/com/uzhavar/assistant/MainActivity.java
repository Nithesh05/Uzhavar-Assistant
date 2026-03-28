package com.uzhavar.assistant;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final int RECORD_AUDIO_PERMISSION_CODE = 1;

    private TextView tvInstruction, tvVoiceIndicator;
    private Button btnStart, btnDone, btnSpeak, btnRepeat;
    private DrawerLayout drawerLayout;

    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Vibrator vibrator;

    private int currentStep = 0;
    private String currentMessage = "";

    private final String[] stepMessages = {
            "வணக்கம்! நான் உங்களுக்கு உதவுகிறேன்",
            "முதலில் லாகின் செய்யுங்க... முடிச்சா ஆச்சு சொல்லுங்க",
            "விவசாயியின் ஆதார் எண் உள்ளிடுங்க... முடிச்சா சொல்லுங்க",
            "OTP அல்லது கைரேகை வைங்க... முடிச்சா சொல்லுங்க",
            "உரம் தேர்வு செய்யுங்க",
            "எத்தனை மூட்டை வேணும்?",
            "உரத்துக்கு விலை உள்ளிடுங்க",
            "எல்லா விவரங்களையும் சரி பார்த்து உறுதிப்படுத்துங்க",
            "பில் அச்சாகும்"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        initViews();
        setupNavigationDrawer();
        initTTS();
        initSpeechRecognizer();

        requestPermissions();
    }

    private void initViews() {
        tvInstruction = findViewById(R.id.tvInstruction);
        tvVoiceIndicator = findViewById(R.id.tvVoiceIndicator);
        btnStart = findViewById(R.id.btnStart);
        btnDone = findViewById(R.id.btnDone);
        btnSpeak = findViewById(R.id.btnSpeak);
        btnRepeat = findViewById(R.id.btnRepeat);

        btnStart.setOnClickListener(v -> { vibrate(); handleNextStep(); });
        btnDone.setOnClickListener(v -> { vibrate(); handleNextStep(); });
        btnRepeat.setOnClickListener(v -> { vibrate(); repeatMessage(); });
        btnSpeak.setOnClickListener(v -> { vibrate(); startListening(); });
    }

    private void setupNavigationDrawer() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                Locale tamilLocale = new Locale("ta", "IN");
                int result = tts.setLanguage(tamilLocale);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "Tamil TTS is not installed", Toast.LENGTH_SHORT).show();
                }

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        runOnUiThread(() -> tvVoiceIndicator.setVisibility(View.INVISIBLE));
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        // Can auto-start listening here, but strictly "Wait for user input" is required
                    }

                    @Override
                    public void onError(String utteranceId) { }
                });

                // Start App
                playStep(0);
            }
        });
    }

    private void initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                tvVoiceIndicator.setVisibility(View.VISIBLE);
                tvVoiceIndicator.setText("🎤 கேட்கிறது…");
            }

            @Override
            public void onBeginningOfSpeech() { }

            @Override
            public void onRmsChanged(float rmsdB) { }

            @Override
            public void onBufferReceived(byte[] buffer) { }

            @Override
            public void onEndOfSpeech() {
                tvVoiceIndicator.setText("⏳ சரிபார்க்கிறது…");
            }

            @Override
            public void onError(int error) {
                tvVoiceIndicator.setVisibility(View.INVISIBLE);
                Toast.makeText(MainActivity.this, "குரல் பதிவு தோல்வி. பட்டனைப் பயன்படுத்துங்கள்.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onResults(Bundle results) {
                tvVoiceIndicator.setVisibility(View.INVISIBLE);
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spokenText = matches.get(0).toLowerCase();
                    processUserSpeech(spokenText);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) { }

            @Override
            public void onEvent(int eventType, Bundle params) { }
        });
    }

    private void startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return;
        }

        if (tts.isSpeaking()) {
            tts.stop();
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "பேசுங்கள்...");

        speechRecognizer.startListening(intent);
    }

    private void processUserSpeech(String text) {
        if (text.contains("ஆச்சு") || text.contains("ஆம்") || text.contains("தொடங்கு") || text.contains("யூரியா") || text.contains("டி.ஏ.பி")) {
            handleNextStep();
        } else if (text.contains("மறுபடியும்")) {
            repeatMessage();
        } else if (text.contains("இல்லை")) {
            speakText("சரி, மறுபடியும் சொல்றேன்.");
            new Handler(Looper.getMainLooper()).postDelayed(this::repeatMessage, 2000);
        } else {
            speakText("புரியல, மறுபடியும் சொல்லுங்க");
        }
    }

    private void handleNextStep() {
        if (tts.isSpeaking()) {
            tts.stop();
        }
        
        if (currentStep < stepMessages.length - 1) {
            currentStep++;
            playStep(currentStep);
        } else {
            speakText("அனைத்தும் முடிந்தது. நன்றி!");
            tvInstruction.setText("அனைத்தும் முடிந்தது. நன்றி!");
            currentStep = 0; // reset
        }
    }

    private void repeatMessage() {
        speakText(currentMessage);
    }

    private void playStep(int step) {
        currentMessage = stepMessages[step];
        tvInstruction.setText(currentMessage);
        speakText(currentMessage);
    }

    private void speakText(String text) {
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "MessageId");
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "MessageId");
    }

    private void vibrate() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(50);
            }
        }
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_CODE);
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_about) {
            Toast.makeText(this, "உழவர் உதவி - VisionTek Assistant", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_contact) {
            Toast.makeText(this, "Instagram: @nivax.tech", Toast.LENGTH_LONG).show();
        } else if (id == R.id.nav_feedback) {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:feedback@example.com")); 
            intent.putExtra(Intent.EXTRA_SUBJECT, "Feedback for Uzhavar Assistant");
            startActivity(Intent.createChooser(intent, "Send Feedback"));
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
