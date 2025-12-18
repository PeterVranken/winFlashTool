package winFlashTool;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

class HexDumpUtil {

    /**
     * Writes the given byte array to a text file.
     * Each byte is formatted as "0xAB" and 16 bytes are written per line.
     *
     * @param fileName path/name of the output file
     * @param data     the bytes to write
     * @throws IOException if writing the file fails
     */
    static void writeBytesAsHexLines(String fileName, byte[] data) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        try (BufferedWriter out = new BufferedWriter(new FileWriter(fileName))) {
            int countInLine = 0;
            for (int i = 0; i < data.length; i++) {
                // Format one byte as 0xAB
                int unsigned = data[i] & 0xFF;
                String token = String.format("0x%02X", unsigned);

                // Write token, followed by comma+space except after the 16th in a line
                out.write(token);
                countInLine++;

                boolean endOfLine = (countInLine == 16);
                boolean lastByte  = (i == data.length - 1);

                if (endOfLine || lastByte) {
                    out.newLine();
                    countInLine = 0;
                } else {
                    out.write(", ");
                }
            }
        }
    }

    // --- tiny demo ---
    public static void main(String[] args) throws IOException {
        byte[] demo = new byte[40];
        for (int i = 0; i < demo.length; i++) demo[i] = (byte) i;
        writeBytesAsHexLines("bytes.txt", demo);
        // bytes.txt will contain lines like:
        // 0x00, 0x01, 0x02, ... 16 per line
    }
}