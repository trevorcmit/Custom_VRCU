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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * WavFile
 * Simple wav file read/write. Simplified for 16Khz Mono PCM.
 * https://ccrma.stanford.edu/courses/422/projects/WaveFormat/
 */
public class WavFile {

    private static final String TAG = "WavFile";

    @SuppressWarnings("resource")
    private static void closeWaveHeader(final String fileName, final int nBytes) {
        ByteBuffer chunkSize = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        try {
            RandomAccessFile f = new RandomAccessFile(fileName, "rw");
            f.seek(4);
            chunkSize.putInt(0, nBytes + 36);
            f.write(chunkSize.array());
            f.seek(40);
            chunkSize.putInt(0, nBytes);
            f.write(chunkSize.array());
            f.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static byte[] getWavHeader() {
        ByteBuffer hdr = ByteBuffer.allocate(44);
        hdr.putInt(0x52494646); // RIFF
        hdr.putInt(0x00000000); // ChunkSize= nbytes+36 -> should be modified later..
        hdr.putInt(0x57415645); // WAVE
        hdr.putInt(0x666d7420); // fmt
        hdr.putInt(0x10000000); // Subchunksize=16
        hdr.putInt(0x01000100); // PCM, Mono
        hdr.putInt(0x803E0000); // Samplerate=16000 hz
        hdr.putInt(0x007D0000); // ByteRate=2*SampleRate*NChannels
        hdr.putInt(0x02001000); // BlockAlign, BitsPerSample,
        hdr.putInt(0x64617461); // data
        hdr.putInt(0x00000000); // SubChunk2size = nbytes -> should be modified later..
        return (hdr.array());
    }

    private String mCurrentFileName = null;
    private int mNbytes = 0;
    private FileOutputStream mWAVOutStream = null;
    private FileInputStream mWAVInStream = null;

    /**
     * Constructor
     */
    public WavFile() {
        //
    }

    public void close() {
        if (mWAVOutStream != null) {
            try {
                mWAVOutStream.close();
                closeWaveHeader(mCurrentFileName, mNbytes);
                Log.i(TAG, "Close wav file, nbytes is  " + mNbytes);
                mCurrentFileName = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
            mWAVOutStream = null;
        }
        if (mWAVInStream != null) {
            try {
                mWAVInStream.close();
                mCurrentFileName = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
            mWAVInStream = null;
        }
        mCurrentFileName = null;
    }

    public void openr(final String fileName) {
        if (fileName == null) {
            return;
        }
        try {
            if (mCurrentFileName != null) {
                close();
            }
            mCurrentFileName = fileName;
            mNbytes = 0; // Clear the buffer...
            mWAVInStream = new FileInputStream(fileName);
            byte[] wavHdr = new byte[44];
            mWAVInStream.read(wavHdr, 0, 44);
            Log.i(TAG, "Opened file for reading " + mCurrentFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void openw(final String fileName) {
        try {
            if (mCurrentFileName != null) {
                close();
            }
            mCurrentFileName = fileName;
            mNbytes = 0; // Clear the buffer...
            mWAVOutStream = new FileOutputStream(mCurrentFileName);
            byte[] wavHdr = getWavHeader();
            mWAVOutStream.write(wavHdr, 0, 44);
            Log.i(TAG, "Opened file for writing " + mCurrentFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int read(final byte[] data, final int length) {
        int res = 0;
        if (mWAVInStream != null) {
            try {
                res = mWAVInStream.read(data, 0, length);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return res;
    }

    public void write(final short[] values) {
        if (mWAVOutStream == null) {
            return;
        }
        ByteBuffer wavBuffer = ByteBuffer.allocate(values.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        wavBuffer.asShortBuffer().put(values);
        mNbytes += 2 * values.length;
        try {
            mWAVOutStream.write(wavBuffer.array());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}