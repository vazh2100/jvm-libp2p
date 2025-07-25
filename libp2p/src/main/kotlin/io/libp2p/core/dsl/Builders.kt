package io.libp2p.core.dsl

import identify.pb.IdentifyOuterClass
import io.libp2p.core.AddressBook
import io.libp2p.core.ChannelVisitor
import io.libp2p.core.Connection
import io.libp2p.core.ConnectionHandler
import io.libp2p.core.Host
import io.libp2p.core.P2PChannel
import io.libp2p.core.Stream
import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.PrivKey
import io.libp2p.core.crypto.generateKeyPair
import io.libp2p.core.multiformats.Multiaddr
import io.libp2p.core.multistream.MultistreamProtocol
import io.libp2p.core.multistream.MultistreamProtocolDebug
import io.libp2p.core.multistream.MultistreamProtocolV1
import io.libp2p.core.multistream.ProtocolBinding
import io.libp2p.core.mux.StreamMuxer
import io.libp2p.core.mux.StreamMuxerDebug
import io.libp2p.core.mux.StreamMuxerProtocol
import io.libp2p.core.security.SecureChannel
import io.libp2p.core.transport.Transport
import io.libp2p.etc.types.lazyVar
import io.libp2p.etc.types.toProtobuf
import io.libp2p.etc.util.netty.LoggingHandlerShort
import io.libp2p.host.HostImpl
import io.libp2p.host.MemoryAddressBook
import io.libp2p.network.NetworkImpl
import io.libp2p.protocol.IdentifyBinding
import io.libp2p.security.noise.NoiseXXSecureChannel
import io.libp2p.transport.ConnectionUpgrader
import io.libp2p.transport.tcp.TcpTransport
import io.netty.channel.ChannelHandler
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import java.util.concurrent.CopyOnWriteArrayList

typealias TransportCtor = (ConnectionUpgrader) -> Transport
typealias SecureTransportCtor = (PrivKey, List<ProtocolBinding<*>>) -> Transport
typealias SecureChannelCtor = (PrivKey, List<StreamMuxer>) -> SecureChannel
typealias IdentityFactory = () -> PrivKey

class HostConfigurationException(message: String) : RuntimeException(message)

/**
 * Starts a fluent builder to construct a new Host.
 */
fun host(fn: Builder.() -> Unit) = Builder().apply(fn).build(Builder.Defaults.Standard)
fun host(defMode: Builder.Defaults, fn: Builder.() -> Unit) = Builder().apply(fn).build(defMode)

open class Builder {
    enum class Defaults {
        None,
        Standard
    }

    protected open val identity = IdentityBuilder()
    protected open val secureChannels = SecureChannelsBuilder()
    protected open val muxers = MuxersBuilder()
    protected open val transports = TransportsBuilder()
    protected open val secureTransports = SecureTransportsBuilder()
    protected open val addressBook = AddressBookBuilder()
    protected open val protocols = ProtocolsBuilder()
    protected open val connectionHandlers = ConnectionHandlerBuilder()
    protected open val network = NetworkConfigBuilder()
    protected open val debug = DebugBuilder()
    var multistreamProtocol: MultistreamProtocol = MultistreamProtocolV1
    var secureMultistreamProtocol: MultistreamProtocol by lazyVar { multistreamProtocol }
    var muxerMultistreamProtocol: MultistreamProtocol by lazyVar { multistreamProtocol }
    var streamMultistreamProtocol: MultistreamProtocol by lazyVar { multistreamProtocol }

    /**
     * Sets an identity for this host. If unset, libp2p will default to a random identity.
     */
    fun identity(fn: IdentityBuilder.() -> Unit): Builder = apply { fn(identity) }

    /**
     * Manipulates the security channels for this host.
     */
    fun secureChannels(fn: SecureChannelsBuilder.() -> Unit): Builder = apply { fn(secureChannels) }

    /**
     * Manipulates the stream muxers for this host.
     */
    fun muxers(fn: MuxersBuilder.() -> Unit): Builder = apply { fn(muxers) }

    /**
     * Manipulates the transports for this host.
     */
    open fun transports(fn: TransportsBuilder.() -> Unit): Builder = apply { fn(transports) }

    /**
     * [AddressBook] implementation
     */
    fun addressBook(fn: AddressBookBuilder.() -> Unit): Builder = apply { fn(addressBook) }

