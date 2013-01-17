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

  * An android device with Bluetooth, GPS or network location positioning
  * Android version 2.0 or above
    Android version 1.* don't have a prober Bluetooth API.

Settings
--------

There are currently 3 settings:

  * Force serial port number
    You need to set this to use the software with foolography unleashed.
    If you don't force the serial port number a free port will be choosen
    from android and advertised via the SDP protocol.
  * Serial port number
    Force the serial port number and use channel 1 for foolography.
  * Update interval
    Interval for sending NMEA sentences to the connected devices.
    Minimum setting is 250ms, but actually there is not much sense in
    setting it below 1000ms.

Supported devices for foolography unleashed
-------------------------------------------

It depends more on the used Android ROM/firmware than on the actual device,
Currently known to work devices/software versions are:

  * HTC G1/Dream - Cyanogenmod 6
  * HTC One S - Stock ROM
  * HTC Vision/Desire Z - Cyanogenmod 7
  * Google Nexus S - Stock ROM
  * LG Optimus P990/2x - Cyanogenmod 7
  * Samsung Galaxy S3 - Stock ROM

Known not to work:

  * HTC Desire - Stock ROM
  * LG Optimus P990/2x - Stock ROM

Instuctions for foolography unleashed
-------------------------------------

Before you start make sure your device is discoverable, either by the
system setting, or make it discoverable with BlueMouse's menu option.
Start the BlueMouse app and try to pair you unleashed with the phone
with the paring code 0000. The first pairing will usually fail,
because the unleashed doesn't wait long enough for you to enter the code.
But the second time you shouldn't get asked for a paring code anymore
and the unleashed device should immediately connect.

For foolography devices the serial channel has to be forced to channel 1.

License
-------

BlueMouse is license under the GPLv3 and the license
can be found in the LICENSE file delivered with this source code.
Some code comes from the android SDK example and WAS licensed under the Apache 2.0 license.

