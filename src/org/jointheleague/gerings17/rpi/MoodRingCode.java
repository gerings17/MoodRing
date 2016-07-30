package org.jointheleague.gerings17.rpi;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;

public class MoodRingCode {
	
	//https://github.com/gerings17/Adafruit_Python_TMP/blob/master/Adafruit_TMP/TMP006.py

	// Coefficient values, found from this whitepaper:
	// http://www.ti.com/lit/ug/sbou107/sbou107.pdf
	final static float TMP006_B0 = -0.0000294f;
	final static float TMP006_B1 = -0.00000057f;
	final static float TMP006_B2 = 0.00000000463f;
	final static float TMP006_C2 = 13.4f;
	final static float TMP006_TREF = 298.15f;
	final static float TMP006_A2 = -0.00001678f;
	final static float TMP006_A1 = 0.00175f;
	final static float TMP006_S0 = 6.4f; // * 10^-14

	// Default device I2C address.
	final static byte TMP006_I2CADDR=(byte) 0x40;

	// Register addresses.
	final static byte TMP006_CONFIG = (byte) 0x02;
	final static byte TMP006_MANID = (byte) 0xFE;
	final static byte TMP006_DEVID = (byte) 0xFF;
	final static byte TMP006_VOBJ = (byte) 0x00;
	final static byte TMP006_TAMB = (byte) 0x01;

	// Config register values.
	final static short TMP006_CFG_RESET = (short) 0x8000;
	final static short TMP006_CFG_MODEON = (short) 0x7000;
	final static short CFG_1SAMPLE = (short) 0x0000;
	final static short CFG_2SAMPLE = (short) 0x0200;
	final static short CFG_4SAMPLE = (short) 0x0400;
	final static short CFG_8SAMPLE = (short) 0x0600;
	final static short CFG_16SAMPLE = (short) 0x0800;
	final static short TMP006_CFG_DRDYEN = (short) 0x0100; 
	final static short TMP006_CFG_DRDY=(short)0x0080;

}
