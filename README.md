# Mend Cloud Native Security Scanner #

This plugin downloads Mend.io Scanner CLI and performs image scan to detect vulnerabilities and other security risks. 


## Plugin Prerequisites ##

Scanner requires 4 variables to be configured:
 ```User Email - mend email```
 ```User Key -  mend user key```
 ```Mend URL - mend application environment```
 ```Repositories - list of image repositories to scan from (separated by comma)```


This plugin is defined to be run (as a build step) after a new image creation on the jenkins machine:
According to the given repositories, for each repository the latest created image will be scanned and a summary table will be displayed. 
This table consists of vulnerabilities data with their risk and fix version (if exists). 
Also, other security risks, such as secrets detection will be presented.

* The plugin assumed the local latest image was created and still exists on the jenkins machine.
* The plugin download a dedicated scanner CLI according to the OS and the machine architecture

