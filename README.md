# TelloAndroid
An Android app for controlling the DJI Ryze Tello drone and streaming the video.

# What's special in this?
- Implemented every method mentioned in the SDK.
- Added many safety checks to prevent sending an invaliid command to the drone.
- Tello's status is received an converted into a `HashMap<String, String>`.
- Video stream now working without third party libraries.
- Has built-in video decoder. You just have to set an output `TextureView` using `setVideoOut(yourTextureView)`.

#Usage
This class is very easy to implement and work with.
All you have to do is to ensure that you've connected your device to Tello WiFi before calling the constructor.
````
//After you've connected to the Drone's WiFi
Tello mTello = new Tello(context);
mTello.setTelloListener(new Tello.TelloListener(){
  @Override
  public void onMessageReceived(String command, String response){
    // command is the String sent to Tello by us.
    // response is the String returned by Tello in response to command
    // Do something
  }
  
  @Override
  //This is called when Exceptions are caught in the class
  public void onErrorReceived(String exceptionName, String message){
    // Do something
  }
});
mTello.setVideoOut(someTextureView);
mTello.startVideo();

mTello.takeoff();
mTello.forward(50); //Units: cm
mTello.flip("b"); // Only use "l", "r", "f" or "b". Others are discarded
mTello.land();

//Or you can setup Joysicks and use this when they are updated
mTello.rc(a, b, c, d); //a, b, c and d are the four joystick channels
````
See! It's that simple! No more struggling for processing video!

#Enjoy!
