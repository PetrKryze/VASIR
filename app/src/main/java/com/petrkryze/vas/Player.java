package com.petrkryze.vas;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;

import java.io.IOException;

/**
 * Created by Petr on 07.02.2020. Yay!
 */

public class Player {
    // private final String TAG = "Player";
    public static final int SAVED_PROGRESS_DEFAULT = -1;

    private MediaPlayer mediaPlayer;
    private final AudioManager audioManager;

    private Recording recording = null;
    private final PlayerListener listener;

    private boolean isPrepared = false;
    private boolean isSeeking = false;

    private final Handler handler;
    private int lastTick = 0;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null) {
                int current_ms = mediaPlayer.getCurrentPosition();
                int current = (int) Math.floor((double) current_ms / 1000);
                if (lastTick != current) {
                    listener.onTimeTick(mediaPlayer.getDuration() - current_ms);
                    lastTick = current;
                }
                listener.onUpdateProgress(current_ms);
                if (mediaPlayer.isPlaying()) {
                    handler.postDelayed(tick, 250);
                }
            }
        }
    };

    public interface PlayerListener {
        void onTrackPrepared(int duration);
        void onTrackFinished();
        void onHeadphonesMissing();
        void onVolumeDown();
        void onTimeTick(int tick_ms);
        void onUpdateProgress(int current_ms);
    }

    public Player(Context context, final PlayerListener listener) {
        this.listener = listener;
        this.mediaPlayer = new MediaPlayer();

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        mediaPlayer.setAudioAttributes(attributes);
        mediaPlayer.setOnPreparedListener(mp -> {
            Player.this.listener.onTrackPrepared(mediaPlayer.getDuration());
            isPrepared = true;
        });
        mediaPlayer.setOnCompletionListener(mp -> listener.onTrackFinished());
        mediaPlayer.setOnSeekCompleteListener(mp -> {
            isSeeking = false;
            doTick();
        });
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        HandlerThread handlerThread = new HandlerThread("ElapsedTime");
        handlerThread.start();
        this.handler = new Handler(handlerThread.getLooper());
    }

    public boolean isPrepared() {
        return isPrepared;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isSeeking() {
        return isSeeking;
    }

    public boolean isPlaying() {
        if (mediaPlayer != null) {
            return mediaPlayer.isPlaying();
        } else {
            return false;
        }
    }

    public void setCurrentTrack(Recording recording) throws IOException {
        this.recording = recording;
        mediaPlayer.reset();
        isPrepared = false;
        mediaPlayer.setDataSource(this.recording.getPath());
        mediaPlayer.prepareAsync();
    }

    public boolean play() {
        if (isHeadphonesIn() && !isVolumeZero() && isPrepared) {
            mediaPlayer.start();
            doTick();
            return true;
        }

        // Returns true if play start was successful
        return false;
    }

    public void pause() {
        mediaPlayer.pause();
    }

    public void rewind() {
        seekTo(0);
    }

    public void seekTo(int target) {
        isSeeking = true;
        mediaPlayer.seekTo(target);
    }

    public void doTick() {
        handler.post(tick);
    }

    public void clean() {
        this.recording = null;
        isPrepared = false;

        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
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
}
