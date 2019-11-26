package no.nav.rekrutteringsbistand.api.stilling

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.junit.WireMockRule
import no.nav.rekrutteringsbistand.api.Testdata.enAnnenStilling
import no.nav.rekrutteringsbistand.api.Testdata.enAnnenStillingsinfo
import no.nav.rekrutteringsbistand.api.Testdata.enStilling
import no.nav.rekrutteringsbistand.api.Testdata.enStillingsinfo
import no.nav.rekrutteringsbistand.api.stillingsinfo.StillingsinfoRepository
import no.nav.rekrutteringsbistand.api.support.config.MockConfig.Companion.sokResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpHeaders.*
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
internal class StillingComponentTest {

    @get:Rule
    val wiremock = WireMockRule(options().port(9914))

    @LocalServerPort
    var port = 0

    val localBaseUrl by lazy { "http://localhost:$port/rekrutteringsbistand-api" }

    @Autowired
    lateinit var repository: StillingsinfoRepository

    private val restTemplate = TestRestTemplate(TestRestTemplate.HttpClientOption.ENABLE_COOKIES)

    val objectMapper = ObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @Before
    fun authenticateClient() {
        restTemplate.getForObject("$localBaseUrl/local/cookie-isso", String::class.java)
    }

    @Test
    fun `hentStilling skal returnere en stilling uten stillingsinfo hvis det ikke er lagret`() {
        mockUtenAuthorization("/b2b/api/v1/ads/${enStilling.uuid}", enStilling)
        restTemplate.getForObject("$localBaseUrl/rekrutteringsbistand/api/v1/stilling/${enStilling.uuid}", Stilling::class.java).also {
            assertThat(it).isEqualTo(enStilling)
        }
    }

    @Test
    fun `hentStilling skal returnere stilling beriket med stillingsinfo`() {
        repository.lagre(enStillingsinfo)

        mockUtenAuthorization("/b2b/api/v1/ads/${enStilling.uuid}", enStilling)

        restTemplate.getForObject("$localBaseUrl/rekrutteringsbistand/api/v1/stilling/${enStilling.uuid}", Stilling::class.java).also {
            assertThat(it.rekruttering).isEqualTo(enStillingsinfo.asDto())
            assertThat(it.uuid).isEqualTo(enStillingsinfo.stillingsid.asString())
        }
    }

    @Test
    fun `sok skal returnere sok`() {
        val headers = HttpHeaders()

        val request = HttpEntity("body", headers)
        restTemplate.postForObject("$localBaseUrl/search-api/underenhet/_search", request, String::class.java).also {
            assertThat(it).isEqualToIgnoringWhitespace(sokResponse)
        }
    }

    @Test
    fun `hentStillinger skal returnere stillinger beriket med stillingsinfo`() {
        repository.lagre(enStillingsinfo)
        repository.lagre(enAnnenStillingsinfo)

        val opprinneligeStillinger = Page(
                content = listOf(enStilling, enAnnenStilling),
                totalElements = 2,
                totalPages = 1
        )

        mock("/rekrutteringsbistand/api/v1/ads", opprinneligeStillinger)

        val stillinger: List<Stilling> = restTemplate.exchange(
                "$localBaseUrl/rekrutteringsbistand/api/v1/ads",
                HttpMethod.GET,
                null,
                object : ParameterizedTypeReference<Page<Stilling>>() {}
        ).body!!.content

        assertThat(stillinger.first().rekruttering).isEqualTo(enStillingsinfo.asDto())
        assertThat(stillinger.last().rekruttering).isEqualTo(enAnnenStillingsinfo.asDto())
    }

    private fun mock(urlPath: String, body: Any) {
        wiremock.stubFor(
                WireMock.get(urlPathMatching(urlPath))
                        .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON.toString()))
                        .withHeader(ACCEPT, equalTo(APPLICATION_JSON.toString()))
                        .withHeader(AUTHORIZATION, matching("Bearer .*}"))
                        .willReturn(aResponse().withStatus(200)
                                .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                                .withBody(objectMapper.writeValueAsString(body)))
        )
    }

    private fun mockUtenAuthorization(urlPath: String, body: Any) {
        wiremock.stubFor(
                WireMock.get(urlPathMatching(urlPath))
                        .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON.toString()))
                        .withHeader(ACCEPT, equalTo(APPLICATION_JSON.toString()))
                        .willReturn(aResponse().withStatus(200)
                                .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                                .withBody(objectMapper.writeValueAsString(body)))
        )
    }

    @After
    fun tearDown() {
        repository.slett(enStillingsinfo.stillingsinfoid)
        repository.slett(enAnnenStillingsinfo.stillingsinfoid)
    }
}
