# BLEHelper
Bluetooth Low Energy Helper Class

Steps needed to implement BLE in any existing Android project

1. Add google play services location to build.gradle:
   
    Latest Android:

  ```implementation 'com.google.android.gms:play-services-location:19.0.1’```

    Older Android (before AndroidX):
  
```implementation 'com.google.android.gms:play-services-location:16.0.0'```

2. Add required permissions to Android Manifest:

```
<uses-permission android:name="android.permission.BLUETOOTH"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"/>
```
  
3. Copy BLEHelper.Java into project:
4. If older Android, import Nullable in BLEHelper class:

```import android.support.annotation.Nullable;```

5. If Android 12 or above, comment out permissions in BLEHelper.Java
6. Add permission callbacks to mainActivity:

``` @Override
protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
  super.onActivityResult(requestCode, resultCode, data);
  BLEHelper.getInstance(this).onActivityResult(requestCode, resultCode, data);
  //Log.d("OnActivityResult: ", String.valueOf(requestCode) + "|" + String.valueOf(resultCode));
}
```

7. Add to mainActivity (check if already existing):

``` 7. @Override
public void onRequestPermissionsResult(
    int requestCode, String[] permissions, int[] grantResults) {
  BLEHelper.getInstance(this).onRequestPermissionsResult(requestCode, permissions, grantResults);
  }
  ```
  
8. In onCreate, create observers for LiveData:

``` // Create the observer which updates the UI.
final Observer<String> readDataObserver = new Observer<String>() {
  @Override
  public void onChanged(@Nullable final String batteryStatus) {
    // perform action
    // below is example that worked for Google
    if (batteryStatus.startsWith("!TGB")){
      Log.d("Battery Update", batteryStatus.substring(4));
      batteryText.setText("Battery: " + batteryStatus.substring(4) + "%");
  }
    
  }
};

// Observe the LiveData, passing in this activity as the LifecycleOwner and the observer.
BLEHelper.getInstance(this).getReadData().observe(this, readDataObserver);

// Create the observer which updates the UI.
final Observer<Boolean> isConnectedObserver = new Observer<Boolean>() {
  @Override
  public void onChanged(@Nullable final Boolean connectionStatus) {
    // perform action
    // example that worked for Google
    Log.d("LiveDataChanged", connectionStatus.toString());
     switch (connectionStatus.toString()) {
       case "true":
         connectionText.setText("Bluetooth: Connected");
         break;
       case "false":
         connectionText.setText("Bluetooth: Disconnected");
         break;
     }
  }
};

// Observe the LiveData, passing in this activity as the LifecycleOwner and the observer.
BLEHelper.getInstance(this).getIsConnected().observe(this, isConnectedObserver);
```

9. Start/Stop transcript:

Create button, id "startStopButton"

In XML, add:
```android:onClick="startStopClicked”```

In mainActivity, add (Google example): 

```
public void startStopClicked(View v)
{
  //Log.d("button", String.valueOf(audioRecord.getRecordingState()) + "|" + String.valueOf(recognizer.isStopped));
  Button startStopButton = (Button) findViewById(R.id.startStopButton);
  if ((!recognizer.isStopped) && (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)) {
    audioRecord.stop();
    recognizer.unregisterCallback(transcriptUpdater);
    networkChecker.unregisterNetworkCallback();
    Log.d("STOPPED", "AUDIO & RECOGNIZER STOPPED");
    startStopButton.setText("Start");
    return;
  }
  if ((recognizer.isStopped) && (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED)) {
    constructRepeatingRecognitionSession(); //IMP
    startRecording();
    Log.d("STARTED", "AUDIO & RECOGNIZER STARTED");
    startStopButton.setText("Stop");
    return;
  }
}
```

10. BLE button:

Create button in XML

In XML, add: ```android:onClick="bluetoothButtonClicked"```

In MainActivity, add:

```
public void bluetoothButtonClicked(View v) {
  if (BLEHelper.getInstance(this).getScanning()) {
    BLEHelper.getInstance(this).stopBleScan();
    try {
      Thread.sleep(100);
    } catch (Exception e) { }
  }
  BLEHelper.getInstance(this).startBleScan();
}
```

11. When you want to add BLE initiate, in MainActivity add:

```BLEHelper.getInstance(this).startBleScan();```

12. When you want to write data to BLE connected device, add:

```BLEHelper.getInstance(this).write(data);```

13. Adding word wrapping logic

Import textutils class (in build.gradle):

```implementation 'org.apache.commons:commons-text:1.9'```

Declare variables in mainActivity (or wherever calling function:

```
   private String sendString = "";
   private int charactersPerLine = 18;
   private String previousTranscript = "!@";
   private String previousText = "";
   private long previousTime = 0;
   private int previousNumberofLines = 5;
   private int currentNumberofLines = 0;
```

Add function in mainActivity:

```
private void sendData (String transcript, boolean isFinal){
   sendString = WordUtils.wrap(transcript, charactersPerLine) + "\n"; //last \n is helper for FW - clearing line, setting cursor, getting oled.row etc
   currentNumberofLines = StringUtils.countMatches(sendString, "\n"); //keep in mind last \n added boosts count by +1
   if((!previousText.equals("")) || (currentNumberofLines > 5)) {
      for (int i = 0; i < currentNumberofLines - previousNumberofLines; i++) {
          sendString += "\2";
      }
   }
   if (currentNumberofLines > 5){
      sendString = sendString.substring(StringUtils.ordinalIndexOf(sendString, "\n", currentNumberofLines-5) +1);//+1 bc we want to move past new line
   }
   previousNumberofLines = currentNumberofLines;
   //Log.d("Number of new lines: ", String.valueOf(StringUtils.countMatches(WordUtils.wrap(formattedTranscript.toString(), charactersPerLine), "\n")));
   if(isFinal){
      previousText += transcript;
      sendString += "\3";
      previousNumberofLines = 1;
   }
   Log.d("Sending to BT: ", sendString);
   BLEHelper.getInstance(this).write("\1" + sendString + "\0");

   previousTranscript = transcript;
   previousTime = Calendar.getInstance().getTimeInMillis();
   }
```

Call function wherever recognizer callBack is called

Notes:
* Modified write function for specific uuids
* Modified characteristic notification for specific UUID
* Modified ShowDevices to only show devices if name!=null


