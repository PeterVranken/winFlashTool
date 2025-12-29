/**
 * @file SignalWithAutoReset.java
 * Signal with auto reset: A task A can notify an event to another task B. B can suspend
 * and non-busy wait for the signal. A can send the signal and thereby resume B.<p>
 *   Auto reset means that the event is a Boolean; notifications by B are not counted or
 * accumulated. If A sends the event repeatedly before B has consumed it then B will get
 * only a single notification. If B has received the event then it is implicitly reset, so
 * that a new notification by A is immediately possible.<p>
 *   If A send the event before B checks it then B will immediately get it without
 * suspending and waiting for it.
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
/* Interface of class SignalWithAutoReset
 *   signal
 *   await
 */

package winFlashTool.basics;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class implements a signal with auto reset<p>
 *   A task A can notify an event to another task B. B can suspend and non-busy wait for
 * the signal. A can send the signal and thereby resume B.<p>
 *   Auto reset means that the event is a Boolean; notifications by B are not counted or
 * accumulated. If A sends the event repeatedly before B has consumed it then B will get
 * only a single notification. If B has received the event then it is implicitly reset, so
 * that a new notification by A is immediately possible.<p>
 *   If A send the event before B checks it then B will immediately get it without
 * suspending and waiting for it.
 */
public final class SignalWithAutoReset {
    private final Semaphore sem_ = new Semaphore( /*noInitialPermits*/ 0
                                                , /*firstRequesterGetsIt*/ false
                                                );
    private final AtomicBoolean signal_ = new AtomicBoolean(false);

    /**
     * A task uses this method to send the signal to any other task, which might query it.
     */
    public void signal() {
        /* The Semaphore is incremented if the signal is currently unset. At the same time,
           the signal is set. Test-and-set of the Boolean signal is an atomic operation,
           but incrementing the semphare not. Other tasks can see the signal set without
           the semaphore providing a permit (yet).
             Note, the count of the semaphore will always be 0 or 1. */
        if (signal_.compareAndSet(false, true)) {
            assert sem_.availablePermits() == 0;
            sem_.release();
        } else {
            /* Signal has not been consumed yet by another thread. Nothing to be done. */
        }
    } /* signal */

    /**
     * Any task check the status of the signal and waits until reception with this
     * method.<p>
     *   The method returns immediately with true if the signal has already been sent by
     * the other task. Otherwise either it suspends until the signal is received and
     * returns then with true or it suspends for the specified time span and returns then
     * with false - whatever comes first.
     *   @throws InterruptedException
     * If some code explicitly interrupts the waiting, suspended thread then this exception
     * indicates the situation. Interruption can be done by calling Thread.interrupt() for
     * the waiting thread. Note, the signal has not been received in this case.
     *   @return
     * Get true if the signal has been received, regardless whether with or without waiting
     * for it.<p>
     *   Get false if the timeout elapsed without receiving the signal.
     *   @param timeoutInMs
     * The maximum wait time in suspended state for reception of the signal. Unit is
     * Millisecond.
     */
    public boolean await(int timeoutInMs) throws InterruptedException {
        if (sem_.tryAcquire((long)timeoutInMs, TimeUnit.MILLISECONDS)) {
            /* We consumed the semaphore's permit and clear the signal for next cycle. */
            assert signal_.get() &&  sem_.availablePermits() == 0;
            signal_.set(false);
            return true;
            
        } else {
            /* Timeout has elapsed without receiving the signal. */
            return false;
        }
    } /* await */
    
} /* SignalWithAutoReset */
