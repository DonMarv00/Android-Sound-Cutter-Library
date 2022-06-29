# Android-Sound-Cutter-Library
Sound Cutter Library for Android based on Google's Ringdroid App. Tested with Android 11

<img src="https://api.citroncode.com/shared/audiocutter.jpg" width="280" alt="Screenshot 1">
 
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
	  implementation 'com.github.HBiSoft:PickiT:2.0.5'
}
```
Step 3. Add the View to your Layout
```
<com.citroncode.soundcutter.CutterView
        android:layout_width="wrap_content"
        android:id="@+id/cutterview"
        android:layout_height="wrap_content"/>
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

Here an example to check if the sound has been saved:
```
 Boolean stop = false;

 private void save(String name){
        cutterView.saveSound(name);
	ProgressDialog pg_dialog = new ProgressDialog(this);
        pg_dialog.setMessage(getResources().getString(R.string.please_wait_this_may_take_a_moment));
        pg_dialog.setCancelable(false);
        pg_dialog.show();

            Handler handler = new Handler();
            Runnable r = new Runnable() {
                public void run() {
                    if(cutterView.wasSaved() && savedAudio.length() != 0){
                    	//Do whatever you want, the sound has been saved!
                        pg_dialog.dismiss();
                        stop = true;
                        finish();
                    }
                    if (!stop) {
                        handler.postDelayed(this, 1000);
                    }

                }
            };

            handler.postDelayed(r, 1000);

    }
```

3. saveInternal()

returns the file path of the cutted sound in the app folder (located at /data/data/package-name)

# TODO
- Callback when file was saved
- Add FFMPEG to cut and save files
- Design changes with xml attributes
