namespace java com.twitter.example.thrift
namespace rb ParrotEcho

service EchoService {
    string echo(1: string message)
}
