# TestApp

App that uses Google Activity Recognition API to get the users current activity.
Saves the results obtained from it in a log file.
Each activity has an unique id.
IN_VEHICLE -> 0
ON_BICYCLE -> 1
ON_FOOT -> 2
RUNNING -> 8
STILL -> 3
TILTING -> 5
UNKNOWN -> 4
WALKING -> 7
The results are stored by ascending id order, followed by the writing time and the battery percentage.
Each id has an attributed value that range from 0% to 100% probability.
The sampling interval can be changed in the first 30 seconds since the first press on the Sampling interval button. After that the button locks (turns red in case someone tries to change the interval) and the sampling interval can only be changed 30 minutes after that same first press.
Note: In order to make the app work non stop it is necessary that the user go to the Settings and put the app on the whitelist, i.e., turn off any battery optimizations for the app.
This could be achieved with code but it does not works properly for all android devices.
The necessary code would be to add <uses-permission  android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/> to the Manifest file and in the main activity call for ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.
A possible sulotuin would be add this to the onCreate:  
if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Intent intent = new Intent();
        String packageName = getPackageName();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
        }
    }

