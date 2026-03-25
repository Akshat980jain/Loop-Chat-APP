package com.loopchat.app.util

object PhoneUtils {
    
    /**
     * Strips all non-digit characters from a phone number string.
     */
    fun normalize(phone: String): String {
        return phone.replace(Regex("[^0-9]"), "")
    }

    /**
     * Compares two phone numbers based on their last 10 digits.
     * This handles cases where one might have a country code and the other doesn't.
     */
    fun areSame(phone1: String, phone2: String): Boolean {
        val n1 = normalize(phone1)
        val n2 = normalize(phone2)
        
        if (n1.isEmpty() || n2.isEmpty()) return false
        
        // If they are exactly the same, return true
        if (n1 == n2) return true
        
        // Take the last 10 digits (common for most mobile numbers)
        val s1 = n1.takeLast(10)
        val s2 = n2.takeLast(10)
        
        return s1.length >= 10 && s1 == s2
    }
}
