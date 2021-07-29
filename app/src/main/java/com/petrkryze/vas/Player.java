package com.petrkryze.vas;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;

/**
 * Created by Petr on 07.02.2020. Yay!
 */

public class Player {
    private static final String TAG = "Player";
    public static final int SAVED_PROGRESS_DEFAULT = -1;

    private MediaPlayer mediaPlayer;
    private final AudioManager audioManager;
    private State state = State.END;

    private final PlayerListener listener;

    private boolean isSeeking = false;

    private final Handler handler;
    private int lastTick = 0;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null) {
                int current_ms = 0;
                if (!isStateAnyOf(State.ERROR)) {
                    current_ms = mediaPlayer.getCurrentPosition();
                } else {
                    logInvalidState("getCurrentPosition");
                }

                int current = (int) Math.floor((double) current_ms / 1000);
                if (lastTick != current) {
                    listener.onTimeTick(current_ms);
                    lastTick = current;
                }
                listener.onUpdateProgress(current_ms);

                if (!isStateAnyOf(State.ERROR)) {
                    if (mediaPlayer.isPlaying()) {
                        handler.postDelayed(tick, 250);
                    }
                } else {
                    logInvalidState("isPlaying");
                }
            }
        }
    };

    private enum State {IDLE, INITIALIZED, PREPARING, PREPARED, STARTED, PAUSED, STOPPED, PLAYBACK_COMPLETED, END, ERROR}

    public interface PlayerListener {
        void onTrackPrepared(int duration);
        void onTrackFinished();
        void onHeadphonesMissing();
        void onVolumeDown();
        void onTimeTick(int tick_ms);
        void onUpdateProgress(int current_ms);
        void onError();
    }

    public Player(Context context, final PlayerListener listener) {
        this.listener = listener;
        this.mediaPlayer = new MediaPlayer();

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        if (!isStateAnyOf(State.ERROR)) {
            mediaPlayer.setAudioAttributes(attributes);
        } else {
            logInvalidState("setAudioAttributes");
        }

        mediaPlayer.setOnPreparedListener(mp -> { // Can be called in any state
            state = State.PREPARED;

            if (isStateAnyOf(State.PREPARED, State.STARTED, State.PAUSED, State.STOPPED, State.PLAYBACK_COMPLETED)) {
                Player.this.listener.onTrackPrepared(mediaPlayer.getDuration());
            } else {
                logInvalidState("getDuration");
            }
        });

        mediaPlayer.setOnCompletionListener(mp -> { // Can be called in any state
            state = State.PLAYBACK_COMPLETED;
            listener.onTrackFinished();
        });

        mediaPlayer.setOnSeekCompleteListener(mp -> { // Can be called in any state
            isSeeking = false;
            doTick();
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            state = State.ERROR;
            Log.e(TAG, "Player: Error! Reseting the player.");
            listener.onError();
            return true;
        });
        state = State.IDLE;

        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        HandlerThread handlerThread = new HandlerThread("ElapsedTime");
        handlerThread.start();
        this.handler = new Handler(handlerThread.getLooper());
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isSeeking() {
        return isSeeking;
    }

    public boolean isPlaying() {
        if (mediaPlayer != null) {
            if (!isStateAnyOf(State.ERROR)) {
                return mediaPlayer.isPlaying();
            } else {
                logInvalidState("isPlaying");
                return false;
            }
        } else {
            Log.e(TAG, "isPlaying: mediaPlayer is null");
            return false;
        }
    }

    public boolean setCurrentTrack(Context context, Uri uri) throws IOException {
        if (!isSeeking && uri != null) {
            //noinspection ConstantConditions
            isSeeking = false;
            mediaPlayer.reset(); // Can be called in any state
            state = State.IDLE;

            if (isStateAnyOf(State.IDLE)) { // Can only be called in IDLE state
                mediaPlayer.setDataSource(context, uri);
                state = State.INITIALIZED;
            } else {
                state = State.ERROR;
                logInvalidState("setDataSource");
                throw new IllegalStateException("mediaPlayer.setDataSource() called in a non-IDLE state");
            }

            if (isStateAnyOf(State.INITIALIZED, State.STOPPED)) {
                mediaPlayer.prepareAsync();
                state = State.PREPARING;
            } else {
                state = State.ERROR;
                logInvalidState("prepareAsync");
                throw new IllegalStateException("mediaPlayer.prepareAsync() called in an invalid state");
            }

            return true;
        } else {
            return false;
        }
    }

    public boolean start() {
        if (isHeadphonesIn() && !isVolumeZero() && !isSeeking) {
            if (isStateAnyOf(State.PREPARED, State.STARTED, State.PAUSED, State.PLAYBACK_COMPLETED)) {
                mediaPlayer.start();
                state = State.STARTED;
                doTick();
                return true;
            } else {
                logInvalidState("start");
                return false;
            }
        }
        // Returns true if play start was successful
        return false;
    }

    public boolean pause() {
        if (isPlaying() && !isSeeking) {
            if (isStateAnyOf(State.STARTED, State.PAUSED, State.PLAYBACK_COMPLETED)) {
                dontTick();
                mediaPlayer.pause();
                state = State.PAUSED;
                return true;
            } else {
                logInvalidState("pause");
                return false;
            }
        }
        return false;
    }

    public void rewind() {
        seekTo(0);
    }

    public void seekTo(int target) {
        if (!isSeeking) {
            if (isStateAnyOf(State.PREPARED, State.STARTED, State.PAUSED, State.PLAYBACK_COMPLETED)) {
                dontTick();
                isSeeking = true;
                mediaPlayer.seekTo(target);
            } else {
                logInvalidState("seekTo");
            }
        }
    }

    private void doTick() {
        handler.post(tick);
    }

    public void dontTick() {
        handler.removeCallbacks(tick);
    }

    public void clean() {
        isSeeking = false;
        dontTick();

        if (mediaPlayer != null) {
            mediaPlayer.release(); // Can be called in any state
            state = State.END;
            mediaPlayer = null;
        }
    }

    private boolean isVolumeZero() {
        if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC) ||
                (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0)) {
            listener.onVolumeDown();
            return true;
        }
        return false;
    }

    private boolean isHeadphonesIn() {
        AudioDeviceInfo[] audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo deviceInfo : audioDevices){
            if (deviceInfo.getType()==AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    || deviceInfo.getType()==AudioDeviceInfo.TYPE_WIRED_HEADSET){
                return true;
            }
        }
        listener.onHeadphonesMissing();
        return false;
    }

    private boolean isStateAnyOf(State... states) {
        return Arrays.stream(states).anyMatch(state1 -> state1 == state);
    }

    private void logInvalidState(String methodName) {
        Log.w(TAG, "mediaPlayer." + methodName + "() called in an invalid state: STATE = " + state);
    }
}
