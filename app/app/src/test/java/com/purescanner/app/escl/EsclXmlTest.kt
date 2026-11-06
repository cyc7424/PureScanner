package com.purescanner.app.escl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EsclXmlTest {

    private val capabilitiesXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <scan:ScannerCapabilities xmlns:scan="http://schemas.hp.com/imaging/escl/2011/05/03"
                                  xmlns:pwg="http://www.pwg.org/schemas/2010/12/sm">
          <pwg:Version>2.63</pwg:Version>
          <pwg:MakeAndModel>Epson Laserjet</pwg:MakeAndModel>
          <scan:UUID>abc-123</scan:UUID>
          <scan:Platen>
            <scan:PlatenInputCaps>
              <scan:MinWidth>16</scan:MinWidth>
              <scan:MaxWidth>2550</scan:MaxWidth>
              <scan:MinHeight>16</scan:MinHeight>
              <scan:MaxHeight>3507</scan:MaxHeight>
              <scan:SettingProfiles>
                <scan:SettingProfile>
                  <scan:ColorModes>
                    <scan:ColorMode>BlackAndWhite1</scan:ColorMode>
                    <scan:ColorMode>Grayscale8</scan:ColorMode>
                    <scan:ColorMode>RGB24</scan:ColorMode>
                  </scan:ColorModes>
                  <scan:DocumentFormats>
                    <pwg:DocumentFormat>application/pdf</pwg:DocumentFormat>
                    <pwg:DocumentFormat>image/jpeg</pwg:DocumentFormat>
                    <scan:DocumentFormatExt>image/jpeg</scan:DocumentFormatExt>
                  </scan:DocumentFormats>
                  <scan:SupportedResolutions>
                    <scan:DiscreteResolutions>
                      <scan:DiscreteResolution>
                        <scan:XResolution>600</scan:XResolution><scan:YResolution>600</scan:YResolution>
                      </scan:DiscreteResolution>
                      <scan:DiscreteResolution>
                        <scan:XResolution>200</scan:XResolution><scan:YResolution>200</scan:YResolution>
                      </scan:DiscreteResolution>
                      <scan:DiscreteResolution>
                        <scan:XResolution>300</scan:XResolution><scan:YResolution>300</scan:YResolution>
                      </scan:DiscreteResolution>
                    </scan:DiscreteResolutions>
                  </scan:SupportedResolutions>
                </scan:SettingProfile>
              </scan:SettingProfiles>
            </scan:PlatenInputCaps>
          </scan:Platen>
        </scan:ScannerCapabilities>
    """.trimIndent()

    @Test
    fun `parses capabilities`() {
        val caps = EsclXml.parseCapabilities(capabilitiesXml)

        assertEquals("Epson Laserjet", caps.makeAndModel)
        assertEquals("abc-123", caps.uuid)
        assertNull(caps.feeder)

        val platen = caps.platen
        assertNotNull(platen)
        platen!!
        assertEquals(2550, platen.maxWidth)
        assertEquals(3507, platen.maxHeight)
        assertEquals(listOf(200, 300, 600), platen.resolutions)
        assertEquals(listOf(ColorMode.Gray, ColorMode.Color), platen.colorModes)
        assertEquals(listOf(DocumentFormat.Pdf, DocumentFormat.Jpeg), platen.formats)
    }

    @Test
    fun `paper sizes are limited by scanner area`() {
        val platen = EsclXml.parseCapabilities(capabilitiesXml).platen!!
        // MaxHeight 3507 is one unit short of A4's 3508; that must still count as A4 (the request gets clamped).
        assertTrue(platen.supports(PaperSize.A4))
        assertTrue(platen.supports(PaperSize.A5))
        assertFalse(platen.supports(PaperSize.Legal))
    }

    @Test
    fun `parses resolution range`() {
        val xml = """
            <scan:ScannerCapabilities xmlns:scan="http://schemas.hp.com/imaging/escl/2011/05/03">
              <scan:Platen><scan:PlatenInputCaps>
                <scan:MaxWidth>2550</scan:MaxWidth><scan:MaxHeight>3507</scan:MaxHeight>
                <scan:SettingProfiles><scan:SettingProfile>
                  <scan:SupportedResolutions><scan:ResolutionRange>
                    <scan:XResolutionRange><scan:Min>100</scan:Min><scan:Max>600</scan:Max></scan:XResolutionRange>
                  </scan:ResolutionRange></scan:SupportedResolutions>
                </scan:SettingProfile></scan:SettingProfiles>
              </scan:PlatenInputCaps></scan:Platen>
            </scan:ScannerCapabilities>
        """.trimIndent()

        assertEquals(listOf(100, 150, 200, 300, 600), EsclXml.parseCapabilities(xml).platen!!.resolutions)
    }

    @Test
    fun `parses status`() {
        val xml = """
            <scan:ScannerStatus xmlns:scan="http://schemas.hp.com/imaging/escl/2011/05/03"
                                xmlns:pwg="http://www.pwg.org/schemas/2010/12/sm">
              <pwg:State>Processing</pwg:State>
              <scan:AdfState>ScannerAdfLoaded</scan:AdfState>
            </scan:ScannerStatus>
        """.trimIndent()

        val status = EsclXml.parseStatus(xml)
        assertEquals(ScannerState.Processing, status.state)
        assertEquals("ScannerAdfLoaded", status.adfState)
    }

    @Test(expected = EsclException.Parse::class)
    fun `garbage xml throws parse exception`() {
        EsclXml.parseCapabilities("this is not xml")
    }

    @Test
    fun `scan settings xml`() {
        val xml = EsclXml.buildScanSettings(
            ScanSettings(paperSize = PaperSize.A4, resolution = 300, colorMode = ColorMode.Color),
        )
        assertTrue(xml.contains("<pwg:Width>2480</pwg:Width>"))
        assertTrue(xml.contains("<pwg:Height>3508</pwg:Height>"))
        assertTrue(xml.contains("<pwg:InputSource>Platen</pwg:InputSource>"))
        assertTrue(xml.contains("<scan:ColorMode>RGB24</scan:ColorMode>"))
        assertTrue(xml.contains("<scan:XResolution>300</scan:XResolution>"))
        assertTrue(xml.contains("<pwg:DocumentFormat>image/jpeg</pwg:DocumentFormat>"))
    }

    @Test
    fun `scan region is clamped to scanner maximum`() {
        val xml = EsclXml.buildScanSettings(ScanSettings(paperSize = PaperSize.A4, maxWidth = 2400, maxHeight = 3507))
        assertTrue(xml.contains("<pwg:Width>2400</pwg:Width>"))
        assertTrue(xml.contains("<pwg:Height>3507</pwg:Height>"))
    }
}
