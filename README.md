
# Camera2 API App with Photo Cycle and Video Creation

This Android app uses Camera2 API to capture images and create a video. The app allows users to switch between front and back cameras, take photos in a cycle, and create a video from the captured images using FFMPEG.

## Features

- **Preview mode**: Upon opening the app, the camera preview is displayed.
- **Switch camera**: A button in the top left corner allows switching between front and back cameras.
- **Capture photos**: Pressing the center button will start a photo capture cycle. Pressing it again stops the cycle and creates a video.
- **Video creation**: After capturing a series of photos, the app uses FFMPEG to stitch the images into a video.
- **Material Design**: The app uses Material Design icons for the interface elements.

## Requirements

- Android SDK 21 (Lollipop) or higher.
- Camera2 API.
- FFMPEG library (for video creation).

## Installation

To get started with this project:

1. Clone this repository:

   ```bash
   git clone <repository-url>
   ```

2. Open the project in Android Studio.

3. Ensure that you have the necessary permissions in your `AndroidManifest.xml`.

4. Build and run the app on an Android device or emulator (SDK 21 or higher).

## Permissions

The app requires the following permissions:

- Camera access
- Write external storage

```xml
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
```



