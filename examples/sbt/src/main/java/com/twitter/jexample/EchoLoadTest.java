package com.twitter.jexample;

import com.twitter.example.thrift.EchoService;
import com.twitter.parrot.processor.ThriftLoadTest;
import com.twitter.parrot.server.ParrotRequest;
import com.twitter.parrot.server.ParrotService;
import com.twitter.parrot.thrift.ParrotJob;
import com.twitter.util.Future;
import com.twitter.util.FutureEventListener;
import org.apache.thrift.protocol.TBinaryProtocol;

import java.util.List;

public class EchoLoadTest extends ThriftLoadTest {
    EchoService.ServiceToClient client = null;

    public EchoLoadTest(ParrotService<ParrotRequest, byte[]> parrotService) {
	super(parrotService);
        client = new EchoService.ServiceToClient(service(), new TBinaryProtocol.Factory());
    }

    public void processLines(ParrotJob job, List<String> lines) {
        for(String line: lines) {
            Future<String> future = client.echo(line);
            future.addEventListener(new FutureEventListener<String>() {
                public void onSuccess(String msg) {
                    System.out.println("response: " + msg);
                }

                public void onFailure(Throwable cause) {
                    System.out.println("Error: " + cause);
                }
            });
        }
    }
}
