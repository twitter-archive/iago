namespace java com.twitter.parrot.thrift
namespace rb Parrot

enum ParrotState {
    STARTING,
    RUNNING,
    PAUSED,
    STOPPING,
    SHUTDOWN,
    UNKNOWN
}

struct ParrotStatus {
    1: optional i32 linesProcessed;
    2: optional ParrotState status;
    3: optional double requestsPerSecond;
    4: optional double queueDepth;
    5: optional i32 totalProcessed;
}

struct ParrotLog { // pager & tailer interface
	1: i64 offset;
	2: i32 length;
	3: string data;
}

service ParrotServerService {
    void setRate(1: i32 newRate),
    ParrotStatus sendRequest(1: list<string> lines),
    ParrotStatus getStatus(),
    void start(),
    oneway void shutdown(),
    void pause(),
    void resume(),
    string fetchThreadStacks(),
    ParrotLog getLog(1: i64 offset, 2: i32 length, 3: string fileName)
}
