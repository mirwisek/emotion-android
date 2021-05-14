package com.speech.recognizer;

public interface OnAudioRecordedListener {
    void onComplete();
    void onError(Exception ex);
}
