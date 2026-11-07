package com.calmyjane.jaymusic.core.synology

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class SynologyClientTest {

    private val server = MockWebServer()
    private val client = SynologyClient(OkHttpClient())
    private val requests = mutableListOf<RecordedRequest>()
    private var loginResponse = """{"success":true,"data":{"sid":"SID123"}}"""

    private val apiInfo = """
        {"success":true,"data":{
          "SYNO.API.Auth":{"path":"entry.cgi","minVersion":1,"maxVersion":7},
          "SYNO.FileStation.List":{"path":"entry.cgi","minVersion":1,"maxVersion":2},
          "SYNO.FileStation.Download":{"path":"entry.cgi","minVersion":1,"maxVersion":2},
          "SYNO.AudioStation.Song":{"path":"AudioStation/song.cgi","minVersion":1,"maxVersion":3}
        }}
    """.trimIndent()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val url = request.requestUrl!!
                val body = when {
                    url.encodedPath == "/webapi/query.cgi" -> apiInfo
                    request.method == "POST" -> loginResponse
                    url.queryParameter("method") == "list_share" ->
                        """{"success":true,"data":{"total":1,"offset":0,"shares":[{"isdir":true,"name":"music","path":"/music"}]}}"""
                    url.queryParameter("api") == "SYNO.AudioStation.Song" && url.queryParameter("method") == "list" ->
                        """{"success":true,"data":{"total":2,"offset":0,"songs":[
                          {"id":"music_1","path":"/volume1/music/a.mp3","title":"A","type":"file","additional":{"song_rating":{"rating":4}}},
                          {"id":"music_2","path":"/volume1/music/b.mp3","title":"B","type":"file","additional":{"song_rating":{"rating":0}}}]}}"""
                    url.queryParameter("method") == "setrating" -> """{"success":true}"""
                    url.queryParameter("method") == "list" -> listPage(url.queryParameter("offset")!!.toInt())
                    else -> """{"success":false,"error":{"code":101}}"""
                }
                return MockResponse().setBody(body).setHeader("Content-Type", "application/json")
            }
        }
        server.start()
    }

    /** Pretends the folder has 1500 files so pagination is exercised. */
    private fun listPage(offset: Int): String {
        val count = if (offset == 0) 1000 else 500
        val files = (offset until offset + count).joinToString(",") { i ->
            """{"isdir":false,"name":"$i.mp3","path":"/music/$i.mp3","additional":{"size":$i,"time":{"mtime":1700000000}}}"""
        }
        return """{"success":true,"data":{"total":1500,"offset":$offset,"files":[$files]}}"""
    }

    @After
    fun tearDown() = server.shutdown()

    private fun baseUrl() = "localhost:${server.port}/"

    @Test
    fun `logs in with form post and uses the latest supported auth version`() = runTest {
        val session = client.login(baseUrl(), "jay", "secret", deviceName = "phone")
        assertEquals("SID123", session.sid)
        assertNull(session.deviceId)
        val login = requests.first { it.method == "POST" }
        val form = login.body.readUtf8()
        assertTrue(form, form.contains("version=6"))
        assertTrue(form, form.contains("passwd=secret"))
        assertTrue("password must not be in the URL", login.requestUrl!!.queryParameter("passwd") == null)
    }

    @Test
    fun `reports 2-factor requirement and stores device token`() = runTest {
        loginResponse = """{"success":false,"error":{"code":403}}"""
        try {
            client.login(baseUrl(), "jay", "secret", deviceName = "phone")
            fail("expected exception")
        } catch (e: SynologyException) {
            assertTrue(e.isOtpRequired)
        }
        loginResponse = """{"success":true,"data":{"sid":"S","did":"DEVICE"}}"""
        val session = client.login(baseUrl(), "jay", "secret", deviceName = "phone", otpCode = "123456")
        assertEquals("DEVICE", session.deviceId)
        val form = requests.last { it.method == "POST" }.body.readUtf8()
        assertTrue(form, form.contains("otp_code=123456"))
        assertTrue(form, form.contains("enable_device_token=yes"))
    }

    @Test
    fun `wrong password is reported as bad credentials`() = runTest {
        loginResponse = """{"success":false,"error":{"code":400}}"""
        try {
            client.login(baseUrl(), "jay", "wrong", deviceName = "phone")
            fail("expected exception")
        } catch (e: SynologyException) {
            assertTrue(e.isBadCredentials)
            assertEquals("Wrong user name or password", e.message)
        }
    }

    @Test
    fun `lists folders across pages`() = runTest {
        val session = client.login(baseUrl(), "jay", "secret", deviceName = "phone")
        val files = client.listFolder(session, "/music")
        assertEquals(1500, files.size)
        assertEquals(1499L, files.last().size)
        assertEquals(1700000000L, files.first().modified)
        val shares = client.listShares(session)
        assertEquals(listOf("/music"), shares.map { it.path })
    }

    @Test
    fun `download url carries json encoded path and session`() = runTest {
        val session = client.login(baseUrl(), "jay", "secret", deviceName = "phone")
        val url = client.downloadUrl(session, "/music/AC,DC/Back.mp3")
        assertEquals("""["/music/AC,DC/Back.mp3"]""", url.queryParameter("path"))
        assertEquals("SID123", url.queryParameter("_sid"))
        assertEquals("/webapi/entry.cgi", url.encodedPath)
    }

    @Test
    fun `normalizes user typed addresses`() {
        assertEquals("http://nas.local:5000/", SynologyClient.normalizeBaseUrl("nas.local:5000")!!.toString())
        assertEquals("https://x.com:5001/", SynologyClient.normalizeBaseUrl(" https://x.com:5001/webman/ ")!!.toString())
        assertNull(SynologyClient.normalizeBaseUrl(""))
    }

    @Test
    fun `reads and writes audio station ratings`() = runTest {
        val session = client.login(baseUrl(), "jay", "secret", deviceName = "phone", sessionName = "AudioStation")
        assertTrue(client.hasAudioStation(session))
        val form = requests.last { it.method == "POST" }.body.readUtf8()
        assertTrue(form, form.contains("session=AudioStation"))
        val ratings = client.listRatings(session)
        assertEquals(listOf(ServerRating("music_1", "/volume1/music/a.mp3", 4), ServerRating("music_2", "/volume1/music/b.mp3", 0)), ratings)
        client.setRating(session, "music_2", 5)
        val set = requests.last().requestUrl!!
        assertEquals("/webapi/AudioStation/song.cgi", set.encodedPath)
        assertEquals("5", set.queryParameter("rating"))
        assertEquals("music_2", set.queryParameter("id"))
    }
}
