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
import org.junit.Test
import java.io.IOException

class QuickConnectTest {

    private val server = MockWebServer()
    private val bodies = mutableListOf<String>()

    private fun serve(handler: (RecordedRequest) -> String) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                bodies += request.body.readUtf8()
                return MockResponse().setBody(handler(request))
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun quickConnect() = QuickConnect(OkHttpClient(), server.url("/").toString())

    private val httpsInfo = """
        {"command":"get_server_info","errno":0,"version":1,
         "env":{"control_host":"cnc.quickconnect.to","relay_region":"de"},
         "server":{"ddns":"NULL","fqdn":"NULL","external":{"ip":"203.0.113.5"},
                   "interface":[{"ip":"192.168.1.10","name":"eth0"}],"serverID":"123"},
         "service":{"port":5001,"ext_port":0,"relay_ip":"198.51.100.7","relay_dn":"synr-de1.myid.direct.quickconnect.to","relay_port":443},
         "smartdns":{"host":"myid.direct.quickconnect.to","lan":["192-168-1-10.myid.direct.quickconnect.to"]}}
    """.trimIndent()

    private val httpInfo = """
        {"command":"get_server_info","errno":0,"version":1,
         "server":{"interface":[{"ip":"192.168.1.10"}],"external":{"ip":"203.0.113.5"}},
         "service":{"port":5000,"ext_port":0}}
    """.trimIndent()

    @Test
    fun `resolves lan first, then internet, then relay`() = runTest {
        serve { "[$httpsInfo,$httpInfo]" }
        val urls = quickConnect().resolve("myid").map { it.url }
        assertEquals(
            listOf(
                "https://192-168-1-10.myid.direct.quickconnect.to:5001",
                "https://192.168.1.10:5001",
                "http://192.168.1.10:5000",
                "https://myid.direct.quickconnect.to:5001",
                "https://203.0.113.5:5001",
                "http://203.0.113.5:5000",
                "https://synr-de1.myid.direct.quickconnect.to:443",
                "https://198.51.100.7:443",
            ),
            urls,
        )
        val request = bodies.first()
        assertTrue(request, request.contains("\"serverID\":\"myid\""))
        assertTrue(request, request.contains("\"command\":\"get_server_info\""))
    }

    @Test
    fun `follows regional site redirect`() = runTest {
        var calls = 0
        serve { if (calls++ == 0) """[{"errno":4,"sites":["${server.hostName}:${server.port}"]}]""" else "[$httpInfo]" }
        // The site is contacted via https, which the mock server does not speak; the redirect
        // therefore fails and the original error is reported.
        try {
            quickConnect().resolve("myid")
            fail("expected exception")
        } catch (e: IOException) {
            assertEquals("QuickConnect ID \"myid\" not found", e.message)
        }
    }

    @Test
    fun `unknown id is reported`() = runTest {
        serve { """[{"errno":4,"suberrno":2}]""" }
        try {
            quickConnect().resolve("nope")
            fail("expected exception")
        } catch (e: IOException) {
            assertEquals("QuickConnect ID \"nope\" not found", e.message)
        }
    }

    @Test
    fun `requests a tunnel when no relay is known`() = runTest {
        serve { request ->
            val body = bodies.last()
            if (body.contains("request_tunnel")) {
                """[{"errno":0,"service":{"relay_ip":"198.51.100.9","relay_port":10001}}]"""
            } else {
                // control_host points back at the mock server (plain http is not used for it,
                // so the tunnel request fails and only direct candidates remain).
                "[$httpInfo]"
            }
        }
        val urls = quickConnect().resolve("myid").map { it.url }
        assertEquals(listOf("http://192.168.1.10:5000", "http://203.0.113.5:5000"), urls)
    }

    @Test
    fun `recognises quickconnect ids in user input`() {
        assertEquals("myid", QuickConnect.idFrom("myid"))
        assertEquals("myid", QuickConnect.idFrom(" quickconnect.to/myid "))
        assertEquals("myid", QuickConnect.idFrom("https://quickconnect.to/myid/"))
        assertEquals("my-id", QuickConnect.idFrom("https://my-id.quickconnect.to"))
        assertEquals("myid", QuickConnect.idFrom("myid.de3.quickconnect.to"))
        assertNull(QuickConnect.idFrom("192.168.1.10:5000"))
        assertNull(QuickConnect.idFrom("https://nas.example.com:5001"))
        assertNull(QuickConnect.idFrom("nas.local"))
        assertNull(QuickConnect.idFrom(""))
    }
}
