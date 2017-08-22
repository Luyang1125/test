package net.thdev.mediacodecexample.decoder;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;
import android.util.TimingLogger;
import android.view.Surface;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;

public class VideoDecoderThread extends Thread {
	private static final String VIDEO = "h264/";
	private static final String TAG = "VideoDecoder";
	private MediaExtractor mExtractor;
	private MediaCodec mDecoder;
	private long mTimeoutUs = 10000l;

	private static final int qp = 0;
	private static final int seg = 4;

	private static final int width = 2160/seg;
	private static final int height = 1200;
	
	private boolean eosReceived;

	public byte[] readFile(int frame_ind, int seg_ind) {
		if( seg_ind < 0 || seg_ind >= seg ) {
			Log.d(TAG, "Se_ind out of bound: " + seg_ind);
			return null;
		}
		String sb = "/Valkyrie2/qp_" + qp + "_" + seg + "/Valkyrie2_qp_" + qp + "_" + frame_ind + "_" + seg_ind+".pgm";
		//String sb = "/Thelab2/qp_" + qp + "_" + seg + "/Valkyrie2_qp_" + qp + "_" + frame_ind + "_" + seg_ind+".pgm";
		File file = new File(Environment.getExternalStorageDirectory() + sb);
		int size = (int) file.length();
		byte[] bytes = new byte[size];
		int numRead = 0;
		try {
			BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file));
			numRead = buf.read(bytes, 0, bytes.length);
			buf.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		return bytes;
	}

	public byte[] readConfigure() {
		byte[] bytes = readFile(0,0);

		if(bytes== null||bytes.length==0||bytes[0]!=0||bytes[1]!=0||bytes[2]!=0||bytes[3]!=1){
			return null;
		}

		int i = 4;
		while( i+3 < bytes.length ){
			if(bytes[i]==0&&bytes[i+1]==0&&bytes[i+2]==0&&bytes[i+3]==1){
				byte[] slice = Arrays.copyOfRange(bytes, 0, i);
				return slice;
			}
			i++;
		}

		return null;
	}

	public byte[] readStreamData(int frame_ind, int seg_ind) {
		byte[] bytes = readFile(frame_ind, seg_ind);

		if(bytes== null||bytes.length==0||bytes[0]!=0||bytes[1]!=0||bytes[2]!=0||bytes[3]!=1){
			return null;
		}

		if(frame_ind == 0 && seg_ind == 0){
			int i = 4;
			while( i+3 < bytes.length ){
				if(bytes[i]==0&&bytes[i+1]==0&&bytes[i+2]==0&&bytes[i+3]==1){
					byte[] slice = Arrays.copyOfRange(bytes, i, bytes.length);
					return slice;
				}
				i++;
			}
		}

		return bytes;
	}
	
	public boolean init(Surface surface, String filePath) {
		eosReceived = false;
		byte[] buf = readConfigure();
		ByteBuffer bb = ByteBuffer.wrap(buf);
		try {

			MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
			format.setByteBuffer("csd-0", bb);
			mDecoder = MediaCodec.createDecoderByType("video/avc");

			mDecoder.configure(format, surface, null, 0);
			mDecoder.start();
			//mConfigured = true;

		} catch (Exception e) {
			e.printStackTrace();
		}

		return true;
	}



	@Override
	public void run() {

		MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
		TimingLogger timings;
		//while(true) {
			for (int i = 0; i < 100; i++) {
				for(int j = 0; j<seg; j++){
				byte[] buf = readStreamData(i, j);
				timings = new TimingLogger(TAG, "Frame #"+i+"Segment #"+j);
				int inputBufferId = mDecoder.dequeueInputBuffer(mTimeoutUs);
				timings.addSplit("Dequeue Input Buffer");
				if (inputBufferId >= 0) {
					ByteBuffer inputBuffer = mDecoder.getInputBuffer(inputBufferId);

					timings.addSplit("Get Input Buffer");
					// fill inputBuffer with valid data

					if (inputBuffer != null) {
						inputBuffer.put(buf);
						mDecoder.queueInputBuffer(inputBufferId, 0, buf.length, 1000, BUFFER_FLAG_KEY_FRAME);
						timings.addSplit("Queue Input Buffer");
					}
					int outputBufferId = -1;
					while(outputBufferId<=0) {
						outputBufferId =mDecoder.dequeueOutputBuffer(info, 100000000);
					}
					timings.addSplit("Dequeue Output Buffer");
					if (outputBufferId >= 0) {
						ByteBuffer outputBuffer = mDecoder.getOutputBuffer(outputBufferId);
						MediaFormat bufferFormat = mDecoder.getOutputFormat(outputBufferId); // option A
						timings.addSplit("Get Output Buffer");
						// bufferFormat is identical to outputFormat
						// outputBuffer is ready to be processed or rendered.
						mDecoder.releaseOutputBuffer(outputBufferId, true);
						timings.addSplit("Release Output Buffer");
					} else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
						// Subsequent data will conform to new format.
						// Can ignore if using getOutputFormat(outputBufferId)
						continue;
					}
					timings.dumpToLog();
				}

			}
		}
/*
		BufferInfo info = new BufferInfo();
		ByteBuffer[] inputBuffers = mDecoder.getInputBuffers();
		mDecoder.getOutputBuffers();
		
		boolean isInput = true;
		boolean first = false;
		long startWhen = 0;
		
		while (!eosReceived) {
			if (isInput) {
				int inputIndex = mDecoder.dequeueInputBuffer(10000);
				if (inputIndex >= 0) {
					// fill inputBuffers[inputBufferIndex] with valid data
					ByteBuffer inputBuffer = inputBuffers[inputIndex];
					
					int sampleSize = mExtractor.readSampleData(inputBuffer, 0);
					
					if (mExtractor.advance() && sampleSize > 0) {
						mDecoder.queueInputBuffer(inputIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);
						
					} else {
						Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
						mDecoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
						isInput = false;
					}
				}
			}
			
			int outIndex = mDecoder.dequeueOutputBuffer(info, 10000);
			switch (outIndex) {
			case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
				Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
				mDecoder.getOutputBuffers();
				break;
				
			case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
				Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED format : " + mDecoder.getOutputFormat());
				break;
				
			case MediaCodec.INFO_TRY_AGAIN_LATER:
//				Log.d(TAG, "INFO_TRY_AGAIN_LATER");
				break;
				
			default:
				if (!first) {
					startWhen = System.currentTimeMillis();
					first = true;
				}
				try {
					long sleepTime = (info.presentationTimeUs / 1000) - (System.currentTimeMillis() - startWhen);
					Log.d(TAG, "info.presentationTimeUs : " + (info.presentationTimeUs / 1000) + " playTime: " + (System.currentTimeMillis() - startWhen) + " sleepTime : " + sleepTime);
					
					if (sleepTime > 0)
						Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
				
				mDecoder.releaseOutputBuffer(outIndex, true );
				break;
			}
			
			// All decoded frames have been rendered, we can stop playing now
			if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
				Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
				break;
			}
		}
		
		mDecoder.stop();
		mDecoder.release();
		mExtractor.release();
		*/
	}
	
	public void close() {
		eosReceived = true;
	}
}
