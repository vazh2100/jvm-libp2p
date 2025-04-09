package io.libp2p.transport.quic;

import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.core.Stream;
import io.libp2p.core.StreamPromise;
import io.libp2p.core.crypto.*;
import io.libp2p.core.dsl.*;
import io.libp2p.core.multiformats.*;
import io.libp2p.protocol.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import kotlin.*;
import org.junit.jupiter.api.*;

public class QuicKuboTestJava {
  @Test
  //    @Disabled
  void pingKubo() throws Exception {
    if (System.getProperty("os.name").toLowerCase().startsWith("windows")) return;
    PeerId peerId = PeerId.fromBase58(getKuboPeerId());

    Host clientHost =
        new HostBuilder()
            //                .secureTransport(QuicTransport::Ed25519)
            .keyType(KeyType.ED25519)
            .secureTransport(QuicTransport::Ecdsa)
            .build();

    CompletableFuture<Void> clientStarted = clientHost.start();
    clientStarted.get(5, TimeUnit.SECONDS);
    System.out.println("Client started");

    Assertions.assertEquals(0, clientHost.listenAddresses().size());

    StreamPromise<PingController> ping =
        clientHost
            .getNetwork()
            .connect(peerId, new Multiaddr("/ip4/127.0.0.1/udp/4001/quic-v1"))
            .thenApply(it -> it.muxerSession().createStream(new Ping()))
            .get(5, TimeUnit.SECONDS);

    Stream pingStream = ping.getStream().get(5, TimeUnit.SECONDS);
    System.out.println("Ping stream created");
    PingController pingCtr = ping.getController().get(5, TimeUnit.SECONDS);
    System.out.println("Ping controller created");

    for (int i = 0; i < 1000; i++) {
      long latency = pingCtr.ping().get(1, TimeUnit.SECONDS);
      System.out.println("Ping is " + latency);
    }
    pingStream.close().get(5, TimeUnit.SECONDS);
    System.out.println("Ping stream closed");

    Assertions.assertThrows(
        ExecutionException.class, () -> pingCtr.ping().get(5, TimeUnit.SECONDS));

    clientHost.stop().get(5, TimeUnit.SECONDS);
    System.out.println("Client stopped");
  }

  private static String getKuboPeerId() throws IOException, URISyntaxException {
    String url = "http://localhost:5001/api/v0/id";
    HttpURLConnection conn = (HttpURLConnection) new URI(url).toURL().openConnection();
    conn.setConnectTimeout(1_000);
    conn.setDoInput(true);
    conn.setDoOutput(true);

    DataOutputStream dout = new DataOutputStream(conn.getOutputStream());

    dout.write(new byte[0]);
    dout.flush();

    DataInputStream din = new DataInputStream(conn.getInputStream());
    String resp = new String(din.readAllBytes());
    din.close();
    int start = resp.indexOf("ID") + 5;
    int end = resp.indexOf("\"", start);
    return resp.substring(start, end);
  }

  @Test
  void keyPairGeneration() {
    Pair<PrivKey, PubKey> pair = KeyKt.generateKeyPair(KeyType.SECP256K1);
    PeerId peerId = PeerId.fromPubKey(pair.component2());
    System.out.println("PeerId: " + peerId.toHex());
  }
}
