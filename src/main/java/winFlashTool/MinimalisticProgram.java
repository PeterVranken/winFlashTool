package winFlashTool;

import java.lang.StringBuffer;
import peak.can.basic.*;
import org.apache.logging.log4j.*;
import winFlashTool.ccp.TimeoutTimer;

public class MinimalisticProgram
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(MinimalisticProgram.class);

    public static void main(String[] args)
    {
        PCANBasic can = null;
        TPCANStatus status = null;
        can = new PCANBasic();
        if(!can.initializeAPI())
        {
            _logger.error("Unable to initialize the API");
            return;
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
        long tiReadMax = 0
           , tiReadMin = Long.MAX_VALUE
           , tiReadAvg = 0
           , noRead = 0;
        long tiReadEmptyQMax = 0
           , tiReadEmptyQMin = Long.MAX_VALUE
           , tiReadEmptyQAvg = 0
           , noReadEmptyQ = 0;
        long tiExe = System.nanoTime()
           , noCycles = 0;
        TimeoutTimer timeoutNoRx = new TimeoutTimer(/*timeoutMillis*/ 1000*10);
        while((status == TPCANStatus.PCAN_ERROR_OK
               ||  status == TPCANStatus.PCAN_ERROR_QRCVEMPTY
              )
              && !timeoutNoRx.hasTimedOut()
             )
        {
            ++ noCycles;

            int noMsgsInQ = 0;
            //while(can.Read(TPCANHandle.PCAN_USBBUS1, msg, null) == TPCANStatus.PCAN_ERROR_OK)
            while(true)
            {
                final long tiStart = System.nanoTime();
                status = can.Read(TPCANHandle.PCAN_USBBUS1, msg, null);
                final long tiRead = System.nanoTime() - tiStart;

                if(status != TPCANStatus.PCAN_ERROR_OK)
                {
                    /* noCycles > 10: Avoid unrealistic maximum due to setup and
                       initialization effects. */
                    if(status == TPCANStatus.PCAN_ERROR_QRCVEMPTY &&  noCycles > 10)
                    {
                        ++ noReadEmptyQ;
                        tiReadEmptyQAvg += tiRead;
                        if(tiRead > tiReadEmptyQMax)
                            tiReadEmptyQMax = tiRead;
                        if(tiRead < tiReadEmptyQMin)
                            tiReadEmptyQMin = tiRead;
                    }

                    break;
                }

                timeoutNoRx.restart();

                if(noCycles > 10)
                {
                    ++ noRead;
                    tiReadAvg += tiRead;
                    if(tiRead > tiReadMax)
                        tiReadMax = tiRead;
                    if(tiRead < tiReadMin)
                        tiReadMin = tiRead;
                }

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
                    _logger.error("Unable to write the CAN message");
            }
            //if(noMsgsInQ > 1)
            //    System.out.printf("noMsgsInQ: %d\n", noMsgsInQ);
            //_logger.info("Loop left because of status {}", status.getValue());
        }

        tiExe = System.nanoTime() - tiExe;
        _logger.info( "Executed {} polling cycles, average execution time of cycle is {} ns"
                    , noCycles
                    , tiExe / noCycles
                    );

        /* Translate last error into human understandable text. */
        StringBuffer errMsg = new StringBuffer();
        status = can.GetErrorText(status, /*Language*/ (short)0, errMsg);
        assert status == TPCANStatus.PCAN_ERROR_OK: "Error in decoding error message";
        _logger.error("Last error: {}", errMsg.toString());

        assert noReadEmptyQ > 0: "This simple programm assumes a minimum runtime";
        _logger.info( "Timing of can.Read() (all measures in ns):\n"
                      + "CAN message received:\n"
                      + "  Max: {}\n"
                      + "  Min: {}\n"
                      + "  Avg: {}\n"
                      + "Empty queue:\n"
                      + "  Max: {}\n"
                      + "  Min: {}\n"
                      + "  Avg: {}\n"
                      + "Both modes in average:\n"
                      + "  Avg: {}\n"
                    , tiReadMax
                    , noRead > 0? tiReadMin: 0
                    , noRead > 0? tiReadAvg / noRead: 0
                    , tiReadEmptyQMax
                    , tiReadEmptyQMin
                    , tiReadEmptyQAvg / noReadEmptyQ
                    , (tiReadEmptyQAvg + tiReadAvg) / (noRead + noReadEmptyQ)
                    );
    }
}
