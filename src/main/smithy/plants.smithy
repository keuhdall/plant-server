$version: "2"

namespace plants

use alloy#simpleRestJson

@simpleRestJson
service PlantsService {
  version: "1.0.0",
  operations: [Health]
}

@http(method: "POST", uri: "/api/health", code: 200)
operation Health {
  input: Plant
}

structure Plant {
  @required
  id: Integer
  @required
  waterLevel: Integer
}