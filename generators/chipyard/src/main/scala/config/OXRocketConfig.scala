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



// Copied from hjkwon's chipyard. by swsok
///////////////////////////////////////////////////////////////////////////////////////////////////////
// ETRI Logic
///////////////////////////////////////////////////////////////////////////////////////////////////////
class WithOMNILogic extends Config(
    new chipyard.harness.WithOMNIAdapter ++                       // add hjkwon
    new chipyard.iobinders.WithOMNICells // Adding hjkwon
)

class OMNIConfig extends Config(
  new chipyard.config.WithL2TLBs(1024) ++  
  new omnixtendagentetri.WithOMNI(tl_dw=512, mbus_sel= true) ++  // Use OmniXtend Verilog, connect Tilelink
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=4, capacityKB=2048) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new WithOMNILogic ++
  new chipyard.config.AbstractConfig)
//
///////////////////////////////////////////////////////////////////////////////////////////////////////
// ETRI Logic
///////////////////////////////////////////////////////////////////////////////////////////////////////

