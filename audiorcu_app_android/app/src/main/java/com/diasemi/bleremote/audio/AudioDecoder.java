/*
 *******************************************************************************
 *
 * Copyright (C) 2017-2018 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.bleremote.audio;

import android.util.Log;

import com.diasemi.bleremote.Constants;

import java.util.Arrays;

/**
 * IMA ADPCM decoder.
 *
 * This IMA ADPCM has been implemented based on the paper spec of IMA
 * Digital Audio Focus and Technical Working Groups, Recommended Practices
 * for Enhancing Digital Audio Compatibility Revision 3.00, 21 October 1992.
 * http://www.cs.columbia.edu/~hgs/audio/dvi/IMA_ADPCM.pdf
 *
 * There are some enhancement/modifications.
 * 1) Encoder: The divisor is calculated with full precision by upshifting
 *    both the stepSize and Difference by 3.
 * 2) The new predicted difference is calculated with full precision by
 *    doing the mult/shift-add 3 bits more.
 * 3) The exact same prediction is used in Encoder and Decoder. Note that
 *    some implementations of IMA-ADPCM do not do this.
 * 4) There is an alternative (but bit-true) calculation of the prediction
 *    difference using a multiplier instead of shift add. May be faster.
 */
public class AudioDecoder {

    private static final String TAG = "AudioDecoder";

    // Constant tables used by IMA.
    private static final int[] indexTable =
    {
            -1, -1, -1, -1, 2, 4, 6, 8,
            -1, -1, -1, -1, 2, 4, 6, 8
    };

    private static final int[] stepSizeTable =
    {
            7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55,
            60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279,
            307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166,
            1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026,
            4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635,
            13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
    };

    // Upsample filter
    private static final int[] FilterCoefs =
    {
            -368, -404, 558, 2116, 2484, 806, -1044, -639, 1245, 1318,
            -1025, -2036, 651, 3109, 257, -4890, -2666, 10146, 23150,
            23150, 10146, -2666, -4890, 257, 3109, 651, -2036, -1025,
            1318, 1245, -639, -1044, 806, 2484, 2116, 558, -404, -368
    };

    // Decoder config
    private int mode = 0;
    private int imaOr = 0;
    private int imaSize = 4;
    private boolean upSample = false;
    private boolean usePartialSamples = false;
    // Decoder state
    private int imaIndex = 0;
    private short imaPredictedSample = 0;
    private int inputBuffer = 0; // current input buffer (partial samples stored here)
    private int inputBits = 0; // number of bits in inputBuffer
    private short[] filterTaps = new short[FilterCoefs.length / 2];

    public AudioDecoder() {
        setMode(0); // Default mode
    }

    void reset() {
        Log.d(TAG, "Reset decoder");
        imaIndex = 0;
        imaPredictedSample = 0;
        inputBuffer = 0;
        inputBits = 0;
        Arrays.fill(filterTaps, (short) 0);
    }

    /*
     * Set the Data Rate
     * 0: 64 Kbit/s = ima 4bps, 16 KHz.
     * 1: 48 Kbit/s = ima 3bps, 16 KHz.
     * 2: 32 Kbit/s = ima 4bps, 8 KHz (downsample).
     * 3: 24 Kbit/s = ima 3bps, 8 KHz (downsample).
     */
    public void setMode(final int mode) {
        switch (mode) {
            case Constants.AUDIO_MODE_64KBPS:
                imaOr = 0;
                imaSize = 4;
                upSample = false;
                break;
            case Constants.AUDIO_MODE_48KBPS:
                imaOr = 1;
                imaSize = 3;
                upSample = false;
                break;
            case Constants.AUDIO_MODE_32KBPS:
                imaOr = 0;
                imaSize = 4;
                upSample = true;
                break;
            case Constants.AUDIO_MODE_24KBPS:
                imaOr = 1;
                imaSize = 3;
                upSample = true;
                break;
            default:
                Log.e(TAG, "Unsupported mode: " + mode);
                return;
        }
        if (this.mode != mode) {
            inputBuffer = 0;
            inputBits = 0;
        }
        this.mode = mode;
        Log.d(TAG, "Decoder mode: " + mode + ", imaSize=" + imaSize + ", upsample=" + upSample);
    }

    public int getMode() {
        return mode;
    }

    public void setUsePartialSamples(boolean usePartialSamples) {
        if (this.usePartialSamples != usePartialSamples) {
            inputBuffer = 0;
            inputBits = 0;
        }
        this.usePartialSamples = usePartialSamples;
    }

