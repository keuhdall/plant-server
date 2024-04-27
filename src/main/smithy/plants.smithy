$version: "2"

namespace plants

use alloy#simpleRestJson

@simpleRestJson
service PlantsService {
  version: "1.0.0",
  operations: [Health, Ping]
}

@http(method: "POST", uri: "/api/health", code: 200)
operation Health {
  input: Plant
}

@http(method: "POST", uri: "/api/ping", code: 200)
operation Ping {
  input: Message
  output: Message
}

structure Plant {
  @required id: Integer
  @required noWater: Boolean
}

structure Message {
  @required message: String
}