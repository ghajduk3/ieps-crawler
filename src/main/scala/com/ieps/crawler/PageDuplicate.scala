package com.ieps.crawler
import com.typesafe.scalalogging.StrictLogging
import info.debatty.java.stringsimilarity.ShingleBased
import info.debatty.java.lsh
import info.debatty.java.lsh.MinHash
import info.debatty.java.lsh.LSHMinHash
import scala.collection.JavaConverters._
class PageDuplicate(textFromHtm:String) extends StrictLogging{
  var notProcessedText: String = textFromHtm

  def preProcessingText: String = {
    val processedText = notProcessedText.toLowerCase.replaceAll("""[\p{Punct}&&[^.]]""", "").trim.replaceAll(" +", " ")
    processedText
  }

  //Shingling in Scala
  def shingling = {
    var shingles: Vector[Int] = Vector()
    var shingle: Int = 0
    val content: String = preProcessingText
    var i: Int = 0
    val K: Int = 5

    while ( {
      i <= content.length - K
    }) {
      shingle = content.substring(i, i + K).hashCode
      if (!shingles.contains(shingle)) {
        shingles = shingles :+ shingle
      }

      {
        i +=1

      }
    }
  shingles.toSet

  }
  val javaSet = shingling.map(new Integer(_)).asJava
  val numOfBins = 10
  val sizeOfvector = shingling.size
  val stages = 4
  var instminhash = new MinHash(4,sizeOfvector)
  val signature = instminhash.signature(javaSet)







}
