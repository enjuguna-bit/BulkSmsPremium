package com.afriserve.smsmanager.data.csv

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

/**
 * Unit tests for CSV parsing functionality
 */
class CsvParserTest {
    
    @Test
    fun testDelimiterDetection_comma() {
        val line = "firstName,lastName,phone,email"
        assertEquals(',', CsvParser.detectDelimiter(line))
    }
    
    @Test
    fun testDelimiterDetection_semicolon() {
        val line = "firstName;lastName;phone;email"
        assertEquals(';', CsvParser.detectDelimiter(line))
    }
    
    @Test
    fun testDelimiterDetection_tab() {
        val line = "firstName\tlastName\tphone\temail"
        assertEquals('\t', CsvParser.detectDelimiter(line))
    }
    
    @Test
    fun testPhoneColumnDetection() {
        assertTrue(CsvHeader.isPhoneColumn("phone"))
        assertTrue(CsvHeader.isPhoneColumn("Phone"))
        assertTrue(CsvHeader.isPhoneColumn("PHONE"))
        assertTrue(CsvHeader.isPhoneColumn("phone number"))
        assertTrue(CsvHeader.isPhoneColumn("mobile"))
        assertTrue(CsvHeader.isPhoneColumn("contact"))
        assertTrue(CsvHeader.isPhoneColumn("telephone"))
        assertTrue(CsvHeader.isPhoneColumn("mobile phone"))
    }
    
    @Test
    fun testPhoneColumnDetection_negative() {
        assertFalse(CsvHeader.isPhoneColumn("email"))
        assertFalse(CsvHeader.isPhoneColumn("name"))
        assertFalse(CsvHeader.isPhoneColumn("address"))
    }
    
    @Test
    fun testBasicCsvParsing() {
        val csv = """firstName,lastName,phone
John,Doe,5551234567
Jane,Smith,5559876543"""
        
        val result = CsvParser.parseWithDetection(csv)
        
        assertEquals(3, result.columnCount)
        assertEquals(2, result.validCount)
        assertEquals(0, result.invalidCount)
    }
    
    @Test
    fun testPhoneValidation_valid() {
        val csv = """name,phone
John,5551234567
Jane,(555)123-4567
Bob,+1-555-123-4567
Alice,555 123 4567"""
        
        val result = CsvParser.parseWithDetection(csv)
        assertEquals(4, result.validCount)
    }
    
    @Test
    fun testPhoneValidation_invalid() {
        val csv = """name,phone
John,123
Jane,
Bob,invalid"""
        
        val result = CsvParser.parseWithDetection(csv)
        assertEquals(0, result.validCount)
        assertEquals(3, result.invalidCount)
    }
    
    @Test
    fun testMultiplePhoneColumns() {
        val csv = """firstName,phone,mobile,contact
John,5551234567,5559876543,5552223333
Jane,5554445555,5555556666,5556667777"""
        
        val result = CsvParser.parseWithDetection(csv)
        assertTrue(result.detectionResult?.hasMultiplePhoneColumns == true)
        assertEquals(3, result.detectionResult?.potentialPhoneColumns?.size)
    }
    
    @Test
    fun testCsvWithQuotedFields() {
        val csv = """"firstName","lastName","phone"
"John","Doe","555-123-4567"
"Jane","Smith, Jr.","555-987-6543""""
        
        val result = CsvParser.parseWithDetection(csv)
        assertEquals(2, result.validCount)
    }
    
    @Test
    fun testPlaceholderGeneration() {
        val header = CsvHeader(0, "firstName")
        assertEquals("{firstName}", header.placeholder)
        
        val header2 = CsvHeader(1, "Order Number")
        assertEquals("{OrderNumber}", header2.placeholder)
    }
    
    @Test
    fun testEmptyFile() {
        val csv = ""
        val result = CsvParser.parseWithDetection(csv)
        assertTrue(result.parseErrors.isNotEmpty())
    }
    
    @Test
    fun testHeaderDetection() {
        val csv = """firstName,lastName,phone,email,dateOfBirth
John,Doe,5551234567,john@example.com,1990-01-01"""
        
        val result = CsvParser.parseWithDetection(csv)
        assertEquals(5, result.headers.size)
        assertEquals("firstName", result.headers[0].name)
        assertEquals("{firstName}", result.headers[0].placeholder)
    }
}

/**
 * Unit tests for phone number validation
 */
class PhoneValidationTest {
    
    @Test
    fun testValidPhoneNumbers() {
        val phones = listOf(
            "5551234567",
            "+1-555-123-4567",
            "(555) 123-4567",
            "555.123.4567",
            "555 123 4567",
            "+447911123456",
            "+33123456789"
        )
        
        val result = CsvParsingResult(
            recipients = phones.map { CsvRecipient(it) },
            validRecipients = phones.map { CsvRecipient(it) }
        )
        
        assertEquals(7, result.validCount)
    }
    
    @Test
    fun testInvalidPhoneNumbers() {
        val phones = listOf(
            "123",           // Too short
            "555-1234",      // Not enough digits
            "invalid",       // Non-numeric
            ""               // Empty
        )
        
        val result = CsvParsingResult(
            recipients = phones.map { CsvRecipient(it) },
            invalidRecipients = phones.map { it to "Invalid format" }
        )
        
        assertEquals(0, result.validCount)
        assertEquals(4, result.invalidCount)
    }
}

/**
 * Unit tests for message preview
 */
class CsvPreviewTest {
    
    @Test
    fun testMessagePreviewGeneration() {
        val template = "Hello {firstName}, your order {orderNumber} is ready"
        val recipients = listOf(
            CsvRecipient(
                phoneNumber = "5551234567",
                data = mapOf("firstName" to "John", "orderNumber" to "12345")
            ),
            CsvRecipient(
                phoneNumber = "5559876543",
                data = mapOf("firstName" to "Jane", "orderNumber" to "12346")
            )
        )
        
        val previews = CsvPreviewService.generateMessagePreviews(template, recipients)
        
        assertEquals(2, previews.size)
        assertTrue(previews[0].contains("Hello John, your order 12345 is ready"))
        assertTrue(previews[0].contains("[To: 5551234567]"))
    }
    
    @Test
    fun testPhoneValidationSummary() {
        val recipients = listOf(
            CsvRecipient("5551234567"),
            CsvRecipient("5559876543"),
            CsvRecipient("invalid"),
            CsvRecipient("")
        )
        
        val validation = CsvPreviewService.validatePhoneNumbers(recipients)
        
        assertEquals(4, validation.totalCount)
        assertEquals(2, validation.validCount)
        assertEquals(2, validation.invalidCount)
        assertEquals(50, validation.validityPercentage)
    }
}