    /**
     * Available protocols as implementations of [ProtocolBinding] interface
     * These protocols would be available when acting as a stream responder, and
     * could be actively created by calling [io.libp2p.core.Host.newStream]
     *
     * If the protocol class also implements the [ConnectionHandler] interface
     * it is automatically added as a connection handler
     *
     * The protocol may implement the [ConnectionHandler] interface if it wishes to
     * actively open an outbound stream for every new [io.libp2p.core.Connection].
     */
    fun protocols(fn: ProtocolsBuilder.() -> Unit): Builder = apply { fn(protocols) }

    fun connectionHandlers(fn: ConnectionHandlerBuilder.() -> Unit): Builder = apply { fn(connectionHandlers) }

    /**
     * Manipulates network configuration
     */
    fun network(fn: NetworkConfigBuilder.() -> Unit): Builder = apply { fn(network) }

    /**
     * Can be used for debug/logging purposes to inject debug handlers
     * to different pipeline points
     */
    fun debug(fn: DebugBuilder.() -> Unit): Builder = apply { fn(debug) }

    /**
     * Constructs the Host with the provided parameters.
     */
    fun build(def: Defaults): Host {
        if (def == Defaults.None) {
            if (identity.factory == null) throw IllegalStateException("No identity builder")

            if (secureTransports.isEmpty() && transports.values.isEmpty()) throw HostConfigurationException("at least one transport is required")
            if (secureTransports.isEmpty() && secureChannels.values.isEmpty()) throw HostConfigurationException("at least one secure channel or secure transport is required")
            if (secureTransports.isEmpty() && muxers.values.isEmpty()) throw HostConfigurationException("at least one muxer or secure transport is required")
        }
        if (def == Defaults.Standard) {
            if (identity.factory == null) identity.random()
            if (transports.values.isEmpty()) transports { add(::TcpTransport) }
            if (secureChannels.values.isEmpty()) secureChannels { add(::NoiseXXSecureChannel) }
            if (muxers.values.isEmpty()) muxers { add(StreamMuxerProtocol.Mplex) }
        }

        if (debug.beforeSecureHandler.handlers.isNotEmpty()) {
            (secureMultistreamProtocol as? MultistreamProtocolDebug)?.also {
                val broadcast = ChannelVisitor.createBroadcast(*debug.beforeSecureHandler.handlers.toTypedArray())
                secureMultistreamProtocol = it.copyWithHandlers(preHandler = broadcast.toChannelHandler())
            } ?: throw IllegalStateException("beforeSecureHandler can't be installed as MultistreamProtocol doesn't support debugging interface: ${secureMultistreamProtocol.javaClass}")
        }

        if (debug.afterSecureHandler.handlers.isNotEmpty()) {
            (muxerMultistreamProtocol as? MultistreamProtocolDebug)?.also {
                val broadcast = ChannelVisitor.createBroadcast(*debug.afterSecureHandler.handlers.toTypedArray())
                muxerMultistreamProtocol = it.copyWithHandlers(preHandler = broadcast.toChannelHandler())
            } ?: throw IllegalStateException("afterSecureHandler can't be installed as MultistreamProtocol doesn't support debugging interface: ${muxerMultistreamProtocol.javaClass}")
        }

        val streamVisitors = ChannelVisitor.createBroadcast<Stream>()
        (streamMultistreamProtocol as? MultistreamProtocolDebug)?.also {
            val broadcastPre =
                ChannelVisitor.createBroadcast(*(debug.streamPreHandler.handlers + (streamVisitors as ChannelVisitor<Stream>)).toTypedArray())
            val broadcast = ChannelVisitor.createBroadcast(*debug.streamHandler.handlers.toTypedArray())
            streamMultistreamProtocol =
                it.copyWithHandlers(broadcastPre.toChannelHandler(), broadcast.toChannelHandler())
        } ?: throw IllegalStateException("streamPreHandler or streamHandler can't be installed as MultistreamProtocol doesn't support debugging interface: ${streamMultistreamProtocol.javaClass}")

        val privKey = identity.factory!!()

        protocols.values.mapNotNull { (it as? IdentifyBinding) }.map { it.protocol }.find { it.idMessage == null }?.apply {
            // initializing Identify with appropriate values
            IdentifyOuterClass.Identify.newBuilder().apply {
                agentVersion = "jvm/0.1"
                protocolVersion = "p2p/0.1"
                publicKey = privKey.publicKey().bytes().toProtobuf()
                addAllListenAddrs(network.listen.map { Multiaddr(it).serialize().toProtobuf() })
                addAllProtocols(protocols.flatMap { it.protocolDescriptor.announceProtocols })
            }.build().also {
                this.idMessage = it
            }
        }

        val updatableProtocols: MutableList<ProtocolBinding<Any>> = CopyOnWriteArrayList(protocols.values)
        val muxers = muxers.map { it.createMuxer(streamMultistreamProtocol, updatableProtocols) }

        val secureChannels = secureChannels.values.map { it(privKey, muxers) }

        if (debug.muxFramesHandler.handlers.isNotEmpty()) {
            val broadcast = ChannelVisitor.createBroadcast(*debug.muxFramesHandler.handlers.toTypedArray())
            muxers.mapNotNull { it as? StreamMuxerDebug }.forEach {
                it.muxFramesDebugHandler = broadcast
            }
        }

        val upgrader = ConnectionUpgrader(secureMultistreamProtocol, secureChannels, muxerMultistreamProtocol, muxers)

        val allTransports =
            listOf(
                transports.values.map { it(upgrader) },
                secureTransports.values.map { it(privKey, updatableProtocols) }
            ).flatten()

        val addressBook = addressBook.impl

        val connHandlerProtocols = protocols.values.mapNotNull { it as? ConnectionHandler }
        val broadcastConnHandler = ConnectionHandler.createBroadcast(
            connHandlerProtocols +
                connectionHandlers.values
        )
        val networkImpl = NetworkImpl(allTransports, broadcastConnHandler)

        return HostImpl(
            privKey,
            networkImpl,
            addressBook,
            network.listen.map { Multiaddr(it) },
            updatableProtocols,
            broadcastConnHandler,
            streamVisitors
        )
    }
}

