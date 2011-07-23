BlueMouse
=========

Overview
--------

BlueMouse is an Android app that lets your mobile phone act like 
a Bluetooth GPS mouse. It creates a serial Bluetooth channel on port 1
and listens for incoming connections. If a client connects it will send out
NMEA GGA and RMC sentences.

The main motivation for this app was to create an app
that can provide GPS data to the foolography unleashed products.
More information can be found on the foolography homepage 
-> http://www.foolography.com

Requirements
------------

  * An android devices with Bluetooth, GPS or network location positioning
  * Android version 2.0 or above
    Android version 1.* don't have a prober Bluetooth API.

Supported devices
-----------------

It depends more on the used Android ROM/firmware than on the actual device,
Currently known to work devices/software versions are:

  * HTC G1/Dream with Cyanogenmod 6
  * Google Nexus S with stock ROM
  * HTC Vision/Desire Z with Cyanogenmod 7

Known not to work:

  * HTC Desire with stock ROM
  * LG Optimus P990/2x stock ROM

Instuctions for foolography unleashed
-------------------------------------

Start the BlueMouse app and try to pair you unleashed with the phone
with the paring code 0000. The first pairing will usually fail,
because the unleashed doesn't wait long enough for you to enter the code.
But the second time you shouldn't get asked for a paring code anymore
and the unleashed device should immediately connect.

License
-------

BlueMouse is license under the GPLv3 and the license
can be found in the LICENSE file delivered with this source code.
Some code comes from the android SDK example and WAS licensed under the Apache 2.0 license.

