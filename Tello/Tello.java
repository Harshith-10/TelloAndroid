/*
*Copyright (C) 2021 Harshith Doddipalli
*
*Licensed under the Apache License, Version 2.0 (the "License");
*you may not use this file except in compliance with the License.
*You may obtain a copy of the License at
*
*	http://www.apache.org/licenses/LICENSE-2.0
*
*Unless required by applicable law or agreed to in writing, software
*distributed under the License is distributed on an "AS IS" BASIS,
*WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*See the License for the specific language governing permissions and
*limitations under the License.
*/

package com.hdr.tello.v2;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import java.util.ArrayList;
import java.util.HashMap;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/*
	A class that can be used to control the Tello drone
	and also receive it's video stream.
	Created by Harshith on 16 July, 2021
*/

public class Tello {
	public String LOG = "";
	
	private Context context;
	private DatagramSocket socket1;
	private DatagramSocket socket2;
	private DatagramSocket socket3;
	private InetAddress mainIP;
	private TextView logView;
	private TextureView m_surface;	
        private MediaCodec m_codec;
        private DecodeFramesTask m_frameTask;
	private VideoReceiver provider;
	
	private byte[] header_sps = {0, 0, 0, 1, 103, 66, 0, 42, (byte) 149, (byte) 168, 30, 0, (byte) 137, (byte) 249, 102, (byte) 224, 32, 32, 32, 64};
        private byte[] header_pps = {0, 0, 0, 1, 104, (byte) 206, 60, (byte) 128, 0, 0, 0, 1, 6, (byte) 229, 1, (byte) 151, (byte) 128};
	
	private byte[] frame = new byte[0];
	private byte[] buffer1;
	private byte[] buffer2 = new byte[0];
	
	private HaspMap<String, String> statsMap = new HashMap<>(); //Refer to SDK for getting keys
	
	public TelloListener listener;
	
