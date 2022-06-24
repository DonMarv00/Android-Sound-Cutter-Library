# Android-Sound-Cutter-Library
Sound Cutter Library for Android based on Google's Ringdroid App. Tested with Android 11

# Usage

Step 1. Add the JitPack repository to your build file

```
allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```
Step 2. Add the dependency
```
dependencies {
	  implementation 'com.github.MarvinStelter:Android-Sound-Cutter-Library:v1.0.0'
}
```

# Functions

1. setSound

Just provide the uri from the file picker but make sure that the app has file permissions.

```
CutterView cutterView = binding.cutterview;
cutterView.setSound(uri,this,MainActivity.this);
```

2. saveSound()

The file will be saved to the Music folder as an .mp3 file

```
cutterView.saveSound("File name goes here");
```

3. saveInternal()

returns the file path of the cutted sound in the app folder (located at /data/data<package name>)

# TODO
- Callback when file was saved
- Add FFMPEG to cut and save files
- Design changes with xml attributes
