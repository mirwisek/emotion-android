package com.speech.recognizer;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AudioRecorder {

    static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC; // for raw audio, use MediaRecorder.AudioSource.UNPROCESSED, see note in MediaRecorder section
    static final int SAMPLE_RATE = 16_000;
    static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    static final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    static final int BUFFER_SIZE_RECORDING = 2 * AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_ENCODING);
    static final String TAG = "ffnet";
    boolean continueRecording = false;
    private final String outputFile;
    private Thread recordingThread;
    // Since we are post processing meta-data of WAV file, so on stopping recording doesn't mean it's
    // a go for uploading, so we are using a recording listener to call when recording file is ready
    private OnAudioRecordedListener recordedListener;

    protected AudioRecord audioRecord;

    public AudioRecorder(String outputFile, OnAudioRecordedListener recordedListener) {
        this.outputFile = outputFile;
        this.recordedListener = recordedListener;
    }

    public synchronized void startRecording() {
        if(recordingThread != null)
            return;

        continueRecording = true;
        recordingThread = new Thread(() -> {
            try {
                writeAudioData();
            } catch (IOException e) {
                Log.e(TAG, "startRecording: " + e.getMessage(), e);
                e.printStackTrace();
            }
        });
        recordingThread.start();

    }

    public void writeAudioData() throws IOException { // to be called in a Runnable for a Thread created after call to startRecording()


        // assign size so that bytes are read in in chunks inferior to AudioRecord internal buffer size
        byte[] data = new byte[BUFFER_SIZE_RECORDING];
//        byte[] data = new byte[BUFFER_SIZE_RECORDING/2];

        audioRecord = new AudioRecord(AUDIO_SOURCE, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_ENCODING, BUFFER_SIZE_RECORDING);

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) { // check for proper initialization
            Log.e(TAG, "error initializing");
            return;
        }

        audioRecord.startRecording();
        // fileName is path to a file, where audio data should be written
        try(FileOutputStream outputStream = new FileOutputStream(outputFile)) {

            // Write out the wav file header
            writeWavHeader(outputStream, SAMPLE_RATE, AUDIO_ENCODING);

            int read;
            while (continueRecording) { // continueRecording can be toggled by a button press, handled by the main (UI) thread
                read = audioRecord.read(data, 0, data.length);

                try {
                    outputStream.write(data, 0, read);
                }
                catch (IOException e) {
                    Log.d(TAG, "exception while writing to file");
                    e.printStackTrace();
                }
            }

            try {
                outputStream.flush();
                outputStream.close();
            }
            catch (IOException e) {
                Log.d(TAG, "exception while closing output stream " + e.toString());
                e.printStackTrace();
            }

            // Clean up
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        } catch (IOException e) {
            // handle error
            Log.e(TAG, "writeAudioData: File Exception", e);
        }

        try {
            // This is not put in the try/catch/finally above since it needs to run
            // after we close the FileOutputStream
            updateWavHeader(new File(outputFile));
            recordedListener.onComplete();
        } catch (IOException ex) {
            recordedListener.onError(ex);
            throw new IOException("Error updating Wav File header, the recording file couldn't be opened");
        }
    }

    private static void writeWavHeader(OutputStream out, int sampleRate, int encoding) throws IOException {
        short channels;
        // We are not passing Channel as argument rather taking global value
        switch (CHANNEL_CONFIG) {
            case AudioFormat.CHANNEL_IN_MONO:
                channels = 1;
                break;
            case AudioFormat.CHANNEL_IN_STEREO:
                channels = 2;
                break;
            default:
                throw new IllegalArgumentException("Unacceptable channel mask");
        }

        short bitDepth;
        switch (encoding) {
            case AudioFormat.ENCODING_PCM_8BIT:
                bitDepth = 8;
                break;
            case AudioFormat.ENCODING_PCM_16BIT:
                bitDepth = 16;
                break;
            case AudioFormat.ENCODING_PCM_FLOAT:
                bitDepth = 32;
                break;
            default:
                throw new IllegalArgumentException("Unacceptable encoding");
        }

        writeWavHeader(out, channels, sampleRate, bitDepth);
    }


    /**
     * Writes the proper 44-byte RIFF/WAVE header to/for the given stream
     * Two size fields are left empty/null since we do not yet know the final stream size
     *
     * @param out        The stream to write the header to
     * @param channels   The number of channels
     * @param sampleRate The sample rate in hertz
     * @param bitDepth   The bit depth
     * @throws IOException
     */
    private static void writeWavHeader(OutputStream out, short channels, int sampleRate, short bitDepth) throws IOException {
        // Convert the multi-byte integers to raw bytes in little endian format as required by the spec
        byte[] littleBytes = ByteBuffer
                .allocate(14)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(channels)
                .putInt(sampleRate)
                .putInt(sampleRate * channels * (bitDepth / 8))
                .putShort((short) (channels * (bitDepth / 8)))
                .putShort(bitDepth)
                .array();

        // Not necessarily the best, but it's very easy to visualize this way
        out.write(new byte[]{
                // RIFF header
                'R', 'I', 'F', 'F', // ChunkID
                0, 0, 0, 0, // ChunkSize (must be updated later)
                'W', 'A', 'V', 'E', // Format
                // fmt subchunk
                'f', 'm', 't', ' ', // Subchunk1ID
                16, 0, 0, 0, // Subchunk1Size
                1, 0, // AudioFormat
                littleBytes[0], littleBytes[1], // NumChannels
                littleBytes[2], littleBytes[3], littleBytes[4], littleBytes[5], // SampleRate
                littleBytes[6], littleBytes[7], littleBytes[8], littleBytes[9], // ByteRate
                littleBytes[10], littleBytes[11], // BlockAlign
                littleBytes[12], littleBytes[13], // BitsPerSample
                // data subchunk
                'd', 'a', 't', 'a', // Subchunk2ID
                0, 0, 0, 0, // Subchunk2Size (must be updated later)
        });
    }


    /**
     * Updates the given wav file's header to include the final chunk sizes
     *
     * @param wav The wav file to update
     * @throws IOException Throws exception when random access file can't be allocated
     */
    private static void updateWavHeader(File wav) throws IOException {
        byte[] sizes = ByteBuffer
                .allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                // There are probably a bunch of different/better ways to calculate
                // these two given your circumstances. Cast should be safe since if the WAV is
                // > 4 GB we've already made a terrible mistake.
                .putInt((int) (wav.length() - 8)) // ChunkSize
                .putInt((int) (wav.length() - 44)) // Subchunk2Size
                .array();

        //noinspection CaughtExceptionImmediatelyRethrown
        try (RandomAccessFile accessWave = new RandomAccessFile(wav, "rw")) {
            // ChunkSize
            accessWave.seek(4);
            accessWave.write(sizes, 0, 4);

            // Subchunk2Size
            accessWave.seek(40);
            accessWave.write(sizes, 4, 4);
        } catch (IOException ex) {
            // Rethrow but we still close accessWave in our finally
            throw ex;
        }
    }


    public synchronized void stopRecording() {
        if(recordingThread == null)
            return;
        continueRecording = false;
        recordingThread = null;
    }

}
