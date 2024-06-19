package omnixtend

case class OmniXtendPacket(
  ethPreambleSFD: Array[Byte],  // 8 bytes
  destAddress: Array[Byte],     // 6 bytes
  srcAddress: Array[Byte],      // 6 bytes
  etherType: Array[Byte],       // 2 bytes
  tloeHeader: TLoEHeader,
  tileLinkMessages: Seq[TileLinkMessage],
  padding: Option[Array[Byte]],
  tloeFrameMask: Array[Byte],   // 8 bytes
  ethernetFCS: Array[Byte]      // 4 bytes
) {
  def toAXIStreamData(): Array[Byte] = {
    val headerData = ethPreambleSFD ++ destAddress ++ srcAddress ++ etherType
    val tloeHeaderData = tloeHeader.toBytes
    val tileLinkMessagesData = tileLinkMessages.flatMap(_.toBytes).toArray
    val paddingData = padding.getOrElse(Array.empty[Byte])
    headerData ++ tloeHeaderData ++ tileLinkMessagesData ++ paddingData ++ tloeFrameMask ++ ethernetFCS
  }

  def printPacket(): Unit = {
    println(s"Ethernet Preamble/SFD: ${ethPreambleSFD.map("%02X".format(_)).mkString(" ")}")
    println(s"Destination Address: ${destAddress.map("%02X".format(_)).mkString(" ")}")
    println(s"Source Address: ${srcAddress.map("%02X".format(_)).mkString(" ")}")
    println(s"EtherType: ${etherType.map("%02X".format(_)).mkString(" ")}")
    tloeHeader.printHeader()
    tileLinkMessages.zipWithIndex.foreach { case (msg, idx) =>
      println(s"TileLink Message ${idx + 1}:")
      msg.printMessage()
    }
    padding.foreach(p => println(s"Padding: ${p.map("%02X".format(_)).mkString(" ")}"))
    println(s"TLoE Frame Mask: ${tloeFrameMask.map("%02X".format(_)).mkString(" ")}")
    println(s"Ethernet FCS: ${ethernetFCS.map("%02X".format(_)).mkString(" ")}")
  }
}

