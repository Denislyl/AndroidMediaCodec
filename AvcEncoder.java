package com.avc.demo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

@SuppressLint("NewApi") public class AvcEncoder {
	private MediaCodec mediaCodec;

	int m_width;
	int m_height;
	//boolean RecordEncDataFlag = true;
	boolean RecordEncDataFlag = false;
	byte[] m_info = null;
	FileOutputStream FileOut = null; 

	private byte[] yuv420 = null; 
	public AvcEncoder(int width, int height, int framerate, int bitrate) { 
		Log.d("Codec", "AvcEncoder IN");
		m_width  = width;
		m_height = height;
		yuv420 = new byte[width*height*3/2];

		getSupportColorFormat();
		try {
			mediaCodec = MediaCodec.createEncoderByType("video/avc");
		} catch (IOException e) {
			e.printStackTrace();
		}
		MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);
	    mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
	    mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
	    mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
	    mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 30);
	    
	    mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
	    mediaCodec.start();
	    if(RecordEncDataFlag)
	    {
	    	try {
	    		FileOut = new FileOutputStream(new File("/sdcard/app_camera_enc.h264"));
	    	} catch (FileNotFoundException e) {
	    		// TODO Auto-generated catch block
	    		e.printStackTrace();
	    	}
	    }
	}

	private int getSupportColorFormat() {
		int numCodecs = MediaCodecList.getCodecCount();
		MediaCodecInfo codecInfo = null;
		for (int i = 0; i < numCodecs && codecInfo == null; i++) {
			MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
			if (!info.isEncoder()) {
				continue;
			}
			String[] types = info.getSupportedTypes();
			boolean found = false;
			for (int j = 0; j < types.length && !found; j++) {
				if (types[j].equals("video/avc")) {
					System.out.println("found");
					found = true;
				}
			}
			if (!found)
				continue;
			codecInfo = info;
		}

		Log.e("AvcEncoder", "Found " + codecInfo.getName() + " supporting " + "video/avc");

		// Find a color profile that the codec supports
		MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType("video/avc");
		Log.e("AvcEncoder",
				"length-" + capabilities.colorFormats.length + "==" + Arrays.toString(capabilities.colorFormats));

		for (int i = 0; i < capabilities.colorFormats.length; i++) {

			switch (capabilities.colorFormats[i]) {
				case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
				case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
				case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible:
					Log.e("AvcEncoder", "supported color format::" + capabilities.colorFormats[i]);
					break;//return capabilities.colorFormats[i];
				default:
					Log.e("AvcEncoder", "other color format " + capabilities.colorFormats[i]);
					break;
			}
		}

		return 0;
	}
	
	public void close() {
	    try {
	        mediaCodec.stop();
	        mediaCodec.release();
	    } catch (Exception e){ 
	        e.printStackTrace();
	    }
	    
		try {
			FileOut.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public int offerEncoder(byte[] input, byte[] output) 
	{	
		Log.d("......................................................Codec", "Encoder in");
		int pos = 0;
		yuv420= input;
	    try {
	        ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
	        ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
	        int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
	        Log.d("......................................................Codec", "inputBufferIndex = " +inputBufferIndex);
	        if (inputBufferIndex >= 0)
	        {
	            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
	            inputBuffer.clear();
	            inputBuffer.put(yuv420);
	            mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, 0, 0);
	        }

	        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
	        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,0);
	        Log.d("......................................................Codec", "outputBufferIndex = " +outputBufferIndex);
	        while (outputBufferIndex >= 0) 
	        {
	            ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
	            byte[] outData = new byte[bufferInfo.size];
	            outputBuffer.get(outData);
	            
	            if(m_info != null)
	            {            	
	            	System.arraycopy(outData, 0,  output, 0, outData.length);
	 	            pos += outData.length;
					Log.d("Encoder", "m_info: " + pos);
	            }
	            else
	            {
	            	 ByteBuffer spsPpsBuffer = ByteBuffer.wrap(outData);  
	                 //if (spsPpsBuffer.getInt() == 0x00000001)
					if(bufferInfo.flags == 2)
	                 {  
	                	 m_info = new byte[outData.length];
	                	 System.arraycopy(outData, 0, m_info, 0, outData.length);
	                	 System.arraycopy(outData, 0, output, pos, outData.length);
	                	 pos+=outData.length;
	                 } 
	                 else 
	                 {
						 Log.d("Encoder", "errrrr: ");
	                        return -1;
	                 }
					Log.d("Encoder", "m_info: " + Arrays.toString(m_info));
	            }

	            mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
	            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
	        }

	        if(bufferInfo.flags == 1)// if( nv12[4] == 0x65) //key frame
	        {
	        	Log.d("Encoder", "Key frame");
	        	System.arraycopy(output, 0,  yuv420, 0, pos);
	        	System.arraycopy(m_info, 0,  output, 0, m_info.length);
	        	System.arraycopy(yuv420, 0,  output, m_info.length, pos);
	        	pos += m_info.length;
	        }
	        
	    } catch (Throwable t) {
	        t.printStackTrace();
	    }
	    //Log.d("......................................................Codec", "Encoder out");
	    
//		if(RecordEncDataFlag)
//		{
//			try {
//				FileOut.write(nv12,0,pos);
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
	    return pos;
	}
}
