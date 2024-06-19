package chipyard

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.diplomacy.{AsynchronousCrossing}

// --------------
// OmniXtend Rocket Configs
// --------------

class OXRocketConfig extends Config(
  new omnixtend.WithOX(useAXI4=false, useBlackBox=false) ++ 
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++         // single rocket-core
  new chipyard.config.AbstractConfig)

