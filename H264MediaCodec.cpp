#include <string.h>
#include <time.h>
#include <stdio.h>
#include "H264MediaCodec.h"

#ifndef WIN32

#define TIMEOUT_US 0

#define SAFE_DELETE(x) { if(x){ delete x; x = NULL;}}
#define SAFE_DELETE_ARRAY(x) { if(x){ delete[] x; x = NULL;}}

unsigned int timeGetTime() {
	unsigned int uptime = 0;
	struct timespec on;
	//if (clock_gettime(CLOCK_THREAD_CPUTIME_ID, &on) == 0)
	if (clock_gettime(CLOCK_MONOTONIC, &on) == 0)
		uptime = on.tv_sec * 1000 + on.tv_nsec / 1000000;
	return uptime;
}

H264MediaCodec::H264MediaCodec()
{
}


H264MediaCodec::~H264MediaCodec()
{
}

void H264MediaCodec::InitCodec(int height , int width, int framerate ,int bitrate)
{
    m_height = height;
    m_width = width;
	const char* mime = "video/avc";
	//编码器
	m_encoder = AMediaCodec_createEncoderByType(mime);
	if (m_encoder == NULL)
	{
		LOGE("MediaCodecH264: could not create Encoder");
	}
	AMediaFormat *m_format = AMediaFormat_new();
	AMediaFormat_setString(m_format, AMEDIAFORMAT_KEY_MIME, "video/avc");
	AMediaFormat_setInt32(m_format, AMEDIAFORMAT_KEY_WIDTH, m_width);
	AMediaFormat_setInt32(m_format, AMEDIAFORMAT_KEY_HEIGHT, m_height);

	//bitrate = 500000;
	//framerate = 30;
	AMediaFormat_setInt32(m_format, AMEDIAFORMAT_KEY_BIT_RATE, bitrate);
	AMediaFormat_setInt32(m_format, AMEDIAFORMAT_KEY_FRAME_RATE, framerate);
	AMediaFormat_setInt32(m_format, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, framerate);
	AMediaFormat_setInt32(m_format, AMEDIAFORMAT_KEY_COLOR_FORMAT, 21);
	media_status_t status = AMediaCodec_configure(m_encoder, m_format, NULL, NULL, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
	if (status != 0)
	{
		LOGE("AMediaCodec_configure() failed with error %i for format %u", (int)status, 21);
	}
	else
	{
		if ((status = AMediaCodec_start(m_encoder)) != AMEDIA_OK)
		{
			LOGE("AMediaCodec_start: Could not start encoder.");
		}
		else
		{
			LOGD("AMediaCodec_start: encoder successfully started");
		}
	}
	AMediaFormat_delete(m_format);

	//解码器
	m_decoder = AMediaCodec_createDecoderByType(mime);
	if (m_decoder == NULL)
	{
		LOGE("MediaCodecH264: could not create Decoder");
	}
	else
	{
		AMediaFormat *m_format2 = AMediaFormat_new();
		AMediaFormat_setString(m_format2, AMEDIAFORMAT_KEY_MIME, "video/avc");
		AMediaFormat_setInt32(m_format2, AMEDIAFORMAT_KEY_WIDTH, m_width);
		AMediaFormat_setInt32(m_format2, AMEDIAFORMAT_KEY_HEIGHT, m_height);
		AMediaFormat_setInt32(m_format2, AMEDIAFORMAT_KEY_COLOR_FORMAT, 21);
		//AMediaFormat_setBuffer(m_format2, "csd-0",sps, sizeof(sps));
		//AMediaFormat_setBuffer(m_format2, "csd-1",pps, sizeof(pps));

		if ((status = AMediaCodec_configure(m_decoder, m_format2, NULL, NULL, 0)) != AMEDIA_OK)
        {
			LOGD("MediaCodecH264Dec: configuration failure: %i", (int)status);
		}

		if ((status = AMediaCodec_start(m_decoder)) != AMEDIA_OK) {
			LOGD("MediaCodecH264Dec: starting failure: %i", (int)status);
		}
		AMediaFormat_delete(m_format2);
	}

}

void H264MediaCodec::Release()
{

}


void H264MediaCodec::Encode(byte* inputNV12)
{
	ssize_t ibufidx, obufidx;
	AMediaCodecBufferInfo info;
	size_t bufsize;

	/*First queue input image*/
	uint8_t *buf;

	ibufidx = AMediaCodec_dequeueInputBuffer(m_encoder, TIMEOUT_US);
	if (ibufidx >= 0)
	{
		buf = AMediaCodec_getInputBuffer(m_encoder, ibufidx, &bufsize);
		if (buf)
		{
			memcpy(buf, inputNV12, m_YUVSize);
			auto curTime = timeGetTime();
			AMediaCodec_queueInputBuffer(m_encoder, ibufidx, 0, bufsize, curTime, 0);
		}
		else
		{
			LOGD("MediaCodecH264Enc: obtained InputBuffer, but no address.");
		}
	}
	else if (ibufidx == AMEDIA_ERROR_UNKNOWN)
	{
		LOGD("MediaCodecH264Enc: AMediaCodec_dequeueInputBuffer() had an exception");
	}
	//int pos = 0;
	/*Second, dequeue possibly pending encoded frames*/
	while ((obufidx = AMediaCodec_dequeueOutputBuffer(m_encoder, &info, TIMEOUT_US)) >= 0)
	{
		auto oBuf = AMediaCodec_getOutputBuffer(m_encoder, obufidx, &bufsize);
		if (oBuf)
		{
			if (m_info == NULL)
			{
				m_infoSize = info.size;
				m_info = new byte[m_infoSize];
				if (info.flags == 2)	//head frame
				{
					memcpy(m_info, oBuf, m_infoSize);
					LOGD("obBuf %d %d flag:%d offest:%d size:%d", m_infoSize, bufsize, info.flags, info.offset, info.size);
					char str[256] = { 0 };
					for (int i = 0; i < m_infoSize; ++i)
					{
						sprintf(str, "%s %d", str, m_info[i]);
					}
					LOGD("obBuf %s", str);
					//pos += m_infoSize;
					continue;
				}
				else
				{
					LOGD("errorrr");
					return;
				}
			}
			LOGD("m_infoSize %d %d flag:%d offest:%d size:%d", m_infoSize, bufsize, info.flags, info.offset, info.size);

			H264Data *data = new H264Data();
            m_H264DataList.push_back(data);
			data->flag = info.flags;
			if (info.flags == 1)   //key frame
			{
				data->dataPtr = new byte[bufsize + m_infoSize];
				memcpy(data->dataPtr, m_info, m_infoSize);
				memcpy(data->dataPtr + m_infoSize, oBuf, bufsize);
				data->size = bufsize + m_infoSize;
			}
			else
			{
				data->dataPtr = new byte[bufsize];
				memcpy(data->dataPtr, oBuf, bufsize);
				data->size = bufsize;
			}
			LOGD("Out finish");
		}
		AMediaCodec_releaseOutputBuffer(m_encoder, obufidx, false);
	}

	if (obufidx == AMEDIA_ERROR_UNKNOWN)
	{
		LOGD("MediaCodecH264Enc: AMediaCodec_dequeueOutputBuffer() had an exception, H264MediaCodec is lost");
		AMediaCodec_stop(m_encoder);
		AMediaCodec_delete(m_encoder);
	}

}

void H264MediaCodec::Decode()
{
	if (m_decoder == NULL)
	{
		return;
	}
	ssize_t oBufidx = -1;
	size_t bufsize = 0;
	AMediaCodecBufferInfo info;

	uint8_t *buf = NULL;
	ssize_t iBufidx = -1;

	/*First put our H264 bitstream into the decoder*/
	while (!m_H264DataList.empty())
	{
		iBufidx = AMediaCodec_dequeueInputBuffer(m_decoder, TIMEOUT_US);
		LOGD("decoder iBufidx %d %d", iBufidx, m_H264DataList.size());
		if (iBufidx >= 0)
		{
			buf = AMediaCodec_getInputBuffer(m_decoder, iBufidx, &bufsize);
			int bufsize = 0;
			auto iter = m_H264DataList.begin();
			char str[512] = { 0 };
			for (int i = 0; i < 100; ++i)
			{
				sprintf(str, "%s %d", str, *((*iter)->dataPtr + i));
			}
			LOGD("obBuf after %s", str);
			if (buf)
			{
				bufsize = (*iter)->size;
				memcpy(buf, (*iter)->dataPtr, bufsize);
			}

			AMediaCodec_queueInputBuffer(m_decoder, iBufidx, 0, bufsize, timeGetTime(), 0);
			SAFE_DELETE_ARRAY((*iter)->dataPtr);
            m_H264DataList.erase(iter);
		}
		else if (iBufidx == -1)
		{
			/*
			* This is a problematic case because we can't wait the decoder to be ready, otherwise we'll freeze the entire
			* video thread.
			* We have no other option to drop the frame, and retry later, but with an I-frame of course.
			**/
			break;
		}
	}

	/*secondly try to get decoded frames from the decoder, this is performed every tick*/
	oBufidx = AMediaCodec_dequeueOutputBuffer(m_decoder, &info, TIMEOUT_US);
	LOGD("Decoder oBufidx %d", oBufidx);
	while (oBufidx >= 0)
	{
		AMediaFormat *format;
		int color = 0;

		uint8_t *buf = AMediaCodec_getOutputBuffer(m_decoder, oBufidx, &bufsize);

		if (buf == NULL)
		{
			LOGD("MediaCodecH264Dec: AMediaCodec_getOutputBuffer() returned NULL");
			//continue;
		}
		else
		{
			int width = 0, height = 0;
			format = AMediaCodec_getOutputFormat(m_decoder);
			if (format != NULL)
			{
				AMediaFormat_getInt32(format, "width", &width);
				AMediaFormat_getInt32(format, "height", &height);
				AMediaFormat_getInt32(format, "color-format", &color);

				AMediaFormat_delete(format);
			}
			if (width != 0 && height != 0)
			{
				if (color == 21)
				{
					LOGD("12121212");
					//NV12
					byte* outNV12 = new byte[m_YUVSize];
					memcpy(outNV12, buf, m_YUVSize);
					m_outputNV12List.push_back(outNV12);
				}
				else
				{
					LOGD("unknown format");
				}
			}
			else
			{
				LOGD("MediaCodecH264Dec: width and height are not known !");
			}
		}
		AMediaCodec_releaseOutputBuffer(m_decoder, oBufidx, false);

		oBufidx = AMediaCodec_dequeueOutputBuffer(m_decoder, &info, TIMEOUT_US);
		LOGD("Decoder oBufidx %d", oBufidx);
	}

	if (oBufidx == AMEDIA_ERROR_UNKNOWN)
	{
		LOGD("MediaCodecH264Dec: AMediaCodec_dequeueOutputBuffer() had an exception");
	}
}

#endif // !WIN32
