package io.libp2p.transport.quic

import io.libp2p.core.*
import io.libp2p.core.crypto.PrivKey
import io.libp2p.core.crypto.PubKey
import io.libp2p.core.crypto.unmarshalPublicKey
import io.libp2p.core.multistream.MultistreamProtocolV1
import io.libp2p.core.multistream.ProtocolBinding
import io.libp2p.core.mux.StreamMuxer
import io.libp2p.core.security.SecureChannel
import io.libp2p.etc.CONNECTION
import io.libp2p.etc.STREAM
import io.libp2p.etc.types.forward
import io.libp2p.transport.implementation.ConnectionOverNetty
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.handler.ssl.SslHandshakeCompletionEvent
import io.netty.incubator.codec.quic.QuicChannel
import io.netty.incubator.codec.quic.QuicStreamType
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.cert.X509CertificateHolder
import java.util.concurrent.CompletableFuture

internal class QuicChannelServerHandler(
    val transport: QuicTransport,
    val localKey: PrivKey,
    val connectionHandler: ConnectionHandler,
    val preHandler: ChannelVisitor<P2PChannel>?
) : ChannelInitializer<QuicChannel>() {

    override fun initChannel(channel: QuicChannel) {
        val connection = ConnectionOverNetty(channel, transport, false)
        channel.attr(CONNECTION).set(connection)
        preHandler?.also { it.visit(connection) }

        channel.pipeline().addLast(object : ChannelInboundHandlerAdapter() {
            override fun userEventTriggered(ctx: ChannelHandlerContext, event: Any) {
                if (event is SslHandshakeCompletionEvent) {
                    if (event.isSuccess) {
                        println("QUIC SslHandshake done")
                        val remoteCert = channel.sslEngine()?.session?.peerCertificates?.firstOrNull()
                        checkNotNull(remoteCert) { "No certificates after QUIC handshake" }
                        val localPeerId = PeerId.fromPubKey(localKey.publicKey())
                        val remotePubKey = getPubKeyFromCert(remoteCert)
                        val remotePeerId = PeerId.fromPubKey(remotePubKey)

                        connection.setSecureSession(
                            SecureChannel.Session(
                                localPeerId,
                                remotePeerId,
                                remotePubKey,
                                null
                            )
                        )

                        connection.setMuxerSession(object : StreamMuxer.Session {
                            override fun <T> createStream(
                                protocols: List<ProtocolBinding<T>>
                            ): StreamPromise<T> {
                                val multiStreamProtocol = MultistreamProtocolV1
                                val multi = multiStreamProtocol.createMultistream(protocols)
                                val controller = CompletableFuture<T>()
                                val streamFut = CompletableFuture<Stream>()
                                channel.createStream(
                                    QuicStreamType.BIDIRECTIONAL,
                                    object : ChannelInboundHandlerAdapter() {
                                        override fun handlerAdded(ctx: ChannelHandlerContext) {
                                            val stream = QuicTransport.createStream(ctx.channel(), connection, true)
                                            ctx.channel().attr(STREAM).set(stream)
                                            multi.toStreamHandler().handleStream(stream)
                                                .forward(controller)
                                                .apply { streamFut.complete(stream) }
                                        }
                                    }
                                )
                                return StreamPromise(streamFut, controller)
                            }
                        }
                        )
                        connectionHandler.handleConnection(connection)
                    } else {
                        println("QUIC SslHandshake failed")
                        ctx.close()
                    }
                }
                super.userEventTriggered(ctx, event)
            }
        })
    }

    fun getPubKeyFromCert(cert: java.security.cert.Certificate): PubKey {
        val bcCertificate = X509CertificateHolder(cert.encoded)
        val libP2PExtensionOid = ASN1ObjectIdentifier("1.3.6.1.4.1.53594.1.1")
        val extension =
            bcCertificate.getExtension(libP2PExtensionOid) ?: throw IllegalStateException("Libp2p extension not found")
        val asn1Sequence = ASN1Sequence.getInstance(extension.extnValue.octets)
        val pbPubKey = asn1Sequence.getObjectAt(0) as ASN1OctetString // 36 bytes
        val pubKey = unmarshalPublicKey(pbPubKey.octets)
        return pubKey
    }
}