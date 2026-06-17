package com.landrop

import com.landrop.server.BruteForceControl
import com.landrop.server.FileSharingManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

class ProductionAuditTests {

    @Before
    fun setUp() {
        // Reset brute force records if possible, otherwise we can use unique IPs
        // Clear records map using reflection if it is private, or just use different target IPs
        try {
            val recordsField = BruteForceControl::class.java.getDeclaredField("records")
            recordsField.isAccessible = true
            val records = recordsField.get(BruteForceControl) as MutableMap<*, *>
            records.clear()
        } catch (e: Exception) {
            // ignore
        }
    }

    @Test
    fun testPinValidationFormat() {
        // Requirements:
        // ✓ PIN digits only (0-9)
        // ✓ Minimum 4 digits
        // ✓ Maximum 8 digits
        
        // Test passes:
        assertTrue(FileSharingManager.isValidPin("1234"))
        assertTrue(FileSharingManager.isValidPin("0000"))
        assertTrue(FileSharingManager.isValidPin("12345678"))
        
        // Test rejects:
        assertFalse(FileSharingManager.isValidPin("123"))
        assertFalse(FileSharingManager.isValidPin("123456789"))
        assertFalse(FileSharingManager.isValidPin("abcd"))
        assertFalse(FileSharingManager.isValidPin("1234a"))
        assertFalse(FileSharingManager.isValidPin("12-34"))
        assertFalse(FileSharingManager.isValidPin("12 34"))
    }

    @Test
    fun testBruteForceLockoutIntervals() {
        val ip = "192.168.1.50"
        
        // Before 5 failed attempts, we're not locked
        for (i in 1..4) {
            val res = BruteForceControl.attemptLogin(ip, false)
            assertTrue("Should not lock on attempt $i", res is BruteForceControl.AuthResult.Failed)
            val lockCheck = BruteForceControl.isLocked(ip)
            assertTrue("Should report not locked on attempt $i", lockCheck is BruteForceControl.LockoutState.NotLocked)
        }
        
        // 5th failed attempt -> locked for 30s
        var res = BruteForceControl.attemptLogin(ip, false)
        assertTrue("Should lock on 5th attempt", res is BruteForceControl.AuthResult.Locked)
        var lockCheck = BruteForceControl.isLocked(ip)
        assertTrue(lockCheck is BruteForceControl.LockoutState.Locked)
        var remaining = (lockCheck as BruteForceControl.LockoutState.Locked).remainingMs
        assertTrue("Lock duration of 30sec should be around 30000ms", remaining in 20000..30000)
        
        // Force reset lock state and simulate up to 9 failures directly, so the 10th failure creates a 2m lock
        setAttemptsAndClearLockout(ip, 9)
        res = BruteForceControl.attemptLogin(ip, false) // 10th attempt
        assertTrue("Should lock on 10th attempt", res is BruteForceControl.AuthResult.Locked)
        lockCheck = BruteForceControl.isLocked(ip)
        assertTrue(lockCheck is BruteForceControl.LockoutState.Locked)
        remaining = (lockCheck as BruteForceControl.LockoutState.Locked).remainingMs
        assertTrue("Lock duration of 2m should be around 120000ms, got $remaining", remaining in 110000..120000)
        
        // Force reset lock state and simulate up to 14 failures directly, so the 15th failure creates a 10m lock
        setAttemptsAndClearLockout(ip, 14)
        res = BruteForceControl.attemptLogin(ip, false) // 15th attempt
        assertTrue("Should lock on 15th attempt", res is BruteForceControl.AuthResult.Locked)
        lockCheck = BruteForceControl.isLocked(ip)
        assertTrue(lockCheck is BruteForceControl.LockoutState.Locked)
        remaining = (lockCheck as BruteForceControl.LockoutState.Locked).remainingMs
        assertTrue("Lock duration of 10m should be around 600000ms, got $remaining", remaining in 590000..600000)
        
        // Force reset lock state and simulate up to 19 failures directly, so the 20th failure creates a 30m lock
        setAttemptsAndClearLockout(ip, 19)
        res = BruteForceControl.attemptLogin(ip, false) // 20th attempt
        assertTrue("Should lock on 20th attempt", res is BruteForceControl.AuthResult.Locked)
        lockCheck = BruteForceControl.isLocked(ip)
        assertTrue(lockCheck is BruteForceControl.LockoutState.Locked)
        remaining = (lockCheck as BruteForceControl.LockoutState.Locked).remainingMs
        assertTrue("Lock duration of 30m should be around 1800000ms, got $remaining", remaining in 1790000..1800000)
        
        // Force reset lock state and simulate up to 24 failures directly, so the 25th failure creates a 1h lock
        setAttemptsAndClearLockout(ip, 24)
        res = BruteForceControl.attemptLogin(ip, false) // 25th attempt
        assertTrue("Should lock on 25th attempt", res is BruteForceControl.AuthResult.Locked)
        lockCheck = BruteForceControl.isLocked(ip)
        assertTrue(lockCheck is BruteForceControl.LockoutState.Locked)
        remaining = (lockCheck as BruteForceControl.LockoutState.Locked).remainingMs
        assertTrue("Lock duration of 1h should be around 3600000ms, got $remaining", remaining in 3590000..3600000)
    }

    @Test
    fun testSuccessfulLoginResetsCounter() {
        val ip = "192.168.1.60"
        
        // Fail 4 times
        simulateFailures(ip, 4)
        
        // Successful login
        val successRes = BruteForceControl.attemptLogin(ip, true)
        assertTrue(successRes is BruteForceControl.AuthResult.Success)
        
        // Failure count should reset. Let's fail 1 more time and verify it doesn't lock us immediately
        val failRes = BruteForceControl.attemptLogin(ip, false)
        assertTrue("Should not lock because count was reset", failRes is BruteForceControl.AuthResult.Failed)
    }

    @Test
    fun testDifferentIpsUnaffected() {
        val ip1 = "192.168.1.71"
        val ip2 = "192.168.1.72"
        
        // Lock IP 1 with 5 failed attempts
        simulateFailures(ip1, 5)
        assertTrue(BruteForceControl.isLocked(ip1) is BruteForceControl.LockoutState.Locked)
        
        // IP 2 is unaffected and can scan
        assertTrue(BruteForceControl.isLocked(ip2) is BruteForceControl.LockoutState.NotLocked)
        val attemptRes = BruteForceControl.attemptLogin(ip2, true)
        assertTrue(attemptRes is BruteForceControl.AuthResult.Success)
    }

    private fun simulateFailures(ip: String, count: Int) {
        for (i in 1..count) {
            BruteForceControl.attemptLogin(ip, false)
        }
    }

    private fun setAttemptsAndClearLockout(ip: String, attempts: Int) {
        try {
            val recordsField = BruteForceControl::class.java.getDeclaredField("records")
            recordsField.isAccessible = true
            val records = recordsField.get(BruteForceControl) as MutableMap<String, *>
            val record = records[ip]
            if (record != null) {
                val lockedUntilField = record::class.java.getDeclaredField("lockedUntil")
                lockedUntilField.isAccessible = true
                lockedUntilField.setLong(record, 0L)
                
                val attemptsField = record::class.java.getDeclaredField("attempts")
                attemptsField.isAccessible = true
                attemptsField.setInt(record, attempts)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
