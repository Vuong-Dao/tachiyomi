package eu.kanade.tachiyomi.data.source.model

import java.io.Serializable

interface SChapter : Serializable {

    var url: String

    var name: String

    var date_upload: Long

    var chapter_number: Float

    fun copyFrom(other: SChapter) {
        name = other.name
        url = other.url
        date_upload = other.date_upload
        chapter_number = other.chapter_number
    }

    companion object {
        fun create(): SChapter {
            return SChapterImpl()
        }
    }

}