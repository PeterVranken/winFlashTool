/*****************************************************************************
| Project Name:   C C P - Driver
|    File Name:   CCP.H
|
|  Description:
|   CCP driver example.
|   CANape CAN Calibration Tool.
|
|-----------------------------------------------------------------------------
|               C O P Y R I G H T
|-----------------------------------------------------------------------------
| Copyright (c)  2001-2003 by Vector Informatik GmbH.     All rights reserved.
|-----------------------------------------------------------------------------
|               A U T H O R   I D E N T I T Y
|-----------------------------------------------------------------------------
| Initials     Name                      Company
| --------     ---------------------     -------------------------------------
| Bus          Sabine Bücherl            Vector Informatik GmbH
| Ds           Sven Deckardt             Vector Informatik GmbH
| Hp           Armin Happel              Vector Informatik GmbH
| Tri          Frank Triem               Vector Informatik GmbH
| Za           Rainer Zaiser             Vector Informatik GmbH
|-----------------------------------------------------------------------------
|               R E V I S I O N   H I S T O R Y
|-----------------------------------------------------------------------------
|  Date       Version  Author  Description
| ----------  -------  ------  -----------------------------------------------
| 2000-24-09  1.29.00  Za      - New define CCP_CHECKSUM_BLOCKSIZE
| 2000-29-11  1.30.00  Za      - #ifndef CCP_EXTERNAL_STATION_ID
| 2001-08-02  1.31.00  Za      - new define CCP_DAQ_BASE_ADDR
|                              - new function ccpGetDaqPointer
| 2001-30-05  1.32.00  Za      - Reserved word "data" in KEIL Compiler for C5x5
|                              - Prefix CCP_ for all #defines
| 2001-14-09  1.33.00  Za      - #define CCP_ODT_ENTRY_SIZE
|                              - #define CCP_INTEL,CCP_MOTOROLA
| 2001-28-10  1.34.00  Za      - ccpSend return value removed
|                              - Transmission error handling should be done by the user
| 2002-08-04  1.35.00  Za      - #define CCP_CPUTYPE_32BIT
|                              - Max checksum block size is DWORD on 32 bit CPUs
| 2002-02-06  1.36.00  Za      - #undef CCP_DAQ for drivers without DAQ fixed
|                              - double - float conversion for SHORT_UPLOAD, DNLOAD and DAQ
| 2002-17-07  1.37.00  Ds      - Fixed the version nr. because the version was in
|                                the comment 1.36 but 135 was define.
|                              - Set #define CCP_DRIVER_VERSION to 137
| 2002-14-11  1.37.01  Hp      - define CCP_MAX_DAQ only if CCP_DAQ is defined
| 2002-27-11  1.37.02  Ds      - delete the query of extended id
| 2003-05-28  1.37.02  Bus     - added V_MEMROM0
| 2003-08-11  1.37.03  Tri     - implemented P_MEM_ROM and P_MEM_RAM to support M16C Mitsubishi.
| 2003-10-14  1.38.00  Tri     - version skipped due to special version for TMS320
| 2003-10-14  1.39.00  Tri     - version skipped due to special version for TMS320
| 2003-10-14  1.40.00  Tri     - merge of versions: 1.37.03, 1.37.02
| 2003-10-16  1.41.00  Ds      - minor bugfix set ROM to CCP_ROM
| 2003-10-16  1.41.01  Ds      - change the position of CCP_ROM
| 2003-10-21  1.42.00  Tri     - change the position of CCP_ROM
| 2019-13-03  Pham Van Lap     - Update and optimize to only using for NXP boot-loader
|***************************************************************************/
/***********************************************************************************
* Includes
***********************************************************************************/
#include "ccp_driver.h"
/***********************************************************************************
* Global variables
***********************************************************************************/
typedef enum
{
    ERASE_DISABLE = 0x00,
    ERASE_ENABLE = 0x01
}ccp_flash_erase_t;
/***********************************************************************************
***********************************************************************************/
typedef struct
{
    CCP_BYTE crm[8];        /* CRM Command Return Message buffer */
    CCP_BYTE sessionStatus;
    CCP_BYTE sendStatus;
    CCP_BYTEPTR mta;     /* Memory Transfer Address */
} ccp_stack_t;
/***********************************************************************************
***********************************************************************************/
can_msg_t canMsg;  /* CAN data store variable */
ccp_stack_t ccp;
ccp_flash_erase_t flash_erase;
CCP_DWORD flashDataSize;
CCP_DWORD flashByteSize;
CCP_DWORD flashBytesLoaded;
CCP_BYTEPTR flashStartAddr;
CCP_BYTEPTR appStartAddr;
CCP_BYTE count = 0;
CCP_BYTE communication = 0; /* 1: CAN active, 0: UART active */
CCP_BYTE memory_read_status = 0; /* 1: read memory, 0: not read */
CCP_BYTE uartDataBuf[8]; /* UART data store variable */
CCP_DWORD flashDataBuf[6]; /* This needs to be word aligned integer mult of 4 */
extern startup_variable_t startVar;
#ifdef C55FMC_MEMORY_SUPPORT
extern const CCP_DWORD FlashBlocks[FLASH_BLOCK_NUMBERS][2];
extern const CCP_DWORD RAMMemory[2];
extern flash_block_select_t blockSelect;
#else
extern flash_ssd_config_t flashSSD;
#endif
/***********************************************************************************
* Function: ccpSendCrm
* Description: Send a CRM, if no other message is pending
***********************************************************************************/
static void ccpSendCrm(void);
/***********************************************************************************
* Function: ccpSendCallBack
* Description: Send notification callback
***********************************************************************************/
static CCP_BYTE ccpSendCallBack(void);
/***********************************************************************************
* Function: Command Processor
* Description:
***********************************************************************************/
static void ccpCommand(CCP_BYTEPTR msg);
/***********************************************************************************
* Function: ccpSend
* Description: Transmit request of a DTO CAN message to the CAN driver
***********************************************************************************/
static void ccpSend(CCP_BYTEPTR msg);
/***********************************************************************************
* Function: ccpFlashClear
* Description: Erase the flash memory specified
***********************************************************************************/
static CCP_BYTE ccpFlashClear(CCP_BYTEPTR address, CCP_DWORD size);
/***********************************************************************************
* Function: ccpFlashProgramm
* Description: Program the flash memory with data specified
***********************************************************************************/
static CCP_BYTE ccpFlashProgramm(CCP_BYTEPTR data, CCP_BYTEPTR address, CCP_BYTE size);
/***********************************************************************************
* Function: ccpRxBufGetData
* Description: Receive 8 byte of Data packet for CCP
***********************************************************************************/
static CCP_BYTE ccpRxBufGetData(CCP_DWORDPTR bufAddress);
/***********************************************************************************
* Function: MemoryAreaValid
* Description: Check memory address is available
***********************************************************************************/
static CCP_BYTE MemoryAreaValid(CCP_BYTEPTR address);
/***********************************************************************************
* Function: ccpReadMTA
* Description: Determine if a valid application is present with key
***********************************************************************************/
static CCP_BYTE ccpReadMTA(CCP_BYTEPTR address, CCP_BYTE size);
/***********************************************************************************
* Function: ccpFlashClear
* Description: This function will erase the flash memory specified
***********************************************************************************/
static CCP_BYTE ccpFlashClear(CCP_BYTEPTR address, CCP_DWORD size)
{
    #ifdef C55FMC_MEMORY_SUPPORT
    flash_state_t opResult;
    #else
    CCP_DWORD i = 0;
    CCP_DWORD dest; /* Address of the target location */
    #endif
    status_t eraseStatus = STATUS_SUCCESS;
    /* Make sure that dataSize is double word aligned (integer multiple of 8) */
// TODO + or |? This is not an uprounding alignmnet, isn't it?
// TODO The ECU should just double-check not modify. It doesn't have the power to check if the extended area won't overlap with earlier or later sent data. All kind of these checks should be done in the host. The ECU may abort/reject in case of failures
    if(size&0x07)flashByteSize = (size & 0xF8)|0x08;
    else flashByteSize = size;
    flashDataSize = size;
    flashStartAddr = address;
    flashBytesLoaded = 0;
    /**************************************************************************/
// TODO Here and at many other locations: It is suspicious that we just check the start address but not the end address of the area. This may be correct as all checks are done by the host, but we should at least document this (if so) and double-check here
    if(MemoryAreaValid(address))
    {
        #if defined(C55FMC_MEMORY_SUPPORT)
        if((flash_erase==ERASE_ENABLE)&&(address<(CCP_BYTEPTR)RAMMemory[0])) /* Clear Flash only */
        {
            FLASH_Init();
            eraseStatus = FLASH_DRV_Erase(ERS_OPT_MAIN_SPACE, &blockSelect);
            if(STATUS_SUCCESS == eraseStatus)
            {
                do
                {
                    // TODO This is a blocking state, where we need to service the SBC.
                    eraseStatus = FLASH_DRV_CheckEraseStatus(&opResult);
                }while(eraseStatus == STATUS_FLASH_INPROGRESS);
            }
        #else
        if((flash_erase==ERASE_ENABLE)&&(address<(CCP_BYTEPTR)flashSSD.DFlashBase)) /* Clear Flash only */
        {
            dest = flashSSD.PFlashBase|FLASH_BL_AREA_SIZE;
            for(i = 0; i < ((flashSSD.PFlashSize-FLASH_BL_AREA_SIZE)/FEATURE_FLS_PF_BLOCK_SECTOR_SIZE); i++)
            {
                eraseStatus = FLASH_DRV_EraseSector(&flashSSD, dest, (uint32_t)FEATURE_FLS_PF_BLOCK_SECTOR_SIZE);
                dest += FEATURE_FLS_PF_BLOCK_SECTOR_SIZE;
            }
        #endif
            flash_erase = ERASE_DISABLE;
            if(eraseStatus==STATUS_SUCCESS) return CRC_ACKNOWLEDGE;
            else return CRC_ACCESS_DENIED;
        }
        return CRC_ACKNOWLEDGE;
    }
    return CRC_OUT_OF_RANGE;
}
/***********************************************************************************
*   Function: ccpFlashProgramm
*   Description: This function will program the flash memory with data specified
***********************************************************************************/
static CCP_BYTE ccpFlashProgramm(CCP_BYTEPTR data, CCP_BYTEPTR address, CCP_BYTE size)
{
    CCP_BYTE i;
    CCP_BYTE indexLeft = 0;
    CCP_DWORD tempSize;
    CCP_BYTEPTR dataPtr;
    #if defined(C55FMC_MEMORY_SUPPORT)
    flash_state_t opResult;
    flash_context_data_t pCtxData;
    #endif
    status_t flashStatus = STATUS_SUCCESS;
    if(flashDataSize > 0) /* is there data left to be programmed based on what was cleared */
    {
        dataPtr = (CCP_BYTEPTR)&flashDataBuf;
        dataPtr = dataPtr + flashBytesLoaded;
// TODO Response is not generaly correct, only is all chunks fit into the alignment grid. Otherwise we need to first increment the address and then split into 4 bytes
        ccp.crm[3] = 0;
        ccp.crm[4] = (CCP_BYTE)((CCP_DWORD)address >> 24);
        ccp.crm[5] = (CCP_BYTE)((CCP_DWORD)address >> 16);
        ccp.crm[6] = (CCP_BYTE)((CCP_DWORD)address >> 8);
        ccp.crm[7] = (CCP_BYTE)((CCP_DWORD)address) + size;
        for(i = 0; i < size; i++)
        {
            if((flashBytesLoaded < CFLASH_DATA_BUF_SIZE)&&((flashDataSize - i) > 0))
            {
                *dataPtr = *data;
                data++;
                dataPtr++;
                flashBytesLoaded++;
                indexLeft++;
            }
        }
        if(flashDataSize > size)
        {
            flashDataSize = flashDataSize - indexLeft;/* keep track of remaining data bytes to program */
            tempSize = flashBytesLoaded; /* set size to remaining data bytes to program */
        }
        else
        {
            flashDataSize = 0;
            tempSize = flashByteSize; /* set size to remaining bytes to program */
            while(flashBytesLoaded < flashByteSize)
            {
                if(*dataPtr != 0xFF)
                {
                    *dataPtr = 0xFF; /* fill with empty data */
                    dataPtr++;
                }
                else flashBytesLoaded = flashByteSize;
            }
        }
        if((flashDataSize == 0)||(tempSize >= CFLASH_DATA_BUF_SIZE))
        {
            if(MemoryAreaValid(address))
            {
                #if defined(C55FMC_MEMORY_SUPPORT)
                if(address < (CCP_BYTEPTR)RAMMemory[0]) /* Programming on Flash Area */
                {
                    flashStatus = FLASH_DRV_Program(&pCtxData, (CCP_DWORD)flashStartAddr, tempSize, (CCP_DWORD)&flashDataBuf);
                    if (STATUS_SUCCESS == flashStatus)
                    {
                        do
                        {
                            // TODO This is a blocking state, where we need to service the SBC.
                            flashStatus = FLASH_DRV_CheckProgramStatus(&pCtxData, &opResult);
                        }while(flashStatus == STATUS_FLASH_INPROGRESS);
                    }
                #else
                if(address < (CCP_BYTEPTR)flashSSD.PFlashSize) /* Programming on Flash Area */
                {
                    flashStatus = FLASH_DRV_Program(&flashSSD, (CCP_DWORD)flashStartAddr, tempSize, (CCP_BYTEPTR)&flashDataBuf);
                #endif
                }
                else /* Programming on RAM Area */
                {
                    for(i = 0; i < tempSize; i++)
                    {
                        *((CCP_BYTEPTR)flashStartAddr + i) = *((CCP_BYTEPTR)flashDataBuf + i);
                        if(*((CCP_BYTEPTR)flashDataBuf + i)!=*((CCP_BYTEPTR)flashStartAddr + i))
                        {
                            flashStatus = STATUS_ERROR;
                            break;
                        }
                    }
                }
                flashStartAddr = flashStartAddr + CFLASH_DATA_BUF_SIZE;
                flashBytesLoaded = 0;
                flashByteSize = flashByteSize - tempSize; /* keep track of remaining bytes to program */
                if(flashStatus==STATUS_SUCCESS) return CRC_ACKNOWLEDGE;
                else return CRC_ACCESS_DENIED; /* access denied or write error */
            }
            return CRC_OUT_OF_RANGE;
        }
        return CRC_ACKNOWLEDGE;
    }
    return CRC_ACCESS_DENIED;
}
/***********************************************************************************
*   Function: VerifyApp
*   Description: This function will check an valid app with the KEY is exist
***********************************************************************************/
CCP_BYTE VerifyApp(void)
{
    CCP_DWORD i;
    CCP_DWORDPTR u32Ptr;
    /* Verify application on flash memory */
    #if defined(C55FMC_MEMORY_SUPPORT)
    u32Ptr = (CCP_DWORDPTR)FlashBlocks[0][0];
    for(i = 0; i<(FlashBlocks[0][1]/4); i++) /* verify app on flash memory */
    {
        if(((*u32Ptr)&RCHW_VAL_MASK) == RCHW_VAL)
        {
    #else
    u32Ptr = (CCP_DWORDPTR)(flashSSD.PFlashBase|FLASH_BL_AREA_SIZE);
    for(i = 0; i<((flashSSD.PFlashSize-FLASH_BL_AREA_SIZE)/4); i++) /* verify app on flash memory */
    {
        if((*u32Ptr) == RCHW_VAL)
        {
    #endif
            startVar.appDelay = *(u32Ptr+2);
            if((*(u32Ptr+3) != 0xFFFFFFFF) && MemoryAreaValid((CCP_BYTEPTR)(*(u32Ptr+3))))
            {
                if(*(CCP_DWORDPTR)(*(u32Ptr+3)) == APP_KEY && MemoryAreaValid((CCP_BYTEPTR)(*(u32Ptr+1))))
                {
                    appStartAddr = (CCP_BYTEPTR)(*((CCP_DWORDPTR)u32Ptr+1));
                    return 1;
                }
                else
                {
                    flash_erase = ERASE_ENABLE;
                    return 0;
                }
            }
            else
            {
                flash_erase = ERASE_ENABLE;
                return 0;
            }
        }
        u32Ptr++;
    }
    /* Verify application on RAM memory */
    #if defined(C55FMC_MEMORY_SUPPORT)
    u32Ptr = (CCP_DWORDPTR)(RAMMemory[0]|RAM_VAR_AREA_SIZE);
    for(i = 0; i<(RAMMemory[1]-RAM_VAR_AREA_SIZE)/4; i++)
    {
        if(((*u32Ptr)&RCHW_VAL_MASK) == RCHW_VAL)
        {
    #else
    u32Ptr = (CCP_DWORDPTR)(flashSSD.DFlashBase|RAM_VAR_AREA_SIZE);
    for(i = 0; i<(flashSSD.DFlashSize-RAM_VAR_AREA_SIZE)/4; i++)
    {
        if((*u32Ptr) == RCHW_VAL)
        {
    #endif
            if((*(u32Ptr+3) != 0xFFFFFFFF) && MemoryAreaValid((CCP_BYTEPTR)(*(u32Ptr+3))))
            {
                if(*(CCP_DWORDPTR)(*(u32Ptr+3)) == APP_KEY && MemoryAreaValid((CCP_BYTEPTR)(*(u32Ptr+1))))
                {
                    appStartAddr = (CCP_BYTEPTR)(*((CCP_DWORDPTR)u32Ptr+1));
                    return 1;
                }
                else
                {
                    flash_erase = ERASE_ENABLE;
                    return 0;
                }
            }
        }
        u32Ptr++;
    }
    flash_erase = ERASE_ENABLE;
    return 0;
}
/***********************************************************************************
*   Function: MemoryAreaValid
*   Description: This function will check an valid app with the KEY is exist
***********************************************************************************/
static CCP_BYTE MemoryAreaValid(CCP_BYTEPTR address)
{
    #if defined(C55FMC_MEMORY_SUPPORT)
    CCP_BYTE i = 0;
    if(address<(CCP_BYTEPTR)RAMMemory[0])
    {
        for(i=0; i<FLASH_BLOCK_NUMBERS; i++)
        {
            if((address>=(CCP_BYTEPTR)FlashBlocks[i][0])&&(address<=(CCP_BYTEPTR)(FlashBlocks[i][0]+FlashBlocks[i][1])))
            {
                return 1;
            }
        }
    }
    else
    {
        if((address>=(CCP_BYTEPTR)RAMMemory[0]+RAM_VAR_AREA_SIZE)&&(address<(CCP_BYTEPTR)(RAMMemory[0]+RAMMemory[1])))
        {
            return 1;
        }
    }
    #else
    if(address<(CCP_BYTEPTR)flashSSD.PFlashSize)
    {
        if((address>=(CCP_BYTEPTR)(flashSSD.PFlashBase|FLASH_BL_AREA_SIZE))&&(address<=(CCP_BYTEPTR)flashSSD.PFlashSize))
        {
            return 1;
        }
    }
    else
    {
    if((address>=(CCP_BYTEPTR)(flashSSD.DFlashBase|RAM_VAR_AREA_SIZE))&&(address<(CCP_BYTEPTR)(flashSSD.DFlashBase+flashSSD.DFlashSize)))
        {
            return 1;
        }
    }
    #endif
    return 0;
}
/***********************************************************************************
*   Function: JumpToApp
*   Description: Jumps to application boot sector
***********************************************************************************/
// TODO Signature of function is bullshit. Either use passed address (from CRO message) or
// rely on VerifyApp(), which checks the list of (virtual) RCHW boot sectors.
//   However, what is the use case of a CCP provided start address? Maybe laoding into RAM
// for testing? But will the flash routines make the destinction between RAM and ROM
// addresses while loading data? Likely not.
//   Could be sorted out by double-checking the CCP provided address against the RCHW
// evaluation and anding the CRO with negative repsonse and no start of app in case of a
// mismatch.
CCP_BYTE JumpToApp(CCP_BYTEPTR address, CCP_BYTE getAddr)
{
//    CCP_BYTE checkApp = VerifyApp();
    if(getAddr) address = appStartAddr;
    if(startVar.validApp)
    {
        /* Reset all configuration of boot-loader to default */
        SystemConfigReset();
        /* Assign application address for startup */
        void (* const AppStartup)(void) = (void(*)())(address);
        /* Jump to user application */
        AppStartup();
    }
    return (startVar.validApp);
}
/***********************************************************************************
*   Function: ccpReadMTA
*   Description: Read n bytes from memory
***********************************************************************************/
inline CCP_BYTE ccpReadMTA(CCP_BYTEPTR address, CCP_BYTE size)
{
    while(size-->0)
    {
        *address = *(ccp.mta);
        address++;
        ccp.mta++;
    }
    return CRC_ACKNOWLEDGE;
}
/***********************************************************************************
*   Function: ccpSendCallBack
*   Description: Send notification callback
***********************************************************************************/

// TODO The triple of ccpSendCallBack(), ccpSend() and ccpSendCrm() looks much like
// bullshit, maybe a relict from some queued sending. Here, we have an immediate response
// on each CRO and no queing or asynconity is required. Reduce to a simple call of CAN
// transmit.
static CCP_BYTE ccpSendCallBack(void)
{
    ccp.sendStatus &= ~CCP_SEND_PENDING;
    if(ccp.sendStatus&CCP_CRM_REQUEST)
    {
        ccp.sendStatus &= ~CCP_CRM_REQUEST;
        ccpSendCrm(); /* Send a CRM message */
        return 1;
    }
    return 0;
}
/***********************************************************************************
*   Function: ccpSend
*   Description: Transmit request of a DTO CAN message to the CAN/UART driver
***********************************************************************************/
static void ccpSend(CCP_BYTEPTR msg)
{
    if(communication) /* FlexCAN communication */
    {
        if(CanTxMbEmpty(CAN_TX_MAILBOX))
        {
            CanTxMsg(CAN_MB_TX_ID, CAN_TX_MAILBOX, CAN_MB_DATA_LENGTH, (CCP_BYTEPTR)msg, CAN_MB_ID_STD);
        }
    }
    else /* UART communication */
    {
        if(UartRxBufEmpty())
        {
            UartTxMsg((CCP_BYTEPTR)msg, UART_DATA_LENGTH);
        }
    }
    ccpSendCallBack();
}
/***********************************************************************************
*   Function: ccpSendCrm
*   Description: Send a CRM, if no other message is pending
***********************************************************************************/
static void ccpSendCrm(void)
{
    if (ccp.sendStatus&CCP_SEND_PENDING)
    {
        ccp.sendStatus |= CCP_CRM_REQUEST;
    }
    else
    {
        ccp.sendStatus |= CCP_CRM_PENDING;
        ccpSend(ccp.crm);
    }
}
/***********************************************************************************
*   Function: ccpCommand
*   Description: Command Processor
*  @param[in]
* Pointer to the array of 8 payload bytes of the CRO message.\n
*   Caution, the data needs to be 4-Byte aligned.
***********************************************************************************/
inline void ccpCommand(CCP_BYTEPTR com)
{
    CCP_BYTE disconnect = 0;
    #if !defined(C55FMC_MEMORY_SUPPORT)
    CCP_DWORD address = 0;
    #endif
    ccp.crm[0] = CCP_PACKET_ID; /* 0xFF */
    ccp.crm[1] = CRC_ACKNOWLEDGE; /* 0x00 */
    ccp.crm[2] = com[1];
    ccp.crm[3] = 0xFE;
    if(com[0] == CCP_CONNECT) /* Check connecting */
    {
        if(com[2] == CCP_STATION_ADDR)
        {
            ccp.sessionStatus = SS_CONNECTED;
        }
    }
    else if(ccp.sessionStatus&SS_CONNECTED) /* Handle other commands only if connected */
    {
        ccp.crm[0] = CCP_PACKET_ID;
        ccp.crm[1] = CRC_ACKNOWLEDGE;
        ccp.crm[2] = com[1];
        switch(com[0]) /* Select command */
        {
            case CCP_SET_MTA: /* Set transfer address */
            {
                #ifdef C55FMC_MEMORY_SUPPORT
                /* Both, our CPU and the CCP protocol use MSB endianess and we have the
                   correct alignement, so we can use a pointer access to read all four
                   bytes at once. */
                ccp.mta = (CCP_BYTEPTR)(*(CCP_DWORDPTR)&com[4]);
                #else
                // TODO Why don't we use the same pointer access here, too?
                // TODO Misleading: Why do we begin with |= ?
                address |= com[4];
                address = (address<<8)|com[5];
                address = (address<<8)|com[6];
                address = (address<<8)|com[7];
                ccp.mta = (CCP_BYTEPTR)address;
                #endif
                if(!MemoryAreaValid(ccp.mta))disconnect = 1;
            }break;

            case CCP_UPLOAD: /* Read only memory */
            {
                if(MemoryAreaValid(ccp.mta))ccpReadMTA(&ccp.crm[3], com[2]);
                else disconnect = 1;
            }break;

            case CCP_CLEAR_MEMORY: /* Clear Memory */
            {
                CCP_DWORD s;
                // TODO Why don't we use 2 times the word pointer access or 4 times the byte access?
                s = (((CCP_DWORD)(*(CCP_BYTEPTR)&com[4]))<<8)|
                    ((CCP_DWORD)(*(CCP_BYTEPTR)&com[5]))     |
                    (((CCP_DWORD)(*(CCP_WORDPTR)&com[2]))<<16);
                if(MemoryAreaValid(ccp.mta))ccpFlashClear(ccp.mta, s);
                else disconnect = 1;
            }break;

            case CCP_PROGRAM6: /* Data programming */
            {
                if(MemoryAreaValid(ccp.mta))
                {
                    ccpFlashProgramm(&com[2], ccp.mta, 6);
                    ccp.mta += 6;
                }
                else disconnect = 1;
            }break;

            // TODO We should add a support for DISCONNECT. Which looks much like the
            // better way to close the connection. Makes sense only, if we leave the
            // time to really send out the response (holds for both the services). This is
            // not so easy, as we don't have an API to get this information from the CAN
            // driver. Effectievly, it would mean to use a reasonable delay before jumping
            // into the application.
            // TODO It might be the better choice to reset the ECU instead of jumping to
            // the application. This will bring us either where we want to be or back into
            // the re-initialized FBL, capable of trying another download. Do we have a
            // reset API? Yes, via SBC servicing.
            case CCP_DIAG_SERVICE: /* Set diagnostic service */
            {
                ccp.crm[3] = 0x01;
                ccp.crm[4] = 0x00;
                #if defined(C55FMC_MEMORY_SUPPORT)
                appStartAddr = (CCP_BYTEPTR)(*(CCP_DWORDPTR)&com[3]);
                #else
                address |= com[3];
                address = (address<<8)|com[4];
                address = (address<<8)|com[5];
                address = (address<<8)|com[6];
                appStartAddr = (CCP_BYTEPTR)address;
                #endif
                // TODO Service number is a 2-byte value. However, careful! The spec says
                // so in the tabular description of the command but uses a single  byte
                // service number in the example. So maybe this is common sense?
                //   Copilot: The exact format of this service is not specified but
                // "usually" the service number is a single byte, e.g., found this way at
                // NI software.
                if((com[2] == 0x08) && VerifyApp())
                {
                    startVar.validApp = 1;
                    ccp.crm[4]=(CCP_BYTE)JumpToApp(appStartAddr, 0);
                    
                    // TODO We won't get here if we loaded a valid aapplication and the
                    // response message won't be sent out any more. However, in case of an
                    // invalid app, the response is wrong. JumpToApp returns a Boolean,
                    // whereas CCP requires a length and type information plus setting the
                    // MTA to let the host subsequently upload the actual result. The
                    // simplest and best way out is to set length of result to 0 and then
                    // return a negative response if app couldn't be started.
                }
            }break;
        } /* end of switch */
    }
    if(!disconnect)ccpSendCrm();
    else
    {
        count++;
        ccp.crm[1] = CRC_OUT_OF_RANGE;
        ccpSend(ccp.crm);
// TODO Add proper error handling
        if(count>5) while(1); /* Press reset button if here */
    }
}
/***********************************************************************************
*   Function: ccpRxBufGetData
*   Description: This function will receive 8 byte of Data packet for CCP
***********************************************************************************/
static CCP_BYTE ccpRxBufGetData(CCP_DWORDPTR bufAddress)
{
    CCP_BYTE i = 0;
    if(communication) /* Get data from CAN mailboxes */
    {
        CanRxMsg(CAN_RX_MAILBOX, &canMsg); /* Get data */
        *bufAddress = (CCP_DWORD)canMsg.data; /* Transfer data to address of bufAddr */
        return (1); /* Data available status */
    }
    else /* Get data from UART buffer */
    {
        if(UartRxNewDataSize() >= 8)
        {
            for(i=0; i<8; i++)uartDataBuf[i] = UartRxDataByte(); /* Get data */
            *bufAddress = (CCP_DWORD)&uartDataBuf; /* Transfer data to address of bufAddr */
            return (1); /* Data available status */
        }
    }
    return (0); /* 8-byte Data packet is not available */
}
/***********************************************************************************
*   Function: ccpProcess
*   Description: Check if RX buffer is fill to start processes CCP message
***********************************************************************************/
inline CCP_BYTE ccpProcess(void)
{
    CCP_DWORD temp;
    if(CanRxMbFull(CAN_RX_MAILBOX)>0)communication = 1;
    else
    {
        UartRxBufFill(); /* Fill UART buffer */
        communication = 0;
    }
// TODO Suspicious code: The return of the pointer to the CAN payload as a DWORD looks much
// like the attempt to ensure proper data alignment. )ccpCommand requires 4 Byte
// alignment.) However, coyping the address doesn't alter the alignment of the data in the
// CAN message object, provided by the CAN driver. This needs to be changed.
    if(ccpRxBufGetData(&temp))
    {
        ccpCommand((CCP_BYTEPTR)temp); /* Receive CCP CRO */
        return 1;
    }
    return 0;
}
/***********************************************************************************
*   Function: ccpInit
*   Description: Initialize variables/drivers for CCP communications and programming.
***********************************************************************************/
inline void ccpInit(void)
{
    CCP_BYTEPTR p;
    CCP_BYTEPTR pl;
    p = (CCP_BYTEPTR)&ccp;
    pl = p + sizeof(ccp);
    while (p<pl) *p++ = 0;
    UartBufInit();
    CanSetRxMsg(CAN_RX_MSG_ID, CAN_RX_MAILBOX, CAN_MB_ID_STD);
    flash_erase = ERASE_ENABLE;
}
/***********************************************************************************
********************************* END OF FILE **************************************
***********************************************************************************/
