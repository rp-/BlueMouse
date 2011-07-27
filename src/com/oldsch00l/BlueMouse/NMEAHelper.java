package com.oldsch00l.BlueMouse;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import android.location.Location;
import android.os.Bundle;

public class NMEAHelper {
	/**
	 * Creates a NMEA checksum for a sentence.
	 * 
	 * The checksum is calculated by XOR every char value, between '$' and
	 * '*'(end), with the current sum.
	 * 
	 * @param sbString
	 *            String to calculate the checksum.
	 * @return The checksum.
	 */
	public static int getNMEAChecksum(final StringBuilder sbString) {
		int checksum = 0;

		for (int i = 0; i < sbString.length(); i++) {
			if (sbString.charAt(i) != '*' && sbString.charAt(i) != '$')
				checksum ^= sbString.charAt(i);
		}
		return checksum;
	}

	public static DecimalFormat locFormat = new DecimalFormat("0000.####");
	public static DecimalFormat shortFormat = new DecimalFormat("##.#");

	public static SimpleDateFormat HHMMSS = new SimpleDateFormat("HHmmss.000",
			Locale.UK);

	public static SimpleDateFormat DDMMYY = new SimpleDateFormat("ddMMyy",
			Locale.UK);
	static {
		HHMMSS.setTimeZone(TimeZone.getTimeZone("GMT"));
		DDMMYY.setTimeZone(TimeZone.getTimeZone("GMT"));
	}

	/**
	 * Creates a valid NMEA GGA Global Positioning System Fix Data.
	 * 
	 * Example:
	 * $GPGGA,191410,4735.5634,N,00739.3538,E,1,04,4.4,351.5,M,48.0,M,,*45
	 * 
	 * @param loc
	 *            object to transfer into a GGA sentence.
	 * @return The GGA sentence as String.
	 */
	public static String getNMEAGGA(final Location loc) {
		StringBuilder sbGPGGA = new StringBuilder();

		char cNorthSouth = loc.getLatitude() >= 0 ? 'N' : 'S';
		char cEastWest = loc.getLongitude() >= 0 ? 'E' : 'W';

		Date curDate = new Date();
		sbGPGGA.append("$GPGGA,");
		sbGPGGA.append(HHMMSS.format(curDate));
		sbGPGGA.append(',');
		sbGPGGA.append(getCorrectPosition(loc.getLatitude()));
		sbGPGGA.append(",");
		sbGPGGA.append(cNorthSouth);
		sbGPGGA.append(',');
		sbGPGGA.append(getCorrectPosition(loc.getLongitude()));
		sbGPGGA.append(',');
		sbGPGGA.append(cEastWest);
		sbGPGGA.append(',');
		sbGPGGA.append('1'); // quality
		sbGPGGA.append(',');
		Bundle bundle = loc.getExtras();
		int satellites = bundle.getInt("satellites", 7);
		sbGPGGA.append(satellites);
		sbGPGGA.append(',');
		sbGPGGA.append(',');
		if (loc.hasAltitude())
			sbGPGGA.append(shortFormat.format(loc.getAltitude()));
		sbGPGGA.append(',');
		sbGPGGA.append('M');
		sbGPGGA.append(',');
		sbGPGGA.append(',');
		sbGPGGA.append('M');
		sbGPGGA.append(',');
		sbGPGGA.append("*");
		int checksum = getNMEAChecksum(sbGPGGA);
		sbGPGGA.append(java.lang.Integer.toHexString(checksum));
		sbGPGGA.append("\r\n");

		return sbGPGGA.toString();
	}

	/**
	 * Returns the correct NMEA position string.
	 * 
	 * Android location object returns the data in the format that is not
	 * excpected by the NMEA data set. We have to multiple the minutes and
	 * seconds by 60.
	 * 
	 * @param degree
	 *            value from the Location.getLatitude() or
	 *            Location.getLongitude()
	 * @return The correct formated string for a NMEA data set.
	 */
	public static String getCorrectPosition(double degree) {
		double val = degree - (int) degree;
		val *= 60;

		val = (int) degree * 100 + val;
		return locFormat.format(Math.abs(val));
	}

	/**
	 * Creates a valid NMEA RMC Recommended Minimum Sentence C.
	 * 
	 * Example:
	 * $GPRMC,053117.000,V,4812.7084,N,01619.3522,E,0.14,237.29,070311,,,N*76
	 * 
	 * @param loc
	 *            object to transfer into a RMC sentence.
	 * @return The RMC sentence as String.
	 */
	public static String getNMEARMC(final Location loc) {
		// $GPRMC,053117.000,V,4812.7084,N,01619.3522,E,0.14,237.29,070311,,,N*76
		StringBuilder sbGPRMC = new StringBuilder();

		char cNorthSouth = loc.getLatitude() >= 0 ? 'N' : 'S';
		char cEastWest = loc.getLongitude() >= 0 ? 'E' : 'W';

		Date curDate = new Date();
		sbGPRMC.append("$GPRMC,");
		sbGPRMC.append(HHMMSS.format(curDate));
		sbGPRMC.append(",A,");
		sbGPRMC.append(getCorrectPosition(loc.getLatitude()));
		sbGPRMC.append(",");
		sbGPRMC.append(cNorthSouth);
		sbGPRMC.append(",");
		sbGPRMC.append(getCorrectPosition(loc.getLongitude()));
		sbGPRMC.append(',');
		sbGPRMC.append(cEastWest);
		sbGPRMC.append(',');
		// sbGPRMC.append(location.getSpeed());
		sbGPRMC.append(",");
		sbGPRMC.append(shortFormat.format(loc.getBearing()));
		sbGPRMC.append(",");
		sbGPRMC.append(DDMMYY.format(curDate));
		sbGPRMC.append(",,,");
		sbGPRMC.append("A");
		sbGPRMC.append("*");
		int checksum = getNMEAChecksum(sbGPRMC);
		sbGPRMC.append(java.lang.Integer.toHexString(checksum));
		// if(D) Log.v(TAG, sbGPRMC.toString());
		sbGPRMC.append("\r\n");

		return sbGPRMC.toString();
	}
}
