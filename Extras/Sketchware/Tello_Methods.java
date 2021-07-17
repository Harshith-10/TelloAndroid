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

private HashMap<String, String> statsMap = new HashMap<>();

//In onCreate()
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
//End onCreate()

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
