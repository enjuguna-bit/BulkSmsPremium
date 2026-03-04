package com.afriserve.smsmanager.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the Result sealed class
 */
class ResultTest {
    
    // ============== Success Tests ==============
    
    @Test
    fun `success result contains data`() {
        val data = "test data"
        val result = Result.Success(data)
        
        assertEquals(data, result.data)
        assertTrue(result.isSuccess)
        assertFalse(result.isError)
        assertFalse(result.isLoading)
    }
    
    @Test
    fun `getOrNull returns data for success`() {
        val data = 42
        val result: Result<Int> = Result.Success(data)
        
        assertEquals(42, result.getOrNull())
    }
    
    @Test
    fun `getOrDefault returns data for success`() {
        val result: Result<Int> = Result.Success(42)
        
        assertEquals(42, result.getOrDefault(0))
    }
    
    @Test
    fun `getOrThrow returns data for success`() {
        val result: Result<String> = Result.Success("hello")
        
        assertEquals("hello", result.getOrThrow())
    }
    
    // ============== Error Tests ==============
    
    @Test
    fun `error result contains exception`() {
        val exception = RuntimeException("test error")
        val result = Result.Error(exception)
        
        assertEquals(exception, result.exception)
        assertEquals("test error", result.message)
        assertTrue(result.isError)
        assertFalse(result.isSuccess)
        assertFalse(result.isLoading)
    }
    
    @Test
    fun `getOrNull returns null for error`() {
        val result: Result<Int> = Result.Error(Exception("error"))
        
        assertNull(result.getOrNull())
    }
    
    @Test
    fun `getOrDefault returns default for error`() {
        val result: Result<Int> = Result.Error(Exception("error"))
        
        assertEquals(99, result.getOrDefault(99))
    }
    
    @Test(expected = RuntimeException::class)
    fun `getOrThrow throws for error`() {
        val result: Result<String> = Result.Error(RuntimeException("failure"))
        
        result.getOrThrow()
    }
    
    // ============== Loading Tests ==============
    
    @Test
    fun `loading result has correct state`() {
        val result: Result<Int> = Result.Loading
        
        assertTrue(result.isLoading)
        assertFalse(result.isSuccess)
        assertFalse(result.isError)
    }
    
    @Test
    fun `getOrNull returns null for loading`() {
        val result: Result<Int> = Result.Loading
        
        assertNull(result.getOrNull())
    }
    
    @Test
    fun `getOrDefault returns default for loading`() {
        val result: Result<Int> = Result.Loading
        
        assertEquals(0, result.getOrDefault(0))
    }
    
    // ============== Map Tests ==============
    
    @Test
    fun `map transforms success data`() {
        val result: Result<Int> = Result.Success(5)
        val mapped = result.map { it * 2 }
        
        assertTrue(mapped is Result.Success)
        assertEquals(10, (mapped as Result.Success).data)
    }
    
    @Test
    fun `map preserves error`() {
        val error = Exception("error")
        val result: Result<Int> = Result.Error(error)
        val mapped = result.map { it * 2 }
        
        assertTrue(mapped is Result.Error)
        assertEquals(error, (mapped as Result.Error).exception)
    }
    
    @Test
    fun `map preserves loading`() {
        val result: Result<Int> = Result.Loading
        val mapped = result.map { it * 2 }
        
        assertTrue(mapped is Result.Loading)
    }
    
    // ============== FlatMap Tests ==============
    
    @Test
    fun `flatMap chains success results`() {
        val result: Result<Int> = Result.Success(5)
        val flatMapped = result.flatMap { Result.Success(it.toString()) }
        
        assertTrue(flatMapped is Result.Success)
        assertEquals("5", (flatMapped as Result.Success).data)
    }
    
    @Test
    fun `flatMap can produce error from success`() {
        val result: Result<Int> = Result.Success(5)
        val error = Exception("transformed error")
        val flatMapped = result.flatMap { Result.Error(error) }
        
        assertTrue(flatMapped is Result.Error)
    }
    
    // ============== Callback Tests ==============
    
    @Test
    fun `onSuccess executes for success`() {
        var executed = false
        val result: Result<Int> = Result.Success(42)
        
        result.onSuccess { executed = true }
        
        assertTrue(executed)
    }
    
    @Test
    fun `onSuccess does not execute for error`() {
        var executed = false
        val result: Result<Int> = Result.Error(Exception())
        
        result.onSuccess { executed = true }
        
        assertFalse(executed)
    }
    
    @Test
    fun `onError executes for error`() {
        var executed = false
        val result: Result<Int> = Result.Error(Exception())
        
        result.onError { executed = true }
        
        assertTrue(executed)
    }
    
    @Test
    fun `onError does not execute for success`() {
        var executed = false
        val result: Result<Int> = Result.Success(42)
        
        result.onError { executed = true }
        
        assertFalse(executed)
    }
    
    @Test
    fun `onLoading executes for loading`() {
        var executed = false
        val result: Result<Int> = Result.Loading
        
        result.onLoading { executed = true }
        
        assertTrue(executed)
    }
    
    // ============== Companion Object Tests ==============
    
    @Test
    fun `catching wraps successful operation`() {
        val result = Result.catching { 1 + 1 }
        
        assertTrue(result is Result.Success)
        assertEquals(2, (result as Result.Success).data)
    }
    
    @Test
    fun `catching wraps exception`() {
        val result = Result.catching { throw IllegalStateException("test") }
        
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).exception is IllegalStateException)
    }
    
    @Test
    fun `error with message creates proper error`() {
        val result = Result.error("Something went wrong")
        
        assertTrue(result is Result.Error)
        assertEquals("Something went wrong", (result as Result.Error).message)
    }
    
    // ============== Extension Tests ==============
    
    @Test
    fun `toResult converts non-null to success`() {
        val value: String? = "hello"
        val result = value.toResult()
        
        assertTrue(result is Result.Success)
        assertEquals("hello", (result as Result.Success).data)
    }
    
    @Test
    fun `toResult converts null to error`() {
        val value: String? = null
        val result = value.toResult()
        
        assertTrue(result is Result.Error)
    }
    
    @Test
    fun `combine merges two success results`() {
        val result1: Result<Int> = Result.Success(1)
        val result2: Result<String> = Result.Success("a")
        
        val combined = result1.combine(result2)
        
        assertTrue(combined is Result.Success)
        assertEquals(Pair(1, "a"), (combined as Result.Success).data)
    }
    
    @Test
    fun `combine returns error if first is error`() {
        val result1: Result<Int> = Result.Error(Exception("first"))
        val result2: Result<String> = Result.Success("a")
        
        val combined = result1.combine(result2)
        
        assertTrue(combined is Result.Error)
    }
    
    @Test
    fun `combine returns error if second is error`() {
        val result1: Result<Int> = Result.Success(1)
        val result2: Result<String> = Result.Error(Exception("second"))
        
        val combined = result1.combine(result2)
        
        assertTrue(combined is Result.Error)
    }
}
