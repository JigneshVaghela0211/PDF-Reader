package com.pdf.pdfreader.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE pdfPath = :pdfPath ORDER BY pageIndex ASC")
    fun getBookmarksForPdf(pdfPath: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE pdfPath = :pdfPath AND pageIndex = :pageIndex")
    suspend fun deleteBookmark(pdfPath: String, pageIndex: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE pdfPath = :pdfPath AND pageIndex = :pageIndex)")
    suspend fun isBookmarked(pdfPath: String, pageIndex: Int): Boolean
}
