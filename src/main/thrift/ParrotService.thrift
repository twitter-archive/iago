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

struct TargetHost {
    1: string scheme;
    2: string host;
    3: i32 port;
}

struct ParrotJob {
    1: string name;
    2: list<TargetHost> victims;
    3: map<string, string> headers;
    4: i32 arrivalRate;
    5: i32 concurrency;
    6: i32 rampPeriod;
    7: string processor;
    8: optional map<string, string> parserArgs;
    9: optional i64 created;
}

struct ParrotStatus {
    1: optional i32 linesProcessed;
    2: optional ParrotState status;
    3: optional double requestsPerSecond;
    4: optional double queueDepth;
    5: optional i32 totalProcessed;
    6: optional ParrotJob job;
}

struct ParrotJobRef {
    1: i32 jobId;
}

service ParrotServerService {
    ParrotJobRef createJob(1: ParrotJob job),
    void adjustRateForJob(1: string job, 2: i32 adjustment),
    ParrotStatus sendRequest(1: ParrotJobRef job, 2: list<string> lines),
    ParrotStatus getStatus(),
    void start(),
    oneway void shutdown(),
    void pause(),
    void resume(),
    string fetchThreadStacks()
}
