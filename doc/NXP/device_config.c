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
* @File Name             : device_config.c
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
#include "device_config.h"
extern startup_variable_t startVar;
/****************************************************************************************/
flash_block_select_t blockSelect =
{
    .lowBlockSelect = 0x02UL, /* Partition 1 Block 1 */
    .midBlockSelect = 0x02UL, /* Partition 3 Block 1 */
    .highBlockSelect = 0x00UL,
    .first256KBlockSelect = 0xFFFFFFFFUL,
    .second256KBlockSelect = 0x00UL,
};
/****************************************************************************************/
/** Contains which flash blocks are valid to be flashed. First one is searched for application signature. */
const uint32_t FlashBlocks[FLASH_BLOCK_NUMBERS][2] =
{
//	{0x00000000, 0x10000}, /* {064k_block_addr, 064k_block_size} -> Data flash
//	{0x00010000, 0x10000}, /* {064k_block_addr, 064k_block_size} -> Partition for search a valid RCHW of second application */
//	{0x00020000, 0x10000}, /* {064k_block_addr, 064k_block_size} -> Data flash
//	{0x00030000, 0x10000}, /* {064k_block_addr, 064k_block_size} -> Data flash
//	{0x00800000, 0x40000}, /* {256k_block_addr, 256k_block_size} -> skipped because would be highest priority for boot address */
	{0x00840000, 0x40000}, /* {256k_block_addr, 256k_block_size} -> Needs to contain application signature */
    {0x00880000, 0x40000}, /* {256k_block_addr, 256k_block_size} */
    {0x008C0000, 0x40000}, /* {256k_block_addr, 256k_block_size} */
    {0x00900000, 0x40000}, /* {256k_block_addr, 256k_block_size} */
    {0x00940000, 0x40000}, /* {256k_block_addr, 256k_block_size} */
    {0x00980000, 0x40000}, /* {256k_block_addr, 256k_block_size} */
    {0x009C0000, 0x40000}, /* {256k_block_addr, 256k_block_size} */
    {0x00A00000, 0x40000}, /* {256k_block_addr, 256k_block_size} */
    {0x00A40000, 0x40000}, /* {256k_block_addr, 256k_block_size} */
    {0x00A80000, 0x40000}, /* {256k_block_addr, 256k_block_size} */
    {0x00AC0000, 0x40000}, /* {256k_block_addr, 256k_block_size} */
    {0x00B00000, 0x40000}, /* {256k_block_addr, 256k_block_size} */
    {0x00B40000, 0x40000}, /* {256k_block_addr, 256k_block_size} */
    {0x00B80000, 0x40000}, /* {256k_block_addr, 256k_block_size} */
    {0x00BC0000, 0x40000}, /* {256k_block_addr, 256k_block_size} */
//    {0x00C00000, 0x40000}, /* {256k_block_addr, 256k_block_size} -> not valid for MPC5775B */
//    {0x00C40000, 0x40000}, /* {256k_block_addr, 256k_block_size} -> not valid for MPC5775B */
//    {0x00C80000, 0x40000}, /* {256k_block_addr, 256k_block_size} -> not valid for MPC5775B */
//    {0x00CC0000, 0x40000}, /* {256k_block_addr, 256k_block_size} -> not valid for MPC5775B */
//    {0x00D00000, 0x40000}, /* {256k_block_addr, 256k_block_size} -> not valid for MPC5775B */
//    {0x00D40000, 0x40000}, /* {256k_block_addr, 256k_block_size} -> not valid for MPC5775B */
//    {0x00D80000, 0x40000}, /* {256k_block_addr, 256k_block_size} -> not valid for MPC5775B */
//    {0x00DC0000, 0x40000}, /* {256k_block_addr, 256k_block_size} -> not valid for MPC5775B */
//    {0x00E00000, 0x40000}, /* {256k_block_addr, 256k_block_size} -> not valid for MPC5775B */
//    {0x00E40000, 0x40000}, /* {256k_block_addr, 256k_block_size} -> not valid for MPC5775B */
//    {0x00E80000, 0x40000}, /* {256k_block_addr, 256k_block_size} -> not valid for MPC5775B */
//    {0x00EC0000, 0x40000}, /* {256k_block_addr, 256k_block_size} -> not valid for MPC5775B */
//    {0x00F00000, 0x40000}, /* {256k_block_addr, 256k_block_size} -> not valid for MPC5775B */
//    {0x00F40000, 0x40000}, /* {256k_block_addr, 256k_block_size} -> not valid for MPC5775B */
//    {0x00F80000, 0x40000}, /* {256k_block_addr, 256k_block_size} -> not valid for MPC5775B */
//    {0x00FC0000, 0x40000}  /* {256k_block_addr, 256k_block_size} -> not valid for MPC5775B */
};
/****************************************************************************************/
const uint32_t RAMMemory[2] = {0x40000000, 0x80000}; /* {RAM start address, 512K} */
/****************************************************************************************/
/*******************************************************************************
Function Name : SystemClockInit
Engineer      : Pham Van Lap
Date          : June-22-2018
Notes         : PLL0 connect to XOSC is default (40MHz)
Issues        : NONE
*******************************************************************************/
void SystemClockInit(void)
{
	/* Wait for stable XOSC */
	while(((SIU->RSR & SIU_RSR_XOSC_MASK)>>SIU_RSR_XOSC_SHIFT)==0);
	/* fPLL0_PHI = 40*(24/(2x6) = 40*2 = 80MHz */
	PLLDIG->PLL0DV = (PLLDIG->PLL0DV & ~PLLDIG_PLL0DV_MFD_MASK)|PLLDIG_PLL0DV_MFD(24);
	PLLDIG->PLL0DV = (PLLDIG->PLL0DV & ~PLLDIG_PLL0DV_PREDIV_MASK)|PLLDIG_PLL0DV_PREDIV(2);
	PLLDIG->PLL0DV = (PLLDIG->PLL0DV & ~PLLDIG_PLL0DV_RFDPHI_MASK)|PLLDIG_PLL0DV_RFDPHI(6);
    /* Turn on PLL0 */
    PLLDIG->PLL0CR = (PLLDIG->PLL0CR & ~PLLDIG_PLL0CR_CLKCFG_MASK)|PLLDIG_PLL0CR_CLKCFG(3);
    /* Wait for PLL0 lock */
    while(((PLLDIG->PLL0SR & PLLDIG_PLL0SR_LOCK_MASK)>>PLLDIG_PLL0SR_LOCK_SHIFT)==0);
    /* The system clock is driven by the non-FM PLL (PLL0) */
    SIU->SYSDIV = (SIU->SYSDIV & ~SIU_SYSDIV_SYSCLKSEL_MASK)|SIU_SYSDIV_SYSCLKSEL(3);
    /* Clock is connected to the non-FM clock domain (output of PLL0) */
    SIU->SYSDIV = (SIU->SYSDIV & ~SIU_SYSDIV_PERCLKSEL_MASK)|SIU_SYSDIV_PERCLKSEL(1);
    /* The crystal oscillator (XOSC) is the clock source for the MCAN modules */
    SIU->SYSDIV = (SIU->SYSDIV & ~SIU_SYSDIV_MCANSEL_MASK)|SIU_SYSDIV_MCANSEL(0);
}

