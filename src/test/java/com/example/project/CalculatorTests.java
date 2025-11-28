/*
 * Copyright 2015-2025 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package com.example.project;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CalculatorTests {

    // https://docs.junit.org/current/user-guide/
    @Test
    @DisplayName("1 + 1 = 2")
    void addsTwoNumbers() {
        Calculator calculator = new Calculator();
        assertEquals(2, calculator.add(1, 1), "1 + 1 should equal 2");
    }

    @Test
    @DisplayName("1 + 1 != 3")
    void addsTwoNumbersBad() {
        Calculator calculator = new Calculator();
        assertTrue(3 != calculator.add(1, 1), "1 + 1 should not equal 3");
    }

    @ParameterizedTest(name = "{0} + {1} = {2}", quoteTextArguments = false)
    @CsvSource(textBlock = """
            0,    1,   1
            1,    2,   3
            49,  51, 100
            1,  100, 101
            """)
    void add(int first, int second, int expectedResult) {
        Calculator calculator = new Calculator();
        assertEquals(expectedResult, calculator.add(first, second),
                () -> first + " + " + second + " should equal " + expectedResult);
    }
}