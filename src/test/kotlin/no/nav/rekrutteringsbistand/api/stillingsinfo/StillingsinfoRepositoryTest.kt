package no.nav.rekrutteringsbistand.api.stillingsinfo

import arrow.core.Some
import arrow.core.getOrElse
import no.nav.rekrutteringsbistand.api.Testdata.enStillingsinfo
import no.nav.rekrutteringsbistand.api.Testdata.enStillingsinfoOppdatering
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest
@ActiveProfiles("local")
class StillingsinfoRepositoryTest {

    val tilLagring = enStillingsinfo

    @Autowired
    lateinit var repository: StillingsinfoRepository

    @Test
    fun `skal kunne lagre og hente ut rekrutteringsbistand`() {
        repository.lagre(tilLagring)
        val lagretRekrutteringsbistand = repository.hentForStilling(tilLagring.stillingsid)

        assertThat(lagretRekrutteringsbistand).isEqualTo(Some(tilLagring))
    }

    @Test
    fun `Skal kunne oppdatere eierident og eiernavn på rekrutteringsbistand`() {
        repository.lagre(tilLagring)
        repository.oppdaterEierIdentOgEierNavn(enStillingsinfoOppdatering)

        val endretRekrutteringsbistand = repository.hentForStilling(tilLagring.stillingsid).getOrElse { fail("Testsetup") }

        assertThat(endretRekrutteringsbistand.eier.navident).isEqualTo(enStillingsinfoOppdatering.eier.navident)
        assertThat(endretRekrutteringsbistand.eier.navn).isEqualTo(enStillingsinfoOppdatering.eier.navn)
    }

    @After
    fun cleanUp() {
        repository.slett(tilLagring.stillingsinfoid)
    }
}