/******************************************************************************
Function Name : UART_Init
Engineer      : Pham Van Lap
Date          : Feb-18-2019
Notes         : 40MHz from PLL0_PHI, 115200b/s
******************************************************************************/
void UART_Init()
{
	/* Set pin for UART0(eSCI_0) on SCH-28088 Rev.D - J21 */
    SIU->PCR[89] |= SIU_PCR_PA(1)|SIU_PCR_OBE(1);
    SIU->PCR[90] |= SIU_PCR_PA(1)|SIU_PCR_IBE(1);
    /* Note: SBR = 40MHz/(16 * baudrate) < 8191 */
    UART->CR2 = 0x2000;  /* Module is enabled (default setting) */
    UART->BRR |= eSCI_BRR_SBR(22); /* 115200 baud */
    UART->CR1 |= eSCI_CR1_TE(1); /* TX enabled */
    UART->CR1 |= eSCI_CR1_RE(1); /* RX enabled */
}
/******************************************************************************
*   Function: CCP_Flash_Init
*   Description: This function sets up program/erase structures a and unlocks
*                flash sectors for program/erase.
*   Caveats:
******************************************************************************/
void FLASH_Init(void)
{
    FLASH_DRV_Init();
    /* Unlock all blocks in low address space */
    FLASH_DRV_SetLock(C55_BLOCK_LOW, 0x0UL);
    /* Unlock all blocks in mid address space */
    FLASH_DRV_SetLock(C55_BLOCK_MID, 0x0UL);
    /* Unlock all blocks in high address space */
    FLASH_DRV_SetLock(C55_BLOCK_HIGH, 0x0UL);
    /* Unlock all blocks in first 256K blocks */
    FLASH_DRV_SetLock(C55_BLOCK_256K_FIRST, 0x0UL);
    /* Unlock all blocks in second 256K blocks */
    FLASH_DRV_SetLock(C55_BLOCK_256K_SECOND, 0x0UL);
}
/******************************************************************************
*   Function      : FlexCAN Init
*   Engineer      : Pham Van Lap
*   Date          : Feb-18-2019
*   Description   : 40MHz from PLL0, 500Kb/s
******************************************************************************/
void FlexCAN_Init(void)
{
    uint32_t databyte;
    SIU->PCR[83] = 0x400;  /* FlexCAN_0 TX */
    SIU->PCR[84] = 0x500;  /* FlexCAN_0 RX */
    /* Init can module */
    FlexCAN->MCR = (FlexCAN->MCR & ~CAN_MCR_MDIS_MASK) | CAN_MCR_MDIS(0U);
    FlexCAN->MCR = (FlexCAN->MCR & ~CAN_MCR_FRZ_MASK) | CAN_MCR_FRZ(0U);
    FlexCAN->MCR = (FlexCAN->MCR & ~CAN_MCR_HALT_MASK) | CAN_MCR_HALT(0U);
    /* Wait until enabled */
    while(((FlexCAN->MCR & CAN_MCR_LPMACK_MASK) >> CAN_MCR_LPMACK_SHIFT) != 0U);
    /* Enter the freeze mode. */
    FlexCAN->MCR = (FlexCAN->MCR & ~CAN_MCR_FRZ_MASK) | CAN_MCR_FRZ(1U);
    FlexCAN->MCR = (FlexCAN->MCR & ~CAN_MCR_HALT_MASK) | CAN_MCR_HALT(1U);
    if(((FlexCAN->MCR & CAN_MCR_MDIS_MASK) >> CAN_MCR_MDIS_SHIFT) != 0U)
    {
        FlexCAN->MCR &= ~CAN_MCR_MDIS_MASK;
    }
    while(((FlexCAN->MCR & CAN_MCR_FRZACK_MASK) >> CAN_MCR_FRZACK_SHIFT) == 0U);
    /* Reset the FLEXCAN */
    FlexCAN->MCR = (FlexCAN->MCR & ~CAN_MCR_SOFTRST_MASK) | CAN_MCR_SOFTRST(1U);
    /* Wait for reset cycle to complete */
    while(((FlexCAN->MCR & CAN_MCR_SOFTRST_MASK) >> CAN_MCR_SOFTRST_SHIFT) != 0U);
    /* Avoid Abort Transmission, use Inactive MB */
    FlexCAN->MCR = (FlexCAN->MCR & ~CAN_MCR_AEN_MASK) | CAN_MCR_AEN(1U);
    /* Clears FlexCAN memory positions that require initialization. */
    /* Clear MB region */
    for(databyte = 0; databyte < (FEATURE_CAN0_MAX_MB_NUM * 4U); databyte++)
    {
        FlexCAN->RAMn[databyte] = 0;
    }
    /* Clear RXIMR region */
    for(databyte = 0; databyte < FEATURE_CAN0_MAX_MB_NUM; databyte++)
    {
        FlexCAN->RXIMR[databyte] = 0;
    }
    /* Rx global mask*/
    FlexCAN->RXMGMASK = (uint32_t)(CAN_RXMGMASK_MG_MASK);
    /* Rx reg 14 mask*/
    FlexCAN->RX14MASK =  (uint32_t)(CAN_RX14MASK_RX14M_MASK);
    /* Rx reg 15 mask*/
    FlexCAN->RX15MASK = (uint32_t)(CAN_RX15MASK_RX15M_MASK);
    /* Disable all MB interrupts */
    FlexCAN->IMASK1 = 0x0;
    /* Clear all MB interrupt flags */
    FlexCAN->IFLAG1 = CAN_IMASK1_BUF31TO0M_MASK;
    FlexCAN->IMASK2 = 0x0;
    FlexCAN->IFLAG2 = CAN_IMASK2_BUF63TO32M_MASK;
    //FlexCAN->IMASK3 = 0x0;
    //FlexCAN->IFLAG3 = CAN_IMASK3_BUF95TO64M_MASK;
    /* Clear all error interrupt flags */
    FlexCAN->ESR1 = 0x003B0006U;
    FlexCAN->MCR = (FlexCAN->MCR & ~CAN_MCR_SRXDIS_MASK);
    /* Select normal mode */
    FlexCAN->MCR = (FlexCAN->MCR & ~CAN_MCR_SUPV_MASK);
    FlexCAN->CTRL1 = (FlexCAN->CTRL1 & ~CAN_CTRL1_LOM_MASK);
    FlexCAN->CTRL1 = (FlexCAN->CTRL1 & ~CAN_CTRL1_LPB_MASK);
    /* Set the maximum number of MBs is 16 */
    FlexCAN->MCR = (FlexCAN->MCR & ~CAN_MCR_MAXMB_MASK) | ((15 << CAN_MCR_MAXMB_SHIFT) & CAN_MCR_MAXMB_MASK);
    /* Sets the FlexCAN time segments for setting up bit rate. */
    /* 40MHz from PLL0, 500kb/s */
    FlexCAN->CTRL1 = (FlexCAN->CTRL1 & ~CAN_CTRL1_PROPSEG_MASK)|CAN_CTRL1_PROPSEG(4);
    FlexCAN->CTRL1 = (FlexCAN->CTRL1 & ~CAN_CTRL1_PSEG2_MASK)|CAN_CTRL1_PSEG2(4);
    FlexCAN->CTRL1 = (FlexCAN->CTRL1 & ~CAN_CTRL1_PSEG1_MASK)|CAN_CTRL1_PSEG1(4);
    FlexCAN->CTRL1 = (FlexCAN->CTRL1 & ~CAN_CTRL1_RJW_MASK)|CAN_CTRL1_RJW(3);
    FlexCAN->CTRL1 = (FlexCAN->CTRL1 & ~CAN_CTRL1_PRESDIV_MASK)|CAN_CTRL1_PRESDIV(4);
    /* Exit of freeze mode. */
    FlexCAN->MCR = (FlexCAN->MCR & ~CAN_MCR_HALT_MASK);
    FlexCAN->MCR = (FlexCAN->MCR & ~CAN_MCR_FRZ_MASK);
    /* Wait till exit freeze mode */
    while(((FlexCAN->MCR & CAN_MCR_FRZACK_MASK) >> CAN_MCR_FRZACK_SHIFT) != 0U);
}
/***********************************************************************************
*   Function: SystemConfigReset
*   Description: Reset all configuration to default
*   Caveats:
************************************************************************************/
void SystemConfigReset(void)
{

}
/***********************************************************************************
********************************* END OF FILE **************************************
***********************************************************************************/
