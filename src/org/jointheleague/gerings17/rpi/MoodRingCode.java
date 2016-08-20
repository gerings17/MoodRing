package org.jointheleague.gerings17.rpi;

import java.io.IOException;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;

import static java.lang.Math.pow;

public class MoodRingCode implements Runnable {

    // Default device I2C address.
    final static private byte TMP006_I2CADDR = (byte) 0x40;

    private enum Register {

        //@formatter:off
        V_OBJ(                  0x00),
        T_DIE(                  0x01),
        CONFIG(                 0x02),
        MANUFACTURER_ID(        0xFE),
        DEVICE_ID(              0xFF);
        //@formatter:on

        private final byte address;

        private Register(int address) {
            this.address = (byte) address;
        }

        public byte getAddress() {
            return address;
        }
    }

    private enum Configuration {
        //@formatter:off
        RESET(                  0x8000),
        MODEON_SAMPLE_1(        0x7000), 
        MODEON_SAMPLE_2(        0x7200), 
        MODEON_SAMPLE_4(        0x7400), 
        MODEON_SAMPLE_8(        0x7600), 
        MODEON_SAMPLE_16(       0x7800);
        //@formatter:on

        private final short code;
        
        private Configuration(int code) {
            this.code = (short) code;
        }

        public short getCode() {
            return code;
        }
    }

    // 0 degrees Celsius == 273.15 degrees Kelvin
    final static private double CELSIUS_OFFSET = 273.15;
    private I2CBus bus;
    private I2CDevice device;

    private volatile boolean running = true;

    public void setRunning(boolean running) {
        this.running = running;
    }

    public static void main(String[] args) throws InterruptedException {
        MoodRingCode ring = new MoodRingCode();

        new Thread(ring).start();
         Thread.sleep(60000);
         ring.setRunning(false);

    }

    public void run() {
        initialize();
        try {
            while (running) {
                double temp = readTemp();
                System.out.format("Temp. = %.4f \n", temp);
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
            device.write(Register.CONFIG.getAddress(), buffer, 0, 2);
            System.out.println("Device reset");

            device.read(Register.MANUFACTURER_ID.getAddress(), buffer, 0, 2);
            int manufacturerId = readUnsignedShort(buffer);
            device.read(Register.DEVICE_ID.getAddress(), buffer, 0, 2);
            int deviceId = readUnsignedShort(buffer);
            System.out.format("Manufacturer = %04X\nDevice       = %04X\n", manufacturerId, deviceId);

            buffer = writeShort(Configuration.MODEON_SAMPLE_4.getCode());
            device.write(Register.CONFIG.getAddress(), buffer, 0, 2);
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
        double tRef = CELSIUS_OFFSET + 25.0;
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
            device.read(Register.V_OBJ.getAddress(), buffer, 0, 2);
            double vObj = readSignedShort(buffer) * voltUnit;

            device.read(Register.T_DIE.getAddress(), buffer, 0, 2);
            double tDie = (readSignedShort(buffer) >> 2) * tempUnit + CELSIUS_OFFSET;

            double t = tDie - tRef;
            double s = s0 * (1 + t * (a1 + t * a2));
            double vOffset = b0 + t * (b1 + t * b2);
            double d = vObj - vOffset;
            double f_vObj = d * (1 + d * c2);
            double tObj = pow(pow(tDie, 4.0) + f_vObj / s, 0.25);
            // Convert to Celsius
            return tObj - CELSIUS_OFFSET;

        } catch (IOException e) {
            return -CELSIUS_OFFSET; // Absolute zero!
        }

    }

}
