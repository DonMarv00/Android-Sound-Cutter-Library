package com.citroncode.soundcutter.utils;


public class Utility {

    //audio format in which file after trim will be saved.
    public static final String AUDIO_FORMAT = ".wav";

    //audio mime type in which file after trim will be saved.
    public static final String AUDIO_MIME_TYPE = "audio/wav";

    public static long getCurrentTime() {
        return System.nanoTime() / 1000000;
    }

}