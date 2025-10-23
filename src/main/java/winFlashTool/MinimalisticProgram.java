package winFlashTool;

import java.lang.StringBuffer;
import peak.can.basic.*;

public class MinimalisticProgram
{
    public static void main(String[] args)
    {
        PCANBasic can = null;
        TPCANStatus status = null;
        can = new PCANBasic();
        if(!can.initializeAPI())
        {
            System.out.println("Unable to initialize the API");
            System.exit(0);
        }
        
        /* Arguments 3..5 are not used for the Plug&Play device PEAK-USB and PEAK-USB-FD.
           We set them to "don't care". */
        status = can.Initialize( TPCANHandle.PCAN_USBBUS1
                               , TPCANBaudrate.PCAN_BAUD_1M
                               , /*HwType*/ TPCANType.PCAN_TYPE_NONE
                               , /*IOPort*/ 0
                               , /*Interrupt*/ (short)0
                               );
        final TPCANMsg msg = new TPCANMsg();
        byte byte0 = 0;
        while(status == TPCANStatus.PCAN_ERROR_OK
              ||  status == TPCANStatus.PCAN_ERROR_QRCVEMPTY
             )
        {
            int noMsgsInQ = 0;
            //while(can.Read(TPCANHandle.PCAN_USBBUS1, msg, null) == TPCANStatus.PCAN_ERROR_OK)
            while(true)
            {
                status = can.Read(TPCANHandle.PCAN_USBBUS1, msg, null);
                if(status != TPCANStatus.PCAN_ERROR_OK)
                    break;
                 
                ++ noMsgsInQ;

                /* To see the effect of our SW, we modify the message. */
                final byte dlc = msg.getLength();
                if(dlc > 0)
                {
                    final byte[] data = msg.getData();
                    data[0] = byte0++;
                    msg.setData(data, dlc);

//                    for (byte b: data)
//                        System.out.printf("0x%02X, ", b);
//                    System.out.printf("\n");
                }

                /* To avoid bus conflicts, we modify the CAN ID. */
                msg.setID((msg.getID() + 1) & 0x7FF);

                status = can.Write(TPCANHandle.PCAN_USBBUS1, msg);
                if(status != TPCANStatus.PCAN_ERROR_OK)
                {
                    System.out.println("Unable to write the CAN message");
                    System.exit(0);
                }
            }
            //if(noMsgsInQ > 1)
            //    System.out.printf("noMsgsInQ: %d\n", noMsgsInQ);
            //System.out.printf("Loop left because of status %d\n", status.getValue());
        }
        
        /* Translate last error into human understandable text. */
        StringBuffer errMsg = new StringBuffer();
        status = can.GetErrorText(status, /*Language*/ (short)0, errMsg);
        assert status == TPCANStatus.PCAN_ERROR_OK: "Error in decoding error message";
        System.out.println("Last error: " + errMsg.toString());
    }
}
