package com.afriserve.smsmanager.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

/**
 * Utility class for file operations
 */
public class FileUtils {
    
    /**
     * Get the display name of a file from a content URI
     * This method correctly resolves the filename from content URIs,
     * including proper file extensions for modern Android file pickers
     * 
     * @param context The application context
     * @param uri The content URI to resolve
     * @return The display name of the file, or a fallback name if not found
     */
    public static String getFileNameFromUri(Context context, Uri uri) {
        if (uri == null) {
            return "unknown_file";
        }
        
        // First try to get the display name from content resolver
        String fileName = null;
        
        if (context != null) {
            try (Cursor cursor = context.getContentResolver().query(
                    uri, 
                    new String[]{OpenableColumns.DISPLAY_NAME}, 
                    null, 
                    null, 
                    null)) {
                
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                // Log error but continue with fallback
                android.util.Log.w("FileUtils", "Error getting display name from URI", e);
            }
        }
        
        // Fallback to last path segment if display name not found
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = uri.getLastPathSegment();
        }
        
        // Additional fallback for content URIs that might return encoded names
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "imported_file";
        }
        
        // Try to add appropriate extension if missing and MIME type is available
        if (context != null && !hasFileExtension(fileName)) {
            String mimeType = context.getContentResolver().getType(uri);
            if (mimeType != null) {
                String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                if (extension != null && !extension.isEmpty()) {
                    fileName += "." + extension;
                }
            }
        }
        
        return fileName;
    }
    
    /**
     * Check if a filename has a file extension
     * 
     * @param fileName The filename to check
     * @return true if the filename has an extension, false otherwise
     */
    private static boolean hasFileExtension(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 && lastDotIndex < fileName.length() - 1;
    }
}
