package com.ieps.crawler.utils

import java.security.MessageDigest

object HashGenerator {

  private def generate(hashType: String, content: String): String = {
    val arr: Array[Byte] = content.getBytes()
    val checksum: Array[Byte] = MessageDigest.getInstance(hashType) digest arr
    checksum.map("%02X" format _).mkString
  }

  def generateSHA256(content: String): Option[String] = {
    Some(generate("SHA-256", content))
  }
}