	private TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener(){
		@Override
		public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
			MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 960, 720);
			format.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
			format.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));
			format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 960*720);

			try {
				m_codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
				m_codec.configure(format, new Surface(m_surface.getSurfaceTexture()), null, 0);
				m_codec.start();
			}catch(Exception e){
				sendExcept(e);
			}
		}
		
		@Override
		public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
		}
		
		@Override
		public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
			return false;
		}
		
		@Override
		public void onSurfaceTextureUpdated(SurfaceTexture surface) {
		}
	};
	
	public interface TelloListener{
		void onMessageReceived(String param1, String param2);
		void onErrorReceived(String param1, String param2);
	}
	
	public Tello(Context _context){
		this.context = _context;
		connect();
	}
	
	private void connect(){
		try{
			mainIP = InetAddress.getByName("192.168.10.1");
			socket1 = new DatagramSocket(8889);
			InetSocketAddress videoIP = new InetSocketAddress("0.0.0.0", 11111);
			socket2 = new DatagramSocket(videoIP);
			InetSocketAddress statusIP = new InetSocketAddress("0.0.0.0", 8890);
			socket3 = new DatagramSocket(statusIP);
			showMessage("Networking components successfully initiated.");
			sendCommand("command");
		}catch(Exception e){
			log("init", e);
		}
		provider = new VideoReceiver();
		m_framesTask = new DecodeFramesTask();
	}
	
	public void setTelloListener(TelloListener mListener){
		this.listener = mListener;
	}
	
	public void setVideoOut(TextureView mTextureView){
		this.m_surface = mTextureView;
		m_surface.setSurfaceTextureListener(textureListener);
	}
	
	public void startVideo() throws TelloVideoException {
		if(m_surface == null){
			throw new TelloVideoException("Caused by calling startVideo() without calling setVideoOut(TextureView).");
			return;
		}
		
		provider.start();
		m_framesTask.start();
	}
	
	public void takeoff(){
		sendCommand("takeoff");
	}
	
	public void land(){
		sendCommand("land");
	}
	
	public void streamon(){
		sendCommand("streamon");
	}
	
	public void streamoff(){
		sendCommand("streamoff");
	}
	
	public void emergency(){
		//WARNING: Use at your own risk. Stops all the motors at once. May damage the drone if not used with caution!
		sendCommand("emergency");
	}
	
	//Commands from here that need a value of x, y or z have an input range from 20cm - 500cm. Rotation range is from 1 - 3600
	public void up(int x){
		if(x < 20){
			x = 20;
		}else if(x > 500){
			x = 500;
		}
		sendCommand("up " + String.valueOf(x));
	}
	
	public void down(int x){
		if(x < 20){
			x = 20;
		}else if(x > 500){
			x = 500;
		}
		sendCommand("down " + String.valueOf(x));
	}
	
	public void left(int x){
		if(x < 20){
			x = 20;
		}else if(x > 500){
			x = 500;
		}
		sendCommand("left " + String.valueOf(x));
	}
	
	public void right(int x){
		if(x < 20){
			x = 20;
		}else if(x > 500){
			x = 500;
		}
		sendCommand("up " + String.valueOf(x));
	}
	
	public void forward(int x){
		if(x < 20){
			x = 20;
		}else if(x > 500){
			x = 500;
		}
		sendCommand("forward " + String.valueOf(x));
	}
	
	public void back(int x){
		if(x < 20){
			x = 20;
		}else if(x > 500){
			x = 500;
		}
		sendCommand("back " + String.valueOf(x));
	}
	
	public void cw(int x){
		if(x < 1){
			x = 1;
		}else if(x > 3600){
			x = 3600;
		}
		sendCommand("cw " + String.valueOf(x));
	}
	
	public void ccw(int x){
		if(x < 1){
			x = 1;
		}else if(x > 3600){
			x = 3600;
		}
		sendCommand("ccw " + String.valueOf(x));
	}
	
	public boolean flip(String dir){
		if((dir.equals("l") || dir.equals("r")) || (dir.equals("f") || dir.equals("b"))){
			sendCommand("flip " + dir);
			return true;
		}
		return false;
	}
	
	public void go(int x, int y, int z, int speed){
		if(x < 20){
			x = 20;
		}else if(x > 500){
			x = 500;
		}
		if(y < 20){
			y = 20;
		}else if(y > 500){
			y = 500;
		}
		if(z < 20){
			z = 20;
		}else if(z > 500){
			z = 500;
		}
		if(speed < 10){
			speed = 10;
		}else if(speed > 100){
			speed = 100;
		}
		sendCommand("go " + String.valueOf(x) + " " + String.valueOf(y) + " " + String.valueOf(z) + " " + String.valueOf(speed));
	}
	
	public void curve(int x1, int y1, int z1, int x2, int y2, int z2, int speed){
		if(x1 < 20){
			x1 = 20;
		}else if(x1 > 500){
			x1 = 500;
		}
		if(y1 < 20){
			y1 = 20;
		}else if(y1 > 500){
			y1 = 500;
		}
		if(z1 < 20){
			z1 = 20;
		}else if(z1 > 500){
			z1 = 500;
		}
		if(x2 < 20){
			x2 = 20;
		}else if(x2 > 500){
			x2 = 500;
		}
		if(y2 < 20){
			y2 = 20;
		}else if(y2 > 500){
			y2 = 500;
		}
		if(z2 < 20){
			z2 = 20;
		}else if(z2 > 500){
			z2 = 500;
		}
		if(speed < 10){
			speed = 10;
		}else if(speed > 60){
			speed = 60;
		}
		sendCommand("curve " + String.valueOf(x1) + " " + String.valueOf(y1) + " " + String.valueOf(z1) + " " + String.valueOf(x2) + " " + String.valueOf(y2) + " " + String.valueOf(z2) + " " + String.valueOf(speed));
	}
	
	//Set commands
	public void speed(int spd){
		if(spd < 10){
			spd = 10;
		}else if(spd > 100){
			spd = 100;
		}
		sendCommand("speed " + String.valueOf(spd));
	}
	
	public void rc(int a, int b, int c, int d){
		if(a < -100){
			a = -100;
		}else if(a > 100){
			a = 100;
		}
		if(b < -100){
			b = -100;
		}else if(b > 100){
			b = 100;
		}
		if(c < -100){
			c = -100;
		}else if(c > 100){
			c = 100;
		}
		if(d < -100){
			d = -100;
		}else if(d > 100){
			d = 100;
		}
		sendCommand("rc " + String.valueOf(a) + " " + String.valueOf(b) + " " + String.valueOf(c) + " " + String.valueOf(d));
	}
	
	public void hover(){
		sendCommand("stop");
	}
	
	//Get commands
	public String getSpeed(){
		return sendCommandToGet("speed?"); // Units: cm/s
	}
	
	public String getBattery(){
		return sendCommandToGet("battery?");
	}
	
	public String getTime(){
		return sendCommandToGet("time?");
	}
	
	public String getHeight(){
		return sendCommandToGet("height?");
	}
	
	public String getTemp(){
		return sendCommandToGet("temp?");
	}
	
	public String getAttitude(){
		return sendCommandToGet("attitude?");
	}
	
	public String getBaro(){
		return sendCommandToGet("baro?");
	}
	
	public String getAcceleration(){
		return sendCommandToGet("acceleration?");
	}
	
	public String getTOF(){
		return sendCommandToGet("tof?");
	}
	
	public String getWiFiSNR(){
		return sendCommandToGet("wifi?");
	}
	
	public HashMap<String, String> getStatus(){
		return statsMap;
	}
	
	private void log(String txt){
		LOG = LOG + "\n" + txt;
		logView.setText(LOG);
	}
	
	private void log(String tag, Exception e){
		log(tag + ": " + e.toString() + ", " + e.getMessage());
	}
	
	private void showMessage(String msg){
		Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
	}
	
	private void onMessage(String cmd, String resp){
		if(listener != null) listener.onMessageReceived(cmd, resp);
	}
	
	private void onError(String name, String msg){
		if(listener != null) listener.onErrorReceived(name, msg);
	}
	
	public void sendCommand(String command){
		(new CommandTask()).execute(command);
	}
	
	public String sendCommandToGet(String command){
		return (new CommandTask()).execute(command).get();
	}
	
	public void sendExcept(final Exception e){
		context.runOnUiThread(new Runnable(){
			@Override
			public void run(){
				onError(e.toString(), e.getMessage());
			}
		});
	}
	
	private class CommandTask extends AsyncTask<String, String, String> {	
		@Override
		protected String doInBackground(String... strings) {
			final String command = strings[0];
			byte[] buf = command.getBytes();
			DatagramPacket packet = new DatagramPacket(buf, buf.length, mainIP, 8889);
			try {
				socket1.send(packet);
				buf = new byte[128];
				packet = new DatagramPacket(buf, buf.length);
				socket1.setSoTimeout(250);
				socket1.receive(packet);
				String doneText = new String(buf, 0, packet.getLength(), StandardCharsets.UTF_8);
				publishProgress(command, doneText, "ok");
			} catch (Exception e) {
				publishProgress(e.toString(), e.getMessage(), "fail");
			}
			return "";
		}
		
		@Override
		protected void onProgressUpdate(String... args){
			final String command = args[0];
			final String message = args[1];
			final String status  = args[2];
			
			if(status.equals("ok")){
				onMessage(command, message);
			}else{
				onError(command, message);
			}
		}

		@Override
		protected void onPostExecute(String s) {
			super.onPostExecute(s);
		}
        }
	
	private class VideoReceiver extends Thread {
		private boolean keepRunning = true;

		@Override
		public void run() {
			byte[] slice = new byte[1470];
			DatagramPacket packet = new DatagramPacket(slice, slice.length);
			
			while(keepRunning) {
				try{
					socket2.receive(packet);
					processData(slice, packet.getLength());
				}catch(Exception e){
					sendExcept(e);
				}
			}
		}
		
		private void processData(byte[] slice, int len){
			buffer1 = new byte[buffer2.length+len];
			if(buffer2.length == 0){
				System.arraycopy(slice, 2, buffer1, 0, slice.length-2);
			}else{
				System.arraycopy(buffer2, 0, buffer1, 0, buffer2.length);
				System.arraycopy(slice, 2, buffer1, buffer2.length, slice.length-2);
			}
			buffer2 = buffer1;
			checkEOF(len);
		}
		
		private void checkEOF(int len){
			if(len != 1460){
				frame = buffer2;
				buffer2 = new byte[0];
				log("Frame size: " + String.valueOf(frame.length));
			}
		}

		public void kill(){
			keepRunning = false;
		}
	}
	
	private class StatusReceiver extends Thread {
		private boolean keepRunning = true;
		public String stats = "";

		@Override
		public void run() {
			byte[] slice = new byte[1024];
			DatagramPacket packet = new DatagramPacket(slice, slice.length);
			
			while(keepRunning) {
				try{
					socket3.receive(packet);
					processData(slice, packet.getLength());
				}catch(Exception e){
					sendExcept(e);
				}
			}
		}
		
		private void processData(byte[] slice, int len){
			stats = new String(slice, 0, len, StandardCharsets.UTF_8);
			if(isEnd(stats)){
				kill();
				return;
			}
			processString(stats);
		}
		
		private void processString(String data){
			for(final String entry : data.split(";")){
				if(entry == ""){
					continue;
				}
				final String[] parts = entry.split(":");
				if(parts.length == 2){
					statsMap.put(parts[0], parts[1]);
				}else{
					continue;
				}
			}
		}
		
		private boolean isEnd(String stat){
			if(stat.equals("end")){
				return true;
			}
			return false;
		}

		public void kill(){
			keepRunning = false;
			provider.kill();
			m_frameTask.kill();
			showMessage("Tello has sent an end message. Every process has been stopped. Reconnect Tello and Relaunch the app to continue.");
			context.finishAffinity();
		}
	}
	
	private class DecodeFramesTask extends Thread {
		public boolean keepRunning = true;
		
		@Override
		public void run(){
			while(keepRunning){
				try{
					Thread.sleep(50); //20 fps
				}catch(Exception e){
					sendExcept(e);
				}
				
				if(frame.length == 0){
					continue;
				}
				
				int inputIndex = m_codec.dequeueInputBuffer(-1);
				
				if(inputIndex >= 0){
					ByteBuffer buffer = m_codec.getInputBuffer(inputIndex);
					buffer.reset();
					buffer.put(frame);
					m_codec.queueInputBuffer(inputIndex, 0, frame.length, 0, 0);
				}

				MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
				int outputIndex = m_codec.dequeueOutputBuffer(info, 0);
				if (outputIndex >= 0){
					m_codec.releaseOutputBuffer(outputIndex, true);
				}
			}
			onProcessEnded();
		}
		
		public void kill(){
			this.keepRunning = false;
		}

		private void onProcessEnded(){
			try {
				m_codec.stop();
				m_codec.release();
			}catch(Exception e){
				sendExcept(e);
			}
		}
	}
	
	private class TelloVideoException extends NullPointerException{
		@Override
		public TelloVideoException(String message){
			super(message);
		}
	}
}
