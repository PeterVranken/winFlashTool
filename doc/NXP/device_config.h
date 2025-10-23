/***********************************************************************************
* Copyright (c) 2018, NXP
* All rights reserved.
*
* Redistribution and use in source and binary forms, with or without modification,
* are permitted provided that the following conditions are met:
*
* o Redistributions of source code must retain the above copyright notice, this list
*   of conditions and the following disclaimer.
*
* o Redistributions in binary form must reproduce the above copyright notice, this
*   list of conditions and the following disclaimer in the documentation and/or
*   other materials provided with the distribution.
*
* o Neither the name of NXP, Inc. nor the names of its
*   contributors may be used to endorse or promote products derived from this
*   software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
* ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
* WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
* ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
* (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
* LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
* ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
* (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
* SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
***********************************************************************************/
/***********************************************************************************
* Project Name           : NXP Processor Boot-Loader
*
* @Revision Number       : 1.0
*
* @File Name             : device_config.h
*
* Target Compiler        : S32 Design Studio
*
* Created By             : Pham Van Lap
*
* Created on Date        : 11-Feb-2019
*
* @Brief Description     : Source file for Boot-Loader
************************************************************************************
*
*                             Revision History
*-----------------------------------------------------------------------------------
*     Date                  Author                     Description
*   ----------           --------------           -----------------------
*   03-21-2018            Pham Van Lap               Update and Optimize
*   ----------           --------------           -----------------------
*   04-21-2018            Pham Van Lap                   Optimize
***********************************************************************************/
#ifndef _DEVICE_CONFIG_H_
#define _DEVICE_CONFIG_H_
/***********************************************************************************
* Includes
***********************************************************************************/
#include "device_registers.h"
#include "flash_c55_driver.h"
#include "uart_driver.h"
#include "can_driver.h"
#include "s32_core_e200.h"
/**********************************************************************************/
extern void VTABLE(void);
/**********************************************************************************/
#define C55FMC_MEMORY_SUPPORT
#define FLASH_BLOCK_NUMBERS             (15) // PhM: changed for MPC5775B
/***********************************************************************************
* Macros
***********************************************************************************/
#define APP_START_DELAY                 (10000)
#define UART                            (eSCI_0)
#define FlexCAN                         (CAN_0)
#define CFLASH_DATA_BUF_SIZE            (24)
#define RCHW_VAL                        (0x005A0000)
#define RCHW_VAL_MASK                   (0x00FF0000)
#define APP_KEY                         (0x55AA55AA) /* Key for valid application */
#define RAM_VAR_AREA_SIZE               (0x3C00)
#define FLASH_BL_AREA_SIZE              (0x4000)
/***********************************************************************************
* Struts
***********************************************************************************/
typedef struct
{
	uint32_t appStartTimer;
	uint8_t validApp;
	uint32_t appDelay;
}startup_variable_t;
/***********************************************************************************
* Global functions
***********************************************************************************/
void SystemClockInit(void);
void FLASH_Init(void);
void UART_Init(void);
void FlexCAN_Init(void);
void SystemConfigReset(void);
#endif /* _DEVICE_CONFIG_H_ */
/***********************************************************************************
********************************* END OF FILE **************************************
***********************************************************************************/
