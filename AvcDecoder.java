package com.avc.demo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Vector;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.SurfaceHolder;

@SuppressLint("NewApi")
public class AvcDecoder {
    private MediaCodec mediaCodec;
    int mCount = 0;
    Vector mVector;
    byte[] nv12;

    public AvcDecoder(int width, int height, SurfaceHolder surfaceHolder) {
        Log.d("Codec", "AvcDecoder IN");
        try {
            mediaCodec = MediaCodec.createDecoderByType("video/avc");
            MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);
            nv12 = new byte[width * height * 3 / 2];
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
            //mediaCodec.configure(mediaFormat, surfaceHolder.getSurface(), null, 0);
            mediaCodec.configure(mediaFormat, null, null, 0);
            mediaCodec.start();
            MyThread mThread = new MyThread();
            mVector = new Vector(50);
            mThread.start();
            Log.d("Codec", "AvcDecoder OUT");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    public void pushData(byte[] buf, int length) {
        if (length > 0) {
            MyStruct mMyStruct = new MyStruct();
            mMyStruct.h264data = new byte[length];
            System.arraycopy(buf, 0, mMyStruct.h264data, 0, length);
            mMyStruct.size = length;
            mVector.add(mMyStruct);
            byte[] str = new byte[100];
            System.arraycopy(mMyStruct.h264data, 0, str, 0, 100);
            Log.d("Decoder", "pos:  " + mMyStruct.size + "m_info: " + Arrays.toString(str));
        }
    }


    public void onFrame(byte[] buf, int length) {
        ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
        int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
        Log.d("Decoder", "inputBufferIndex:  " + inputBufferIndex);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(buf, 0, length);
            mediaCodec.queueInputBuffer(inputBufferIndex, 0, length, mCount * 1000000, 0);
            mCount++;
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        while (outputBufferIndex >= 0) {
            outputBuffers[outputBufferIndex].get(nv12 , 0 , nv12.length);
            CallbackAdapt.UpdateH264Decode(nv12, outputBufferIndex);
            mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        }

    }

    public void close() {
        try {
            mediaCodec.stop();
            mediaCodec.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    class MyThread extends Thread {

        public void run() {
            while (true) {
                //Log.d("Codec", "Codec thread start!");
                if (mVector.size() >= 1) {
                    MyStruct mMyStruct = (MyStruct) mVector.get(0);
                    onFrame(mMyStruct.h264data, mMyStruct.size);
                    mVector.remove(0);
                } else {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        }
    }


}
