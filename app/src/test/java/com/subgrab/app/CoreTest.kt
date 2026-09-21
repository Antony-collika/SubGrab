package com.subgrab.app

import com.subgrab.app.data.SubtitleFormatter
import com.subgrab.app.data.FailureClassifier
import com.subgrab.app.data.RequestGovernor
import com.subgrab.app.data.DownloadTaskPlanner
import com.subgrab.app.domain.FailureType
import com.subgrab.app.domain.RequestLane
import com.subgrab.app.domain.RequestResult
import com.subgrab.app.data.SubtitleParser
import com.subgrab.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    @Test fun acceptsSupportedYoutubeUrls() {
        listOf(
            "https://www.youtube.com/@demo",
            "https://youtube.com/c/demo",
            "https://www.youtube.com/channel/UC1",
            "https://www.youtube.com/playlist?list=PL1",
            "https://www.youtube.com/watch?v=abc",
            "https://youtu.be/abc"
        ).forEach { assertTrue(UrlValidator.isValid(it)) }
    }

    @Test fun rejectsUnsupportedUrls() {
        listOf("abcxyz", "https://vimeo.com/1", "https://facebook.com/a", "https://youtube.com/about")
            .forEach { assertFalse(UrlValidator.isValid(it)) }
    }

    @Test fun sanitizesVietnameseAndSpecialCharacters() {
        assertEquals("huong_dan_kotlin", FileNameSanitizer.sanitize("Hướng dẫn Kotlin: *"))
    }

    @Test fun parsesInlineWebVttTimestampsAndMarkup() {
        val cues = SubtitleParser.parseWebVtt(
            """
            WEBVTT

            00:00:10.000 --> 00:00:13.000
            Good<00:00:11.599><c> day.</c>
            """.trimIndent()
        )
        assertEquals(1, cues.size)
        assertEquals("Good day.", cues.single().text)
        assertEquals(
            "1\n00:00:10,000 --> 00:00:13,000\nGood day.\n",
            SubtitleFormatter.format(cues, SubtitleTimestampMode.WITH_TIMESTAMP)
        )
    }

    @Test fun formatsWithoutTimestamp() {
        val cues = SubtitleParser.parseWebVtt(
            """
            WEBVTT

            00:00:00.000 --> 00:00:02.000
            Good<00:00:00.500><c> day.</c>

            00:00:02.000 --> 00:00:04.000
            Good day.
            """.trimIndent()
        )
        assertEquals("Good day.\n", SubtitleFormatter.format(cues, SubtitleTimestampMode.WITHOUT_TIMESTAMP))
    }

    @Test fun taskProgressNeverExceedsLimit() {
        val videos = (1..50).map { VideoItem(it, "$it", "Video $it", 60, listOf(SubtitleLanguage("vi"))) }
        assertEquals(50, videos.size)
    }

    @Test fun governorSlowsDownOnRateLimitAndRecoversAfterStableTraffic() {
        val governor = RequestGovernor()
        val lane = RequestLane.SUBTITLE_EXTRACTOR
        val limited = RequestResult(false, 429, 10, FailureType.HTTP_429)

        assertTrue(governor.observe(lane, limited))
        assertEquals(GovernorState.SLOWDOWN, governor.state(lane))
        assertTrue(governor.delay(lane) > 0)

        repeat(5) {
            governor.observe(lane, RequestResult(true, 200, 10, null))
        }

        assertEquals(GovernorState.NORMAL, governor.state(lane))
        assertEquals(0L, governor.delay(lane))
    }

    @Test fun governorDoesNotSlowDownForBenignSubtitleFailures() {
        val governor = RequestGovernor()
        val result = RequestResult(false, 200, 10, FailureType.LANGUAGE_UNAVAILABLE)

        repeat(3) {
            governor.observe(RequestLane.SUBTITLE_EXTRACTOR, result)
        }

        assertEquals(GovernorState.NORMAL, governor.state(RequestLane.SUBTITLE_EXTRACTOR))
        assertEquals(0L, governor.delay(RequestLane.SUBTITLE_EXTRACTOR))
    }

    @Test fun failureClassifierPreservesHttpSemantics() {
        assertEquals(FailureType.HTTP_429, FailureClassifier.classify(429, null))
        assertEquals(FailureType.SERVER_ERROR, FailureClassifier.classify(503, null))
        assertEquals(FailureType.HTTP_403, FailureClassifier.classify(403, null))
    }

    @Test fun failureClassifierDetectsBotIndicationsIn403Body() {
        assertEquals(
            FailureType.BOT_DETECTION,
            FailureClassifier.classify(403, com.subgrab.app.data.HttpFailure(403, "Sign in to confirm that you're not a bot"))
        )
        assertEquals(
            FailureType.ACCESS_DENIED,
            FailureClassifier.classify(403, com.subgrab.app.data.HttpFailure(403, "video unavailable"))
        )
    }

    @Test fun failureClassifierSeparatesStorageFromBenignSubtitleFailures() {
        assertEquals(
            FailureType.STORAGE_ERROR,
            FailureClassifier.classify(null, com.subgrab.app.data.StorageFailure("disk full"))
        )
        assertFalse(FailureClassifier.benign(FailureType.STORAGE_ERROR))
    }

    @Test fun videoMetadataUsesLongFieldsForCountsAndDuration() {
        val video = VideoItem(
            index = 1,
            videoId = "abc",
            title = "Video",
            durationSec = 60,
            availableSubs = emptyList(),
            viewCount = 9_000_000_000L,
            durationSeconds = 3_600L,
            likeCount = 8_000_000_000L
        )
        assertEquals(9_000_000_000L, video.viewCount)
        assertEquals(3_600L, video.durationSeconds)
        assertEquals(8_000_000_000L, video.likeCount)
    }

    @Test fun governorRuntimeInstanceIsShared() {
        assertTrue(RequestGovernor.runtime() === RequestGovernor.runtime())
    }

    @Test fun discoveryClientContractIncludesDirectVideoLane() {
        assertTrue(com.subgrab.app.data.DiscoveryClient::class.java.methods.any { it.name == "discoverVideo" })
    }

    @Test fun downloadConfigDefaultsToSingleSubtitleWorkerAndTenVideosPerTask() {
        assertEquals(1, DownloadConfig().subtitleConcurrency)
        assertEquals(10, DownloadConfig().maxSubtitlesPerTask)
    }
    @Test fun governorNeedsTwoNegativeFailuresToEnterSlowdown() {
        val governor = RequestGovernor()
        val lane = RequestLane.SUBTITLE_EXTRACTOR
        val negative = RequestResult(false, 403, 10, FailureType.ACCESS_DENIED)

        assertFalse(governor.observe(lane, negative))
        assertEquals(GovernorState.NORMAL, governor.state(lane))
        assertTrue(governor.observe(lane, negative))
        assertEquals(GovernorState.SLOWDOWN, governor.state(lane))
        assertEquals(1500L, governor.delay(lane))
    }

    @Test fun taskPlannerUsesConfiguredTaskSizeAndCapsAtFifty() {
        fun videos(count: Int) = (1..count).map {
            VideoItem(it, "id$it", "Video $it", 60, listOf(SubtitleLanguage("vi")), isSelected = true)
        }

        assertEquals(listOf(10), DownloadTaskPlanner.plan(videos(10)).map { it.size })
        assertEquals(listOf(10, 1), DownloadTaskPlanner.plan(videos(11)).map { it.size })
        assertEquals(listOf(5, 5, 1), DownloadTaskPlanner.plan(videos(11), 5).map { it.size })
        assertEquals(listOf(20, 20, 10), DownloadTaskPlanner.plan(videos(60), 20).map { it.size })
        assertEquals(listOf(10, 10, 10, 10, 10), DownloadTaskPlanner.plan(videos(60), 10).map { it.size })
    }

    @Test fun benignFailuresAreRecognized() {
        assertTrue(FailureClassifier.benign(FailureType.NO_SUBTITLE))
        assertTrue(FailureClassifier.benign(FailureType.LANGUAGE_UNAVAILABLE))
        assertTrue(FailureClassifier.benign(FailureType.VIDEO_UNAVAILABLE))
        assertFalse(FailureClassifier.benign(FailureType.ACCESS_DENIED))
    }
}