class NetworkConfigBuilder {
    val listen = mutableListOf<String>()

    fun listen(vararg addrs: String): NetworkConfigBuilder = apply { listen += addrs }
}

class IdentityBuilder {
    var factory: IdentityFactory? = null

    fun random() = random(KeyType.ECDSA)
    fun random(keyType: KeyType): IdentityBuilder = apply { factory = { generateKeyPair(keyType).first } }
}

class AddressBookBuilder {
    var impl: AddressBook by lazyVar { MemoryAddressBook() }

    fun memory(): AddressBookBuilder = apply { impl = MemoryAddressBook() }
}

class SecureTransportsBuilder : Enumeration<SecureTransportCtor>()
class TransportsBuilder : Enumeration<TransportCtor>()
class SecureChannelsBuilder : Enumeration<SecureChannelCtor>()
class MuxersBuilder : Enumeration<StreamMuxerProtocol>()
class ProtocolsBuilder : Enumeration<ProtocolBinding<Any>>()
class ConnectionHandlerBuilder : Enumeration<ConnectionHandler>()

class DebugBuilder {
    /**
     * Injects the [ChannelHandler] to the wire closest point.
     * Could be primarily useful for security handshake debugging/monitoring
     */
    val beforeSecureHandler = DebugHandlerBuilder<Connection>("wire.sec.before")

    /**
     * Injects the [ChannelHandler] right after the connection cipher
     * to handle plain wire messages
     */
    val afterSecureHandler = DebugHandlerBuilder<Connection>("wire.sec.after")

    /**
     * Injects the [ChannelHandler] right after the [StreamMuxer] pipeline handler
     * It intercepts [io.libp2p.mux.MuxFrame] instances
     */
    val muxFramesHandler = DebugHandlerBuilder<Connection>("wire.mux.frames")

    val streamPreHandler = DebugHandlerBuilder<Stream>("wire.stream.pre")

    val streamHandler = DebugHandlerBuilder<Stream>("wire.stream")
}

class DebugHandlerBuilder<TChannel : P2PChannel>(var name: String) {
    val handlers = mutableListOf<ChannelVisitor<TChannel>>()

    fun addHandler(handler: ChannelVisitor<TChannel>) {
        handlers += handler
    }

    fun addNettyHandler(handler: ChannelHandler) {
        addHandler { it.pushHandler(handler) }
    }

    fun addLogger(level: LogLevel, loggerName: String = name) {
        addNettyHandler(LoggingHandler(loggerName, level))
    }

    fun addCompactLogger(level: LogLevel, loggerName: String = name) {
        addNettyHandler(LoggingHandlerShort(loggerName, level))
    }
}

open class Enumeration<T>(val values: MutableList<T> = mutableListOf()) : MutableList<T> by values {
    operator fun (T).unaryPlus() {
        values += this
    }
}