    public boolean getUsePartialSamples() {
        return usePartialSamples;
    }

    /**
     * Process the IMA-ADPCM encoded bytes
     *
     * @param inputBytes: input byte array.
     */
    public short[] process(final byte[] inputBytes) {
        int samples = (inputBytes.length * 8 + inputBits) / imaSize;
        short[] outSamples;
        if (upSample) {
            outSamples = new short[samples * 2];
            short[] tmpSamples = new short[samples];
            decodeImaAdpcm(inputBytes, tmpSamples);
            upSample(tmpSamples, outSamples);
        } else {
            outSamples = new short[samples];
            decodeImaAdpcm(inputBytes, outSamples);
            // Keep most recent output for upsample filter (to use if mode changes)
            int copy = Math.min(samples, filterTaps.length);
            System.arraycopy(filterTaps, copy, filterTaps, 0, filterTaps.length - copy);
            System.arraycopy(outSamples, samples - copy, filterTaps, filterTaps.length - copy, copy);
        }
        return outSamples;
    }

    /**
     * decodeImaAdpcm decodes a block of len ima-bytes to len outSamples. The
     * IMA decoder supports 2 different rates: 4 bits/sample, 3 bits/sample.
     *
     * @param inputBytes: input byte array.
     * @param outSamples: output short array.
     */
    private void decodeImaAdpcm(final byte[] inputBytes, final short[] outSamples) {
        int predictedSample = imaPredictedSample;
        int index = imaIndex;
        int shift = 4 - imaSize;
        int current = 0;

        for (int i = 0; i < outSamples.length; i++) {
            int stepSize = stepSizeTable[index]; // Find new quantizer stepsize
            // Get current input bits
            if (inputBits < 8) {
                inputBuffer = inputBuffer << 8 & 0xFFFF;
                if (current < inputBytes.length) {
                    inputBuffer |= inputBytes[current++] & 0x00FF;
                }
                inputBits += 8;
            }
            inputBits -= imaSize;
            int inp = (((inputBuffer >> inputBits) << shift) | imaOr) & 0x0F ;
            // Compute predicted sample estimate
            // This part is different from regular IMA-ADPCM
            // The prediction is calculated with full precision
            int diff = stepSize;
            if ((inp & 4) != 0) {
                diff += stepSize << 3;
            }
            if ((inp & 2) != 0) {
                diff += stepSize << 2;
            }
            if ((inp & 1) != 0) {
                diff += stepSize << 1;
            }
            diff >>= 3;
            // Adjust predictedSample based on calculated difference
            if ((inp & 8) != 0) {
                predictedSample -= diff;
            } else {
                predictedSample += diff;
            }
            // Saturate if there is overflow
            if (predictedSample > 32767) {
                predictedSample = 32767;
            }
            if (predictedSample < -32768) {
                predictedSample = -32768;
            }
            // 16-bit output sample can be stored at this point
            outSamples[i] = (short) predictedSample;
            // Compute new stepsize
            // Adjust index into stepSizeTable using newIMA
            index += indexTable[inp];
            if (index < 0) {
                index = 0;
            }
            if (index > 88) {
                index = 88;
            }
        }

        if (usePartialSamples) {
            inputBits -= 8;
            inputBuffer >>= 8;
        } else {
            inputBits = 0;
            inputBuffer = 0;
        }
        imaIndex = index;
        imaPredictedSample = (short) predictedSample;
    }

    /**
     * @param inputSamples: len input samples
     * @param outSamples: len*2 output samples
     */
    private void upSample(final short[] inputSamples, final short[] outSamples) {
        for (int i = 0; i < inputSamples.length; i++) {
            int acc1 = 0;
            int acc2 = 0;
            for (int j = 0; j < filterTaps.length - 1; j++) {
                acc1 += FilterCoefs[2 * j] * filterTaps[j];
                acc2 += FilterCoefs[2 * j + 1] * filterTaps[j];
                filterTaps[j] = filterTaps[j + 1];
            }
            filterTaps[filterTaps.length - 1] = inputSamples[i];
            acc1 += FilterCoefs[FilterCoefs.length - 2] * inputSamples[i];
            acc2 += FilterCoefs[FilterCoefs.length - 1] * inputSamples[i];
            outSamples[2 * i] = rndSat(acc2 / 32768);
            outSamples[2 * i + 1] = rndSat(acc1 / 32768);
        }
    }

    private short rndSat(int acc) {
        if (acc > 32767) {
            acc = 32767;
        } else if (acc < -32768) {
            acc = -32768;
        }
        return (short) acc;
    }
}
