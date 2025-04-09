package io.libp2p.security.tls

import io.libp2p.core.PeerId
import io.libp2p.crypto.keys.generateEd25519KeyPair
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.encoders.Hex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CertificatesTest {

    @Test
    fun ed25519Peer() {
        val hex = "308201773082011ea003020102020900f5bd0debaa597f52300a06082a8648ce3d04030230003020170d3735303130313030303030305a180f34303936303130313030303030305a30003059301306072a8648ce3d020106082a8648ce3d030107034200046bf9871220d71dcb3483ecdfcbfcc7c103f8509d0974b3c18ab1f1be1302d643103a08f7a7722c1b247ba3876fe2c59e26526f479d7718a85202ddbe47562358a37f307d307b060a2b0601040183a25a01010101ff046a30680424080112207fda21856709c5ae12fd6e8450623f15f11955d384212b89f56e7e136d2e17280440aaa6bffabe91b6f30c35e3aa4f94b1188fed96b0ffdd393f4c58c1c047854120e674ce64c788406d1c2c4b116581fd7411b309881c3c7f20b46e54c7e6fe7f0f300a06082a8648ce3d040302034700304402207d1a1dbd2bda235ff2ec87daf006f9b04ba076a5a5530180cd9c2e8f6399e09d0220458527178c7e77024601dbb1b256593e9b96d961b96349d1f560114f61a87595"
        val certBytes = Hex.decode(hex)
        val certHolder = X509CertificateHolder(certBytes)
        val cert = JcaX509CertificateConverter().setProvider(BouncyCastleProvider()).getCertificate(certHolder)
        getPublicKeyFromCert(arrayOf(cert))
        val peerIdFromCert = verifyAndExtractPeerId(arrayOf(cert))
        val expectedPeerId = PeerId.fromBase58("12D3KooWJRSrypvnpHgc6ZAgyCni4KcSmbV7uGRaMw5LgMKT18fq")
        assertEquals(peerIdFromCert, expectedPeerId)
    }

    @Test
    fun ecdsaPeer() {
        val hex = "308201c030820166a003020102020900eaf419a6e3edb4a6300a06082a8648ce3d04030230003020170d3735303130313030303030305a180f34303936303130313030303030305a30003059301306072a8648ce3d020106082a8648ce3d030107034200048dbf1116c7c608d6d5292bd826c3feb53483a89fce434bf64538a359c8e07538ff71f6766239be6a146dcc1a5f3bb934bcd4ae2ae1d4da28ac68b4a20593f06ba381c63081c33081c0060a2b0601040183a25a01010101ff0481ae3081ab045f0803125b3059301306072a8648ce3d020106082a8648ce3d0301070342000484b93fa456a74bd0153919f036db7bc63c802f055bc7023395d0203de718ee0fc7b570b767cdd858aca6c7c4113ff002e78bd2138ac1a3b26dde3519e06979ad04483046022100bc84014cea5a41feabdf4c161096564b9ccf4b62fbef4fe1cd382c84e11101780221009204f086a84cb8ed8a9ddd7868dc90c792ee434adf62c66f99a08a5eba11615b300a06082a8648ce3d0403020348003045022054b437be9a2edf591312d68ff24bf91367ad4143f76cf80b5658f232ade820da022100e23b48de9df9c25d4c83ddddf75d2676f0b9318ee2a6c88a736d85eab94a912f"
        val certBytes = Hex.decode(hex)
        val certHolder = X509CertificateHolder(certBytes)
        val cert = JcaX509CertificateConverter().setProvider(BouncyCastleProvider()).getCertificate(certHolder)
        getPublicKeyFromCert(arrayOf(cert))
        val peerIdFromCert = verifyAndExtractPeerId(arrayOf(cert))
        val expectedPeerId = PeerId.fromBase58("QmZcrvr3r4S3QvwFdae3c2EWTfo792Y14UpzCZurhmiWeX")
        assertEquals(peerIdFromCert, expectedPeerId)
    }

    @Test
    fun secp256k1Peer() {
        val hex = "3082018230820128a003020102020900f3b305f55622cfdf300a06082a8648ce3d04030230003020170d3735303130313030303030305a180f34303936303130313030303030305a30003059301306072a8648ce3d020106082a8648ce3d0301070342000458f7e9581748ff9bdd933b655cc0e5552a1248f840658cc221dec2186b5a2fe4641b86ab7590a3422cdbb1000cf97662f27e5910d7569f22feed8829c8b52e0fa38188308185308182060a2b0601040183a25a01010101ff0471306f042508021221026b053094d1112bce799dc8026040ae6d4eb574157929f1598172061f753d9b1b04463044022040712707e97794c478d93989aaa28ae1f71c03af524a8a4bd2d98424948a782302207b61b7f074b696a25fb9e0059141a811cccc4cc28042d9301b9b2a4015e87470300a06082a8648ce3d04030203480030450220143ae4d86fdc8675d2480bb6912eca5e39165df7f572d836aa2f2d6acfab13f8022100831d1979a98f0c4a6fb5069ca374de92f1a1205c962a6d90ad3d7554cb7d9df4"
        val certBytes = Hex.decode(hex)
        val certHolder = X509CertificateHolder(certBytes)
        val cert = JcaX509CertificateConverter().setProvider(BouncyCastleProvider()).getCertificate(certHolder)
        getPublicKeyFromCert(arrayOf(cert))
        val peerIdFromCert = verifyAndExtractPeerId(arrayOf(cert))
        val expectedPeerId = PeerId.fromBase58("16Uiu2HAm2dSCBFxuge46aEt7U1oejtYuBUZXxASHqmcfVmk4gsbx")
        assertEquals(peerIdFromCert, expectedPeerId)
    }

    @Test
    fun rustCert() {
        val hex = "3082018230820129a00302010202144d1178a3bb828459ce1e266baa234ed8f0615c06300a06082a8648ce3d04030230003020170d3735303130313030303030305a180f34303936303130313030303030305a30003059301306072a8648ce3d020106082a8648ce3d03010703420004089ff3ab6e4b42cb2252a41aff3b8cb7c6f71f7050f6604ff138219f35652de1f6a006f487cd15d88db31e12dcd3b080cd53aa5869a649a13762b6193029f61ca37f307d307b060a2b0601040183a25a01010101ff046a30680424080112207f249e77411a3fa0c3f6305a8446cd45f9fb73ae2412f230f21943cf15dabc3d044025544b48ff50963b5f26b277906a08ba3f231d2d80f399801f856e21e3d9ec2b84c51f8063eb4ae70e52cd940ff82a5aa29b82f3f82b5fb2ae67a9d5bba75c0b300a06082a8648ce3d0403020347003044022031580479526dd6a38a3cc1e90122ac9437d3633aa63f697165099e3d3c4cb3b70220525a60d13802089a9cbb0752646a2801df74d06d6f7785ff21931dca4e188e16"
        val certBytes = Hex.decode(hex)
        val certHolder = X509CertificateHolder(certBytes)
        val cert = JcaX509CertificateConverter().setProvider(BouncyCastleProvider()).getCertificate(certHolder)
        getPublicKeyFromCert(arrayOf(cert))
        val peerIdFromCert = verifyAndExtractPeerId(arrayOf(cert))
        val expectedPeerId = PeerId.fromBase58("12D3KooWJNgLEeuYt54A58gcnsggjHhVt6YBsrK71QRXTzK9WABn")
        assertEquals(peerIdFromCert, expectedPeerId)
    }

    @Test
    fun invalidCert() {
        val hex = "308201773082011da003020102020830a73c5d896a1109300a06082a8648ce3d04030230003020170d3735303130313030303030305a180f34303936303130313030303030305a30003059301306072a8648ce3d020106082a8648ce3d03010703420004bbe62df9a7c1c46b7f1f21d556deec5382a36df146fb29c7f1240e60d7d5328570e3b71d99602b77a65c9b3655f62837f8d66b59f1763b8c9beba3be07778043a37f307d307b060a2b0601040183a25a01010101ff046a3068042408011220ec8094573afb9728088860864f7bcea2d4fd412fef09a8e2d24d482377c20db60440ecabae8354afa2f0af4b8d2ad871e865cb5a7c0c8d3dbdbf42de577f92461a0ebb0a28703e33581af7d2a4f2270fc37aec6261fcc95f8af08f3f4806581c730a300a06082a8648ce3d040302034800304502202dfb17a6fa0f94ee0e2e6a3b9fb6e986f311dee27392058016464bd130930a61022100ba4b937a11c8d3172b81e7cd04aedb79b978c4379c2b5b24d565dd5d67d3cb3c"
        val certBytes = Hex.decode(hex)
        val certHolder = X509CertificateHolder(certBytes)
        val cert = JcaX509CertificateConverter().setProvider(BouncyCastleProvider()).getCertificate(certHolder)
        assertThrows<IllegalStateException>({ verifyAndExtractPeerId(arrayOf(cert)) })
    }

    @Test
    fun buildEd25519Cert() {
        val host = generateEd25519KeyPair()
        val conn = generateEd25519KeyPair()
        val cert = buildCert(host.first, conn.first)
        getPublicKeyFromCert(arrayOf(cert))
        val peerIdFromCert = verifyAndExtractPeerId(arrayOf(cert))
        val expectedPeerId = PeerId.fromPubKey(host.second)
        assertEquals(peerIdFromCert, expectedPeerId)
    }
}
