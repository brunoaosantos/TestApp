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
Note: this only works if the device screen rotation is locked. 


