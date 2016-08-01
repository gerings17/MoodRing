package org.jointheleague.gerings17.rpi;

import java.io.IOException;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;

import static java.lang.Math.pow;

public class MoodRingCode implements Runnable {

    //@formatter:off
    // Default device I2C address.
    final static private byte TMP006_I2CADDR   = (byte) 0x40;


    // Config register values.
    
    
    //@formatter:on
    
    private enum Register {
        
        //@formatter:off
        V_OBJ(                  0x00),
        T_DIE(                  0x01),
        CONFIG(                 0x02),
        MANUFACTURER_ID(        0xFE),
        DEVICE_ID(              0xFF);
        //@formatter:on
        
        private Register(int code) {
            this.code = (byte) code;
        }
        
        private final byte code;

        public byte getCode() {
            return code;
        }
    }

    private enum Configuration {
        //@formatter:off
        RESET(                  0x8000),
        MODEON(                 0x7000),
        SAMPLE_1(               0x0000), 
        SAMPLE_2(               0x0200), 
        SAMPLE_4(               0x0400), 
        SAMPLE_8(               0x0600), 
        SAMPLE_16(              0x0800);
        //@formatter:on

        private Configuration(int code) {
            this.code = code;
        }

        private final int code;

        public int getCode() {
            return code;
        }
    }

    // 0 degrees C == 273.15 degrees K
    final static private double KELVIN_OFFSET = 273.15; 
    I2CBus bus;
    I2CDevice device;
    private volatile boolean running = true;

    public void setRunning(boolean running) {
        this.running = running;
    }

    public static void main(String[] args) throws InterruptedException {
        MoodRingCode ring = new MoodRingCode();

        new Thread(ring).start();
        // Thread.sleep(20000);
        // ring.setRunning(false);

    }

    public void run() {
        initialize();
        try {
            while (running) {
                double temp = readTemp();
                System.out.format("Temp. = %.2f \n", temp);
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
        }
    }

    private static byte[] writeShort(short n) {
        byte[] result = new byte[2];
        result[0] = (byte) ((n >> 8) & 0xFF);
        result[1] = (byte) (n & 0xFF);
        return result;
    }

    private static int readUnsignedShort(byte[] buffer) {
        return ((buffer[0] & 0xFF) << 8) | (buffer[1] & 0xFF);
    }

    private static int readSignedShort(byte[] buffer) {
        return (buffer[0] << 8) | (buffer[1] & 0xFF);
    }

    private boolean initialize() {
        System.out.println("Initialization started ...");

        try {
            bus = I2CFactory.getInstance(I2CBus.BUS_1);
            System.out.println("Connected to the I2C bus OK!");

            device = bus.getDevice(TMP006_I2CADDR);
            System.out.println("Connected to the device OK!");

            byte[] buffer = null;
            
            buffer = writeShort((short) Configuration.RESET.getCode());
            device.write(Register.CONFIG.getCode(), buffer, 0, 2);
            System.out.println("Device reset");

            device.read(Register.MANUFACTURER_ID.getCode(), buffer, 0, 2);
            int manId = readUnsignedShort(buffer);
            device.read(Register.DEVICE_ID.getCode(), buffer, 0, 2);
            int deviceId = readUnsignedShort(buffer);
            System.out.format("Manufacturer = %04X\nDevice       = %04X\n", manId, deviceId);

            buffer = writeShort((short) (Configuration.MODEON.getCode() | Configuration.SAMPLE_4.getCode()));
            device.write(Register.CONFIG.getCode(), buffer, 0, 2);
            System.out.println("Entered continuous conversion mode.");

            return true;
        } catch (IOException e) {
            return false;
        }

    }

    private double readTemp() {

        // Temperature conversion according to
        // http://www.ti.com/lit/ug/sbou107/sbou107.pdf, Section 5.1
        double a1 = 1.75E-3;
        double a2 = -1.678E-5;
        double tRef = KELVIN_OFFSET + 25.0;
        double s0 = 6.4E-14; // (Should be calibrated)
        double b0 = -2.94E-5;
        double b1 = -5.7E-7;
        double b2 = 4.63E-9;
        double c2 = 13.4;
        // See http://www.ti.com/lit/ds/symlink/tmp006.pdf, Section 8.3.6
        double voltUnit = 156.25E-9;
        double tempUnit = 0.03125;

        byte[] buffer = new byte[2];

        try {
            device.read(Register.V_OBJ.getCode(), buffer, 0, 2);
            double vObj = readSignedShort(buffer) * voltUnit;

            device.read(Register.T_DIE.getCode(), buffer, 0, 2);
            double tDie = (readSignedShort(buffer) >> 2) * tempUnit + KELVIN_OFFSET;

            double t = tDie - tRef;
            double s = s0 * (1 + t * (a1 + t * a2));
            double vOffset = b0 + t * (b1 + t * b2);
            double d = vObj - vOffset;
            double f_vObj = d * (1 + d * c2);
            double tObj = pow(pow(tDie, 4.0) + f_vObj / s, 0.25);
            return tObj - KELVIN_OFFSET;

        } catch (IOException e) {
            return -KELVIN_OFFSET; // Absolute zero!
        }

    }

}
