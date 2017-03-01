package cjp.payments.util

import java.security.MessageDigest
import org.apache.commons.codec.binary.Hex


class WorldPayMacHelper() {

  def checkMac(orderKey: String,
               paymentAmount: String,
               paymentCurrency: String,
               paymentStatus: String,
               mac: String,
               macSecret:String): Boolean = {
    mac == createMac(orderKey, paymentAmount, paymentCurrency, paymentStatus, macSecret)
  }

  def createMac(orderKey: String,
                paymentAmount: String,
                paymentCurrency: String,
                paymentStatus: String,
                macSecret:String): String = {
    val msg = orderKey + paymentAmount + paymentCurrency + paymentStatus + macSecret
    val digest = MessageDigest.getInstance("MD5").digest(msg.getBytes)
    new String(Hex.encodeHex(digest))
  }

}
