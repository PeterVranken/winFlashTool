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
#ifndef _CCP_DRIVER_H_
#define _CCP_DRIVER_H_
/**************************************************************************
**************************************************************************/
#include "device_config.h"
/**************************************************************************
**************************************************************************/
#define CCP_BYTE                        uint8_t
#define CCP_BYTEPTR                     uint8_t*
#define CCP_WORD                        uint16_t
#define CCP_WORDPTR                     uint16_t*
#define CCP_DWORD                       uint32_t
#define CCP_DWORDPTR                    uint32_t*
/**************************************************************************
 * Returncodes
**************************************************************************/
#define CRC_OK                          0x00
/**************************************************************************
 * C3 (Errors)
**************************************************************************/
#define CRC_CMD_UNKNOWN                 0x30
#define CRC_CMD_SYNTAX                  0x31
#define CRC_OUT_OF_RANGE                0x32
#define CRC_ACCESS_DENIED               0x33
#define CRC_OVERLOAD                    0x34
#define CRC_ACCESS_LOCKED               0x35
/**************************************************************************
 * Session Status
**************************************************************************/
#define SS_TMP_DISCONNECTED             0x10
#define SS_CONNECTED                    0x20
/**************************************************************************
 * Return values for ccpWriteMTA and ccpCheckWriteEEPROM
**************************************************************************/
#define CCP_WRITE_DENIED                0x00
#define CCP_WRITE_OK                    0x01
#define CCP_WRITE_PENDING               0x02
#define CCP_WRITE_ERROR                 0x03
/**************************************************************************
 * Bit masks for ccp.SendStatus
**************************************************************************/
#define CCP_CRM_REQUEST                 0x01
#define CCP_DTM_REQUEST                 0x02
#define CCP_USR_REQUEST                 0x04
#define CCP_CMD_PENDING                 0x08
#define CCP_CRM_PENDING                 0x10
#define CCP_DTM_PENDING                 0x20
#define CCP_USR_PENDING                 0x40
#define CCP_TX_PENDING                  0x80
#define CCP_SEND_PENDING (CCP_DTM_PENDING|CCP_CRM_PENDING|CCP_USR_PENDING)
/**************************************************************************
 * CCP parameters
**************************************************************************/
#define CCP_STATION_ADDR                0x00
/**************************************************************************
 * ccp structure type
**************************************************************************/
#define CCP_CONNECT                     0x01
#define CCP_SET_MTA                     0x02
#define CCP_UPLOAD                      0x04
#define CCP_CLEAR_MEMORY                0x10
#define CCP_PROGRAM                     0x18
#define CCP_DIAG_SERVICE                0x20
#define CCP_PROGRAM6                    0x22
#define CCP_CONNECTED                   0x01
#define CCP_STATION_ADDR                0x00
#define CCP_PACKET_ID                   0xFF
#define CRC_OUT_OF_RANGE                0x32
#define CRC_ACKNOWLEDGE                 0x00
#define CRC_ACCESS_DENIED               0x33
/**************************************************************************
 * Command Processor
**************************************************************************/
CCP_BYTE ccpProcess(void);
/**************************************************************************
 * Jumps to application boot sector
**************************************************************************/
CCP_BYTE JumpToApp(CCP_BYTEPTR address, CCP_BYTE getAddr);
/**************************************************************************
 * Initialization
**************************************************************************/
void ccpInit(void);
/***********************************************************************************
*   Function: VerifyApp
*   Description: This function will check an valid app with the KEY is exist
***********************************************************************************/
CCP_BYTE VerifyApp(void);
/**************************************************************************
**************************************************************************/
#endif /* _CCP_DRIVER_H_ */
/**************************************************************************
**************************************************************************/
