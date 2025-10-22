/**
 * @file TimeoutTimer.java
 * A tiny warpper around System.nanoTime(), which supports real-time events in the CCP
 * protocol implementation.
 *
 * Copyright (C) 2025 Peter Vranken (mailto:Peter_Vranken@Yahoo.de)
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
/* Interface of class TimeoutTimer
 *   TimeoutTimer
 */

package winFlashTool;

public class TimeoutTimer {
    private final long timeoutNs;
    private long startTime;

    public TimeoutTimer(long timeoutMillis) {
        this.timeoutNs = timeoutMillis * 1_000_000;
        this.startTime = System.nanoTime();
    }

    public void restart() {
        this.startTime = System.nanoTime();
    }

    public boolean hasTimedOut() {
        return System.nanoTime() - startTime >= timeoutNs;
    }

    public long getElapsedMillis() {
        return (System.nanoTime() - startTime) / 1_000_000;
    }

    public long getRemainingMillis() {
        long remainingNs = timeoutNs - (System.nanoTime() - startTime);
        return Math.max(remainingNs / 1_000_000, 0);
    }
}