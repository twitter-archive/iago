namespace java com.twitter.parrot.thrift
namespace rb ParrotEcho

service EchoService {
    string echo(1: string message)
}
